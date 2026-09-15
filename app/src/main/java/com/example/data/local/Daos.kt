package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.BusinessEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.ProductEntity
import com.example.data.model.SaleEntity
import com.example.data.model.SaleItemEntity
import com.example.data.model.SaleWithItems
import com.example.data.model.StockMovementEntity
import com.example.data.model.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    fun getUserById(id: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun findUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun findUserById(id: String): UserEntity?

    @Query("SELECT * FROM users ORDER BY name ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)
}

@Dao
interface BusinessDao {
    @Query("SELECT * FROM businesses WHERE userId = :userId LIMIT 1")
    fun getBusinessByUserId(userId: String): Flow<BusinessEntity?>

    @Query("SELECT * FROM businesses WHERE userId = :userId LIMIT 1")
    suspend fun findBusinessByUserId(userId: String): BusinessEntity?

    @Query("""
        SELECT b.* FROM businesses b 
        LEFT JOIN users u ON b.userId = u.id 
        WHERE LOWER(u.email) = LOWER(:email) 
           OR LOWER(b.email) = LOWER(:email) 
           OR b.userId = :email 
           OR LOWER(b.userId) = LOWER(:email)
           OR b.userId IN (SELECT id FROM users WHERE LOWER(email) = LOWER(:email))
        LIMIT 1
    """)
    suspend fun findBusinessByUserEmail(email: String): BusinessEntity?

    @Query("""
        SELECT b.* FROM businesses b 
        LEFT JOIN users u ON b.userId = u.id 
        WHERE LOWER(u.email) = LOWER(:email) 
           OR LOWER(b.email) = LOWER(:email) 
           OR b.userId = :email 
           OR LOWER(b.userId) = LOWER(:email)
           OR b.userId IN (SELECT id FROM users WHERE LOWER(email) = LOWER(:email))
        LIMIT 1
    """)
    fun getBusinessByUserEmail(email: String): Flow<BusinessEntity?>

    @Query("SELECT * FROM businesses ORDER BY createdAt DESC LIMIT 1")
    fun getAnyBusiness(): Flow<BusinessEntity?>

    @Query("SELECT * FROM businesses ORDER BY createdAt DESC LIMIT 1")
    suspend fun findAnyBusiness(): BusinessEntity?

    @Query("SELECT * FROM businesses WHERE id = :id LIMIT 1")
    fun getBusinessById(id: String): Flow<BusinessEntity?>

    @Query("SELECT COUNT(*) FROM businesses")
    suspend fun getBusinessCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBusiness(business: BusinessEntity)

    @Update
    suspend fun updateBusiness(business: BusinessEntity)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE businessId = :businessId ORDER BY name ASC")
    fun getProductsByBusiness(businessId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    fun getProductById(id: String): Flow<ProductEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProductById(id: String)

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductByIdDirect(id: String): ProductEntity?

    @Query("UPDATE products SET currentStock = :newStock WHERE id = :productId")
    suspend fun updateStock(productId: String, newStock: Double)

    @Query("DELETE FROM products WHERE businessId = :businessId AND isDemo = 1")
    suspend fun deleteDemoProducts(businessId: String)
}

@Dao
interface SaleDao {
    @Transaction
    @Query("SELECT * FROM sales WHERE businessId = :businessId ORDER BY date DESC")
    fun getSalesByBusiness(businessId: String): Flow<List<SaleWithItems>>

    @Transaction
    @Query("SELECT * FROM sales WHERE businessId = :businessId AND date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getSalesBetweenDates(businessId: String, startDate: Long, endDate: Long): Flow<List<SaleWithItems>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleEntity)

    @Update
    suspend fun updateSale(sale: SaleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItems(items: List<SaleItemEntity>)

    @Delete
    suspend fun deleteSale(sale: SaleEntity)

    @Query("DELETE FROM sales WHERE id = :saleId")
    suspend fun deleteSaleById(saleId: String)

    @Query("DELETE FROM sale_items WHERE saleId = :saleId")
    suspend fun deleteSaleItemsBySaleId(saleId: String)

    @Query("DELETE FROM sales WHERE businessId = :businessId AND isDemo = 1")
    suspend fun deleteDemoSales(businessId: String)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE businessId = :businessId ORDER BY date DESC")
    fun getExpensesByBusiness(businessId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE businessId = :businessId AND date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getExpensesBetweenDates(businessId: String, startDate: Long, endDate: Long): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: String)

    @Query("DELETE FROM expenses WHERE businessId = :businessId AND isDemo = 1")
    suspend fun deleteDemoExpenses(businessId: String)
}

@Dao
interface StockMovementDao {
    @Query("SELECT * FROM stock_movements WHERE businessId = :businessId ORDER BY date DESC")
    fun getMovementsByBusiness(businessId: String): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY date DESC")
    fun getMovementsByProduct(productId: String): Flow<List<StockMovementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(movement: StockMovementEntity)

    @Query("DELETE FROM stock_movements WHERE businessId = :businessId AND isDemo = 1")
    suspend fun deleteDemoMovements(businessId: String)
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE businessId = :businessId ORDER BY name ASC")
    fun getCustomersByBusiness(businessId: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    fun getCustomerById(id: String): Flow<CustomerEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity)

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomerById(id: String)

    @Query("DELETE FROM customers WHERE businessId = :businessId AND isDemo = 1")
    suspend fun deleteDemoCustomers(businessId: String)
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE businessId = :businessId ORDER BY date DESC")
    fun getPaymentsByBusiness(businessId: String): Flow<List<com.example.data.model.PaymentEntity>>

    @Query("SELECT * FROM payments WHERE saleId = :saleId ORDER BY date DESC")
    fun getPaymentsBySale(saleId: String): Flow<List<com.example.data.model.PaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: com.example.data.model.PaymentEntity)

    @Delete
    suspend fun deletePayment(payment: com.example.data.model.PaymentEntity)

    @Query("DELETE FROM payments WHERE saleId = :saleId")
    suspend fun deletePaymentsBySaleId(saleId: String)

    @Query("DELETE FROM payments WHERE businessId = :businessId AND isDemo = 1")
    suspend fun deleteDemoPayments(businessId: String)
}

