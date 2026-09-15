package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.BusinessEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.PaymentEntity
import com.example.data.model.ProductEntity
import com.example.data.model.SaleEntity
import com.example.data.model.SaleItemEntity
import com.example.data.model.StockMovementEntity
import com.example.data.model.UserEntity

@Database(
    entities = [
        UserEntity::class,
        BusinessEntity::class,
        ProductEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        ExpenseEntity::class,
        StockMovementEntity::class,
        CustomerEntity::class,
        PaymentEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun businessDao(): BusinessDao
    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun customerDao(): CustomerDao
    abstract fun paymentDao(): PaymentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE businesses ADD COLUMN razaoSocial TEXT")
                    db.execSQL("ALTER TABLE businesses ADD COLUMN cnpj TEXT")
                    db.execSQL("ALTER TABLE businesses ADD COLUMN email TEXT")
                    db.execSQL("ALTER TABLE businesses ADD COLUMN address TEXT")
                    db.execSQL("ALTER TABLE businesses ADD COLUMN state TEXT")
                    db.execSQL("ALTER TABLE businesses ADD COLUMN cep TEXT")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE customers ADD COLUMN cpfCnpj TEXT")
                    db.execSQL("ALTER TABLE customers ADD COLUMN address TEXT")
                } catch (_: Exception) {}
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "meu_negocio.db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
