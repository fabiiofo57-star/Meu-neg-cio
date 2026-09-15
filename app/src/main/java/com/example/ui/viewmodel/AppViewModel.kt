package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.GeminiVoiceAction
import com.example.data.ai.GeminiVoiceAssistant
import com.example.data.ai.VoiceActionType
import com.example.data.ai.VoicePaymentMethod
import com.example.data.auth.AuthRepository
import com.example.data.auth.AuthState
import com.example.data.demo.DemoDataSeeder
import com.example.data.local.AppDatabase
import com.example.data.model.BusinessEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.ProductEntity
import com.example.data.model.SaleEntity
import com.example.data.model.SaleItemEntity
import com.example.data.model.SalePaymentStatus
import com.example.data.model.SaleWithItems
import com.example.data.model.StockMovementEntity
import com.example.data.model.UserEntity
import com.example.data.preferences.ThemePreferencesManager
import com.example.data.repository.BusinessRepository
import com.example.data.repository.CustomerRepository
import com.example.data.repository.ExpenseRepository
import com.example.data.repository.ProductRepository
import com.example.data.repository.SaleRepository
import com.example.data.repository.StockMovementRepository
import com.example.data.sync.CloudBusinessResult
import com.example.data.sync.FirebaseStorageHelper
import com.example.data.sync.FirestoreSyncHelper
import com.example.ui.components.toBrlCurrency
import com.example.ui.theme.AppearanceMode
import com.example.ui.theme.AppIconStyle
import com.example.ui.theme.AppThemeKey
import com.example.ui.theme.AppThemeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.UUID

data class DailyChartPoint(
    val dayLabel: String,
    val timestamp: Long,
    val sales: Double,
    val expenses: Double
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    val firestoreSyncHelper = FirestoreSyncHelper(application, database)
    val authRepository = AuthRepository(application, database.userDao(), database.businessDao(), viewModelScope)
    val businessRepository = BusinessRepository(database.businessDao(), firestoreSyncHelper)
    val productRepository = ProductRepository(database.productDao(), database.stockMovementDao(), firestoreSyncHelper)
    val saleRepository = SaleRepository(database.saleDao(), database.productDao(), database.stockMovementDao(), database.paymentDao(), firestoreSyncHelper)
    val expenseRepository = ExpenseRepository(database.expenseDao(), firestoreSyncHelper)
    val customerRepository = CustomerRepository(database.customerDao(), firestoreSyncHelper)
    val paymentRepository = com.example.data.repository.PaymentRepository(database.paymentDao(), firestoreSyncHelper)
    val stockMovementRepository = StockMovementRepository(database.stockMovementDao())
    val demoDataSeeder = DemoDataSeeder(database)
    val themePreferencesManager = ThemePreferencesManager(application)
    val firebaseStorageHelper = FirebaseStorageHelper(application)

    val themeState: StateFlow<AppThemeState> = themePreferencesManager.themeState
    val authState: StateFlow<AuthState> = authRepository.authState
    val savedUsers: StateFlow<List<UserEntity>> = authRepository.savedUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Cloud Sync States
    val syncState: StateFlow<com.example.data.sync.CloudSyncState> = firestoreSyncHelper.syncState
    val isOnline: StateFlow<Boolean> = firestoreSyncHelper.isOnline

    // Feedback message for UX operations
    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    // Flag para aguardar restauração dos dados da nuvem no primeiro login em novo aparelho
    private val _isInitialSyncLoading = MutableStateFlow<Boolean>(false)
    val isInitialSyncLoading: StateFlow<Boolean> = _isInitialSyncLoading.asStateFlow()

    // Flag que indica se está verificando se o usuário já possui um negócio no banco local ou nuvem
    private val _isCheckingBusiness = MutableStateFlow<Boolean>(true)
    val isCheckingBusiness: StateFlow<Boolean> = _isCheckingBusiness.asStateFlow()

    fun setCheckingBusiness(checking: Boolean) {
        _isCheckingBusiness.value = checking
    }

    // Active Business Flow
    val currentBusiness: StateFlow<BusinessEntity?> = authState.flatMapLatest { auth ->
        when (auth) {
            is AuthState.Authenticated -> {
                businessRepository.getBusinessByUserId(auth.user.id).flatMapLatest { biz ->
                    if (biz != null) {
                        flowOf(biz)
                    } else {
                        // Se não tem negócio com esse userId específico, busca por e-mail no banco local
                        businessRepository.getBusinessByUserEmail(auth.user.email).flatMapLatest { emailBiz ->
                            if (emailBiz != null) {
                                viewModelScope.launch(Dispatchers.IO) {
                                    val linkedBiz = emailBiz.copy(userId = auth.user.id)
                                    businessRepository.saveBusiness(linkedBiz)
                                }
                                flowOf(emailBiz)
                            } else {
                                // Se ainda não encontrou, recupera QUALQUER negócio existente no aparelho
                                businessRepository.getAnyBusiness().map { anyBiz ->
                                    if (anyBiz != null) {
                                        viewModelScope.launch(Dispatchers.IO) {
                                            val linkedBiz = anyBiz.copy(userId = auth.user.id)
                                            businessRepository.saveBusiness(linkedBiz)
                                        }
                                        anyBiz
                                    } else null
                                }
                            }
                        }
                    }
                }
            }
            else -> flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Products Flow
    val products: StateFlow<List<ProductEntity>> = currentBusiness.flatMapLatest { business ->
        if (business != null) productRepository.getProducts(business.id)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Sales Flow
    val sales: StateFlow<List<SaleWithItems>> = currentBusiness.flatMapLatest { business ->
        if (business != null) saleRepository.getSales(business.id)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Expenses Flow
    val expenses: StateFlow<List<ExpenseEntity>> = currentBusiness.flatMapLatest { business ->
        if (business != null) expenseRepository.getExpenses(business.id)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Customers Flow
    val customers: StateFlow<List<CustomerEntity>> = currentBusiness.flatMapLatest { business ->
        if (business != null) customerRepository.getCustomers(business.id)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Stock Movements Flow
    val stockMovements: StateFlow<List<StockMovementEntity>> = currentBusiness.flatMapLatest { business ->
        if (business != null) stockMovementRepository.getMovements(business.id)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Financial Metrics computed reactively
    val financialSummary = combine(sales, expenses) { salesList, expensesList ->
        val now = Calendar.getInstance()
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startOfMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todaySalesTotal = salesList.filter { it.sale.date >= startOfToday }.sumOf { it.sale.totalAmount }
        val todayExpensesTotal = expensesList.filter { it.date >= startOfToday }.sumOf { it.amount }
        val todayProfit = todaySalesTotal - todayExpensesTotal

        val monthSalesTotal = salesList.filter { it.sale.date >= startOfMonth }.sumOf { it.sale.totalAmount }
        val monthExpensesTotal = expensesList.filter { it.date >= startOfMonth }.sumOf { it.amount }
        val monthProfit = monthSalesTotal - monthExpensesTotal

        // Last 7 days chart points
        val chartPoints = mutableListOf<DailyChartPoint>()
        for (i in 6 downTo 0) {
            val dayCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startDay = dayCal.timeInMillis
            val endDay = startDay + (24 * 60 * 60 * 1000) - 1

            val daySales = salesList.filter { it.sale.date in startDay..endDay }.sumOf { it.sale.totalAmount }
            val dayExpenses = expensesList.filter { it.date in startDay..endDay }.sumOf { it.amount }

            val label = when (i) {
                0 -> "Hoje"
                1 -> "Ontem"
                else -> {
                    val dayOfMonth = dayCal.get(Calendar.DAY_OF_MONTH)
                    val month = dayCal.get(Calendar.MONTH) + 1
                    String.format("%02d/%02d", dayOfMonth, month)
                }
            }
            chartPoints.add(DailyChartPoint(label, startDay, daySales, dayExpenses))
        }

        val pendingSales = salesList.filter { it.sale.remainingAmount > 0.005 }
        val totalPending = pendingSales.sumOf { it.sale.remainingAmount }
        val pendingCount = pendingSales.size
        val overdueCount = pendingSales.count { it.sale.effectiveStatus == com.example.data.model.SalePaymentStatus.ATRASADO }

        FinancialSummary(
            todaySales = todaySalesTotal,
            todayExpenses = todayExpensesTotal,
            todayProfit = todayProfit,
            monthSales = monthSalesTotal,
            monthExpenses = monthExpensesTotal,
            monthProfit = monthProfit,
            dailyChart = chartPoints,
            totalPendingToReceive = totalPending,
            pendingSalesCount = pendingCount,
            overdueSalesCount = overdueCount
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        FinancialSummary()
    )

    init {
        // Quando o usuário autenticar, isola preferências e ativa sincronização para o UID específico
        viewModelScope.launch {
            authState.collect { auth ->
                try {
                    if (auth is AuthState.Authenticated) {
                        _isCheckingBusiness.value = true
                        val userId = auth.user.id
                        val userEmail = auth.user.email
                        themePreferencesManager.switchUser(userId)
                        firestoreSyncHelper.setActiveUser(userId)
                        firestoreSyncHelper.syncUserProfile(auth.user)

                        try {
                            // 1. Imediatamente verifica se há negócio associado a este userId no banco de dados local
                            var localBiz = businessRepository.findBusinessByUserId(userId)

                            // 2. Se não encontrou por userId, verifica por e-mail no banco de dados local
                            if (localBiz == null && userEmail.isNotBlank()) {
                                val emailBiz = businessRepository.findBusinessByUserEmail(userEmail)
                                if (emailBiz != null) {
                                    val linkedBiz = emailBiz.copy(userId = userId)
                                    businessRepository.saveBusiness(linkedBiz)
                                    localBiz = linkedBiz
                                }
                            }

                            // 3. Se não encontrou por e-mail, verifica se há QUALQUER negócio já cadastrado no banco de dados do aparelho
                            if (localBiz == null) {
                                val anyBiz = businessRepository.findAnyBusiness()
                                if (anyBiz != null) {
                                    val linkedBiz = anyBiz.copy(userId = userId)
                                    businessRepository.saveBusiness(linkedBiz)
                                    localBiz = linkedBiz
                                }
                            }

                            // 4. Se não possui nenhum negócio salvo no banco local (novo login/novo aparelho), recupera da nuvem (Firestore)
                            if (localBiz == null) {
                                _isInitialSyncLoading.value = true
                                try {
                                    val cloudResult = withTimeoutOrNull(12000L) {
                                        firestoreSyncHelper.checkAndFetchCloudData(userId, userEmail)
                                    }
                                    if (cloudResult is CloudBusinessResult.Exists) {
                                        val linkedBiz = cloudResult.business.copy(userId = userId)
                                        businessRepository.saveBusiness(linkedBiz)
                                        localBiz = linkedBiz
                                    }
                                } catch (e: Exception) {
                                    Log.e("AppViewModel", "Erro ao restaurar dados da nuvem", e)
                                } finally {
                                    _isInitialSyncLoading.value = false
                                }
                            } else {
                                // Já tem negócio e dados salvos no aparelho! Inicia sincronização em segundo plano sem travar o app
                                viewModelScope.launch(Dispatchers.IO) {
                                    try {
                                        firestoreSyncHelper.fullSyncFromCloud(userId, userEmail)
                                        firestoreSyncHelper.startRealtimeSync(userId)
                                    } catch (e: Exception) {
                                        Log.w("AppViewModel", "Sync em segundo plano: ${e.message}")
                                    }
                                }
                            }

                            // 5. Garantia para usuários cadastrados: se após todas as buscas (local e nuvem)
                            // ainda não existir negócio salvo, cria o negócio imediatamente para que o usuário
                            // acesse o painel diretamente sem cair na tela de criar outra conta!
                            if (localBiz == null) {
                                val nameCandidate = auth.user.name.takeIf { it.isNotBlank() && !it.equals("Empreendedor", ignoreCase = true) }
                                    ?: userEmail.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() }
                                val autoBiz = BusinessEntity(
                                    id = "biz_${userId}",
                                    userId = userId,
                                    name = if (nameCandidate.contains("Negócio", ignoreCase = true)) nameCandidate else "Negócio do $nameCandidate",
                                    category = "Comércio Geral",
                                    phone = auth.user.phone ?: "",
                                    city = "São Paulo",
                                    email = userEmail
                                )
                                businessRepository.saveBusiness(autoBiz)
                                localBiz = autoBiz
                                Log.d("AppViewModel", "Negócio inicial auto-provisionado com sucesso para $userId ($userEmail)")
                            }
                        } finally {
                            _isCheckingBusiness.value = false
                        }
                    } else {
                        _isCheckingBusiness.value = false
                        _isInitialSyncLoading.value = false
                        themePreferencesManager.switchUser(null)
                        firestoreSyncHelper.setActiveUser(null)
                    }
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Erro no observador de autenticação", e)
                    _isCheckingBusiness.value = false
                    _isInitialSyncLoading.value = false
                }
            }
        }

        // Sincroniza foto e identidade visual do negócio com as preferências de tema ativas entre aparelhos
        viewModelScope.launch {
            try {
                currentBusiness.collect { biz ->
                    if (biz != null) {
                        try {
                            if (!biz.logoUri.isNullOrBlank() && themePreferencesManager.themeState.value.profilePhotoUri != biz.logoUri) {
                                themePreferencesManager.setProfilePhoto(biz.logoUri)
                            }
                            if (!biz.coverPhotoUri.isNullOrBlank() && themePreferencesManager.themeState.value.coverPhotoUri != biz.coverPhotoUri) {
                                themePreferencesManager.setCoverPhoto(biz.coverPhotoUri)
                            }
                        } catch (e: Exception) {
                            Log.w("AppViewModel", "Aviso ao aplicar fotos do tema: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Erro no observador de tema do negócio", e)
            }
        }
    }

    fun showFeedback(msg: String) {
        _feedbackMessage.value = msg
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun dismissInitialSyncLoading() {
        _isInitialSyncLoading.value = false
        _isCheckingBusiness.value = false
    }

    fun syncNow() {
        val auth = authState.value
        if (auth is AuthState.Authenticated) {
            viewModelScope.launch {
                showFeedback("Sincronizando com a nuvem...")
                val res = firestoreSyncHelper.fullSyncFromCloud(auth.user.id)
                if (res.isSuccess) {
                    showFeedback("Dados sincronizados com sucesso!")
                } else {
                    showFeedback("Não foi possível sincronizar agora. Verifique sua conexão.")
                }
            }
        }
    }

    fun switchTestUser(slot: String) {
        viewModelScope.launch {
            val user = authRepository.switchTestUser(slot)
            themePreferencesManager.switchUser(user.id)
            firestoreSyncHelper.setActiveUser(user.id)
            val existing = businessRepository.getBusinessByUserId(user.id).firstOrNull()
            if (existing == null) {
                val isB = slot.equals("B", ignoreCase = true)
                val newBiz = BusinessEntity(
                    id = if (isB) "biz_test_user_b" else "biz_test_user_a",
                    userId = user.id,
                    name = if (isB) "Oficina do Usuário B" else "Padaria do Usuário A",
                    category = if (isB) "Serviços Automotivos" else "Panificação & Confeitaria",
                    phone = "(11) 98765-4321",
                    city = if (isB) "Rio de Janeiro" else "São Paulo",
                    coverPresetId = if (isB) "gradient_sapphire" else "gradient_emerald"
                )
                businessRepository.saveBusiness(newBiz)
            }
            firestoreSyncHelper.fullSyncFromCloud(user.id)
            firestoreSyncHelper.startRealtimeSync(user.id)
            showFeedback("Alternado para ${user.name}. Dados 100% isolados!")
        }
    }

    fun signOut() {
        _isCheckingBusiness.value = false
        _isInitialSyncLoading.value = false
        themePreferencesManager.switchUser(null)
        firestoreSyncHelper.setActiveUser(null)
        authRepository.signOut()
    }

    // Business actions
    fun createBusiness(name: String, category: String, phone: String, city: String, logoUri: String? = null) {
        val auth = authState.value
        if (auth is AuthState.Authenticated) {
            viewModelScope.launch(Dispatchers.IO) {
                val business = BusinessEntity(
                    userId = auth.user.id,
                    name = name,
                    category = category,
                    phone = phone,
                    city = city,
                    logoUri = logoUri
                )
                businessRepository.saveBusiness(business)
                showFeedback("Negócio configurado com sucesso!")
            }
        }
    }

    fun updateBusiness(business: BusinessEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            businessRepository.updateBusiness(business)
            showFeedback("Dados do negócio atualizados!")
        }
    }

    // Product actions
    fun saveProduct(
        id: String? = null,
        name: String,
        category: String,
        code: String?,
        salePrice: Double,
        costPrice: Double,
        currentStock: Double,
        minStock: Double,
        photoUri: String? = null
    ) {
        val business = currentBusiness.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (id == null) {
                val newProduct = ProductEntity(
                    businessId = business.id,
                    name = name,
                    category = category,
                    code = code,
                    salePrice = salePrice,
                    costPrice = costPrice,
                    currentStock = currentStock,
                    minStock = minStock,
                    photoUri = photoUri
                )
                productRepository.insertProduct(newProduct)
                showFeedback("Produto adicionado.")
            } else {
                val updated = ProductEntity(
                    id = id,
                    businessId = business.id,
                    name = name,
                    category = category,
                    code = code,
                    salePrice = salePrice,
                    costPrice = costPrice,
                    currentStock = currentStock,
                    minStock = minStock,
                    photoUri = photoUri
                )
                productRepository.updateProduct(updated)
                showFeedback("Produto atualizado.")
            }
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            productRepository.deleteProduct(product)
            showFeedback("Produto removido.")
        }
    }

    fun adjustStock(productId: String, type: String, quantity: Double, reason: String, allowNegative: Boolean) {
        val business = currentBusiness.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = productRepository.adjustStock(
                businessId = business.id,
                productId = productId,
                type = type,
                quantity = quantity,
                reason = reason,
                allowNegative = allowNegative
            )
            result.onSuccess {
                showFeedback("Estoque atualizado.")
            }.onFailure { err ->
                showFeedback(err.message ?: "Erro ao atualizar estoque")
            }
        }
    }

    // Sale actions
    fun registerSale(
        items: List<SaleItemEntity>,
        discount: Double,
        paymentMethod: String,
        date: Long,
        observation: String?,
        customerId: String?,
        customerName: String?,
        paidAmount: Double = 0.0,
        dueDate: Long? = null
    ) {
        val business = currentBusiness.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            saleRepository.registerSale(
                businessId = business.id,
                items = items,
                discount = discount,
                paymentMethod = paymentMethod,
                date = date,
                observation = observation,
                customerId = customerId,
                customerName = customerName,
                paidAmount = paidAmount,
                dueDate = dueDate
            )
            showFeedback("Venda registrada com sucesso.")
        }
    }

    fun recordSalePayment(sale: SaleEntity, amount: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            saleRepository.recordPartialPayment(sale, amount)
            showFeedback("Pagamento de ${amount.toBrlCurrency()} registrado com sucesso!")
        }
    }

    fun deleteSale(saleWithItems: SaleWithItems) {
        viewModelScope.launch(Dispatchers.IO) {
            saleRepository.deleteSale(saleWithItems)
            showFeedback("Venda cancelada e removida.")
        }
    }

    // Expense actions
    fun saveExpense(
        id: String? = null,
        description: String,
        category: String,
        amount: Double,
        date: Long,
        observation: String?
    ) {
        val business = currentBusiness.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (id == null) {
                val expense = ExpenseEntity(
                    businessId = business.id,
                    description = description,
                    category = category,
                    amount = amount,
                    date = date,
                    observation = observation
                )
                expenseRepository.insertExpense(expense)
                showFeedback("Despesa registrada.")
            } else {
                val updated = ExpenseEntity(
                    id = id,
                    businessId = business.id,
                    description = description,
                    category = category,
                    amount = amount,
                    date = date,
                    observation = observation
                )
                expenseRepository.updateExpense(updated)
                showFeedback("Despesa atualizada.")
            }
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            expenseRepository.deleteExpense(expense)
            showFeedback("Despesa excluída.")
        }
    }

    // Customer actions
    fun saveCustomer(
        id: String? = null,
        name: String,
        phone: String,
        email: String?,
        observation: String?,
        cpfCnpj: String? = null,
        address: String? = null
    ) {
        val business = currentBusiness.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (id == null) {
                val customer = CustomerEntity(
                    businessId = business.id,
                    name = name,
                    phone = phone,
                    email = email,
                    observation = observation,
                    cpfCnpj = cpfCnpj,
                    address = address
                )
                customerRepository.insertCustomer(customer)
                showFeedback("Cliente cadastrado.")
            } else {
                val updated = CustomerEntity(
                    id = id,
                    businessId = business.id,
                    name = name,
                    phone = phone,
                    email = email,
                    observation = observation,
                    cpfCnpj = cpfCnpj,
                    address = address
                )
                customerRepository.updateCustomer(updated)
                showFeedback("Cliente atualizado.")
            }
        }
    }

    fun deleteCustomer(customer: CustomerEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            customerRepository.deleteCustomer(customer)
            showFeedback("Cliente removido.")
        }
    }

    // Demo Data management
    fun insertDemoData() {
        val business = currentBusiness.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            demoDataSeeder.seedDemoData(business.id)
            showFeedback("Dados de teste inseridos com sucesso!")
        }
    }

    fun clearDemoData() {
        val business = currentBusiness.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            demoDataSeeder.clearDemoData(business.id)
            showFeedback("Dados fictícios de teste removidos.")
        }
    }

    // Theme & Visual Personalization Management
    fun setThemeKey(themeKey: AppThemeKey) {
        themePreferencesManager.setThemeKey(themeKey)
        showFeedback("Tema ${themeKey.title} aplicado!")
    }

    fun setAppearanceMode(mode: AppearanceMode) {
        themePreferencesManager.setAppearanceMode(mode)
        showFeedback("Modo ${mode.title} aplicado!")
    }

    fun setIconStyle(style: AppIconStyle) {
        themePreferencesManager.setIconStyle(style)
        showFeedback("Estilo de ícones ${style.title} aplicado!")
    }

    fun setCoverPreset(presetId: String) {
        themePreferencesManager.setCoverPreset(presetId)
        showFeedback("Capa elegante aplicada!")
    }

    fun updateCoverPhoto(uri: String?) {
        themePreferencesManager.setCoverPhoto(uri)
        val business = currentBusiness.value
        if (business != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val updated = business.copy(coverPhotoUri = uri)
                businessRepository.updateBusiness(updated)
            }
        }
        showFeedback(if (uri == null) "Foto de capa removida." else "Foto de capa aplicada!")
    }

    fun updateThemeState(state: AppThemeState) {
        themePreferencesManager.updateState(state)
        showFeedback("Aparência atualizada!")
    }

    fun restoreDefaultAppearance() {
        themePreferencesManager.restoreDefaults()
        showFeedback("Aparência padrão restaurada com sucesso!")
    }

    // Photo & Business Profile Management
    suspend fun saveImageAndGetUri(uri: android.net.Uri): String {
        val auth = authState.value
        val userId = if (auth is AuthState.Authenticated) auth.user.id else "local_user"
        val fileName = "logo_${System.currentTimeMillis()}"
        return firebaseStorageHelper.saveImageLocallyAndSync(uri, fileName, userId)
    }

    suspend fun saveCoverImageAndGetUri(uri: android.net.Uri): String {
        val auth = authState.value
        val userId = if (auth is AuthState.Authenticated) auth.user.id else "local_user"
        val fileName = "cover_${System.currentTimeMillis()}"
        return firebaseStorageHelper.saveImageLocallyAndSync(uri, fileName, userId)
    }

    fun updateBusinessLogo(logoUri: String?) {
        val business = currentBusiness.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = business.copy(logoUri = logoUri)
            businessRepository.updateBusiness(updated)
            themePreferencesManager.setProfilePhoto(logoUri)

            // Sincroniza também com o perfil de usuário
            val auth = authState.value
            if (auth is AuthState.Authenticated) {
                val updatedUser = auth.user.copy(photoUrl = logoUri)
                authRepository.updateUser(updatedUser)
                firestoreSyncHelper.syncUserProfile(updatedUser)
            }
            showFeedback(if (logoUri == null) "Foto removida." else "Foto do perfil atualizada!")
        }
    }

    fun updateBusinessProfile(
        name: String,
        category: String,
        phone: String,
        city: String,
        description: String?,
        logoUri: String?
    ) {
        val business = currentBusiness.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = business.copy(
                name = name,
                category = category,
                phone = phone,
                city = city,
                description = description,
                logoUri = logoUri
            )
            businessRepository.updateBusiness(updated)
            showFeedback("Identidade do negócio salva com sucesso!")
        }
    }

    // =========================================================================
    // GEMINI AI ASSISTANT (COMANDOS POR VOZ / TEXTO INTELIGENTE)
    // =========================================================================
    val geminiVoiceAssistant = GeminiVoiceAssistant(getApplication())
    val appVoiceSpeaker = com.example.data.ai.AppVoiceSpeaker(getApplication())

    private val _isVoiceAssistantOpen = MutableStateFlow(false)
    val isVoiceAssistantOpen: StateFlow<Boolean> = _isVoiceAssistantOpen.asStateFlow()

    private val _isVoiceProcessing = MutableStateFlow(false)
    val isVoiceProcessing: StateFlow<Boolean> = _isVoiceProcessing.asStateFlow()

    private val _voiceActionResult = MutableStateFlow<GeminiVoiceAction?>(null)
    val voiceActionResult: StateFlow<GeminiVoiceAction?> = _voiceActionResult.asStateFlow()

    private val _voiceErrorMessage = MutableStateFlow<String?>(null)
    val voiceErrorMessage: StateFlow<String?> = _voiceErrorMessage.asStateFlow()

    val isVoiceSpeaking: StateFlow<Boolean> = appVoiceSpeaker.isSpeaking

    fun speakVoiceText(text: String) {
        appVoiceSpeaker.speak(text)
    }

    fun stopVoiceSpeaking() {
        appVoiceSpeaker.stop()
    }

    fun openVoiceAssistant() {
        appVoiceSpeaker.stop()
        _voiceActionResult.value = null
        _voiceErrorMessage.value = null
        _isVoiceProcessing.value = false
        _isVoiceAssistantOpen.value = true
    }

    fun closeVoiceAssistant() {
        appVoiceSpeaker.stop()
        _isVoiceAssistantOpen.value = false
        _isVoiceProcessing.value = false
        _voiceActionResult.value = null
        _voiceErrorMessage.value = null
    }

    fun processVoiceInput(spokenText: String) {
        val trimmed = spokenText.trim()
        if (trimmed.isBlank()) return

        appVoiceSpeaker.stop()
        viewModelScope.launch {
            _isVoiceProcessing.value = true
            _voiceErrorMessage.value = null
            _voiceActionResult.value = null
            try {
                val action = geminiVoiceAssistant.parseVoiceInput(trimmed)
                _voiceActionResult.value = action
                val toSpeak = action.spokenResponse ?: action.summary
                if (toSpeak.isNotBlank()) {
                    appVoiceSpeaker.speak(toSpeak)
                }
            } catch (e: Exception) {
                _voiceErrorMessage.value = "Erro ao interpretar fala: ${e.localizedMessage}"
            } finally {
                _isVoiceProcessing.value = false
            }
        }
    }

    fun confirmVoiceAction(action: GeminiVoiceAction) {
        closeVoiceAssistant()
        val business = currentBusiness.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (action.actionType) {
                    VoiceActionType.SALE -> {
                        // 1. Cliente
                        var custId: String? = null
                        var custName = action.customerName
                        if (!custName.isNullOrBlank()) {
                            val existingCustomers = customers.value
                            val match = existingCustomers.firstOrNull { it.name.equals(custName, ignoreCase = true) }
                            if (match != null) {
                                custId = match.id
                                custName = match.name
                            } else {
                                val newCust = CustomerEntity(
                                    businessId = business.id,
                                    name = custName,
                                    phone = ""
                                )
                                customerRepository.insertCustomer(newCust)
                                custId = newCust.id
                            }
                        }

                        // 2. Produtos / Itens
                        val existingProducts = products.value
                        val saleItems = mutableListOf<SaleItemEntity>()

                        val itemsToProcess = if (action.items.isNotEmpty()) {
                            action.items
                        } else {
                            listOf(
                                com.example.data.ai.GeminiVoiceSaleItem(
                                    productName = "Item Avulso",
                                    quantity = 1.0,
                                    unitPrice = action.totalAmount ?: 0.0
                                )
                            )
                        }

                        for (item in itemsToProcess) {
                            var product = existingProducts.firstOrNull { it.name.equals(item.productName, ignoreCase = true) }
                            if (product == null) {
                                val newProd = ProductEntity(
                                    businessId = business.id,
                                    name = item.productName,
                                    category = "Geral",
                                    salePrice = item.unitPrice,
                                    costPrice = item.unitPrice * 0.6,
                                    currentStock = 50.0,
                                    minStock = 5.0
                                )
                                productRepository.insertProduct(newProd)
                                product = newProd
                            }

                            val unitPrice = if (item.unitPrice > 0) item.unitPrice else product.salePrice
                            saleItems.add(
                                SaleItemEntity(
                                    saleId = "",
                                    productId = product.id,
                                    productName = product.name,
                                    quantity = item.quantity,
                                    unitPrice = unitPrice,
                                    unitCost = product.costPrice,
                                    subtotal = item.quantity * unitPrice
                                )
                            )
                        }

                        val total = if (action.totalAmount != null && action.totalAmount > 0) {
                            action.totalAmount
                        } else {
                            saleItems.sumOf { it.subtotal }
                        }

                        val isAPrazo = action.paymentMethod == VoicePaymentMethod.A_PRAZO || action.paymentStatus == SalePaymentStatus.PENDENTE
                        val paid = if (isAPrazo) (action.paidAmount ?: 0.0) else total
                        val dueDate = if (isAPrazo && action.daysUntilDue != null) {
                            System.currentTimeMillis() + (action.daysUntilDue * 86400000L)
                        } else null

                        saleRepository.registerSale(
                            businessId = business.id,
                            items = saleItems,
                            discount = 0.0,
                            paymentMethod = action.paymentMethod.label,
                            date = System.currentTimeMillis(),
                            observation = "Preenchido via IA Gemini: \"${action.rawSpokenText}\"",
                            customerId = custId,
                            customerName = custName,
                            paidAmount = paid,
                            dueDate = dueDate
                        )
                        showFeedback("Venda registrada com sucesso pela IA!")
                    }

                    VoiceActionType.PRODUCT -> {
                        val name = action.productName ?: "Novo Produto"
                        val price = action.productPrice ?: 0.0
                        val cost = action.productCostPrice ?: (price * 0.6)
                        val stock = action.productStock ?: 10.0
                        val cat = action.productCategory ?: "Geral"

                        val product = ProductEntity(
                            businessId = business.id,
                            name = name,
                            category = cat,
                            salePrice = price,
                            costPrice = cost,
                            currentStock = stock,
                            minStock = 5.0
                        )
                        productRepository.insertProduct(product)
                        showFeedback("Produto \"$name\" cadastrado com sucesso pela IA!")
                    }

                    VoiceActionType.EXPENSE -> {
                        val desc = action.expenseDescription ?: "Despesa"
                        val amount = action.expenseAmount ?: 0.0
                        val cat = action.expenseCategory ?: "Geral"

                        val expense = ExpenseEntity(
                            businessId = business.id,
                            description = desc,
                            category = cat,
                            amount = amount,
                            date = System.currentTimeMillis(),
                            observation = "Preenchido via IA Gemini: \"${action.rawSpokenText}\""
                        )
                        expenseRepository.insertExpense(expense)
                        showFeedback("Despesa \"$desc\" lançada com sucesso pela IA!")
                    }

                    VoiceActionType.ADVICE -> {
                        showFeedback("Dica anotada com sucesso!")
                    }

                    VoiceActionType.UNKNOWN -> {
                        showFeedback("A IA não conseguiu identificar os campos. Tente novamente.")
                    }
                }
                closeVoiceAssistant()
            } catch (e: Exception) {
                showFeedback("Erro ao registrar ação: ${e.localizedMessage}")
            }
        }
    }

    fun restoreUserData() {
        val auth = authState.value
        if (auth is AuthState.Authenticated) {
            viewModelScope.launch(Dispatchers.IO) {
                _isCheckingBusiness.value = true
                _isInitialSyncLoading.value = true
                try {
                    val result = firestoreSyncHelper.checkAndFetchCloudData(auth.user.id, auth.user.email)
                    if (result is com.example.data.sync.CloudBusinessResult.Exists) {
                        businessRepository.saveBusiness(result.business.copy(userId = auth.user.id))
                        showFeedback("Dados restaurados com sucesso!")
                    } else {
                        val nameCandidate = auth.user.name.takeIf { it.isNotBlank() && !it.equals("Empreendedor", ignoreCase = true) }
                            ?: auth.user.email.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() }
                        val autoBiz = BusinessEntity(
                            id = "biz_${auth.user.id}",
                            userId = auth.user.id,
                            name = if (nameCandidate.contains("Negócio", ignoreCase = true)) nameCandidate else "Negócio do $nameCandidate",
                            category = "Comércio Geral",
                            phone = auth.user.phone ?: "",
                            city = "São Paulo",
                            email = auth.user.email
                        )
                        businessRepository.saveBusiness(autoBiz)
                        showFeedback("Conta ativada com sucesso!")
                    }
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Erro ao restaurar dados", e)
                    showFeedback("Erro ao restaurar: ${e.message}")
                } finally {
                    _isInitialSyncLoading.value = false
                    _isCheckingBusiness.value = false
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        appVoiceSpeaker.shutdown()
    }
}

data class FinancialSummary(
    val todaySales: Double = 0.0,
    val todayExpenses: Double = 0.0,
    val todayProfit: Double = 0.0,
    val monthSales: Double = 0.0,
    val monthExpenses: Double = 0.0,
    val monthProfit: Double = 0.0,
    val dailyChart: List<DailyChartPoint> = emptyList(),
    val totalPendingToReceive: Double = 0.0,
    val pendingSalesCount: Int = 0,
    val overdueSalesCount: Int = 0
)
