package com.example.ui.screens.reports

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BusinessEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.ProductEntity
import com.example.data.model.SaleWithItems
import com.example.ui.components.toBrlCurrency
import com.example.ui.components.toFormattedDate
import com.example.ui.components.toFormattedDateTime
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.ProfitGreen
import com.example.ui.viewmodel.AppViewModel
import com.example.util.pdf.PdfShareUtils
import com.example.util.pdf.ReportFilter
import com.example.util.pdf.ReportKind
import com.example.util.pdf.ReportPdfGenerator
import java.util.Calendar

enum class TimePeriodOption(val label: String) {
    HOJE("Hoje"),
    ESTA_SEMANA("Esta semana"),
    ESTE_MES("Este mês"),
    MES_ANTERIOR("Mês anterior"),
    ULTIMOS_30_DIAS("Últimos 30 dias"),
    TODOS("Todo o histórico")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: AppViewModel,
    business: BusinessEntity,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val allSales by viewModel.sales.collectAsStateWithLifecycle()
    val allExpenses by viewModel.expenses.collectAsStateWithLifecycle()
    val allProducts by viewModel.products.collectAsStateWithLifecycle()
    val allCustomers by viewModel.customers.collectAsStateWithLifecycle()

    var selectedKind by remember { mutableStateOf(ReportKind.VENDAS) }
    var selectedPeriod by remember { mutableStateOf(TimePeriodOption.ESTE_MES) }
    var selectedPaymentMethod by remember { mutableStateOf("Todas") }

    // Calcula os timestamps de início e fim baseados no período selecionado
    val (startTimestamp, endTimestamp) = remember(selectedPeriod) {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis

        when (selectedPeriod) {
            TimePeriodOption.HOJE -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, now)
            }
            TimePeriodOption.ESTA_SEMANA -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, now)
            }
            TimePeriodOption.ESTE_MES -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, now)
            }
            TimePeriodOption.MES_ANTERIOR -> {
                cal.add(Calendar.MONTH, -1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, maxDay)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                Pair(start, cal.timeInMillis)
            }
            TimePeriodOption.ULTIMOS_30_DIAS -> {
                cal.add(Calendar.DAY_OF_YEAR, -30)
                Pair(cal.timeInMillis, now)
            }
            TimePeriodOption.TODOS -> {
                Pair(0L, Long.MAX_VALUE)
            }
        }
    }

    // Filtra vendas
    val filteredSales = remember(allSales, startTimestamp, endTimestamp, selectedPaymentMethod) {
        allSales.filter { saleWithItems ->
            val s = saleWithItems.sale
            val withinDate = s.date in startTimestamp..endTimestamp
            val matchesMethod = selectedPaymentMethod == "Todas" || s.paymentMethod.equals(selectedPaymentMethod, ignoreCase = true)
            withinDate && matchesMethod
        }
    }

    // Filtra despesas
    val filteredExpenses = remember(allExpenses, startTimestamp, endTimestamp) {
        allExpenses.filter { it.date in startTimestamp..endTimestamp }
    }

    fun generatePdfFile() = ReportPdfGenerator.generateReportPdf(
        context = context,
        business = business,
        filter = ReportFilter(
            kind = selectedKind,
            periodLabel = selectedPeriod.label,
            paymentMethodFilter = if (selectedPaymentMethod != "Todas") selectedPaymentMethod else null
        ),
        sales = filteredSales,
        expenses = filteredExpenses,
        products = allProducts,
        customers = allCustomers
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // 1. Top Bar com Voltar e Título
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back_reports")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Relatórios do Negócio",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Exportação de PDFs e controle gerencial",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. Seletor de Tipo de Relatório (Horizontal Cards/Chips)
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Escolha o tipo de relatório:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            val kinds = listOf(
                Triple(ReportKind.VENDAS, Icons.Default.PointOfSale, "Vendas"),
                Triple(ReportKind.DESPESAS, Icons.Default.Receipt, "Despesas"),
                Triple(ReportKind.FINANCEIRO, Icons.AutoMirrored.Filled.TrendingUp, "Financeiro"),
                Triple(ReportKind.ESTOQUE, Icons.Default.Inventory2, "Estoque"),
                Triple(ReportKind.CLIENTES, Icons.Default.People, "Clientes"),
                Triple(ReportKind.RESUMO_MENSAL, Icons.Default.CalendarMonth, "Mensal")
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(kinds) { (kind, icon, title) ->
                    val isSelected = selectedKind == kind
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) EmeraldPrimary else MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) EmeraldPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .clickable { selectedKind = kind }
                            .testTag("report_kind_${kind.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // 3. Barra de Filtros (Período e Forma de Pagamento)
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp), tint = EmeraldPrimary)
                        Text("Filtrar Período:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Chips de Período
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TimePeriodOption.entries.forEach { option ->
                            val isSelected = selectedPeriod == option
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.clickable { selectedPeriod = option }
                            ) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Se for relatório de vendas, adiciona filtro por forma de pagamento
                    if (selectedKind == ReportKind.VENDAS) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Forma de Pagamento:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(6.dp))

                        val methods = listOf("Todas", "Dinheiro", "Pix", "Cartão de Crédito", "Cartão de Débito", "A Prazo")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            methods.forEach { method ->
                                val isSelected = selectedPaymentMethod == method
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) EmeraldPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary) else null,
                                    modifier = Modifier.clickable { selectedPaymentMethod = method }
                                ) {
                                    Text(
                                        text = method,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isSelected) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Cartões de Métricas Resumo (KPIs em Tempo Real)
        item {
            Spacer(modifier = Modifier.height(14.dp))
            val totalSalesAmount = filteredSales.sumOf { it.sale.totalAmount }
            val totalExpensesAmount = filteredExpenses.sumOf { it.amount }
            val netBalance = totalSalesAmount - totalExpensesAmount
            val totalReceber = filteredSales.sumOf { it.sale.remainingAmount }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Vendas / Entradas",
                    value = totalSalesAmount.toBrlCurrency(),
                    color = ProfitGreen,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Despesas / Custos",
                    value = totalExpensesAmount.toBrlCurrency(),
                    color = ExpenseRed,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Saldo Líquido",
                    value = netBalance.toBrlCurrency(),
                    color = if (netBalance >= 0.0) ProfitGreen else ExpenseRed,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 5. Botões de Ação para Exportação PDF
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
                            Text(
                                text = "Exportar PDF Oficial",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Text(
                            text = "${selectedKind.title} (${selectedPeriod.label})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Compartilhar
                        Button(
                            onClick = {
                                val pdf = generatePdfFile()
                                PdfShareUtils.sharePdf(
                                    context = context,
                                    file = pdf,
                                    title = "${selectedKind.title} - ${business.name}"
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(44.dp)
                                .testTag("btn_share_report_pdf")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Compartilhar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        // Salvar
                        OutlinedButton(
                            onClick = {
                                val pdf = generatePdfFile()
                                val filename = "Relatorio_${selectedKind.name}_${selectedPeriod.name}.pdf"
                                PdfShareUtils.saveToDownloads(context, pdf, filename)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("btn_save_report_pdf")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Salvar", fontSize = 13.sp)
                        }

                        // Imprimir
                        IconButton(
                            onClick = {
                                val pdf = generatePdfFile()
                                val job = "Relatorio_${selectedKind.name}"
                                PdfShareUtils.printPdf(context, pdf, job)
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .testTag("btn_print_report_pdf")
                        ) {
                            Icon(Icons.Default.Print, contentDescription = "Imprimir", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        // 6. Prévia Visual da Tabela (In-App Preview)
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Prévia dos Dados no Relatório:",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            when (selectedKind) {
                ReportKind.VENDAS -> SalesPreviewTable(filteredSales)
                ReportKind.DESPESAS -> ExpensesPreviewTable(filteredExpenses)
                ReportKind.FINANCEIRO, ReportKind.RESUMO_MENSAL -> FinancialPreviewTable(filteredSales, filteredExpenses)
                ReportKind.ESTOQUE -> StockPreviewTable(allProducts)
                ReportKind.CLIENTES -> CustomersPreviewTable(allCustomers, filteredSales)
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = color)
        }
    }
}

@Composable
private fun SalesPreviewTable(sales: List<SaleWithItems>) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (sales.isEmpty()) {
                Text(
                    text = "Nenhuma venda registrada neste período.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EmeraldPrimary.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("DATA", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                    Text("CLIENTE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1.5f))
                    Text("PAGAMENTO", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                    Text("TOTAL", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }

                sales.take(15).forEachIndexed { i, s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(s.sale.date.toFormattedDate(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(s.sale.customerName ?: "Consumidor Final", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.5f))
                        Text(s.sale.paymentMethod, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(s.sale.totalAmount.toBrlCurrency(), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    }
                    if (i < sales.take(15).lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    }
                }

                if (sales.size > 15) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "... e mais ${sales.size - 15} vendas inclusas no PDF completo.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpensesPreviewTable(expenses: List<ExpenseEntity>) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (expenses.isEmpty()) {
                Text(
                    text = "Nenhuma despesa registrada neste período.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ExpenseRed.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("DATA", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                    Text("DESCRIÇÃO", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(2f))
                    Text("CATEGORIA", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                    Text("VALOR", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }

                expenses.take(15).forEachIndexed { i, exp ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(exp.date.toFormattedDate(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(exp.description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(2f))
                        Text(exp.category, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(exp.amount.toBrlCurrency(), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = ExpenseRed), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    }
                    if (i < expenses.take(15).lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FinancialPreviewTable(sales: List<SaleWithItems>, expenses: List<ExpenseEntity>) {
    val totalSales = sales.sumOf { it.sale.totalAmount }
    val totalPaid = sales.sumOf { it.sale.paidAmount }
    val totalPending = sales.sumOf { it.sale.remainingAmount }
    val totalExp = expenses.sumOf { it.amount }
    val net = totalSales - totalExp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FinancialRow("Receita Bruta Total", totalSales.toBrlCurrency(), ProfitGreen)
            FinancialRow("Total Recebido (Efetivo)", totalPaid.toBrlCurrency(), ProfitGreen)
            FinancialRow("A Receber (Prazo/Pendentes)", totalPending.toBrlCurrency(), Color(0xFFD97706))
            FinancialRow("Despesas e Custos", "-${totalExp.toBrlCurrency()}", ExpenseRed)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            FinancialRow(
                "Resultado Líquido (Lucro/Prejuízo)",
                net.toBrlCurrency(),
                if (net >= 0) ProfitGreen else ExpenseRed,
                isBold = true
            )
        }
    }
}

@Composable
private fun FinancialRow(label: String, value: String, valueColor: Color, isBold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (isBold) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = if (isBold) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold) else MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = valueColor
        )
    }
}

@Composable
private fun StockPreviewTable(products: List<ProductEntity>) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("PRODUTO", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(2f))
                Text("ESTOQUE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                Text("VALOR UNIT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                Text("TOTAL", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }

            products.take(15).forEachIndexed { i, prod ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(prod.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(2f))
                    Text("${prod.currentStock.toInt()}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(prod.salePrice.toBrlCurrency(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text((prod.currentStock * prod.salePrice).toBrlCurrency(), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
                if (i < products.take(15).lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                }
            }
        }
    }
}

@Composable
private fun CustomersPreviewTable(customers: List<CustomerEntity>, sales: List<SaleWithItems>) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D9488).copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("CLIENTE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1.5f))
                Text("TELEFONE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1.2f))
                Text("COMPRADO", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                Text("PENDENTE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }

            customers.take(15).forEachIndexed { i, cust ->
                val custSales = sales.filter { it.sale.customerId == cust.id || it.sale.customerName.equals(cust.name, ignoreCase = true) }
                val bought = custSales.sumOf { it.sale.totalAmount }
                val pending = custSales.sumOf { it.sale.remainingAmount }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(cust.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.5f))
                    Text(cust.phone, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.2f))
                    Text(bought.toBrlCurrency(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(pending.toBrlCurrency(), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = if (pending > 0.01) ExpenseRed else ProfitGreen), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
                if (i < customers.take(15).lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                }
            }
        }
    }
}
