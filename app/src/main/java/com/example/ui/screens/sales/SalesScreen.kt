package com.example.ui.screens.sales

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SaleEntity
import com.example.data.model.SalePaymentStatus
import com.example.data.model.SaleWithItems
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EmptyStateView
import com.example.ui.components.StatusPill
import com.example.ui.components.toBrlCurrency
import com.example.ui.components.toFormattedDate
import com.example.ui.components.toFormattedDateTime
import com.example.ui.theme.AppIconSet
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.AppViewModel

enum class SaleFilter(val label: String) {
    TODAS("Todas"),
    PAGAS("Pagas"),
    A_RECEBER("A Receber"),
    ATRASADAS("Atrasadas")
}

@Composable
fun SalesScreen(
    viewModel: AppViewModel,
    onOpenNewSale: () -> Unit
) {
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val themeState by viewModel.themeState.collectAsStateWithLifecycle()
    val currentBusiness by viewModel.currentBusiness.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(SaleFilter.TODAS) }
    var saleToDelete by remember { mutableStateOf<SaleWithItems?>(null) }
    var saleToRecordPayment by remember { mutableStateOf<SaleEntity?>(null) }
    var saleForReceipt by remember { mutableStateOf<SaleWithItems?>(null) }

    val filteredSales = remember(sales, searchQuery, selectedFilter) {
        sales.filter { saleWithItems ->
            val sale = saleWithItems.sale
            val matchesSearch = if (searchQuery.isBlank()) true else {
                saleWithItems.items.any { it.productName.contains(searchQuery, ignoreCase = true) } ||
                (sale.customerName?.contains(searchQuery, ignoreCase = true) == true) ||
                sale.paymentMethod.contains(searchQuery, ignoreCase = true)
            }

            val matchesFilter = when (selectedFilter) {
                SaleFilter.TODAS -> true
                SaleFilter.PAGAS -> sale.effectiveStatus == SalePaymentStatus.PAGO
                SaleFilter.A_RECEBER -> sale.remainingAmount > 0.005
                SaleFilter.ATRASADAS -> sale.effectiveStatus == SalePaymentStatus.ATRASADO
            }

            matchesSearch && matchesFilter
        }
    }

    val totalRevenue = remember(sales) { sales.sumOf { it.sale.totalAmount } }
    val totalPending = remember(sales) { sales.sumOf { it.sale.remainingAmount } }
    val overdueCount = remember(sales) { sales.count { it.sale.effectiveStatus == SalePaymentStatus.ATRASADO } }
    val pendingCount = remember(sales) { sales.count { it.sale.remainingAmount > 0.005 } }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenNewSale,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("fab_new_sale")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = AppIconSet.sales(themeState.iconStyle),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Nova Venda",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Histórico de Vendas",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${sales.size} vendas registradas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = totalRevenue.toBrlCurrency(),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Pending Alert Banner (if pending credit sales exist)
            if (totalPending > 0) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (overdueCount > 0) ExpenseRed.copy(alpha = 0.08f) else WarningAmber.copy(alpha = 0.1f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (overdueCount > 0) ExpenseRed.copy(alpha = 0.35f) else WarningAmber.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (overdueCount > 0) Icons.Default.Warning else Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = if (overdueCount > 0) ExpenseRed else WarningAmber,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Valores a Receber: ${totalPending.toBrlCurrency()}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (overdueCount > 0) ExpenseRed else WarningAmber
                                    )
                                    Text(
                                        text = "$pendingCount venda(s) a prazo" + if (overdueCount > 0) " • $overdueCount ATRASADA(S)" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = { selectedFilter = SaleFilter.A_RECEBER },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (overdueCount > 0) ExpenseRed else WarningAmber,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Filtrar", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar por produto, cliente ou forma de pagamento...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_sales_input"),
                    shape = RoundedCornerShape(14.dp)
                )
            }

            // Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(SaleFilter.values()) { filter ->
                        val isSel = selectedFilter == filter
                        val badgeText = when (filter) {
                            SaleFilter.A_RECEBER -> if (pendingCount > 0) "$pendingCount" else null
                            SaleFilter.ATRASADAS -> if (overdueCount > 0) "$overdueCount" else null
                            else -> null
                        }
                        val badgeColor = if (filter == SaleFilter.ATRASADAS) ExpenseRed else WarningAmber

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSel) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedFilter = filter }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = filter.label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (badgeText != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSel) Color.White.copy(alpha = 0.25f) else badgeColor
                                    ) {
                                        Text(
                                            text = badgeText,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Empty state if none
            if (filteredSales.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Default.PointOfSale,
                        title = if (searchQuery.isBlank()) "Nenhuma venda com este filtro" else "Nenhum resultado para '$searchQuery'",
                        description = if (searchQuery.isBlank()) "Toque no botão abaixo para registrar uma nova venda." else "Tente buscar por outro termo ou limpar os filtros.",
                        actionText = if (searchQuery.isBlank()) "+ Nova Venda" else null,
                        onActionClick = onOpenNewSale
                    )
                }
            } else {
                items(filteredSales, key = { it.sale.id }) { saleWithItems ->
                    val sale = saleWithItems.sale
                    val status = sale.effectiveStatus
                    val isCreditSale = sale.paymentMethod.equals("A Prazo", ignoreCase = true)
                    val remaining = sale.remainingAmount

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .testTag("sale_card_${sale.id.take(6)}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .border(
                                    width = if (status == SalePaymentStatus.ATRASADO) 1.5.dp else 1.dp,
                                    color = if (status == SalePaymentStatus.ATRASADO) ExpenseRed.copy(alpha = 0.5f)
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(16.dp)
                        ) {
                            // Top row: Payment Method + Status Pill + Date + Delete Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Status Pill (PAGO, PAGO PARCIAL, PENDENTE, ATRASADO)
                                    val (statusBg, statusFg, statusIcon) = when (status) {
                                        SalePaymentStatus.PAGO -> Triple(ProfitGreen.copy(alpha = 0.15f), ProfitGreen, Icons.Default.CheckCircle)
                                        SalePaymentStatus.PAGO_PARCIAL -> Triple(InfoBlue.copy(alpha = 0.15f), InfoBlue, Icons.Default.Payments)
                                        SalePaymentStatus.PENDENTE -> Triple(WarningAmber.copy(alpha = 0.15f), WarningAmber, Icons.Default.HourglassBottom)
                                        SalePaymentStatus.ATRASADO -> Triple(ExpenseRed.copy(alpha = 0.15f), ExpenseRed, Icons.Default.Warning)
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = statusBg
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = statusIcon,
                                                contentDescription = null,
                                                tint = statusFg,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = status.label.uppercase(),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                color = statusFg
                                            )
                                        }
                                    }

                                    // Method pill
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = sale.paymentMethod,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { saleToDelete = saleWithItems },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("delete_sale_button_${sale.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Cancelar e Excluir Venda",
                                        tint = ExpenseRed.copy(alpha = 0.85f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Date & Customer
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sale.date.toFormattedDateTime(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (!sale.customerName.isNullOrBlank()) {
                                    Text(
                                        text = "Cliente: ${sale.customerName}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Products list
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                saleWithItems.items.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${item.quantity.toInt()}x ${item.productName}",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = item.subtotal.toBrlCurrency(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Optional discount or observation
                            if (sale.discountAmount > 0 || !sale.observation.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (sale.discountAmount > 0) {
                                        Text(
                                            text = "Desconto: -${sale.discountAmount.toBrlCurrency()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ExpenseRed
                                        )
                                    }
                                    if (!sale.observation.isNullOrBlank()) {
                                        Text(
                                            text = "Obs: ${sale.observation}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // If A Prazo: show detailed progress & due date
                            if (isCreditSale || remaining > 0.005) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Pago: ${sale.paidAmount.toBrlCurrency()}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = ProfitGreen
                                            )
                                            Text(
                                                text = "Restante: ${remaining.toBrlCurrency()}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (remaining > 0) WarningAmber else ProfitGreen
                                                )
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        val progress = if (sale.totalAmount > 0) (sale.paidAmount / sale.totalAmount).toFloat().coerceIn(0f, 1f) else 1f
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = if (progress >= 1f) ProfitGreen else MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Due date text
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val dueText = if (sale.dueDate != null) {
                                                if (status == SalePaymentStatus.ATRASADO) {
                                                    "Venceu em: ${sale.dueDate.toFormattedDate()} (Atrasado!)"
                                                } else {
                                                    "Vencimento: ${sale.dueDate.toFormattedDate()}"
                                                }
                                            } else {
                                                "Sem data de vencimento fixada"
                                            }

                                            Text(
                                                text = dueText,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = if (status == SalePaymentStatus.ATRASADO) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (status == SalePaymentStatus.ATRASADO) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            )

                                            if (remaining > 0.005) {
                                                Button(
                                                    onClick = { saleToRecordPayment = sale },
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Dar Baixa", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                            Spacer(modifier = Modifier.height(8.dp))

                            // Total bottom row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Valor Total da Venda",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = sale.totalAmount.toBrlCurrency(),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        color = if (status == SalePaymentStatus.PAGO) ProfitGreen else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { saleForReceipt = saleWithItems },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("btn_receipt_${sale.id.take(6)}")
                                ) {
                                    Icon(
                                        Icons.Default.ReceiptLong,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = EmeraldPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Gerar Comprovante",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = EmeraldPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog: Confirm Delete Sale
    if (saleToDelete != null) {
        ConfirmDeleteDialog(
            title = "Cancelar e Excluir Venda?",
            message = "Esta ação removerá a venda de ${saleToDelete!!.sale.totalAmount.toBrlCurrency()} e devolverá os produtos vendidos de volta ao estoque.",
            onConfirm = {
                viewModel.deleteSale(saleToDelete!!)
                saleToDelete = null
            },
            onDismiss = { saleToDelete = null }
        )
    }

    // Modal Dialog: Record Payment for Credit Sale ("Dar Baixa / Receber")
    if (saleToRecordPayment != null) {
        val targetSale = saleToRecordPayment!!
        val remaining = targetSale.remainingAmount
        var paymentInput by remember { mutableStateOf(String.format(java.util.Locale.US, "%.2f", remaining)) }
        var paymentError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { saleToRecordPayment = null },
            title = {
                Text(
                    text = "Registrar Pagamento",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Venda #${targetSale.id.take(6).uppercase()}" + if (!targetSale.customerName.isNullOrBlank()) " • Cliente: ${targetSale.customerName}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total:", style = MaterialTheme.typography.bodySmall)
                                Text(targetSale.totalAmount.toBrlCurrency(), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Já Pago:", style = MaterialTheme.typography.bodySmall)
                                Text(targetSale.paidAmount.toBrlCurrency(), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = ProfitGreen))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Restante:", style = MaterialTheme.typography.bodySmall)
                                Text(remaining.toBrlCurrency(), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold, color = WarningAmber))
                            }
                        }
                    }

                    OutlinedTextField(
                        value = paymentInput,
                        onValueChange = { paymentInput = it },
                        label = { Text("Valor recebido hoje (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Quick presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    paymentInput = String.format(java.util.Locale.US, "%.2f", remaining)
                                },
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Quitar tudo",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 6.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }

                        if (remaining > 10.0) {
                            val half = remaining / 2.0
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        paymentInput = String.format(java.util.Locale.US, "%.2f", half)
                                    },
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "50% (${half.toBrlCurrency()})",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    if (paymentError != null) {
                        Text(
                            text = paymentError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = paymentInput.replace(",", ".").toDoubleOrNull() ?: 0.0
                        if (amount <= 0) {
                            paymentError = "Informe um valor maior que R$ 0,00."
                        } else if (amount > remaining + 0.01) {
                            paymentError = "O valor não pode ser superior ao saldo restante (${remaining.toBrlCurrency()})."
                        } else {
                            viewModel.recordSalePayment(targetSale, amount)
                            saleToRecordPayment = null
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Confirmar Recebimento")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { saleToRecordPayment = null },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal Dialog: Comprovante de Venda (Visualizar, PDF, Compartilhar, Imprimir)
    if (saleForReceipt != null && currentBusiness != null) {
        val targetSale = saleForReceipt!!
        val cust = customers.find {
            it.id == targetSale.sale.customerId || (targetSale.sale.customerName != null && it.name.equals(targetSale.sale.customerName, ignoreCase = true))
        }
        SaleReceiptDialog(
            business = currentBusiness!!,
            saleWithItems = targetSale,
            customer = cust,
            onDismiss = { saleForReceipt = null }
        )
    }
}
