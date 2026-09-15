package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.BusinessEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.PaymentEntity
import com.example.data.model.ProductEntity
import com.example.data.model.SaleEntity
import com.example.data.model.SaleItemEntity
import com.example.data.model.SaleWithItems
import com.example.data.model.StockMovementEntity
import com.example.data.model.UserEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Estado da Sincronização com o Cloud Firestore.
 */
sealed interface CloudSyncState {
    data object Idle : CloudSyncState
    data class Syncing(val message: String = "Sincronizando com a nuvem...") : CloudSyncState
    data class Synced(val lastSyncTime: Long = System.currentTimeMillis()) : CloudSyncState
    data class Offline(val message: String = "Modo Offline - Dados salvos localmente") : CloudSyncState
    data class Error(val message: String) : CloudSyncState
}

/**
 * Resultado da consulta e verificação de negócio na nuvem ao autenticar.
 */
sealed interface CloudBusinessResult {
    data class Exists(val business: BusinessEntity) : CloudBusinessResult
    data object NotFound : CloudBusinessResult
    data class Error(val message: String) : CloudBusinessResult
}

/**
 * Gerenciador completo de persistência na nuvem e sincronização offline com Firebase Firestore.
 *
 * ESTRUTURA DO FIRESTORE MULTI-TENANT POR UID:
 * users/{uid}/
 *   ├── profile/info
 *   ├── business/info
 *   ├── products/{productId}
 *   ├── customers/{customerId}
 *   ├── sales/{saleId}
 *   ├── expenses/{expenseId}
 *   ├── stockMovements/{movementId}
 *   ├── payments/{paymentId}
 *   └── settings/preferences
 */
class FirestoreSyncHelper(
    private val context: Context,
    private val db: AppDatabase
) {
    private val tag = "FirestoreSyncHelper"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val storageHelper = FirebaseStorageHelper(context)

    private val _syncState = MutableStateFlow<CloudSyncState>(CloudSyncState.Idle)
    val syncState: StateFlow<CloudSyncState> = _syncState.asStateFlow()

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val listeners = mutableListOf<ListenerRegistration>()

    init {
        setupNetworkMonitoring()
        configureFirestorePersistence()
    }

    private val firestore: FirebaseFirestore?
        get() = try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseFirestore.getInstance()
            } else null
        } catch (e: Exception) {
            Log.w(tag, "Firebase Firestore não inicializado: ${e.message}")
            null
        }

    private var activeUserId: String? = null

    fun setActiveUser(userId: String?) {
        activeUserId = userId
        if (userId == null) {
            stopRealtimeSync()
        }
    }

    private val currentUserId: String?
        get() = activeUserId ?: try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (_: Exception) {
            null
        }

    private fun configureFirestorePersistence() {
        try {
            val fs = firestore ?: return
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build()
            fs.firestoreSettings = settings
        } catch (e: Exception) {
            Log.w(tag, "Configuração de cache local já aplicada ou não permitida: ${e.message}")
        }
    }

    private fun setupNetworkMonitoring() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val activeNetwork = cm.activeNetwork
                val capabilities = cm.getNetworkCapabilities(activeNetwork)
                val online = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                _isOnline.value = online
                if (!online) {
                    _syncState.value = CloudSyncState.Offline()
                }

                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()

                cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        _isOnline.value = true
                        _syncState.value = CloudSyncState.Idle
                        val uid = currentUserId
                        if (uid != null) {
                            scope.launch {
                                fullSyncFromCloud(uid)
                            }
                        }
                    }

                    override fun onLost(network: Network) {
                        _isOnline.value = false
                        _syncState.value = CloudSyncState.Offline("Sem conexão com a internet. Operações salvas localmente.")
                    }
                })
            }
        } catch (e: Exception) {
            Log.w(tag, "Não foi possível registrar NetworkCallback: ${e.message}")
        }
    }

    // =========================================================================
    // 1. PERFIL DO USUÁRIO
    // =========================================================================
    suspend fun syncUserProfile(user: UserEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(Exception("Firebase não disponível"))
        val uid = user.id
        try {
            val data = hashMapOf(
                "uid" to uid,
                "ownerId" to uid,
                "nome" to user.name,
                "email" to user.email,
                "foto" to (user.photoUrl ?: ""),
                "telefone" to (user.phone ?: ""),
                "dataDeCriacao" to user.createdAt,
                "ultimoAcesso" to System.currentTimeMillis(),
                "whatsappPhone" to (user.whatsappPhone ?: user.phone ?: ""),
                "whatsappVerified" to user.whatsappVerified,
                "whatsappConnected" to user.whatsappConnected,
                "updatedAt" to System.currentTimeMillis()
            )
            fs.collection("users").document(uid)
                .collection("profile").document("info")
                .set(data, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Erro ao sincronizar perfil do usuário", e)
            handleError(e)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 2. NEGÓCIO E PREFERÊNCIAS VISUAIS
    // =========================================================================
    suspend fun syncBusiness(business: BusinessEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(Exception("Firebase não disponível"))
        val uid = currentUserId ?: business.userId
        try {
            val data = hashMapOf(
                "id" to business.id,
                "userId" to uid,
                "ownerId" to uid,
                "nomeDoNegocio" to business.name,
                "categoria" to business.category,
                "descricao" to (business.description ?: ""),
                "telefone" to business.phone,
                "cidade" to business.city,
                "logoUri" to (business.logoUri ?: ""),
                "fotoCapaUri" to (business.coverPhotoUri ?: ""),
                "coverPresetId" to (business.coverPresetId ?: "emerald_abstract"),
                "tema" to (business.themeKey ?: "EMERALD"),
                "modoAparencia" to (business.appearanceMode ?: "SYSTEM"),
                "estiloIcones" to (business.iconStyle ?: "FILLED"),
                "razaoSocial" to (business.razaoSocial ?: ""),
                "cnpj" to (business.cnpj ?: ""),
                "email" to (business.email ?: ""),
                "endereco" to (business.address ?: ""),
                "estado" to (business.state ?: ""),
                "cep" to (business.cep ?: ""),
                "dataDeCriacao" to business.createdAt,
                "updatedAt" to System.currentTimeMillis()
            )
            // Salva na coleção principal business/info
            fs.collection("users").document(uid)
                .collection("business").document("info")
                .set(data, SetOptions.merge())
                .await()

            // Também sincroniza na coleção settings/info para compatibilidade total
            fs.collection("users").document(uid)
                .collection("settings").document("info")
                .set(data, SetOptions.merge())
                .await()

            _syncState.value = CloudSyncState.Synced()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Erro ao sincronizar dados do negócio", e)
            handleError(e)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 3. PRODUTOS
    // =========================================================================
    suspend fun syncProduct(product: ProductEntity): Result<Unit> = withContext(Dispatchers.IO) {
        if (product.isDemo) return@withContext Result.success(Unit) // Nunca envia dados de demonstração
        val fs = firestore ?: return@withContext Result.failure(Exception("Firebase não disponível"))
        val uid = currentUserId ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        try {
            val data = hashMapOf(
                "id" to product.id,
                "ownerId" to uid,
                "nome" to product.name,
                "categoria" to product.category,
                "codigo" to (product.code ?: ""),
                "precoVenda" to product.salePrice,
                "custo" to product.costPrice,
                "estoqueAtual" to product.currentStock,
                "estoqueMinimo" to product.minStock,
                "foto" to (product.photoUri ?: ""),
                "businessId" to product.businessId,
                "dataDeCriacao" to product.createdAt,
                "dataDeAtualizacao" to product.updatedAt,
                "updatedAt" to System.currentTimeMillis()
            )
            fs.collection("users").document(uid)
                .collection("products").document(product.id)
                .set(data, SetOptions.merge())
                .await()
            _syncState.value = CloudSyncState.Synced()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Erro ao sincronizar produto ${product.id}", e)
            handleError(e)
            Result.failure(e)
        }
    }

    suspend fun deleteProductFromCloud(productId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(Exception("Firebase não disponível"))
        val uid = currentUserId ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        try {
            fs.collection("users").document(uid)
                .collection("products").document(productId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Erro ao excluir produto da nuvem", e)
            handleError(e)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 4. CLIENTES
    // =========================================================================
    suspend fun syncCustomer(customer: CustomerEntity): Result<Unit> = withContext(Dispatchers.IO) {
        if (customer.isDemo) return@withContext Result.success(Unit)
        val fs = firestore ?: return@withContext Result.failure(Exception("Firebase não disponível"))
        val uid = currentUserId ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        try {
            val data = hashMapOf(
                "id" to customer.id,
                "ownerId" to uid,
                "nome" to customer.name,
                "telefone" to customer.phone,
                "email" to (customer.email ?: ""),
                "cpfCnpj" to (customer.cpfCnpj ?: ""),
                "endereco" to (customer.address ?: ""),
                "observacao" to (customer.observation ?: ""),
                "businessId" to customer.businessId,
                "dataDeCadastro" to customer.createdAt,
                "updatedAt" to System.currentTimeMillis()
            )
            fs.collection("users").document(uid)
                .collection("customers").document(customer.id)
                .set(data, SetOptions.merge())
                .await()
            _syncState.value = CloudSyncState.Synced()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Erro ao sincronizar cliente ${customer.id}", e)
            handleError(e)
            Result.failure(e)
        }
    }

    suspend fun deleteCustomerFromCloud(customerId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(Exception("Firebase não disponível"))
        val uid = currentUserId ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        try {
            fs.collection("users").document(uid)
                .collection("customers").document(customerId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Erro ao excluir cliente da nuvem", e)
            handleError(e)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 5. VENDAS E ITENS
    // =========================================================================
    suspend fun syncSale(sale: SaleEntity, items: List<SaleItemEntity>): Result<Unit> = withContext(Dispatchers.IO) {
        if (sale.isDemo) return@withContext Result.success(Unit)
        val fs = firestore ?: return@withContext Result.failure(Exception("Firebase não disponível"))
        val uid = currentUserId ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        try {
            val itemsList = items.map { item ->
                hashMapOf(
                    "id" to item.id,
                    "productId" to item.productId,
                    "productName" to item.productName,
                    "quantidade" to item.quantity,
                    "valorUnitario" to item.unitPrice,
                    "custoUnitario" to item.unitCost,
                    "subtotal" to item.subtotal
                )
            }

            val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            val horaStr = if (sale.timeString.isNotBlank()) sale.timeString else timeFormatter.format(Date(sale.date))

            val data = hashMapOf(
                "id" to sale.id,
                "ownerId" to uid,
                "clienteId" to (sale.customerId ?: ""),
                "clienteNome" to (sale.customerName ?: ""),
                "data" to sale.date,
                "hora" to horaStr,
                "itens" to itemsList,
                "quantidadeTotal" to items.sumOf { it.quantity },
                "desconto" to sale.discountAmount,
                "valorTotal" to sale.totalAmount,
                "formaDePagamento" to sale.paymentMethod,
                "statusDoPagamento" to sale.effectiveStatus.name.lowercase(),
                "valorPago" to sale.paidAmount,
                "valorRestante" to sale.remainingAmount,
                "dataDeVencimento" to (sale.dueDate ?: 0L),
                "observacao" to (sale.observation ?: ""),
                "businessId" to sale.businessId,
                "dataDeCriacao" to sale.createdAt,
                "updatedAt" to sale.updatedAt
            )
            fs.collection("users").document(uid)
                .collection("sales").document(sale.id)
                .set(data, SetOptions.merge())
                .await()
            _syncState.value = CloudSyncState.Synced()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Erro ao sincronizar venda ${sale.id}", e)
            handleError(e)
            Result.failure(e)
        }
    }

    suspend fun deleteSaleFromCloud(saleId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(Exception("Firebase não disponível"))
        val uid = currentUserId ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        try {
            fs.collection("users").document(uid)
                .collection("sales").document(saleId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Erro ao excluir venda da nuvem", e)
            handleError(e)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 6. PAGAMENTOS
    // =========================================================================
    suspend fun syncPayment(payment: PaymentEntity): Result<Unit> = withContext(Dispatchers.IO) {
        if (payment.isDemo) return@withContext Result.success(Unit)
        val fs = firestore ?: return@withContext Result.failure(Exception("Firebase não disponível"))
        val uid = currentUserId ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        try {
            val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            val horaStr = if (payment.timeString.isNotBlank()) payment.timeString else timeFormatter.format(Date(payment.date))

            val data = hashMapOf(
                "id" to payment.id,
                "ownerId" to uid,
                "saleId" to payment.saleId,
                "customerId" to (payment.customerId ?: ""),
                "customerName" to (payment.customerName ?: ""),
                "valor" to payment.amount,
                "formaDePagamento" to payment.paymentMethod,
                "data" to payment.date,
                "hora" to horaStr,
                "businessId" to payment.businessId,
                "dataDeCriacao" to payment.createdAt,
                "updatedAt" to System.currentTimeMillis()
            )
            fs.collection("users").document(uid)
                .collection("payments").document(payment.id)
                .set(data, SetOptions.merge())
                .await()
            _syncState.value = CloudSyncState.Synced()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Erro ao sincronizar pagamento ${payment.id}", e)
            handleError(e)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 7. DESPESAS
    // =========================================================================
    suspend fun syncExpense(expense: ExpenseEntity): Result<Unit> = withContext(Dispatchers.IO) {
        if (expense.isDemo) return@withContext Result.success(Unit)
        val fs = firestore ?: return@withContext Result.failure(Exception("Firebase não disponível"))
        val uid = currentUserId ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        try {
            val data = hashMapOf(
                "id" to expense.id,
                "ownerId" to uid,
                "descricao" to expense.description,
                "categoria" to expense.category,
                "valor" to expense.amount,
                "data" to expense.date,
                "observacao" to (expense.observation ?: ""),
                "businessId" to expense.businessId,
                "dataDeCriacao" to expense.createdAt,
                "updatedAt" to System.currentTimeMillis()
            )
            fs.collection("users").document(uid)
                .collection("expenses").document(expense.id)
                .set(data, SetOptions.merge())
                .await()
            _syncState.value = CloudSyncState.Synced()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Erro ao sincronizar despesa ${expense.id}", e)
            handleError(e)
            Result.failure(e)
        }
    }

    suspend fun deleteExpenseFromCloud(expenseId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(Exception("Firebase não disponível"))
        val uid = currentUserId ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        try {
            fs.collection("users").document(uid)
                .collection("expenses").document(expenseId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Erro ao excluir despesa da nuvem", e)
            handleError(e)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 8. MOVIMENTAÇÕES DE ESTOQUE
    // =========================================================================
    suspend fun syncStockMovement(movement: StockMovementEntity): Result<Unit> = withContext(Dispatchers.IO) {
        if (movement.isDemo) return@withContext Result.success(Unit)
        val fs = firestore ?: return@withContext Result.failure(Exception("Firebase não disponível"))
        val uid = currentUserId ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        try {
            val data = hashMapOf(
                "id" to movement.id,
                "ownerId" to uid,
                "productId" to movement.productId,
                "productName" to movement.productName,
                "tipo" to movement.type,
                "quantidade" to movement.quantity,
                "estoqueAnterior" to movement.previousStock,
                "estoqueNovo" to movement.newStock,
                "data" to movement.date,
                "motivo" to movement.reason,
                "businessId" to movement.businessId,
                "dataDeCriacao" to movement.createdAt,
                "updatedAt" to System.currentTimeMillis()
            )
            fs.collection("users").document(uid)
                .collection("stockMovements").document(movement.id)
                .set(data, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Erro ao sincronizar movimentação de estoque", e)
            handleError(e)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 9. CONFIGURAÇÕES VISUAIS
    // =========================================================================
    suspend fun syncSettings(
        themeKey: String,
        appearanceMode: String,
        iconStyle: String,
        coverPresetId: String,
        coverPhotoUri: String?,
        profilePhotoUri: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(Exception("Firebase não disponível"))
        val uid = currentUserId ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        try {
            val data = hashMapOf(
                "ownerId" to uid,
                "themeKey" to themeKey,
                "appearanceMode" to appearanceMode,
                "iconStyle" to iconStyle,
                "coverPresetId" to coverPresetId,
                "coverPhotoUri" to (coverPhotoUri ?: ""),
                "profilePhotoUri" to (profilePhotoUri ?: ""),
                "updatedAt" to System.currentTimeMillis()
            )
            fs.collection("users").document(uid)
                .collection("settings").document("preferences")
                .set(data, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Erro ao sincronizar configurações", e)
            handleError(e)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 10. VERIFICAÇÃO E CONSULTA INICIAL NA NUVEM (Ao Autenticar)
    // =========================================================================
    suspend fun checkAndFetchCloudData(userId: String, userEmail: String? = null): CloudBusinessResult = withContext(Dispatchers.IO) {
        activeUserId = userId
        _syncState.value = CloudSyncState.Syncing("Consultando dados do seu negócio...")

        // 1. PRIMEIRO: Verifica se já existe negócio no banco local Room para este userId
        val localBizByUid = db.businessDao().getBusinessByUserId(userId).firstOrNull()
        if (localBizByUid != null) {
            Log.d(tag, "Negócio encontrado no cache local por userId: $userId (${localBizByUid.name})")
            scope.launch {
                fullSyncFromCloud(userId, userEmail)
                startRealtimeSync(userId)
            }
            return@withContext CloudBusinessResult.Exists(localBizByUid)
        }

        // 2. SEGUNDO: Se usuário tem e-mail, verifica no banco local se há negócio associado a este e-mail
        if (!userEmail.isNullOrBlank()) {
            val localBizByEmail = db.businessDao().findBusinessByUserEmail(userEmail.trim())
            if (localBizByEmail != null) {
                Log.d(tag, "Negócio encontrado no cache local por e-mail: $userEmail (${localBizByEmail.name}). Vinculando ao UID $userId...")
                val linkedBiz = localBizByEmail.copy(userId = userId)
                db.businessDao().insertBusiness(linkedBiz)
                scope.launch {
                    fullSyncFromCloud(userId, userEmail)
                    startRealtimeSync(userId)
                }
                return@withContext CloudBusinessResult.Exists(linkedBiz)
            }
        }

        // 3. TERCEIRO: Verifica se há QUALQUER negócio já salvo no aparelho
        val anyLocalBiz = db.businessDao().findAnyBusiness()
        if (anyLocalBiz != null) {
            Log.d(tag, "Negócio local existente encontrado no aparelho: ${anyLocalBiz.name}. Vinculando ao UID $userId...")
            val linkedBiz = anyLocalBiz.copy(userId = userId)
            db.businessDao().insertBusiness(linkedBiz)
            scope.launch {
                fullSyncFromCloud(userId, userEmail)
                startRealtimeSync(userId)
            }
            return@withContext CloudBusinessResult.Exists(linkedBiz)
        }

        // 4. QUARTO: Se o cache local não tem negócio, consulta a nuvem (Firestore)
        val fs = firestore
        if (fs == null) {
            Log.w(tag, "Firestore indisponível para consulta na nuvem")
            return@withContext CloudBusinessResult.NotFound
        }

        try {
            // Constrói lista inteligente de todos os possíveis IDs onde os dados deste usuário podem estar salvos na nuvem
            val candidateUids = linkedSetOf<String>()
            candidateUids.add(userId)

            try {
                FirebaseAuth.getInstance().currentUser?.uid?.let { fbUid ->
                    if (fbUid.isNotBlank()) candidateUids.add(fbUid)
                }
            } catch (_: Exception) {}

            if (!userEmail.isNullOrBlank()) {
                val cleanEmail = userEmail.trim().lowercase()
                val rawEmail = userEmail.trim()
                candidateUids.add("user_${cleanEmail.replace("@", "_").replace(".", "_")}")
                candidateUids.add("google_${cleanEmail.replace("@", "_").replace(".", "_")}")
                candidateUids.add("user_${rawEmail.replace("@", "_").replace(".", "_")}")
                candidateUids.add("google_${rawEmail.replace("@", "_").replace(".", "_")}")
                candidateUids.add(cleanEmail)
                candidateUids.add(rawEmail)
                candidateUids.add(cleanEmail.replace("@", "_").replace(".", "_"))
                candidateUids.add(rawEmail.replace("@", "_").replace(".", "_"))
                candidateUids.add(cleanEmail.substringBefore("@"))
                candidateUids.add("user_${cleanEmail.substringBefore("@")}")
                candidateUids.add("google_${cleanEmail.substringBefore("@")}")
            }

            // Tenta consultar cada UID candidato
            for (candUid in candidateUids) {
                try {
                    val userDocRef = fs.collection("users").document(candUid)
                    val bizDoc = userDocRef.collection("business").document("info").get().await()

                    if (bizDoc.exists()) {
                        val bId = bizDoc.getString("id") ?: userId
                        val rawLogo = bizDoc.getString("logoUri")?.takeIf { it.isNotBlank() }
                        val cachedLogo = storageHelper.decodeAndCacheIfBase64(rawLogo, "logo_cached_$userId") ?: rawLogo
                        val rawCover = bizDoc.getString("fotoCapaUri")?.takeIf { it.isNotBlank() }
                        val cachedCover = storageHelper.decodeAndCacheIfBase64(rawCover, "cover_cached_$userId") ?: rawCover

                        val business = BusinessEntity(
                            id = bId,
                            userId = userId, // Vincula imediatamente ao usuário logado
                            name = bizDoc.getString("nomeDoNegocio") ?: "Meu Negócio",
                            category = bizDoc.getString("categoria") ?: "Comércio Geral",
                            phone = bizDoc.getString("telefone") ?: "",
                            city = bizDoc.getString("cidade") ?: "",
                            description = bizDoc.getString("descricao"),
                            logoUri = cachedLogo,
                            coverPhotoUri = cachedCover,
                            coverPresetId = bizDoc.getString("coverPresetId") ?: "emerald_abstract",
                            themeKey = bizDoc.getString("tema") ?: "EMERALD",
                            appearanceMode = bizDoc.getString("modoAparencia") ?: "SYSTEM",
                            iconStyle = bizDoc.getString("estiloIcones") ?: "FILLED",
                            createdAt = bizDoc.getLongSafe("dataDeCriacao", System.currentTimeMillis()),
                            updatedAt = bizDoc.getLongSafe("updatedAt", System.currentTimeMillis())
                        )
                        db.businessDao().insertBusiness(business)
                        Log.d(tag, "Negócio encontrado no Firestore para UID $candUid: ${business.name}. Baixando subcoleções...")

                        pullSubcollectionsFromCloud(sourceUid = candUid, targetUserId = userId, targetBusinessId = bId)
                        startRealtimeSync(userId)
                        _syncState.value = CloudSyncState.Synced()
                        return@withContext CloudBusinessResult.Exists(business)
                    }

                    // Fallback settings/info
                    val settingsDoc = userDocRef.collection("settings").document("info").get().await()
                    if (settingsDoc.exists() && settingsDoc.contains("nomeDoNegocio")) {
                        val bId = settingsDoc.getString("id") ?: userId
                        val business = BusinessEntity(
                            id = bId,
                            userId = userId,
                            name = settingsDoc.getString("nomeDoNegocio") ?: "Meu Negócio",
                            category = settingsDoc.getString("categoria") ?: "Comércio Geral",
                            phone = settingsDoc.getString("telefone") ?: "",
                            city = settingsDoc.getString("cidade") ?: "",
                            createdAt = settingsDoc.getLongSafe("dataDeCriacao", System.currentTimeMillis())
                        )
                        db.businessDao().insertBusiness(business)
                        pullSubcollectionsFromCloud(sourceUid = candUid, targetUserId = userId, targetBusinessId = bId)
                        startRealtimeSync(userId)
                        _syncState.value = CloudSyncState.Synced()
                        return@withContext CloudBusinessResult.Exists(business)
                    }

                    // Fallback products
                    val prodsDoc = userDocRef.collection("products").limit(1).get().await()
                    if (!prodsDoc.isEmpty) {
                        val defaultBiz = BusinessEntity(
                            id = userId,
                            userId = userId,
                            name = "Meu Negócio",
                            category = "Comércio Geral",
                            phone = "",
                            city = ""
                        )
                        db.businessDao().insertBusiness(defaultBiz)
                        syncBusiness(defaultBiz)
                        pullSubcollectionsFromCloud(sourceUid = candUid, targetUserId = userId, targetBusinessId = userId)
                        startRealtimeSync(userId)
                        _syncState.value = CloudSyncState.Synced()
                        return@withContext CloudBusinessResult.Exists(defaultBiz)
                    }
                } catch (ce: Exception) {
                    Log.w(tag, "Tentativa de consulta com candidato $candUid falhou: ${ce.message}")
                }
            }

            // 5. Busca ampla na coleção users caso o e-mail esteja em profile/info
            if (!userEmail.isNullOrBlank()) {
                try {
                    val cleanEmail = userEmail.trim().lowercase()
                    val allUsers = fs.collection("users").get().await()
                    for (doc in allUsers.documents) {
                        val otherUid = doc.id
                        if (candidateUids.contains(otherUid)) continue
                        try {
                            val prof = doc.reference.collection("profile").document("info").get().await()
                            val em = prof.getString("email")
                            if (em != null && em.trim().equals(cleanEmail, ignoreCase = true)) {
                                val bDoc = doc.reference.collection("business").document("info").get().await()
                                if (bDoc.exists()) {
                                    val bId = bDoc.getString("id") ?: userId
                                    val business = BusinessEntity(
                                        id = bId,
                                        userId = userId,
                                        name = bDoc.getString("nomeDoNegocio") ?: "Meu Negócio",
                                        category = bDoc.getString("categoria") ?: "Comércio Geral",
                                        phone = bDoc.getString("telefone") ?: "",
                                        city = bDoc.getString("cidade") ?: "",
                                        description = bDoc.getString("descricao")
                                    )
                                    db.businessDao().insertBusiness(business)
                                    pullSubcollectionsFromCloud(sourceUid = otherUid, targetUserId = userId, targetBusinessId = bId)
                                    startRealtimeSync(userId)
                                    _syncState.value = CloudSyncState.Synced()
                                    return@withContext CloudBusinessResult.Exists(business)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Busca ampla falhou: ${e.message}")
                }
            }

            // 6. Nenhum negócio encontrado: usuário novo precisa cadastrar
            Log.d(tag, "Nenhum dado encontrado no Firestore para UID: $userId e e-mail: $userEmail. Usuário novo.")
            _syncState.value = CloudSyncState.Synced()
            return@withContext CloudBusinessResult.NotFound
        } catch (e: Exception) {
            Log.e(tag, "Erro ao consultar Firestore para UID: $userId", e)
            handleError(e)
            val fallbackBiz = db.businessDao().getBusinessByUserId(userId).firstOrNull()
                ?: db.businessDao().getAnyBusiness().firstOrNull()
            if (fallbackBiz != null) {
                return@withContext CloudBusinessResult.Exists(fallbackBiz)
            }
            return@withContext CloudBusinessResult.NotFound
        }
    }

    // =========================================================================
    // 11. SINCRONIZAÇÃO COMPLETA DA NUVEM (Ao logar ou trocar de dispositivo)
    // =========================================================================
    private fun DocumentSnapshot.getDoubleSafe(key: String, default: Double = 0.0): Double {
        val v = this.get(key) ?: return default
        return when (v) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull() ?: default
            else -> default
        }
    }

    private fun DocumentSnapshot.getLongSafe(key: String, default: Long = 0L): Long {
        val v = this.get(key) ?: return default
        return when (v) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: default
            else -> default
        }
    }

    private suspend fun pullSubcollectionsFromCloud(
        sourceUid: String,
        targetUserId: String,
        targetBusinessId: String
    ) = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext
        val sourceDocRef = fs.collection("users").document(sourceUid)

        // 1. Produtos
        try {
            val productsSnap = sourceDocRef.collection("products").get().await()
            for (doc in productsSnap.documents) {
                val prod = ProductEntity(
                    id = doc.getString("id") ?: doc.id,
                    businessId = targetBusinessId,
                    name = doc.getString("nome") ?: "Produto",
                    category = doc.getString("categoria") ?: "Geral",
                    code = doc.getString("codigo")?.takeIf { it.isNotBlank() },
                    salePrice = doc.getDoubleSafe("precoVenda", 0.0),
                    costPrice = doc.getDoubleSafe("custo", 0.0),
                    currentStock = doc.getDoubleSafe("estoqueAtual", 0.0),
                    minStock = doc.getDoubleSafe("estoqueMinimo", 0.0),
                    photoUri = doc.getString("foto")?.takeIf { it.isNotBlank() },
                    isDemo = false,
                    createdAt = doc.getLongSafe("dataDeCriacao", System.currentTimeMillis()),
                    updatedAt = doc.getLongSafe("dataDeAtualizacao", System.currentTimeMillis())
                )
                db.productDao().insertProduct(prod)
            }
        } catch (e: Exception) {
            Log.w(tag, "Erro ao puxar produtos de $sourceUid: ${e.message}")
        }

        // 2. Clientes
        try {
            val customersSnap = sourceDocRef.collection("customers").get().await()
            for (doc in customersSnap.documents) {
                val cust = CustomerEntity(
                    id = doc.getString("id") ?: doc.id,
                    businessId = targetBusinessId,
                    name = doc.getString("nome") ?: "Cliente",
                    phone = doc.getString("telefone") ?: "",
                    email = doc.getString("email")?.takeIf { it.isNotBlank() },
                    cpfCnpj = doc.getString("cpfCnpj")?.takeIf { it.isNotBlank() },
                    address = (doc.getString("endereco") ?: doc.getString("address"))?.takeIf { it.isNotBlank() },
                    observation = doc.getString("observacao")?.takeIf { it.isNotBlank() },
                    isDemo = false,
                    createdAt = doc.getLongSafe("dataDeCadastro", System.currentTimeMillis())
                )
                db.customerDao().insertCustomer(cust)
            }
        } catch (e: Exception) {
            Log.w(tag, "Erro ao puxar clientes de $sourceUid: ${e.message}")
        }

        // 3. Vendas e Itens
        try {
            val salesSnap = sourceDocRef.collection("sales").get().await()
            for (doc in salesSnap.documents) {
                val saleId = doc.getString("id") ?: doc.id
                val statusRaw = doc.getString("statusDoPagamento")?.uppercase() ?: "PAGO"
                val sale = SaleEntity(
                    id = saleId,
                    businessId = targetBusinessId,
                    date = doc.getLongSafe("data", System.currentTimeMillis()),
                    totalAmount = doc.getDoubleSafe("valorTotal", 0.0),
                    discountAmount = doc.getDoubleSafe("desconto", 0.0),
                    paymentMethod = doc.getString("formaDePagamento") ?: "Dinheiro",
                    observation = doc.getString("observacao")?.takeIf { it.isNotBlank() },
                    customerId = doc.getString("clienteId")?.takeIf { it.isNotBlank() },
                    customerName = doc.getString("clienteNome")?.takeIf { it.isNotBlank() },
                    isDemo = false,
                    createdAt = doc.getLongSafe("dataDeCriacao", System.currentTimeMillis()),
                    paidAmount = doc.getDoubleSafe("valorPago", doc.getDoubleSafe("valorTotal", 0.0)),
                    dueDate = doc.getLongSafe("dataDeVencimento", 0L).takeIf { it > 0 },
                    paymentStatus = statusRaw,
                    timeString = doc.getString("hora") ?: "",
                    updatedAt = doc.getLongSafe("updatedAt", System.currentTimeMillis())
                )
                db.saleDao().insertSale(sale)

                @Suppress("UNCHECKED_CAST")
                val itemsRaw = doc.get("itens") as? List<Map<String, Any>>
                if (itemsRaw != null) {
                    val items = itemsRaw.map { itemMap ->
                        SaleItemEntity(
                            id = itemMap["id"] as? String ?: UUID.randomUUID().toString(),
                            saleId = saleId,
                            productId = itemMap["produtoId"] as? String ?: itemMap["productId"] as? String ?: "",
                            productName = itemMap["produtoNome"] as? String ?: itemMap["productName"] as? String ?: "Produto",
                            quantity = (itemMap["quantidade"] as? Number)?.toDouble() ?: 1.0,
                            unitPrice = (itemMap["precoUnitario"] as? Number)?.toDouble() ?: (itemMap["valorUnitario"] as? Number)?.toDouble() ?: 0.0,
                            unitCost = (itemMap["custoUnitario"] as? Number)?.toDouble() ?: 0.0,
                            subtotal = (itemMap["subtotal"] as? Number)?.toDouble() ?: 0.0
                        )
                    }
                    db.saleDao().insertSaleItems(items)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Erro ao puxar vendas de $sourceUid: ${e.message}")
        }

        // 4. Despesas
        try {
            val expensesSnap = sourceDocRef.collection("expenses").get().await()
            for (doc in expensesSnap.documents) {
                val exp = ExpenseEntity(
                    id = doc.getString("id") ?: doc.id,
                    businessId = targetBusinessId,
                    description = doc.getString("descricao") ?: "Despesa",
                    category = doc.getString("categoria") ?: "Outros",
                    amount = doc.getDoubleSafe("valor", 0.0),
                    date = doc.getLongSafe("data", System.currentTimeMillis()),
                    observation = doc.getString("observacao")?.takeIf { it.isNotBlank() },
                    isDemo = false,
                    createdAt = doc.getLongSafe("dataDeCriacao", System.currentTimeMillis())
                )
                db.expenseDao().insertExpense(exp)
            }
        } catch (e: Exception) {
            Log.w(tag, "Erro ao puxar despesas de $sourceUid: ${e.message}")
        }

        // 5. Pagamentos
        try {
            val paymentsSnap = sourceDocRef.collection("payments").get().await()
            for (doc in paymentsSnap.documents) {
                val pay = PaymentEntity(
                    id = doc.getString("id") ?: doc.id,
                    businessId = targetBusinessId,
                    saleId = doc.getString("saleId") ?: "",
                    customerId = doc.getString("customerId")?.takeIf { it.isNotBlank() },
                    customerName = doc.getString("customerName")?.takeIf { it.isNotBlank() },
                    amount = doc.getDoubleSafe("valor", 0.0),
                    paymentMethod = doc.getString("formaDePagamento") ?: "Dinheiro",
                    date = doc.getLongSafe("data", System.currentTimeMillis()),
                    timeString = doc.getString("hora") ?: "",
                    isDemo = false,
                    createdAt = doc.getLongSafe("dataDeCriacao", System.currentTimeMillis())
                )
                db.paymentDao().insertPayment(pay)
            }
        } catch (e: Exception) {
            Log.w(tag, "Erro ao puxar pagamentos de $sourceUid: ${e.message}")
        }

        // 6. Movimentações de Estoque
        try {
            val movementsSnap = sourceDocRef.collection("stockMovements").get().await()
            for (doc in movementsSnap.documents) {
                val mov = StockMovementEntity(
                    id = doc.getString("id") ?: doc.id,
                    businessId = targetBusinessId,
                    productId = doc.getString("productId") ?: "",
                    productName = doc.getString("productName") ?: "Produto",
                    type = doc.getString("tipo") ?: "ENTRY",
                    quantity = doc.getDoubleSafe("quantidade", 0.0),
                    previousStock = doc.getDoubleSafe("estoqueAnterior", 0.0),
                    newStock = doc.getDoubleSafe("estoqueNovo", 0.0),
                    date = doc.getLongSafe("data", System.currentTimeMillis()),
                    reason = doc.getString("motivo") ?: "Sincronização",
                    isDemo = false,
                    createdAt = doc.getLongSafe("dataDeCriacao", System.currentTimeMillis())
                )
                db.stockMovementDao().insertMovement(mov)
            }
        } catch (e: Exception) {
            Log.w(tag, "Erro ao puxar movimentações de estoque de $sourceUid: ${e.message}")
        }
    }

    suspend fun fullSyncFromCloud(userId: String, userEmail: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        activeUserId = userId
        val fs = firestore ?: return@withContext Result.failure(Exception("Firebase não disponível"))
        if (!_isOnline.value) {
            _syncState.value = CloudSyncState.Offline("Sem internet. Usando dados locais salvos no aparelho.")
            return@withContext Result.success(Unit)
        }

        _syncState.value = CloudSyncState.Syncing("Buscando seus dados na nuvem...")
        try {
            val candidateUids = linkedSetOf<String>()
            candidateUids.add(userId)
            try {
                FirebaseAuth.getInstance().currentUser?.uid?.let { fbUid ->
                    if (fbUid.isNotBlank()) candidateUids.add(fbUid)
                }
            } catch (_: Exception) {}

            if (!userEmail.isNullOrBlank()) {
                val cleanEmail = userEmail.trim().lowercase()
                val rawEmail = userEmail.trim()
                candidateUids.add("user_${cleanEmail.replace("@", "_").replace(".", "_")}")
                candidateUids.add("google_${cleanEmail.replace("@", "_").replace(".", "_")}")
                candidateUids.add("user_${rawEmail.replace("@", "_").replace(".", "_")}")
                candidateUids.add("google_${rawEmail.replace("@", "_").replace(".", "_")}")
                candidateUids.add(cleanEmail)
                candidateUids.add(rawEmail)
                candidateUids.add(cleanEmail.replace("@", "_").replace(".", "_"))
                candidateUids.add(rawEmail.replace("@", "_").replace(".", "_"))
                candidateUids.add(cleanEmail.substringBefore("@"))
                candidateUids.add("user_${cleanEmail.substringBefore("@")}")
                candidateUids.add("google_${cleanEmail.substringBefore("@")}")
            }

            var sourceUidWithData = userId
            var activeBusinessId: String? = null

            for (candUid in candidateUids) {
                val candDocRef = fs.collection("users").document(candUid)
                val candBizDoc = candDocRef.collection("business").document("info").get().await()
                if (candBizDoc.exists()) {
                    sourceUidWithData = candUid
                    val bId = candBizDoc.getString("id") ?: userId
                    activeBusinessId = bId
                    val rawLogo = candBizDoc.getString("logoUri")?.takeIf { it.isNotBlank() }
                    val cachedLogo = storageHelper.decodeAndCacheIfBase64(rawLogo, "logo_cached_$userId") ?: rawLogo
                    val rawCover = candBizDoc.getString("fotoCapaUri")?.takeIf { it.isNotBlank() }
                    val cachedCover = storageHelper.decodeAndCacheIfBase64(rawCover, "cover_cached_$userId") ?: rawCover

                    val business = BusinessEntity(
                        id = bId,
                        userId = userId,
                        name = candBizDoc.getString("nomeDoNegocio") ?: "Meu Negócio",
                        category = candBizDoc.getString("categoria") ?: "Comércio Geral",
                        phone = candBizDoc.getString("telefone") ?: "",
                        city = candBizDoc.getString("cidade") ?: "",
                        description = candBizDoc.getString("descricao"),
                        logoUri = cachedLogo,
                        coverPhotoUri = cachedCover,
                        coverPresetId = candBizDoc.getString("coverPresetId") ?: "emerald_abstract",
                        themeKey = candBizDoc.getString("tema") ?: "EMERALD",
                        appearanceMode = candBizDoc.getString("modoAparencia") ?: "SYSTEM",
                        iconStyle = candBizDoc.getString("estiloIcones") ?: "FILLED",
                        createdAt = candBizDoc.getLongSafe("dataDeCriacao", System.currentTimeMillis()),
                        updatedAt = candBizDoc.getLongSafe("updatedAt", System.currentTimeMillis())
                    )
                    db.businessDao().insertBusiness(business)
                    break
                }
            }

            val userDocRef = fs.collection("users").document(sourceUidWithData)

            // 1. Sincroniza Perfil
            try {
                val profileSnap = userDocRef.collection("profile").document("info").get().await()
                if (profileSnap.exists()) {
                    val rawPhoto = profileSnap.getString("foto")?.takeIf { it.isNotBlank() }
                    val cachedPhoto = storageHelper.decodeAndCacheIfBase64(rawPhoto, "user_photo_cached_$userId") ?: rawPhoto
                    val user = UserEntity(
                        id = userId,
                        name = profileSnap.getString("nome") ?: "Empreendedor",
                        email = profileSnap.getString("email") ?: "",
                        photoUrl = cachedPhoto,
                        phone = profileSnap.getString("telefone")?.takeIf { it.isNotBlank() },
                        createdAt = profileSnap.getLongSafe("dataDeCriacao", System.currentTimeMillis()),
                        lastAccessAt = System.currentTimeMillis(),
                        whatsappPhone = profileSnap.getString("whatsappPhone"),
                        whatsappVerified = profileSnap.getBoolean("whatsappVerified") ?: false,
                        whatsappConnected = profileSnap.getBoolean("whatsappConnected") ?: false
                    )
                    db.userDao().insertUser(user)
                }
            } catch (pe: Exception) {
                Log.w(tag, "Aviso ao carregar perfil: ${pe.message}")
            }

            // 2. Sincroniza Negócio
            try {
                if (activeBusinessId == null) {
                    val businessSnap = userDocRef.collection("business").document("info").get().await()
                    if (businessSnap.exists()) {
                        val bId = businessSnap.getString("id") ?: userId
                        activeBusinessId = bId
                        val rawLogo = businessSnap.getString("logoUri")?.takeIf { it.isNotBlank() }
                        val cachedLogo = storageHelper.decodeAndCacheIfBase64(rawLogo, "logo_cached_$userId") ?: rawLogo
                        val rawCover = businessSnap.getString("fotoCapaUri")?.takeIf { it.isNotBlank() }
                        val cachedCover = storageHelper.decodeAndCacheIfBase64(rawCover, "cover_cached_$userId") ?: rawCover

                        val business = BusinessEntity(
                            id = bId,
                            userId = userId,
                            name = businessSnap.getString("nomeDoNegocio") ?: "Meu Negócio",
                            category = businessSnap.getString("categoria") ?: "Comércio Geral",
                            phone = businessSnap.getString("telefone") ?: "",
                            city = businessSnap.getString("cidade") ?: "",
                            description = businessSnap.getString("descricao"),
                            logoUri = cachedLogo,
                            coverPhotoUri = cachedCover,
                            coverPresetId = businessSnap.getString("coverPresetId") ?: "emerald_abstract",
                            themeKey = businessSnap.getString("tema") ?: "EMERALD",
                            appearanceMode = businessSnap.getString("modoAparencia") ?: "SYSTEM",
                            iconStyle = businessSnap.getString("estiloIcones") ?: "FILLED",
                            razaoSocial = businessSnap.getString("razaoSocial"),
                            cnpj = businessSnap.getString("cnpj"),
                            email = businessSnap.getString("email"),
                            address = businessSnap.getString("endereco") ?: businessSnap.getString("address"),
                            state = businessSnap.getString("estado") ?: businessSnap.getString("state"),
                            cep = businessSnap.getString("cep"),
                            createdAt = businessSnap.getLongSafe("dataDeCriacao", System.currentTimeMillis()),
                            updatedAt = businessSnap.getLongSafe("updatedAt", System.currentTimeMillis())
                        )
                        db.businessDao().insertBusiness(business)
                    }
                }
            } catch (be: Exception) {
                Log.w(tag, "Aviso ao carregar negócio: ${be.message}")
            }

            val bId = activeBusinessId ?: db.businessDao().getBusinessByUserId(userId).firstOrNull()?.id ?: userId

            if (bId != null) {
                // 3. Sincroniza Produtos
                val productsSnap = userDocRef.collection("products").get().await()
                for (doc in productsSnap.documents) {
                    val prod = ProductEntity(
                        id = doc.getString("id") ?: doc.id,
                        businessId = doc.getString("businessId") ?: bId,
                        name = doc.getString("nome") ?: "Produto",
                        category = doc.getString("categoria") ?: "Geral",
                        code = doc.getString("codigo")?.takeIf { it.isNotBlank() },
                        salePrice = doc.getDoubleSafe("precoVenda", 0.0),
                        costPrice = doc.getDoubleSafe("custo", 0.0),
                        currentStock = doc.getDoubleSafe("estoqueAtual", 0.0),
                        minStock = doc.getDoubleSafe("estoqueMinimo", 0.0),
                        photoUri = doc.getString("foto")?.takeIf { it.isNotBlank() },
                        isDemo = false,
                        createdAt = doc.getLongSafe("dataDeCriacao", System.currentTimeMillis()),
                        updatedAt = doc.getLongSafe("dataDeAtualizacao", System.currentTimeMillis())
                    )
                    db.productDao().insertProduct(prod)
                }

                // 4. Sincroniza Clientes
                val customersSnap = userDocRef.collection("customers").get().await()
                for (doc in customersSnap.documents) {
                    val cust = CustomerEntity(
                        id = doc.getString("id") ?: doc.id,
                        businessId = doc.getString("businessId") ?: bId,
                        name = doc.getString("nome") ?: "Cliente",
                        phone = doc.getString("telefone") ?: "",
                        email = doc.getString("email")?.takeIf { it.isNotBlank() },
                        cpfCnpj = doc.getString("cpfCnpj")?.takeIf { it.isNotBlank() },
                        address = (doc.getString("endereco") ?: doc.getString("address"))?.takeIf { it.isNotBlank() },
                        observation = doc.getString("observacao")?.takeIf { it.isNotBlank() },
                        isDemo = false,
                        createdAt = doc.getLongSafe("dataDeCadastro", System.currentTimeMillis())
                    )
                    db.customerDao().insertCustomer(cust)
                }

                // 5. Sincroniza Vendas e Itens
                val salesSnap = userDocRef.collection("sales").get().await()
                for (doc in salesSnap.documents) {
                    val saleId = doc.getString("id") ?: doc.id
                    val statusRaw = doc.getString("statusDoPagamento")?.uppercase() ?: "PAGO"
                    val sale = SaleEntity(
                        id = saleId,
                        businessId = doc.getString("businessId") ?: bId,
                        date = doc.getLongSafe("data", System.currentTimeMillis()),
                        totalAmount = doc.getDoubleSafe("valorTotal", 0.0),
                        discountAmount = doc.getDoubleSafe("desconto", 0.0),
                        paymentMethod = doc.getString("formaDePagamento") ?: "Dinheiro",
                        observation = doc.getString("observacao")?.takeIf { it.isNotBlank() },
                        customerId = doc.getString("clienteId")?.takeIf { it.isNotBlank() },
                        customerName = doc.getString("clienteNome")?.takeIf { it.isNotBlank() },
                        isDemo = false,
                        createdAt = doc.getLongSafe("dataDeCriacao", System.currentTimeMillis()),
                        paidAmount = doc.getDoubleSafe("valorPago", doc.getDoubleSafe("valorTotal", 0.0)),
                        dueDate = doc.getLongSafe("dataDeVencimento", 0L).takeIf { it > 0 },
                        paymentStatus = statusRaw,
                        timeString = doc.getString("hora") ?: "",
                        updatedAt = doc.getLongSafe("updatedAt", System.currentTimeMillis())
                    )
                    db.saleDao().insertSale(sale)

                    @Suppress("UNCHECKED_CAST")
                    val itemsRaw = doc.get("itens") as? List<Map<String, Any>>
                    if (itemsRaw != null) {
                        val items = itemsRaw.map { itemMap ->
                            SaleItemEntity(
                                id = (itemMap["id"] as? String) ?: java.util.UUID.randomUUID().toString(),
                                saleId = saleId,
                                productId = (itemMap["productId"] as? String) ?: "",
                                productName = (itemMap["productName"] as? String) ?: "Item",
                                quantity = (itemMap["quantidade"] as? Number)?.toDouble() ?: 1.0,
                                unitPrice = (itemMap["valorUnitario"] as? Number)?.toDouble() ?: 0.0,
                                unitCost = (itemMap["custoUnitario"] as? Number)?.toDouble() ?: 0.0,
                                subtotal = (itemMap["subtotal"] as? Number)?.toDouble() ?: 0.0
                            )
                        }
                        db.saleDao().insertSaleItems(items)
                    }
                }

                // 6. Sincroniza Despesas
                val expensesSnap = userDocRef.collection("expenses").get().await()
                for (doc in expensesSnap.documents) {
                    val exp = ExpenseEntity(
                        id = doc.getString("id") ?: doc.id,
                        businessId = doc.getString("businessId") ?: bId,
                        description = doc.getString("descricao") ?: "Despesa",
                        category = doc.getString("categoria") ?: "Outros",
                        amount = doc.getDoubleSafe("valor", 0.0),
                        date = doc.getLongSafe("data", System.currentTimeMillis()),
                        observation = doc.getString("observacao")?.takeIf { it.isNotBlank() },
                        isDemo = false,
                        createdAt = doc.getLongSafe("dataDeCriacao", System.currentTimeMillis())
                    )
                    db.expenseDao().insertExpense(exp)
                }

                // 7. Sincroniza Pagamentos
                val paymentsSnap = userDocRef.collection("payments").get().await()
                for (doc in paymentsSnap.documents) {
                    val pay = PaymentEntity(
                        id = doc.getString("id") ?: doc.id,
                        businessId = doc.getString("businessId") ?: bId,
                        saleId = doc.getString("saleId") ?: "",
                        customerId = doc.getString("customerId")?.takeIf { it.isNotBlank() },
                        customerName = doc.getString("customerName")?.takeIf { it.isNotBlank() },
                        amount = doc.getDoubleSafe("valor", 0.0),
                        paymentMethod = doc.getString("formaDePagamento") ?: "Dinheiro",
                        date = doc.getLongSafe("data", System.currentTimeMillis()),
                        timeString = doc.getString("hora") ?: "",
                        isDemo = false,
                        createdAt = doc.getLongSafe("dataDeCriacao", System.currentTimeMillis())
                    )
                    db.paymentDao().insertPayment(pay)
                }

                // 8. Sincroniza Movimentações de Estoque
                val movementsSnap = userDocRef.collection("stockMovements").get().await()
                for (doc in movementsSnap.documents) {
                    val mov = StockMovementEntity(
                        id = doc.getString("id") ?: doc.id,
                        businessId = doc.getString("businessId") ?: bId,
                        productId = doc.getString("productId") ?: "",
                        productName = doc.getString("productName") ?: "Produto",
                        type = doc.getString("tipo") ?: "ENTRY",
                        quantity = doc.getDoubleSafe("quantidade", 0.0),
                        previousStock = doc.getDoubleSafe("estoqueAnterior", 0.0),
                        newStock = doc.getDoubleSafe("estoqueNovo", 0.0),
                        date = doc.getLongSafe("data", System.currentTimeMillis()),
                        reason = doc.getString("motivo") ?: "Sincronização",
                        isDemo = false,
                        createdAt = doc.getLongSafe("dataDeCriacao", System.currentTimeMillis())
                    )
                    db.stockMovementDao().insertMovement(mov)
                }

                // 9. Migra dados locais que ainda não estão no Firestore (evitando duplicados)
                migrateLocalDataToCloud(userId, bId)
            }

            _syncState.value = CloudSyncState.Synced()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Erro na sincronização completa", e)
            handleError(e)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 11. MIGRAÇÃO DE DADOS LOCAIS EXISTENTES (Sem duplicar)
    // =========================================================================
    private suspend fun migrateLocalDataToCloud(userId: String, businessId: String) = withContext(Dispatchers.IO) {
        try {
            // Migra negócio se necessário
            val localBiz = db.businessDao().getBusinessById(businessId).firstOrNull()
            if (localBiz != null) {
                syncBusiness(localBiz)
            }

            // Migra produtos reais
            val localProducts = db.productDao().getProductsByBusiness(businessId).firstOrNull().orEmpty()
            for (p in localProducts.filter { !it.isDemo }) {
                syncProduct(p)
            }

            // Migra clientes reais
            val localCustomers = db.customerDao().getCustomersByBusiness(businessId).firstOrNull().orEmpty()
            for (c in localCustomers.filter { !it.isDemo }) {
                syncCustomer(c)
            }

            // Migra vendas reais
            val localSales = db.saleDao().getSalesByBusiness(businessId).firstOrNull().orEmpty()
            for (s in localSales.filter { !it.sale.isDemo }) {
                syncSale(s.sale, s.items)
            }

            // Migra pagamentos
            val localPayments = db.paymentDao().getPaymentsByBusiness(businessId).firstOrNull().orEmpty()
            for (pay in localPayments.filter { !it.isDemo }) {
                syncPayment(pay)
            }

            // Migra despesas reais
            val localExpenses = db.expenseDao().getExpensesByBusiness(businessId).firstOrNull().orEmpty()
            for (e in localExpenses.filter { !it.isDemo }) {
                syncExpense(e)
            }

            // Migra movimentações de estoque reais
            val localMovements = db.stockMovementDao().getMovementsByBusiness(businessId).firstOrNull().orEmpty()
            for (m in localMovements.filter { !it.isDemo }) {
                syncStockMovement(m)
            }
        } catch (e: Exception) {
            Log.w(tag, "Aviso durante migração de dados locais: ${e.message}")
        }
    }

    // =========================================================================
    // 12. LISTENERS EM TEMPO REAL
    // =========================================================================
    fun startRealtimeSync(userId: String) {
        activeUserId = userId
        stopRealtimeSync()
        val fs = firestore ?: return
        try {
            val userDocRef = fs.collection("users").document(userId)

            // Listener de negócio
            val bizListener = userDocRef.collection("business").document("info")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    scope.launch {
                        try {
                            val bId = snapshot.getString("id") ?: userId
                            val rawLogo = snapshot.getString("logoUri")?.takeIf { it.isNotBlank() }
                            val cachedLogo = storageHelper.decodeAndCacheIfBase64(rawLogo, "logo_cached_$userId") ?: rawLogo
                            val rawCover = snapshot.getString("fotoCapaUri")?.takeIf { it.isNotBlank() }
                            val cachedCover = storageHelper.decodeAndCacheIfBase64(rawCover, "cover_cached_$userId") ?: rawCover

                            val business = BusinessEntity(
                                id = bId,
                                userId = userId,
                                name = snapshot.getString("nomeDoNegocio") ?: "Meu Negócio",
                                category = snapshot.getString("categoria") ?: "Comércio Geral",
                                phone = snapshot.getString("telefone") ?: "",
                                city = snapshot.getString("cidade") ?: "",
                                description = snapshot.getString("descricao"),
                                logoUri = cachedLogo,
                                coverPhotoUri = cachedCover,
                                coverPresetId = snapshot.getString("coverPresetId") ?: "emerald_abstract",
                                themeKey = snapshot.getString("tema") ?: "EMERALD",
                                appearanceMode = snapshot.getString("modoAparencia") ?: "SYSTEM",
                                iconStyle = snapshot.getString("estiloIcones") ?: "FILLED",
                                createdAt = snapshot.getLongSafe("dataDeCriacao", System.currentTimeMillis()),
                                updatedAt = snapshot.getLongSafe("updatedAt", System.currentTimeMillis())
                            )
                            db.businessDao().insertBusiness(business)
                        } catch (e: Exception) {
                            Log.e(tag, "Erro ao processar listener de negócio", e)
                        }
                    }
                }
            listeners.add(bizListener)

            // Listener de produtos em tempo real (multi-dispositivo)
            val productsListener = userDocRef.collection("products")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    scope.launch {
                        try {
                            for (change in snapshot.documentChanges) {
                                val doc = change.document
                                val prodId = doc.getString("id") ?: doc.id
                                when (change.type) {
                                    com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                        db.productDao().deleteProductById(prodId)
                                    }
                                    com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                    com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                        val prod = ProductEntity(
                                            id = prodId,
                                            businessId = doc.getString("businessId") ?: userId,
                                            name = doc.getString("nome") ?: "Produto",
                                            category = doc.getString("categoria") ?: "Geral",
                                            code = doc.getString("codigo")?.takeIf { it.isNotBlank() },
                                            salePrice = doc.getDoubleSafe("precoVenda", 0.0),
                                            costPrice = doc.getDoubleSafe("custo", 0.0),
                                            currentStock = doc.getDoubleSafe("estoqueAtual", 0.0),
                                            minStock = doc.getDoubleSafe("estoqueMinimo", 0.0),
                                            photoUri = doc.getString("foto")?.takeIf { it.isNotBlank() },
                                            isDemo = false,
                                            createdAt = doc.getLongSafe("dataDeCriacao", System.currentTimeMillis()),
                                            updatedAt = doc.getLongSafe("dataDeAtualizacao", System.currentTimeMillis())
                                        )
                                        db.productDao().insertProduct(prod)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(tag, "Erro ao processar listener de produtos", e)
                        }
                    }
                }
            listeners.add(productsListener)

            // Listener de clientes em tempo real (multi-dispositivo)
            val customersListener = userDocRef.collection("customers")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    scope.launch {
                        try {
                            for (change in snapshot.documentChanges) {
                                val doc = change.document
                                val custId = doc.getString("id") ?: doc.id
                                when (change.type) {
                                    com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                        db.customerDao().deleteCustomerById(custId)
                                    }
                                    com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                    com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                        val cust = CustomerEntity(
                                            id = custId,
                                            businessId = doc.getString("businessId") ?: userId,
                                            name = doc.getString("nome") ?: "Cliente",
                                            phone = doc.getString("telefone") ?: "",
                                            email = doc.getString("email")?.takeIf { it.isNotBlank() },
                                            observation = doc.getString("observacao")?.takeIf { it.isNotBlank() },
                                            isDemo = false,
                                            createdAt = doc.getLongSafe("dataDeCadastro", System.currentTimeMillis())
                                        )
                                        db.customerDao().insertCustomer(cust)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(tag, "Erro ao processar listener de clientes", e)
                        }
                    }
                }
            listeners.add(customersListener)

            // Listener de vendas em tempo real (multi-dispositivo)
            val salesListener = userDocRef.collection("sales")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    scope.launch {
                        try {
                            for (change in snapshot.documentChanges) {
                                val doc = change.document
                                val saleId = doc.getString("id") ?: doc.id
                                when (change.type) {
                                    com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                        db.saleDao().deleteSaleItemsBySaleId(saleId)
                                        db.paymentDao().deletePaymentsBySaleId(saleId)
                                        db.saleDao().deleteSaleById(saleId)
                                    }
                                    com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                    com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                        val statusRaw = doc.getString("statusDoPagamento")?.uppercase() ?: "PAGO"
                                        val sale = SaleEntity(
                                            id = saleId,
                                            businessId = doc.getString("businessId") ?: userId,
                                            date = doc.getLongSafe("data", System.currentTimeMillis()),
                                            totalAmount = doc.getDoubleSafe("valorTotal", 0.0),
                                            discountAmount = doc.getDoubleSafe("desconto", 0.0),
                                            paymentMethod = doc.getString("formaDePagamento") ?: "Dinheiro",
                                            observation = doc.getString("observacao")?.takeIf { it.isNotBlank() },
                                            customerId = doc.getString("clienteId")?.takeIf { it.isNotBlank() },
                                            customerName = doc.getString("clienteNome")?.takeIf { it.isNotBlank() },
                                            isDemo = false,
                                            createdAt = doc.getLongSafe("dataDeCriacao", System.currentTimeMillis()),
                                            paidAmount = doc.getDoubleSafe("valorPago", doc.getDoubleSafe("valorTotal", 0.0)),
                                            dueDate = doc.getLongSafe("dataDeVencimento", 0L).takeIf { it > 0 },
                                            paymentStatus = statusRaw,
                                            timeString = doc.getString("hora") ?: "",
                                            updatedAt = doc.getLongSafe("updatedAt", System.currentTimeMillis())
                                        )
                                        db.saleDao().insertSale(sale)

                                        @Suppress("UNCHECKED_CAST")
                                        val itemsRaw = doc.get("itens") as? List<Map<String, Any>>
                                        if (itemsRaw != null) {
                                            val items = itemsRaw.map { itemMap ->
                                                SaleItemEntity(
                                                    id = (itemMap["id"] as? String) ?: java.util.UUID.randomUUID().toString(),
                                                    saleId = saleId,
                                                    productId = (itemMap["productId"] as? String) ?: "",
                                                    productName = (itemMap["productName"] as? String) ?: "Item",
                                                    quantity = (itemMap["quantidade"] as? Number)?.toDouble() ?: 1.0,
                                                    unitPrice = (itemMap["valorUnitario"] as? Number)?.toDouble() ?: 0.0,
                                                    unitCost = (itemMap["custoUnitario"] as? Number)?.toDouble() ?: 0.0,
                                                    subtotal = (itemMap["subtotal"] as? Number)?.toDouble() ?: 0.0
                                                )
                                            }
                                            db.saleDao().insertSaleItems(items)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(tag, "Erro ao processar listener de vendas", e)
                        }
                    }
                }
            listeners.add(salesListener)

            // Listener de despesas em tempo real (multi-dispositivo)
            val expensesListener = userDocRef.collection("expenses")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    scope.launch {
                        try {
                            for (change in snapshot.documentChanges) {
                                val doc = change.document
                                val expId = doc.getString("id") ?: doc.id
                                when (change.type) {
                                    com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                        db.expenseDao().deleteExpenseById(expId)
                                    }
                                    com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                    com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                        val exp = ExpenseEntity(
                                            id = expId,
                                            businessId = doc.getString("businessId") ?: userId,
                                            description = doc.getString("descricao") ?: "Despesa",
                                            category = doc.getString("categoria") ?: "Outros",
                                            amount = doc.getDoubleSafe("valor", 0.0),
                                            date = doc.getLongSafe("data", System.currentTimeMillis()),
                                            observation = doc.getString("observacao")?.takeIf { it.isNotBlank() },
                                            isDemo = false,
                                            createdAt = doc.getLongSafe("dataDeCriacao", System.currentTimeMillis())
                                        )
                                        db.expenseDao().insertExpense(exp)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(tag, "Erro ao processar listener de despesas", e)
                        }
                    }
                }
            listeners.add(expensesListener)

        } catch (e: Exception) {
            Log.w(tag, "Não foi possível iniciar listeners em tempo real: ${e.message}")
        }
    }

    fun stopRealtimeSync() {
        for (listener in listeners) {
            try {
                listener.remove()
            } catch (_: Exception) {}
        }
        listeners.clear()
    }

    private fun handleError(e: Exception) {
        val msg = when {
            !_isOnline.value -> "Modo Offline: Suas alterações foram salvas localmente e serão sincronizadas quando a conexão voltar."
            e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ->
                "Modo Local Seguro: Dados salvos com segurança no aparelho."
            e.message?.contains("UNAVAILABLE", ignoreCase = true) == true ->
                "Sincronização em segundo plano. Dados salvos com segurança no aparelho."
            else -> "Dados salvos localmente no aparelho com segurança."
        }
        _syncState.value = CloudSyncState.Offline(msg)
    }
}
