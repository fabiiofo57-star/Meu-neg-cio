package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val email: String,
    val photoUrl: String? = null,
    val phone: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessAt: Long = System.currentTimeMillis(),
    val whatsappPhone: String? = null,
    val whatsappVerified: Boolean = false,
    val whatsappConnected: Boolean = false
)

@Entity(
    tableName = "businesses",
    indices = [Index("userId")]
)
data class BusinessEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val name: String,
    val category: String,
    val phone: String,
    val city: String,
    val description: String? = null,
    val logoUri: String? = null,
    val coverPhotoUri: String? = null,
    val coverPresetId: String? = "emerald_abstract",
    val themeKey: String? = "EMERALD",
    val appearanceMode: String? = "SYSTEM",
    val iconStyle: String? = "FILLED",
    val razaoSocial: String? = null,
    val cnpj: String? = null,
    val email: String? = null,
    val address: String? = null,
    val state: String? = null,
    val cep: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "products",
    indices = [Index("businessId")]
)
data class ProductEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val businessId: String,
    val name: String,
    val category: String,
    val code: String? = null,
    val salePrice: Double,
    val costPrice: Double,
    val currentStock: Double,
    val minStock: Double = 0.0,
    val photoUri: String? = null,
    val isDemo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val profitMargin: Double
        get() = salePrice - costPrice

    val markupPercentage: Double
        get() = if (costPrice > 0) ((salePrice - costPrice) / costPrice) * 100.0 else 0.0

    val isLowStock: Boolean
        get() = currentStock <= minStock
}

@Entity(
    tableName = "sales",
    indices = [Index("businessId"), Index("date")]
)
data class SaleEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val businessId: String,
    val date: Long = System.currentTimeMillis(),
    val totalAmount: Double,
    val discountAmount: Double = 0.0,
    val paymentMethod: String, // "Dinheiro", "Pix", "Cartão", "A Prazo", "Outro"
    val observation: String? = null,
    val customerId: String? = null,
    val customerName: String? = null,
    val isDemo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val paidAmount: Double = totalAmount,
    val dueDate: Long? = null,
    val paymentStatus: String = "PAGO", // "PAGO", "PAGO_PARCIAL", "PENDENTE", "ATRASADO"
    val timeString: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    val remainingAmount: Double
        get() = (totalAmount - paidAmount).coerceAtLeast(0.0)

    val effectiveStatus: SalePaymentStatus
        get() {
            val remaining = remainingAmount
            return when {
                remaining <= 0.005 -> SalePaymentStatus.PAGO
                dueDate != null && dueDate < System.currentTimeMillis() -> SalePaymentStatus.ATRASADO
                paidAmount > 0.005 -> SalePaymentStatus.PAGO_PARCIAL
                else -> SalePaymentStatus.PENDENTE
            }
        }
}

enum class SalePaymentStatus(val label: String) {
    PAGO("Pago"),
    PAGO_PARCIAL("Pago Parcial"),
    PENDENTE("Pendente"),
    ATRASADO("Atrasado")
}

@Entity(
    tableName = "sale_items",
    indices = [Index("saleId"), Index("productId")]
)
data class SaleItemEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val saleId: String,
    val productId: String,
    val productName: String,
    val quantity: Double,
    val unitPrice: Double,
    val unitCost: Double,
    val subtotal: Double
)

@Entity(
    tableName = "expenses",
    indices = [Index("businessId"), Index("date")]
)
data class ExpenseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val businessId: String,
    val description: String,
    val category: String, // "Compra", "Aluguel", "Energia", "Internet", "Transporte", "Funcionários", "Impostos", "Manutenção", "Outros"
    val amount: Double,
    val date: Long = System.currentTimeMillis(),
    val observation: String? = null,
    val isDemo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "stock_movements",
    indices = [Index("businessId"), Index("productId"), Index("date")]
)
data class StockMovementEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val businessId: String,
    val productId: String,
    val productName: String,
    val type: String, // "ENTRY", "EXIT", "ADJUSTMENT"
    val quantity: Double,
    val previousStock: Double,
    val newStock: Double,
    val date: Long = System.currentTimeMillis(),
    val reason: String,
    val isDemo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "customers",
    indices = [Index("businessId")]
)
data class CustomerEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val businessId: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val cpfCnpj: String? = null,
    val address: String? = null,
    val observation: String? = null,
    val isDemo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "payments",
    indices = [Index("businessId"), Index("saleId"), Index("date")]
)
data class PaymentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val businessId: String,
    val saleId: String,
    val customerId: String? = null,
    val customerName: String? = null,
    val amount: Double,
    val paymentMethod: String,
    val date: Long = System.currentTimeMillis(),
    val timeString: String = "",
    val isDemo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

