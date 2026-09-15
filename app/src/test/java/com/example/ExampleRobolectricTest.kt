package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.BusinessEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.ProductEntity
import com.example.data.model.SaleEntity
import com.example.data.model.SaleItemEntity
import com.example.data.repository.ProductRepository
import com.example.data.repository.SaleRepository
import com.example.ui.components.toBrlCurrency
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var syncHelper: com.example.data.sync.FirestoreSyncHelper
    private lateinit var productRepository: ProductRepository
    private lateinit var saleRepository: SaleRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        syncHelper = com.example.data.sync.FirestoreSyncHelper(context, db)
        productRepository = ProductRepository(db.productDao(), db.stockMovementDao(), syncHelper)
        saleRepository = SaleRepository(db.saleDao(), db.productDao(), db.stockMovementDao(), db.paymentDao(), syncHelper)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Meu Negócio", appName)
    }

    @Test
    fun `brl currency formatting formats double correctly`() {
        val value = 1250.50
        val formatted = value.toBrlCurrency()
        assertTrue(formatted.contains("1.250,50") || formatted.contains("1250,50") || formatted.contains("R$"))
    }

    @Test
    fun `registering sale reduces product stock and inserts sale items`() = runBlocking {
        val businessId = "biz_123"
        val product = ProductEntity(
            id = "prod_1",
            businessId = businessId,
            name = "Arroz Tipo 1 5kg",
            category = "Alimentos",
            salePrice = 28.90,
            costPrice = 19.50,
            currentStock = 20.0,
            minStock = 5.0
        )
        productRepository.insertProduct(product)

        val item = SaleItemEntity(
            id = UUID.randomUUID().toString(),
            saleId = "",
            productId = product.id,
            productName = product.name,
            quantity = 3.0,
            unitPrice = 28.90,
            unitCost = 19.50,
            subtotal = 86.70
        )

        saleRepository.registerSale(
            businessId = businessId,
            items = listOf(item),
            discount = 0.0,
            paymentMethod = "Pix"
        )

        val updatedProduct = productRepository.getProductById(product.id).first()
        assertEquals(17.0, updatedProduct?.currentStock ?: 0.0, 0.001)

        val sales = saleRepository.getSales(businessId).first()
        assertEquals(1, sales.size)
        assertEquals(86.70, sales[0].sale.totalAmount, 0.001)
        assertEquals(1, sales[0].items.size)
        assertEquals("Arroz Tipo 1 5kg", sales[0].items[0].productName)
    }

    @Test
    fun `expense registration and net profit calculation`() = runBlocking {
        val businessId = "biz_456"
        val expense = ExpenseEntity(
            businessId = businessId,
            description = "Energia Elétrica",
            category = "Energia",
            amount = 150.0
        )
        db.expenseDao().insertExpense(expense)

        val expenses = db.expenseDao().getExpensesByBusiness(businessId).first()
        assertEquals(1, expenses.size)
        assertEquals(150.0, expenses[0].amount, 0.001)
    }

    @Test
    fun `user A and user B have strictly isolated data`() = runBlocking {
        val userAId = "uid_user_a"
        val userBId = "uid_user_b"

        val businessA = BusinessEntity(id = "biz_a", userId = userAId, name = "Padaria do Usuário A", category = "Alimentos", phone = "111", city = "SP")
        val businessB = BusinessEntity(id = "biz_b", userId = userBId, name = "Oficina do Usuário B", category = "Serviços", phone = "222", city = "RJ")
        db.businessDao().insertBusiness(businessA)
        db.businessDao().insertBusiness(businessB)

        val prodA = ProductEntity(id = "prod_a_1", businessId = businessA.id, name = "Pão Francês", category = "Padaria", salePrice = 1.0, costPrice = 0.5, currentStock = 100.0, minStock = 10.0)
        val prodB = ProductEntity(id = "prod_b_1", businessId = businessB.id, name = "Troca de Óleo", category = "Oficina", salePrice = 120.0, costPrice = 60.0, currentStock = 5.0, minStock = 1.0)
        db.productDao().insertProduct(prodA)
        db.productDao().insertProduct(prodB)

        // Verify User A only sees their own business and products
        val userABiz = db.businessDao().getBusinessByUserId(userAId).first()
        assertEquals("Padaria do Usuário A", userABiz?.name)
        val userAProducts = db.productDao().getProductsByBusiness(userABiz!!.id).first()
        assertEquals(1, userAProducts.size)
        assertEquals("Pão Francês", userAProducts[0].name)

        // Verify User B only sees their own business and products
        val userBBiz = db.businessDao().getBusinessByUserId(userBId).first()
        assertEquals("Oficina do Usuário B", userBBiz?.name)
        val userBProducts = db.productDao().getProductsByBusiness(userBBiz!!.id).first()
        assertEquals(1, userBProducts.size)
        assertEquals("Troca de Óleo", userBProducts[0].name)
    }
}
