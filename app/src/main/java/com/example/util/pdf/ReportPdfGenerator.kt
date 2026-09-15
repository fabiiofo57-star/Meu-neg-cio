package com.example.util.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.data.model.BusinessEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.ProductEntity
import com.example.data.model.SaleWithItems
import com.example.ui.components.toBrlCurrency
import com.example.ui.components.toFormattedDate
import com.example.ui.components.toFormattedDateTime
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ReportKind(val title: String) {
    VENDAS("Relatório de Vendas"),
    DESPESAS("Relatório de Despesas"),
    FINANCEIRO("Relatório Financeiro Geral"),
    ESTOQUE("Relatório de Posição de Estoque"),
    CLIENTES("Relatório de Clientes e Cobranças"),
    RESUMO_MENSAL("Resumo Mensal Consolidado")
}

data class ReportFilter(
    val kind: ReportKind,
    val periodLabel: String,
    val customerFilter: String? = null,
    val paymentMethodFilter: String? = null,
    val categoryFilter: String? = null
)

object ReportPdfGenerator {

    private const val PAGE_WIDTH = 595 // A4 standard width (pt)
    private const val PAGE_HEIGHT = 842 // A4 standard height (pt)
    private const val MARGIN_LEFT = 36f
    private const val MARGIN_RIGHT = 559f
    private const val CONTENT_WIDTH = 523f

    fun generateReportPdf(
        context: Context,
        business: BusinessEntity,
        filter: ReportFilter,
        sales: List<SaleWithItems>,
        expenses: List<ExpenseEntity>,
        products: List<ProductEntity>,
        customers: List<CustomerEntity>
    ): File {
        val document = PdfDocument()
        var pageNumber = 1

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas

        // Desenha Cabeçalho da Primeira Página
        var currentY = drawHeader(context, canvas, business, filter, textPaint, rectPaint, linePaint)

        // KPIs / Resumo no topo
        currentY = drawKpis(canvas, filter.kind, sales, expenses, products, customers, currentY, textPaint, rectPaint)

        fun checkNewPage(neededSpace: Float) {
            if (currentY + neededSpace > PAGE_HEIGHT - 50f) {
                // Desenha rodapé da página atual
                drawFooter(canvas, pageNumber, textPaint, linePaint)
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                currentY = drawSubsequentPageHeader(canvas, business, filter.kind, textPaint, linePaint)
            }
        }

        when (filter.kind) {
            ReportKind.VENDAS -> {
                currentY = drawSalesTable(canvas, sales, currentY, textPaint, rectPaint, linePaint, ::checkNewPage)
            }
            ReportKind.DESPESAS -> {
                currentY = drawExpensesTable(canvas, expenses, currentY, textPaint, rectPaint, linePaint, ::checkNewPage)
            }
            ReportKind.FINANCEIRO, ReportKind.RESUMO_MENSAL -> {
                currentY = drawFinancialTable(canvas, sales, expenses, currentY, textPaint, rectPaint, linePaint, ::checkNewPage)
            }
            ReportKind.ESTOQUE -> {
                currentY = drawStockTable(canvas, products, currentY, textPaint, rectPaint, linePaint, ::checkNewPage)
            }
            ReportKind.CLIENTES -> {
                currentY = drawCustomersTable(canvas, customers, sales, currentY, textPaint, rectPaint, linePaint, ::checkNewPage)
            }
        }

        // Rodapé da última página
        drawFooter(canvas, pageNumber, textPaint, linePaint)
        document.finishPage(page)

        val fileName = "relatorio_${filter.kind.name.lowercase()}_${System.currentTimeMillis()}.pdf"
        val outFile = PdfShareUtils.getCachePdfFile(context, fileName)
        val stream = FileOutputStream(outFile)
        document.writeTo(stream)
        stream.close()
        document.close()

        return outFile
    }

    private fun drawHeader(
        context: Context,
        canvas: Canvas,
        business: BusinessEntity,
        filter: ReportFilter,
        textPaint: Paint,
        rectPaint: Paint,
        linePaint: Paint
    ): Float {
        // Faixa de destaque superior
        rectPaint.apply {
            color = Color.parseColor("#059669") // Emerald primary
            style = Paint.Style.FILL
        }
        canvas.drawRect(MARGIN_LEFT, 24f, MARGIN_RIGHT, 28f, rectPaint)

        var y = 46f

        // Nome do Negócio
        textPaint.apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 15f
            color = Color.parseColor("#0F172A")
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText(business.name, MARGIN_LEFT, y, textPaint)

        // Data de Emissão (Alinhada à direita)
        textPaint.apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 9f
            color = Color.parseColor("#64748B")
            textAlign = Paint.Align.RIGHT
        }
        val genTime = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())
        canvas.drawText("Emitido em: $genTime", MARGIN_RIGHT, y, textPaint)
        y += 14f

        // Dados Cadastrais da Empresa
        val fiscalLine = listOfNotNull(
            business.razaoSocial?.takeIf { it.isNotBlank() },
            business.cnpj?.takeIf { it.isNotBlank() }?.let { "CNPJ: $it" }
        ).joinToString(" | ")

        if (fiscalLine.isNotBlank()) {
            textPaint.apply {
                textAlign = Paint.Align.LEFT
                textSize = 9f
                color = Color.parseColor("#475569")
            }
            canvas.drawText(fiscalLine, MARGIN_LEFT, y, textPaint)
            y += 13f
        }

        val contactLine = listOfNotNull(
            business.phone.takeIf { it.isNotBlank() }?.let { "Tel: $it" },
            business.email?.takeIf { it.isNotBlank() },
            business.city.takeIf { it.isNotBlank() }?.let {
                if (!business.state.isNullOrBlank()) "$it/${business.state}" else it
            }
        ).joinToString(" • ")

        if (contactLine.isNotBlank()) {
            textPaint.apply {
                textAlign = Paint.Align.LEFT
                textSize = 8.5f
                color = Color.parseColor("#64748B")
            }
            canvas.drawText(contactLine, MARGIN_LEFT, y, textPaint)
            y += 13f
        }

        y += 4f
        linePaint.apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 1f
        }
        canvas.drawLine(MARGIN_LEFT, y, MARGIN_RIGHT, y, linePaint)
        y += 16f

        // Título do Relatório + Período
        textPaint.apply {
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 14f
            color = Color.parseColor("#047857")
        }
        canvas.drawText(filter.kind.title.uppercase(), MARGIN_LEFT, y, textPaint)

        // Badge de Período
        textPaint.apply {
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 9.5f
            color = Color.parseColor("#1E293B")
        }
        canvas.drawText("Período: ${filter.periodLabel}", MARGIN_RIGHT, y, textPaint)
        y += 18f

        return y
    }

    private fun drawSubsequentPageHeader(
        canvas: Canvas,
        business: BusinessEntity,
        kind: ReportKind,
        textPaint: Paint,
        linePaint: Paint
    ): Float {
        var y = 36f
        textPaint.apply {
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 10f
            color = Color.parseColor("#475569")
        }
        canvas.drawText("${business.name} - ${kind.title}", MARGIN_LEFT, y, textPaint)

        textPaint.apply {
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 8.5f
            color = Color.parseColor("#94A3B8")
        }
        canvas.drawText("Continuação", MARGIN_RIGHT, y, textPaint)

        y += 8f
        linePaint.apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 0.8f
        }
        canvas.drawLine(MARGIN_LEFT, y, MARGIN_RIGHT, y, linePaint)
        y += 14f
        return y
    }

    private fun drawKpis(
        canvas: Canvas,
        kind: ReportKind,
        sales: List<SaleWithItems>,
        expenses: List<ExpenseEntity>,
        products: List<ProductEntity>,
        customers: List<CustomerEntity>,
        startY: Float,
        textPaint: Paint,
        rectPaint: Paint
    ): Float {
        var y = startY

        val totalSales = sales.sumOf { it.sale.totalAmount }
        val totalPaid = sales.sumOf { it.sale.paidAmount }
        val totalPending = sales.sumOf { it.sale.remainingAmount }
        val totalExpenses = expenses.sumOf { it.amount }
        val netProfit = totalSales - totalExpenses
        val ticketMedio = if (sales.isNotEmpty()) totalSales / sales.size else 0.0

        val cards: List<Pair<String, String>> = when (kind) {
            ReportKind.VENDAS -> listOf(
                "Total Faturado" to totalSales.toBrlCurrency(),
                "Vendas Concluídas" to "${sales.size}",
                "Ticket Médio" to ticketMedio.toBrlCurrency(),
                "Recebido" to totalPaid.toBrlCurrency()
            )
            ReportKind.DESPESAS -> {
                val maiorDespesa = expenses.maxOfOrNull { it.amount } ?: 0.0
                listOf(
                    "Total Despesas" to totalExpenses.toBrlCurrency(),
                    "Registros" to "${expenses.size}",
                    "Maior Despesa" to maiorDespesa.toBrlCurrency()
                )
            }
            ReportKind.FINANCEIRO, ReportKind.RESUMO_MENSAL -> listOf(
                "Receita Total" to totalSales.toBrlCurrency(),
                "Despesas Totais" to totalExpenses.toBrlCurrency(),
                "Lucro Líquido" to netProfit.toBrlCurrency(),
                "A Receber" to totalPending.toBrlCurrency()
            )
            ReportKind.ESTOQUE -> {
                val totalItens = products.sumOf { it.currentStock }
                val valorPatrimonio = products.sumOf { it.currentStock * it.salePrice }
                val estoqueBaixo = products.count { it.isLowStock }
                listOf(
                    "Produtos Cadastrados" to "${products.size}",
                    "Unidades em Estoque" to "${totalItens.toInt()}",
                    "Valor em Estoque" to valorPatrimonio.toBrlCurrency(),
                    "Estoque Baixo" to "$estoqueBaixo item(ns)"
                )
            }
            ReportKind.CLIENTES -> {
                listOf(
                    "Total de Clientes" to "${customers.size}",
                    "Total Faturado" to totalSales.toBrlCurrency(),
                    "Total Recebido" to totalPaid.toBrlCurrency(),
                    "Total Pendente" to totalPending.toBrlCurrency()
                )
            }
        }

        val cardHeight = 44f
        val gap = 8f
        val cardWidth = (CONTENT_WIDTH - (gap * (cards.size - 1))) / cards.size

        cards.forEachIndexed { i, (label, value) ->
            val cx = MARGIN_LEFT + i * (cardWidth + gap)
            val rect = RectF(cx, y, cx + cardWidth, y + cardHeight)

            rectPaint.apply {
                color = Color.parseColor("#F8FAFC")
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(rect, 6f, 6f, rectPaint)

            rectPaint.apply {
                color = Color.parseColor("#E2E8F0")
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            canvas.drawRoundRect(rect, 6f, 6f, rectPaint)

            textPaint.apply {
                textAlign = Paint.Align.LEFT
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textSize = 7.5f
                color = Color.parseColor("#64748B")
            }
            canvas.drawText(label, cx + 8f, y + 16f, textPaint)

            textPaint.apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 10f
                color = if (label.contains("Lucro") && netProfit < 0) Color.parseColor("#DC2626") else Color.parseColor("#0F172A")
            }
            canvas.drawText(value, cx + 8f, y + 33f, textPaint)
        }

        return y + cardHeight + 16f
    }

    // Tabela de Vendas
    private fun drawSalesTable(
        canvas: Canvas,
        sales: List<SaleWithItems>,
        startY: Float,
        textPaint: Paint,
        rectPaint: Paint,
        linePaint: Paint,
        checkNewPage: (Float) -> Unit
    ): Float {
        var y = startY

        // Cabeçalho da tabela
        rectPaint.apply {
            color = Color.parseColor("#047857")
            style = Paint.Style.FILL
        }
        canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 20f, rectPaint)

        textPaint.apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 8.5f
            color = Color.WHITE
            textAlign = Paint.Align.LEFT
        }

        canvas.drawText("DATA", MARGIN_LEFT + 6f, y + 14f, textPaint)
        canvas.drawText("CLIENTE", MARGIN_LEFT + 65f, y + 14f, textPaint)
        canvas.drawText("ITENS PRINCIPAIS", MARGIN_LEFT + 185f, y + 14f, textPaint)
        canvas.drawText("FORMA PAGTO", MARGIN_LEFT + 360f, y + 14f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("TOTAL", MARGIN_RIGHT - 6f, y + 14f, textPaint)

        y += 20f

        sales.forEachIndexed { index, saleWithItems ->
            checkNewPage(18f)
            val sale = saleWithItems.sale

            // Linha zebrada
            if (index % 2 == 1) {
                rectPaint.apply {
                    color = Color.parseColor("#F8FAFC")
                    style = Paint.Style.FILL
                }
                canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 18f, rectPaint)
            }

            textPaint.apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textSize = 8f
                color = Color.parseColor("#1E293B")
                textAlign = Paint.Align.LEFT
            }

            canvas.drawText(sale.date.toFormattedDate(), MARGIN_LEFT + 6f, y + 12f, textPaint)

            val cust = (sale.customerName ?: "Consumidor Final").take(18)
            canvas.drawText(cust, MARGIN_LEFT + 65f, y + 12f, textPaint)

            val itemsText = saleWithItems.items.joinToString(", ") { "${it.quantity.toInt()}x ${it.productName}" }.take(32)
            canvas.drawText(itemsText, MARGIN_LEFT + 185f, y + 12f, textPaint)

            canvas.drawText(sale.paymentMethod, MARGIN_LEFT + 360f, y + 12f, textPaint)

            textPaint.apply {
                textAlign = Paint.Align.RIGHT
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(sale.totalAmount.toBrlCurrency(), MARGIN_RIGHT - 6f, y + 12f, textPaint)

            y += 18f
        }

        // Linha de Total
        y += 4f
        linePaint.apply {
            color = Color.parseColor("#047857")
            strokeWidth = 1.5f
        }
        canvas.drawLine(MARGIN_LEFT, y, MARGIN_RIGHT, y, linePaint)
        y += 14f

        textPaint.apply {
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 9.5f
            color = Color.parseColor("#0F172A")
        }
        canvas.drawText("TOTAL GERAL DE VENDAS (${sales.size} registros)", MARGIN_LEFT + 6f, y, textPaint)

        textPaint.apply {
            textAlign = Paint.Align.RIGHT
            textSize = 10f
            color = Color.parseColor("#047857")
        }
        canvas.drawText(sales.sumOf { it.sale.totalAmount }.toBrlCurrency(), MARGIN_RIGHT - 6f, y, textPaint)
        y += 20f

        return y
    }

    // Tabela de Despesas
    private fun drawExpensesTable(
        canvas: Canvas,
        expenses: List<ExpenseEntity>,
        startY: Float,
        textPaint: Paint,
        rectPaint: Paint,
        linePaint: Paint,
        checkNewPage: (Float) -> Unit
    ): Float {
        var y = startY

        rectPaint.apply {
            color = Color.parseColor("#DC2626")
            style = Paint.Style.FILL
        }
        canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 20f, rectPaint)

        textPaint.apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 8.5f
            color = Color.WHITE
            textAlign = Paint.Align.LEFT
        }

        canvas.drawText("DATA", MARGIN_LEFT + 6f, y + 14f, textPaint)
        canvas.drawText("DESCRIÇÃO", MARGIN_LEFT + 70f, y + 14f, textPaint)
        canvas.drawText("CATEGORIA", MARGIN_LEFT + 250f, y + 14f, textPaint)
        canvas.drawText("OBSERVAÇÃO", MARGIN_LEFT + 360f, y + 14f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("VALOR", MARGIN_RIGHT - 6f, y + 14f, textPaint)

        y += 20f

        expenses.forEachIndexed { index, exp ->
            checkNewPage(18f)
            if (index % 2 == 1) {
                rectPaint.apply {
                    color = Color.parseColor("#F8FAFC")
                    style = Paint.Style.FILL
                }
                canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 18f, rectPaint)
            }

            textPaint.apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textSize = 8f
                color = Color.parseColor("#1E293B")
                textAlign = Paint.Align.LEFT
            }

            canvas.drawText(exp.date.toFormattedDate(), MARGIN_LEFT + 6f, y + 12f, textPaint)
            canvas.drawText(exp.description.take(28), MARGIN_LEFT + 70f, y + 12f, textPaint)
            canvas.drawText(exp.category.take(18), MARGIN_LEFT + 250f, y + 12f, textPaint)
            canvas.drawText((exp.observation ?: "-").take(22), MARGIN_LEFT + 360f, y + 12f, textPaint)

            textPaint.apply {
                textAlign = Paint.Align.RIGHT
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.parseColor("#DC2626")
            }
            canvas.drawText(exp.amount.toBrlCurrency(), MARGIN_RIGHT - 6f, y + 12f, textPaint)

            y += 18f
        }

        y += 4f
        linePaint.apply {
            color = Color.parseColor("#DC2626")
            strokeWidth = 1.5f
        }
        canvas.drawLine(MARGIN_LEFT, y, MARGIN_RIGHT, y, linePaint)
        y += 14f

        textPaint.apply {
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 9.5f
            color = Color.parseColor("#0F172A")
        }
        canvas.drawText("TOTAL GERAL DE DESPESAS (${expenses.size} registros)", MARGIN_LEFT + 6f, y, textPaint)

        textPaint.apply {
            textAlign = Paint.Align.RIGHT
            textSize = 10f
            color = Color.parseColor("#DC2626")
        }
        canvas.drawText(expenses.sumOf { it.amount }.toBrlCurrency(), MARGIN_RIGHT - 6f, y, textPaint)
        y += 20f

        return y
    }

    // Tabela Financeira Geral
    private fun drawFinancialTable(
        canvas: Canvas,
        sales: List<SaleWithItems>,
        expenses: List<ExpenseEntity>,
        startY: Float,
        textPaint: Paint,
        rectPaint: Paint,
        linePaint: Paint,
        checkNewPage: (Float) -> Unit
    ): Float {
        var y = startY

        rectPaint.apply {
            color = Color.parseColor("#0284C7")
            style = Paint.Style.FILL
        }
        canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 20f, rectPaint)

        textPaint.apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 8.5f
            color = Color.WHITE
            textAlign = Paint.Align.LEFT
        }

        canvas.drawText("INDICADOR FINANCEIRO", MARGIN_LEFT + 6f, y + 14f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("VALOR TOTAL CONSOLIDADO", MARGIN_RIGHT - 6f, y + 14f, textPaint)

        y += 20f

        val totalSales = sales.sumOf { it.sale.totalAmount }
        val totalPaid = sales.sumOf { it.sale.paidAmount }
        val totalPending = sales.sumOf { it.sale.remainingAmount }
        val totalExpenses = expenses.sumOf { it.amount }
        val netResult = totalSales - totalExpenses

        val rows = listOf(
            Triple("Receita Bruta com Vendas", totalSales.toBrlCurrency(), Color.parseColor("#059669")),
            Triple("Receitas Efetivamente Pagas", totalPaid.toBrlCurrency(), Color.parseColor("#059669")),
            Triple("Contas a Receber (Vendas a Prazo / Pendentes)", totalPending.toBrlCurrency(), Color.parseColor("#D97706")),
            Triple("Despesas e Custos Operacionais", "-${totalExpenses.toBrlCurrency()}", Color.parseColor("#DC2626")),
            Triple("Resultado Líquido do Período (Lucro/Prejuízo)", netResult.toBrlCurrency(), if (netResult >= 0) Color.parseColor("#059669") else Color.parseColor("#DC2626"))
        )

        rows.forEachIndexed { index, (label, valStr, colorVal) ->
            checkNewPage(24f)

            if (index % 2 == 1) {
                rectPaint.apply {
                    color = Color.parseColor("#F8FAFC")
                    style = Paint.Style.FILL
                }
                canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 22f, rectPaint)
            }

            textPaint.apply {
                typeface = Typeface.create(Typeface.DEFAULT, if (index == rows.size - 1) Typeface.BOLD else Typeface.NORMAL)
                textSize = 9f
                color = Color.parseColor("#1E293B")
                textAlign = Paint.Align.LEFT
            }
            canvas.drawText(label, MARGIN_LEFT + 8f, y + 15f, textPaint)

            textPaint.apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 10f
                color = colorVal
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(valStr, MARGIN_RIGHT - 8f, y + 15f, textPaint)

            y += 22f
        }

        y += 10f
        return y
    }

    // Tabela de Estoque
    private fun drawStockTable(
        canvas: Canvas,
        products: List<ProductEntity>,
        startY: Float,
        textPaint: Paint,
        rectPaint: Paint,
        linePaint: Paint,
        checkNewPage: (Float) -> Unit
    ): Float {
        var y = startY

        rectPaint.apply {
            color = Color.parseColor("#4338CA")
            style = Paint.Style.FILL
        }
        canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 20f, rectPaint)

        textPaint.apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 8.5f
            color = Color.WHITE
            textAlign = Paint.Align.LEFT
        }

        canvas.drawText("PRODUTO", MARGIN_LEFT + 6f, y + 14f, textPaint)
        canvas.drawText("CATEGORIA", MARGIN_LEFT + 175f, y + 14f, textPaint)
        canvas.drawText("ESTOQUE", MARGIN_LEFT + 280f, y + 14f, textPaint)
        canvas.drawText("STATUS", MARGIN_LEFT + 360f, y + 14f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("VALOR TOTAL", MARGIN_RIGHT - 6f, y + 14f, textPaint)

        y += 20f

        products.forEachIndexed { index, prod ->
            checkNewPage(18f)

            if (index % 2 == 1) {
                rectPaint.apply {
                    color = Color.parseColor("#F8FAFC")
                    style = Paint.Style.FILL
                }
                canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 18f, rectPaint)
            }

            textPaint.apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textSize = 8f
                color = Color.parseColor("#1E293B")
                textAlign = Paint.Align.LEFT
            }

            canvas.drawText(prod.name.take(24), MARGIN_LEFT + 6f, y + 12f, textPaint)
            canvas.drawText(prod.category.take(16), MARGIN_LEFT + 175f, y + 12f, textPaint)

            val stockText = "${prod.currentStock.toInt()} (mín: ${prod.minStock.toInt()})"
            canvas.drawText(stockText, MARGIN_LEFT + 280f, y + 12f, textPaint)

            val statusText = if (prod.currentStock <= 0) "ZERADO" else if (prod.isLowStock) "BAIXO" else "REGULAR"
            val statusColor = if (prod.currentStock <= 0) Color.parseColor("#DC2626") else if (prod.isLowStock) Color.parseColor("#D97706") else Color.parseColor("#059669")

            textPaint.apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = statusColor
            }
            canvas.drawText(statusText, MARGIN_LEFT + 360f, y + 12f, textPaint)

            textPaint.apply {
                textAlign = Paint.Align.RIGHT
                color = Color.parseColor("#0F172A")
            }
            val totalProdVal = prod.currentStock * prod.salePrice
            canvas.drawText(totalProdVal.toBrlCurrency(), MARGIN_RIGHT - 6f, y + 12f, textPaint)

            y += 18f
        }

        y += 6f
        linePaint.apply {
            color = Color.parseColor("#4338CA")
            strokeWidth = 1.5f
        }
        canvas.drawLine(MARGIN_LEFT, y, MARGIN_RIGHT, y, linePaint)
        y += 14f

        textPaint.apply {
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 9.5f
            color = Color.parseColor("#0F172A")
        }
        canvas.drawText("PATRIMÔNIO TOTAL EM ESTOQUE (${products.size} produtos cadastrados)", MARGIN_LEFT + 6f, y, textPaint)

        textPaint.apply {
            textAlign = Paint.Align.RIGHT
            textSize = 10f
            color = Color.parseColor("#4338CA")
        }
        val grandTotal = products.sumOf { it.currentStock * it.salePrice }
        canvas.drawText(grandTotal.toBrlCurrency(), MARGIN_RIGHT - 6f, y, textPaint)
        y += 20f

        return y
    }

    // Tabela de Clientes
    private fun drawCustomersTable(
        canvas: Canvas,
        customers: List<CustomerEntity>,
        sales: List<SaleWithItems>,
        startY: Float,
        textPaint: Paint,
        rectPaint: Paint,
        linePaint: Paint,
        checkNewPage: (Float) -> Unit
    ): Float {
        var y = startY

        rectPaint.apply {
            color = Color.parseColor("#0D9488")
            style = Paint.Style.FILL
        }
        canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 20f, rectPaint)

        textPaint.apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 8.5f
            color = Color.WHITE
            textAlign = Paint.Align.LEFT
        }

        canvas.drawText("CLIENTE", MARGIN_LEFT + 6f, y + 14f, textPaint)
        canvas.drawText("CONTATO", MARGIN_LEFT + 160f, y + 14f, textPaint)
        canvas.drawText("COMPRADO", MARGIN_LEFT + 280f, y + 14f, textPaint)
        canvas.drawText("PAGO", MARGIN_LEFT + 370f, y + 14f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("PENDENTE", MARGIN_RIGHT - 6f, y + 14f, textPaint)

        y += 20f

        customers.forEachIndexed { index, cust ->
            checkNewPage(18f)

            if (index % 2 == 1) {
                rectPaint.apply {
                    color = Color.parseColor("#F8FAFC")
                    style = Paint.Style.FILL
                }
                canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 18f, rectPaint)
            }

            val custSales = sales.filter { it.sale.customerId == cust.id || it.sale.customerName.equals(cust.name, ignoreCase = true) }
            val bought = custSales.sumOf { it.sale.totalAmount }
            val paid = custSales.sumOf { it.sale.paidAmount }
            val pending = custSales.sumOf { it.sale.remainingAmount }

            textPaint.apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textSize = 8f
                color = Color.parseColor("#1E293B")
                textAlign = Paint.Align.LEFT
            }

            canvas.drawText(cust.name.take(22), MARGIN_LEFT + 6f, y + 12f, textPaint)
            canvas.drawText(cust.phone.take(18), MARGIN_LEFT + 160f, y + 12f, textPaint)
            canvas.drawText(bought.toBrlCurrency(), MARGIN_LEFT + 280f, y + 12f, textPaint)
            canvas.drawText(paid.toBrlCurrency(), MARGIN_LEFT + 370f, y + 12f, textPaint)

            textPaint.apply {
                textAlign = Paint.Align.RIGHT
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = if (pending > 0.01) Color.parseColor("#DC2626") else Color.parseColor("#059669")
            }
            canvas.drawText(pending.toBrlCurrency(), MARGIN_RIGHT - 6f, y + 12f, textPaint)

            y += 18f
        }

        y += 6f
        linePaint.apply {
            color = Color.parseColor("#0D9488")
            strokeWidth = 1.5f
        }
        canvas.drawLine(MARGIN_LEFT, y, MARGIN_RIGHT, y, linePaint)
        y += 14f

        return y
    }

    private fun drawFooter(
        canvas: Canvas,
        pageNumber: Int,
        textPaint: Paint,
        linePaint: Paint
    ) {
        val y = PAGE_HEIGHT - 32f

        linePaint.apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 0.8f
        }
        canvas.drawLine(MARGIN_LEFT, y - 8f, MARGIN_RIGHT, y - 8f, linePaint)

        textPaint.apply {
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 8f
            color = Color.parseColor("#94A3B8")
        }
        canvas.drawText("Meu Negócio Gestão Comercial • Documento para simples conferência interna", MARGIN_LEFT, y + 6f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Página $pageNumber", MARGIN_RIGHT, y + 6f, textPaint)
    }
}
