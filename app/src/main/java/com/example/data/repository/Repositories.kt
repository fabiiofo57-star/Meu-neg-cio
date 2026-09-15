package com.example.data.repository

import com.example.data.local.BusinessDao
import com.example.data.local.CustomerDao
import com.example.data.local.ExpenseDao
import com.example.data.local.PaymentDao
import com.example.data.local.ProductDao
import com.example.data.local.SaleDao
import com.example.data.local.StockMovementDao
import com.example.data.model.BusinessEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.PaymentEntity
import com.example.data.model.ProductEntity
import com.example.data.model.SaleEntity
import com.example.data.model.SaleItemEntity
import com.example.data.model.SaleWithItems
import com.example.data.model.StockMovementEntity
import com.example.data.sync.FirestoreSyncHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class BusinessRepository(
    private val businessDao: BusinessDao,
    private val syncHelper: FirestoreSyncHelper
) {
    fun getBusinessByUserId(userId: String): Flow<BusinessEntity?> =
        businessDao.getBusinessByUserId(userId)

    fun getAnyBusiness(): Flow<BusinessEntity?> =
        businessDao.getAnyBusiness()

    fun getBusinessById(id: String): Flow<BusinessEntity?> =
        businessDao.getBusinessById(id)

    suspend fun findBusinessByUserId(userId: String): BusinessEntity? =
        businessDao.findBusinessByUserId(userId)

    suspend fun findBusinessByUserEmail(email: String): BusinessEntity? =
        businessDao.findBusinessByUserEmail(email)

    fun getBusinessByUserEmail(email: String): Flow<BusinessEntity?> =
        businessDao.getBusinessByUserEmail(email)

    suspend fun findAnyBusiness(): BusinessEntity? =
        businessDao.findAnyBusiness()

    suspend fun getBusinessCount(): Int =
        businessDao.getBusinessCount()

    suspend fun saveBusiness(business: BusinessEntity) {
        businessDao.insertBusiness(business)
        syncHelper.syncBusiness(business)
    }

    suspend fun updateBusiness(business: BusinessEntity) {
        businessDao.insertBusiness(business)
        syncHelper.syncBusiness(business)
    }
}

class ProductRepository(
    private val productDao: ProductDao,
    private val stockMovementDao: StockMovementDao,
    private val syncHelper: FirestoreSyncHelper
) {
    fun getProducts(businessId: String): Flow<List<ProductEntity>> =
        productDao.getProductsByBusiness(businessId)

    fun getProductById(id: String): Flow<ProductEntity?> =
        productDao.getProductById(id)

    suspend fun insertProduct(product: ProductEntity) {
        productDao.insertProduct(product)
        syncHelper.syncProduct(product)
        if (product.currentStock > 0) {
            val mov = StockMovementEntity(
                businessId = product.businessId,
                productId = product.id,
                productName = product.name,
                type = "ENTRY",
                quantity = product.currentStock,
                previousStock = 0.0,
                newStock = product.currentStock,
                reason = "Estoque inicial",
                isDemo = product.isDemo
            )
            stockMovementDao.insertMovement(mov)
            syncHelper.syncStockMovement(mov)
        }
    }

    suspend fun updateProduct(product: ProductEntity) {
        productDao.updateProduct(product)
        syncHelper.syncProduct(product)
    }

    suspend fun deleteProduct(product: ProductEntity) {
        productDao.deleteProductById(product.id)
        syncHelper.deleteProductFromCloud(product.id)
    }

    suspend fun adjustStock(
        businessId: String,
        productId: String,
        type: String, // "ENTRY", "EXIT", "ADJUSTMENT"
        quantity: Double,
        reason: String,
        allowNegative: Boolean = false
    ): Result<Double> {
        val product = productDao.getProductById(productId).firstOrNull()
            ?: return Result.failure(Exception("Produto não encontrado"))

        val current = product.currentStock
        val newStock = when (type) {
            "ENTRY" -> current + quantity
            "EXIT" -> current - quantity
            "ADJUSTMENT" -> quantity
            else -> current
        }

        if (newStock < 0 && !allowNegative) {
            return Result.failure(Exception("Estoque não pode ficar negativo sem confirmação."))
        }

        val updatedProduct = product.copy(currentStock = newStock, updatedAt = System.currentTimeMillis())
        productDao.updateStock(productId, newStock)
        syncHelper.syncProduct(updatedProduct)

        val movement = StockMovementEntity(
            businessId = businessId,
            productId = productId,
            productName = product.name,
            type = type,
            quantity = if (type == "ADJUSTMENT") kotlin.math.abs(newStock - current) else quantity,
            previousStock = current,
            newStock = newStock,
            reason = reason.ifBlank { "Ajuste manual" },
            isDemo = product.isDemo
        )
        stockMovementDao.insertMovement(movement)
        syncHelper.syncStockMovement(movement)

        return Result.success(newStock)
    }
}

class SaleRepository(
    private val saleDao: SaleDao,
    private val productDao: ProductDao,
    private val stockMovementDao: StockMovementDao,
    private val paymentDao: PaymentDao,
    private val syncHelper: FirestoreSyncHelper
) {
    fun getSales(businessId: String): Flow<List<SaleWithItems>> =
        saleDao.getSalesByBusiness(businessId)

    fun getSalesBetween(businessId: String, start: Long, end: Long): Flow<List<SaleWithItems>> =
        saleDao.getSalesBetweenDates(businessId, start, end)

    suspend fun registerSale(
        businessId: String,
        items: List<SaleItemEntity>,
        discount: Double,
        paymentMethod: String,
        date: Long = System.currentTimeMillis(),
        observation: String? = null,
        customerId: String? = null,
        customerName: String? = null,
        isDemo: Boolean = false,
        paidAmount: Double = 0.0,
        dueDate: Long? = null
    ): SaleEntity {
        val grossTotal = items.sumOf { it.subtotal }
        val netTotal = (grossTotal - discount).coerceAtLeast(0.0)

        val isCreditSale = paymentMethod.equals("A Prazo", ignoreCase = true)
        val initialPaid = if (isCreditSale) {
            paidAmount.coerceIn(0.0, netTotal)
        } else {
            netTotal
        }
        val computedStatus = if (netTotal - initialPaid <= 0.005) {
            "PAGO"
        } else if (initialPaid > 0.005) {
            "PAGO_PARCIAL"
        } else {
            "PENDENTE"
        }

        val saleId = UUID.randomUUID().toString()
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val horaStr = timeFormatter.format(Date(date))

        val sale = SaleEntity(
            id = saleId,
            businessId = businessId,
            date = date,
            totalAmount = netTotal,
            discountAmount = discount,
            paymentMethod = paymentMethod,
            observation = observation,
            customerId = customerId,
            customerName = customerName,
            isDemo = isDemo,
            paidAmount = initialPaid,
            dueDate = if (isCreditSale) dueDate else null,
            paymentStatus = computedStatus,
            timeString = horaStr,
            updatedAt = System.currentTimeMillis()
        )

        val itemsWithSaleId = items.map { it.copy(id = UUID.randomUUID().toString(), saleId = saleId) }

        saleDao.insertSale(sale)
        saleDao.insertSaleItems(itemsWithSaleId)

        // Se houve pagamento inicial, registra no histórico de pagamentos
        if (initialPaid > 0) {
            val payment = PaymentEntity(
                id = UUID.randomUUID().toString(),
                businessId = businessId,
                saleId = saleId,
                customerId = customerId,
                customerName = customerName,
                amount = initialPaid,
                paymentMethod = paymentMethod,
                date = date,
                timeString = horaStr,
                isDemo = isDemo
            )
            paymentDao.insertPayment(payment)
            syncHelper.syncPayment(payment)
        }

        // Diminui o estoque e registra movimentação para cada item vendido
        for (item in itemsWithSaleId) {
            val product = productDao.getProductById(item.productId).firstOrNull()
            if (product != null) {
                val previous = product.currentStock
                val newStock = previous - item.quantity
                productDao.updateStock(item.productId, newStock)
                val updatedProduct = product.copy(currentStock = newStock, updatedAt = System.currentTimeMillis())
                syncHelper.syncProduct(updatedProduct)

                val movement = StockMovementEntity(
                    businessId = businessId,
                    productId = item.productId,
                    productName = item.productName,
                    type = "EXIT",
                    quantity = item.quantity,
                    previousStock = previous,
                    newStock = newStock,
                    date = date,
                    reason = "Venda #${saleId.take(6).uppercase()}",
                    isDemo = isDemo
                )
                stockMovementDao.insertMovement(movement)
                syncHelper.syncStockMovement(movement)
            }
        }

        // Sincroniza a venda com Firestore
        syncHelper.syncSale(sale, itemsWithSaleId)

        return sale
    }

    suspend fun updateSale(sale: SaleEntity) {
        saleDao.updateSale(sale)
        syncHelper.syncSale(sale, emptyList())
    }

    suspend fun recordPartialPayment(
        sale: SaleEntity,
        additionalPayment: Double,
        paymentMethod: String = sale.paymentMethod
    ): SaleEntity {
        val newPaid = (sale.paidAmount + additionalPayment).coerceAtMost(sale.totalAmount)
        val remaining = (sale.totalAmount - newPaid).coerceAtLeast(0.0)
        val newStatus = if (remaining <= 0.005) {
            "PAGO"
        } else {
            "PAGO_PARCIAL"
        }
        val updated = sale.copy(
            paidAmount = newPaid,
            paymentStatus = newStatus,
            updatedAt = System.currentTimeMillis()
        )
        saleDao.updateSale(updated)

        // Registra histórico do pagamento parcial
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val horaStr = timeFormatter.format(Date())
        val payment = PaymentEntity(
            id = UUID.randomUUID().toString(),
            businessId = sale.businessId,
            saleId = sale.id,
            customerId = sale.customerId,
            customerName = sale.customerName,
            amount = additionalPayment,
            paymentMethod = paymentMethod,
            date = System.currentTimeMillis(),
            timeString = horaStr,
            isDemo = sale.isDemo
        )
        paymentDao.insertPayment(payment)
        syncHelper.syncPayment(payment)
        syncHelper.syncSale(updated, emptyList())

        return updated
    }

    /**
     * Quando uma venda for cancelada: restaura o estoque correspondente e remove do banco e da nuvem.
     */
    suspend fun deleteSale(saleWithItems: SaleWithItems) {
        val sale = saleWithItems.sale
        val items = saleWithItems.items

        // Restaura estoque
        for (item in items) {
            val product = productDao.getProductByIdDirect(item.productId)
                ?: productDao.getProductById(item.productId).firstOrNull()
            if (product != null) {
                val previous = product.currentStock
                val restoredStock = previous + item.quantity
                productDao.updateStock(item.productId, restoredStock)
                val updatedProduct = product.copy(currentStock = restoredStock, updatedAt = System.currentTimeMillis())
                syncHelper.syncProduct(updatedProduct)

                val movement = StockMovementEntity(
                    businessId = sale.businessId,
                    productId = item.productId,
                    productName = item.productName,
                    type = "ENTRY",
                    quantity = item.quantity,
                    previousStock = previous,
                    newStock = restoredStock,
                    date = System.currentTimeMillis(),
                    reason = "Cancelamento da venda #${sale.id.take(6).uppercase()}",
                    isDemo = sale.isDemo
                )
                stockMovementDao.insertMovement(movement)
                syncHelper.syncStockMovement(movement)
            }
        }

        saleDao.deleteSaleItemsBySaleId(sale.id)
        paymentDao.deletePaymentsBySaleId(sale.id)
        saleDao.deleteSaleById(sale.id)
        syncHelper.deleteSaleFromCloud(sale.id)
    }
}

class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val syncHelper: FirestoreSyncHelper
) {
    fun getExpenses(businessId: String): Flow<List<ExpenseEntity>> =
        expenseDao.getExpensesByBusiness(businessId)

    fun getExpensesBetween(businessId: String, start: Long, end: Long): Flow<List<ExpenseEntity>> =
        expenseDao.getExpensesBetweenDates(businessId, start, end)

    suspend fun insertExpense(expense: ExpenseEntity) {
        expenseDao.insertExpense(expense)
        syncHelper.syncExpense(expense)
    }

    suspend fun updateExpense(expense: ExpenseEntity) {
        expenseDao.updateExpense(expense)
        syncHelper.syncExpense(expense)
    }

    suspend fun deleteExpense(expense: ExpenseEntity) {
        expenseDao.deleteExpenseById(expense.id)
        expenseDao.deleteExpense(expense)
        syncHelper.deleteExpenseFromCloud(expense.id)
    }
}

class CustomerRepository(
    private val customerDao: CustomerDao,
    private val syncHelper: FirestoreSyncHelper
) {
    fun getCustomers(businessId: String): Flow<List<CustomerEntity>> =
        customerDao.getCustomersByBusiness(businessId)

    suspend fun insertCustomer(customer: CustomerEntity) {
        customerDao.insertCustomer(customer)
        syncHelper.syncCustomer(customer)
    }

    suspend fun updateCustomer(customer: CustomerEntity) {
        customerDao.updateCustomer(customer)
        syncHelper.syncCustomer(customer)
    }

    suspend fun deleteCustomer(customer: CustomerEntity) {
        customerDao.deleteCustomerById(customer.id)
        customerDao.deleteCustomer(customer)
        syncHelper.deleteCustomerFromCloud(customer.id)
    }
}

class PaymentRepository(
    private val paymentDao: PaymentDao,
    private val syncHelper: FirestoreSyncHelper
) {
    fun getPayments(businessId: String): Flow<List<PaymentEntity>> =
        paymentDao.getPaymentsByBusiness(businessId)

    fun getPaymentsBySale(saleId: String): Flow<List<PaymentEntity>> =
        paymentDao.getPaymentsBySale(saleId)

    suspend fun insertPayment(payment: PaymentEntity) {
        paymentDao.insertPayment(payment)
        syncHelper.syncPayment(payment)
    }
}

class StockMovementRepository(private val stockMovementDao: StockMovementDao) {
    fun getMovements(businessId: String): Flow<List<StockMovementEntity>> =
        stockMovementDao.getMovementsByBusiness(businessId)

    fun getMovementsByProduct(productId: String): Flow<List<StockMovementEntity>> =
        stockMovementDao.getMovementsByProduct(productId)
}
