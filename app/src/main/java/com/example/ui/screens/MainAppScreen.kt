package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BusinessEntity
import com.example.ui.components.GeminiVoiceDialog
import com.example.ui.navigation.BottomNavItem
import com.example.ui.screens.customization.CustomizationScreen
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.finance.ExpenseFormDialog
import com.example.ui.screens.finance.FinanceScreen
import com.example.ui.screens.more.CustomersScreen
import com.example.ui.screens.more.MoreSettingsScreen
import com.example.ui.screens.products.ProductFormDialog
import com.example.ui.screens.products.ProductsScreen
import com.example.ui.screens.reports.ReportsScreen
import com.example.ui.screens.sales.NewSaleDialog
import com.example.ui.screens.sales.SalesScreen
import com.example.ui.viewmodel.AppViewModel

@Composable
fun MainAppScreen(
    viewModel: AppViewModel,
    business: BusinessEntity,
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(BottomNavItem.INICIO) }
    var showCustomersView by remember { mutableStateOf(false) }
    var showCustomizationView by remember { mutableStateOf(false) }
    var showReportsView by remember { mutableStateOf(false) }

    // Global Action Dialog states
    var showNewSaleDialog by remember { mutableStateOf(false) }
    var showNewExpenseDialog by remember { mutableStateOf(false) }
    var showNewProductDialog by remember { mutableStateOf(false) }

    val products by viewModel.products.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val themeState by viewModel.themeState.collectAsStateWithLifecycle()

    val isVoiceAssistantOpen by viewModel.isVoiceAssistantOpen.collectAsStateWithLifecycle()
    val isVoiceProcessing by viewModel.isVoiceProcessing.collectAsStateWithLifecycle()
    val voiceActionResult by viewModel.voiceActionResult.collectAsStateWithLifecycle()
    val voiceErrorMessage by viewModel.voiceErrorMessage.collectAsStateWithLifecycle()
    val isVoiceSpeaking by viewModel.isVoiceSpeaking.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!showCustomersView && !showCustomizationView && !showReportsView) {
                // Círculozinho da assistente pessoal de negócios
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .clickable { viewModel.openVoiceAssistant() }
                        .testTag("btn_circle_business_assistant")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Sua assistente pessoal de negócios",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                BottomNavItem.entries.forEach { item ->
                    val isSelected = selectedTab == item && !showCustomersView && !showCustomizationView && !showReportsView
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            selectedTab = item
                            showCustomersView = false
                            showCustomizationView = false
                            showReportsView = false
                        },
                        icon = {
                            Icon(
                                imageVector = item.getIcon(themeState.iconStyle),
                                contentDescription = item.title
                            )
                        },
                        label = {
                            Text(
                                text = item.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag(item.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (showReportsView) {
                val currentBusiness by viewModel.currentBusiness.collectAsStateWithLifecycle()
                ReportsScreen(
                    viewModel = viewModel,
                    business = currentBusiness ?: business,
                    onBack = { showReportsView = false }
                )
            } else if (showCustomizationView) {
                CustomizationScreen(
                    viewModel = viewModel,
                    business = business,
                    onBack = { showCustomizationView = false }
                )
            } else if (showCustomersView) {
                CustomersScreen(
                    viewModel = viewModel,
                    onBack = { showCustomersView = false }
                )
            } else {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "tab_animation"
                ) { tab ->
                    when (tab) {
                        BottomNavItem.INICIO -> DashboardScreen(
                            viewModel = viewModel,
                            business = business,
                            onNavigateToSales = { selectedTab = BottomNavItem.VENDAS },
                            onNavigateToProducts = { selectedTab = BottomNavItem.PRODUTOS },
                            onNavigateToFinance = { selectedTab = BottomNavItem.FINANCEIRO },
                            onNavigateToCustomers = { showCustomersView = true },
                            onNavigateToCustomization = { showCustomizationView = true },
                            onNavigateToReports = { showReportsView = true },
                            onOpenNewSaleDialog = { showNewSaleDialog = true },
                            onOpenNewExpenseDialog = { showNewExpenseDialog = true },
                            onOpenNewProductDialog = { showNewProductDialog = true }
                        )

                        BottomNavItem.VENDAS -> SalesScreen(
                            viewModel = viewModel,
                            onOpenNewSale = { showNewSaleDialog = true }
                        )

                        BottomNavItem.PRODUTOS -> ProductsScreen(
                            viewModel = viewModel,
                            onOpenNewProduct = { showNewProductDialog = true }
                        )

                        BottomNavItem.FINANCEIRO -> FinanceScreen(
                            viewModel = viewModel,
                            onOpenNewExpense = { showNewExpenseDialog = true }
                        )

                        BottomNavItem.MAIS -> MoreSettingsScreen(
                            viewModel = viewModel,
                            business = business,
                            onNavigateToCustomers = { showCustomersView = true },
                            onNavigateToCustomization = { showCustomizationView = true },
                            onNavigateToReports = { showReportsView = true },
                            onLogout = onLogout
                        )
                    }
                }
            }
        }
    }

    // Global Modal Dialogs
    if (showNewSaleDialog) {
        NewSaleDialog(
            availableProducts = products,
            availableCustomers = customers,
            onDismiss = { showNewSaleDialog = false },
            onConfirmSale = { items, discount, paymentMethod, date, obs, custId, custName, paidAmount, dueDate ->
                viewModel.registerSale(items, discount, paymentMethod, date, obs, custId, custName, paidAmount, dueDate)
            }
        )
    }

    if (showNewExpenseDialog) {
        ExpenseFormDialog(
            onDismiss = { showNewExpenseDialog = false },
            onSaveExpense = { id, desc, category, amount, date, obs ->
                viewModel.saveExpense(id, desc, category, amount, date, obs)
            }
        )
    }

    if (showNewProductDialog) {
        ProductFormDialog(
            onDismiss = { showNewProductDialog = false },
            onSaveProduct = { id, name, category, code, salePrice, costPrice, stock, minStock ->
                viewModel.saveProduct(id, name, category, code, salePrice, costPrice, stock, minStock)
            }
        )
    }

    GeminiVoiceDialog(
        isOpen = isVoiceAssistantOpen,
        isProcessing = isVoiceProcessing,
        result = voiceActionResult,
        errorMessage = voiceErrorMessage,
        isSpeaking = isVoiceSpeaking,
        onSpeakText = { text -> viewModel.speakVoiceText(text) },
        onStopSpeaking = { viewModel.stopVoiceSpeaking() },
        onDismiss = { viewModel.closeVoiceAssistant() },
        onSubmitSpokenText = { spokenText -> viewModel.processVoiceInput(spokenText) },
        onConfirmAction = { action -> viewModel.confirmVoiceAction(action) }
    )
}
