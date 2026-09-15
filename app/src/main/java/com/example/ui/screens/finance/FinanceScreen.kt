package com.example.ui.screens.finance

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.ExpenseEntity
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EmptyStateView
import com.example.ui.components.SimpleFinanceChart
import com.example.ui.components.StatCard
import com.example.ui.components.toBrlCurrency
import com.example.ui.components.toFormattedDateTime
import com.example.ui.theme.AppIconSet
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.ProfitGreen
import com.example.ui.viewmodel.AppViewModel
import java.util.Calendar

enum class FinancePeriod(val label: String) {
    TODAY("Hoje"),
    THIS_WEEK("Esta semana"),
    THIS_MONTH("Este mês"),
    ALL("Todos")
}

@Composable
fun FinanceScreen(
    viewModel: AppViewModel,
    onOpenNewExpense: () -> Unit
) {
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val summary by viewModel.financialSummary.collectAsStateWithLifecycle()
    val themeState by viewModel.themeState.collectAsStateWithLifecycle()

    var selectedPeriod by remember { mutableStateOf(FinancePeriod.THIS_MONTH) }
    var expenseToDelete by remember { mutableStateOf<ExpenseEntity?>(null) }
    var showOnlyExpensesList by remember { mutableStateOf(false) }

    val filteredData = remember(sales, expenses, selectedPeriod) {
        val now = Calendar.getInstance()
        val startPeriod = when (selectedPeriod) {
            FinancePeriod.TODAY -> Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            FinancePeriod.THIS_WEEK -> Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            FinancePeriod.THIS_MONTH -> Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            FinancePeriod.ALL -> 0L
        }

        val periodSales = sales.filter { it.sale.date >= startPeriod }
        val periodExpenses = expenses.filter { it.date >= startPeriod }

        val totalSales = periodSales.sumOf { it.sale.totalAmount }
        val totalExpenses = periodExpenses.sumOf { it.amount }
        val netProfit = totalSales - totalExpenses
        val profitMargin = if (totalSales > 0) (netProfit / totalSales) * 100 else 0.0

        object {
            val totalSales = totalSales
            val totalExpenses = totalExpenses
            val netProfit = netProfit
            val profitMargin = profitMargin
            val periodExpensesList = periodExpenses
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenNewExpense,
                containerColor = ExpenseRed,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("fab_new_expense")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = AppIconSet.expenses(themeState.iconStyle),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Nova Despesa",
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
                Text(
                    text = "Resumo Financeiro & Lucro",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Period selector tabs
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FinancePeriod.entries.forEach { period ->
                        val isSelected = selectedPeriod == period
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedPeriod = period },
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = period.label,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 10.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            // 4 Financial Summary Stat Cards
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatCard(
                            title = "Total Vendido",
                            value = filteredData.totalSales.toBrlCurrency(),
                            icon = AppIconSet.sales(themeState.iconStyle),
                            accentColor = ProfitGreen,
                            modifier = Modifier.weight(1f),
                            testTag = "finance_stat_vendas"
                        )
                        StatCard(
                            title = "Despesas",
                            value = filteredData.totalExpenses.toBrlCurrency(),
                            icon = AppIconSet.expenses(themeState.iconStyle),
                            accentColor = ExpenseRed,
                            modifier = Modifier.weight(1f),
                            testTag = "finance_stat_despesas"
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatCard(
                            title = "Lucro Líquido",
                            value = filteredData.netProfit.toBrlCurrency(),
                            icon = AppIconSet.profit(themeState.iconStyle),
                            accentColor = if (filteredData.netProfit >= 0) ProfitGreen else ExpenseRed,
                            modifier = Modifier.weight(1f),
                            subtitle = "Vendas - Despesas",
                            testTag = "finance_stat_lucro"
                        )
                        StatCard(
                            title = "Margem de Lucro",
                            value = String.format("%.1f%%", filteredData.profitMargin),
                            icon = Icons.Default.Percent,
                            accentColor = InfoBlue,
                            modifier = Modifier.weight(1f),
                            subtitle = "Retorno s/ vendas",
                            testTag = "finance_stat_margem"
                        )
                    }
                }
            }

            // Chart
            item {
                SimpleFinanceChart(points = summary.dailyChart)
            }

            // Section Toggle: Visão Geral vs Despesas
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Despesas do Período (${filteredData.periodExpensesList.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            if (filteredData.periodExpensesList.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Default.Receipt,
                        title = "Nenhuma despesa no período",
                        description = "Mantenha o controle do seu negócio registrando contas, compras e custos.",
                        actionText = "+ Nova Despesa",
                        onActionClick = onOpenNewExpense
                    )
                }
            } else {
                items(filteredData.periodExpensesList, key = { it.id }) { expense ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .testTag("expense_card_${expense.id.take(6)}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Surface(
                                    color = ExpenseRed.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = expense.category,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = ExpenseRed,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = expense.description,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = expense.date.toFormattedDateTime(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!expense.observation.isNullOrBlank()) {
                                    Text(
                                        text = expense.observation,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = expense.amount.toBrlCurrency(),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ExpenseRed
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(onClick = { expenseToDelete = expense }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Excluir",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (expenseToDelete != null) {
        ConfirmDeleteDialog(
            title = "Excluir Despesa",
            message = "Tem certeza que deseja excluir '${expenseToDelete!!.description}' no valor de ${expenseToDelete!!.amount.toBrlCurrency()}?",
            confirmButtonText = "Excluir Despesa",
            onConfirm = {
                viewModel.deleteExpense(expenseToDelete!!)
                expenseToDelete = null
            },
            onDismiss = { expenseToDelete = null }
        )
    }
}
