package com.example.data.demo

import com.example.data.local.AppDatabase
import com.example.data.model.CustomerEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.ProductEntity
import com.example.data.model.SaleEntity
import com.example.data.model.SaleItemEntity
import com.example.data.model.StockMovementEntity
import java.util.Calendar
import java.util.UUID

class DemoDataSeeder(private val db: AppDatabase) {

    suspend fun seedDemoData(businessId: String) {
        val now = Calendar.getInstance()

        // 1. Demo Products
        val arroz = ProductEntity(
            id = UUID.randomUUID().toString(),
            businessId = businessId,
            name = "Arroz Tipo 1 5kg",
            category = "Alimentos",
            code = "789123456",
            salePrice = 28.90,
            costPrice = 19.50,
            currentStock = 45.0,
            minStock = 10.0,
            isDemo = true
        )
        val feijao = ProductEntity(
            id = UUID.randomUUID().toString(),
            businessId = businessId,
            name = "Feijão Carioca 1kg",
            category = "Alimentos",
            code = "789123457",
            salePrice = 8.50,
            costPrice = 5.20,
            currentStock = 8.0, // Low stock on purpose to test visual alert!
            minStock = 12.0,
            isDemo = true
        )
        val refrigerante = ProductEntity(
            id = UUID.randomUUID().toString(),
            businessId = businessId,
            name = "Refrigerante Cola 2L",
            category = "Bebidas",
            code = "789123458",
            salePrice = 9.90,
            costPrice = 6.00,
            currentStock = 60.0,
            minStock = 15.0,
            isDemo = true
        )
        val agua = ProductEntity(
            id = UUID.randomUUID().toString(),
            businessId = businessId,
            name = "Água Mineral 500ml",
            category = "Bebidas",
            code = "789123459",
            salePrice = 3.00,
            costPrice = 1.20,
            currentStock = 120.0,
            minStock = 30.0,
            isDemo = true
        )

        db.productDao().insertProduct(arroz)
        db.productDao().insertProduct(feijao)
        db.productDao().insertProduct(refrigerante)
        db.productDao().insertProduct(agua)

        // 2. Demo Customers
        val cliente1 = CustomerEntity(
            id = UUID.randomUUID().toString(),
            businessId = businessId,
            name = "Maria Silva",
            phone = "(11) 98765-4321",
            email = "maria.silva@email.com",
            observation = "Cliente frequente, prefere pagamento via Pix",
            isDemo = true
        )
        val cliente2 = CustomerEntity(
            id = UUID.randomUUID().toString(),
            businessId = businessId,
            name = "João Santos",
            phone = "(11) 97654-3210",
            email = "joao.santos@email.com",
            observation = "Compra semanal",
            isDemo = true
        )
        db.customerDao().insertCustomer(cliente1)
        db.customerDao().insertCustomer(cliente2)

        // 3. Demo Sales for the past 5 days (to test dashboard & charts)
        val cal = Calendar.getInstance()

        // Today
        insertSaleHelper(
            businessId = businessId,
            date = cal.timeInMillis,
            customer = cliente1,
            payment = "Pix",
            discount = 0.0,
            items = listOf(
                Pair(arroz, 2.0),
                Pair(refrigerante, 1.0)
            )
        )
        insertSaleHelper(
            businessId = businessId,
            date = cal.timeInMillis,
            customer = null,
            payment = "Dinheiro",
            discount = 1.0,
            items = listOf(
                Pair(agua, 3.0),
                Pair(refrigerante, 2.0)
            )
        )

        // Yesterday
        cal.add(Calendar.DAY_OF_YEAR, -1)
        insertSaleHelper(
            businessId = businessId,
            date = cal.timeInMillis,
            customer = cliente2,
            payment = "Cartão",
            discount = 0.0,
            items = listOf(
                Pair(arroz, 3.0),
                Pair(feijao, 2.0)
            )
        )

        // 2 days ago
        cal.add(Calendar.DAY_OF_YEAR, -1)
        insertSaleHelper(
            businessId = businessId,
            date = cal.timeInMillis,
            customer = null,
            payment = "Pix",
            discount = 2.0,
            items = listOf(
                Pair(refrigerante, 4.0),
                Pair(agua, 6.0)
            )
        )

        // 3 days ago
        cal.add(Calendar.DAY_OF_YEAR, -1)
        insertSaleHelper(
            businessId = businessId,
            date = cal.timeInMillis,
            customer = cliente1,
            payment = "Dinheiro",
            discount = 0.0,
            items = listOf(
                Pair(arroz, 1.0),
                Pair(feijao, 3.0),
                Pair(agua, 2.0)
            )
        )

        // 4. Demo Expenses
        val expCal = Calendar.getInstance()
        // Today expense
        db.expenseDao().insertExpense(
            ExpenseEntity(
                businessId = businessId,
                description = "Reposição de Sacolas Plásticas",
                category = "Compra",
                amount = 45.0,
                date = expCal.timeInMillis,
                observation = "Pacote com 500 unidades",
                isDemo = true
            )
        )
        // Yesterday expense
        expCal.add(Calendar.DAY_OF_YEAR, -1)
        db.expenseDao().insertExpense(
            ExpenseEntity(
                businessId = businessId,
                description = "Conta de Energia Elétrica",
                category = "Energia",
                amount = 180.50,
                date = expCal.timeInMillis,
                observation = "Referente ao mês atual",
                isDemo = true
            )
        )
        // 3 days ago expense
        expCal.add(Calendar.DAY_OF_YEAR, -2)
        db.expenseDao().insertExpense(
            ExpenseEntity(
                businessId = businessId,
                description = "Plano de Internet Fibra",
                category = "Internet",
                amount = 99.90,
                date = expCal.timeInMillis,
                observation = "Conexão da loja",
                isDemo = true
            )
        )
    }

    private suspend fun insertSaleHelper(
        businessId: String,
        date: Long,
        customer: CustomerEntity?,
        payment: String,
        discount: Double,
        items: List<Pair<ProductEntity, Double>>
    ) {
        val saleId = UUID.randomUUID().toString()
        val saleItems = items.map { (prod, qty) ->
            SaleItemEntity(
                id = UUID.randomUUID().toString(),
                saleId = saleId,
                productId = prod.id,
                productName = prod.name,
                quantity = qty,
                unitPrice = prod.salePrice,
                unitCost = prod.costPrice,
                subtotal = prod.salePrice * qty
            )
        }
        val gross = saleItems.sumOf { it.subtotal }
        val net = (gross - discount).coerceAtLeast(0.0)

        val sale = SaleEntity(
            id = saleId,
            businessId = businessId,
            date = date,
            totalAmount = net,
            discountAmount = discount,
            paymentMethod = payment,
            customerId = customer?.id,
            customerName = customer?.name,
            isDemo = true
        )

        db.saleDao().insertSale(sale)
        db.saleDao().insertSaleItems(saleItems)

        for (item in saleItems) {
            db.stockMovementDao().insertMovement(
                StockMovementEntity(
                    businessId = businessId,
                    productId = item.productId,
                    productName = item.productName,
                    type = "EXIT",
                    quantity = item.quantity,
                    previousStock = 50.0,
                    newStock = 50.0 - item.quantity,
                    date = date,
                    reason = "Venda #${saleId.take(6).uppercase()}",
                    isDemo = true
                )
            )
        }
    }

    suspend fun clearDemoData(businessId: String) {
        db.productDao().deleteDemoProducts(businessId)
        db.saleDao().deleteDemoSales(businessId)
        db.expenseDao().deleteDemoExpenses(businessId)
        db.stockMovementDao().deleteDemoMovements(businessId)
        db.customerDao().deleteDemoCustomers(businessId)
    }
}
