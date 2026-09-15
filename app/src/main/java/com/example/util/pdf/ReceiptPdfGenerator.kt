package com.example.util.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.data.model.BusinessEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.SaleEntity
import com.example.data.model.SalePaymentStatus
import com.example.data.model.SaleWithItems
import com.example.ui.components.toBrlCurrency
import com.example.ui.components.toFormattedDate
import com.example.ui.components.toFormattedDateTime
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReceiptPdfGenerator {

    fun generateReceiptPdf(
        context: Context,
        business: BusinessEntity,
        saleWithItems: SaleWithItems,
        customer: CustomerEntity?
    ): File {
        val sale = saleWithItems.sale
        val items = saleWithItems.items
        val status = sale.effectiveStatus

        // Dimensões do comprovante estilo POS compacto (largura 380 pontos ~ 80mm)
        val pageWidth = 400
        val baseHeight = 620
        val itemsHeight = (items.size * 34) + 40
        val extraNotesHeight = if (!sale.observation.isNullOrBlank()) 40 else 0
        val extraDueHeight = if (sale.dueDate != null || sale.remainingAmount > 0.01) 40 else 0
        val totalHeight = (baseHeight + itemsHeight + extraNotesHeight + extraDueHeight).coerceAtLeast(680)

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, totalHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        // Pinturas (Paints)
        val bgPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), totalHeight.toFloat(), bgPaint)

        // Borda externa suave
        val borderPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val cardRect = RectF(12f, 12f, (pageWidth - 12).toFloat(), (totalHeight - 12).toFloat())
        canvas.drawRoundRect(cardRect, 16f, 16f, borderPaint)

        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#1E293B")
        }

        var y = 42f
        val left = 26f
        val right = (pageWidth - 26).toFloat()
        val centerX = pageWidth / 2f

        // 1. Logo do Negócio ou Avatar elegante
        val logoBitmap = loadBitmap(context, business.logoUri)
        if (logoBitmap != null) {
            try {
                val size = 52
                val scaled = Bitmap.createScaledBitmap(logoBitmap, size, size, true)
                val leftLogo = centerX - (size / 2f)
                val logoRect = RectF(leftLogo, y, leftLogo + size, y + size)
                val paintRound = Paint(Paint.ANTI_ALIAS_FLAG)
                canvas.drawBitmap(scaled, leftLogo, y, paintRound)
                y += size + 10f
            } catch (_: Exception) {
                y += 6f
            }
        } else {
            y += 4f
        }

        // Nome do Negócio (Destaque)
        textPaint.apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 17f
            color = Color.parseColor("#0F172A")
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(business.name, centerX, y, textPaint)
        y += 18f

        // Razão Social (se preenchida)
        if (!business.razaoSocial.isNullOrBlank()) {
            textPaint.apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textSize = 10f
                color = Color.parseColor("#475569")
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(business.razaoSocial, centerX, y, textPaint)
            y += 14f
        }

        // CNPJ
        if (!business.cnpj.isNullOrBlank()) {
            textPaint.apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textSize = 10f
                color = Color.parseColor("#475569")
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("CNPJ: ${business.cnpj}", centerX, y, textPaint)
            y += 14f
        }

        // Telefone & E-mail
        val contactLine = listOfNotNull(
            business.phone.takeIf { it.isNotBlank() }?.let { "Tel: $it" },
            business.email?.takeIf { it.isNotBlank() }
        ).joinToString(" • ")

        if (contactLine.isNotBlank()) {
            textPaint.apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textSize = 9.5f
                color = Color.parseColor("#64748B")
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(contactLine, centerX, y, textPaint)
            y += 13f
        }

        // Endereço / Cidade / Estado / CEP
        val addressParts = listOfNotNull(
            business.address?.takeIf { it.isNotBlank() },
            listOfNotNull(
                business.city.takeIf { it.isNotBlank() },
                business.state?.takeIf { it.isNotBlank() }
            ).joinToString("/").takeIf { it.isNotBlank() },
            business.cep?.takeIf { it.isNotBlank() }?.let { "CEP: $it" }
        )
        if (addressParts.isNotEmpty()) {
            textPaint.apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textSize = 9f
                color = Color.parseColor("#64748B")
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(addressParts.joinToString(" - "), centerX, y, textPaint)
            y += 14f
        }

        y += 4f
        drawDashedSeparator(canvas, left, right, y)
        y += 18f

        // Faixa de Título: COMPROVANTE DE VENDA
        val badgeRect = RectF(left + 20, y, right - 20, y + 26)
        val badgePaint = Paint().apply {
            color = Color.parseColor("#F1F5F9")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(badgeRect, 6f, 6f, badgePaint)

        textPaint.apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 11.5f
            color = Color.parseColor("#047857")
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("COMPROVANTE DE VENDA", centerX, y + 17f, textPaint)
        y += 38f

        // Dados da Venda (Número + Data/Hora)
        textPaint.apply {
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 10f
            color = Color.parseColor("#334155")
        }
        val saleCode = "#VND-${sale.id.take(8).uppercase()}"
        canvas.drawText("Venda: $saleCode", left, y, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Data: ${sale.date.toFormattedDateTime()}", right, y, textPaint)
        y += 18f

        // Dados do Cliente
        drawDottedSeparator(canvas, left, right, y)
        y += 14f

        textPaint.apply {
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 10f
            color = Color.parseColor("#1E293B")
        }
        canvas.drawText("DADOS DO CLIENTE", left, y, textPaint)
        y += 14f

        textPaint.apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 9.5f
            color = Color.parseColor("#475569")
        }
        val custName = customer?.name ?: sale.customerName ?: "Consumidor Final"
        canvas.drawText("Nome: $custName", left, y, textPaint)

        val custDoc = customer?.cpfCnpj?.takeIf { it.isNotBlank() }
        if (custDoc != null) {
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("CPF/CNPJ: $custDoc", right, y, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
        }
        y += 13f

        val custPhone = customer?.phone?.takeIf { it.isNotBlank() }
        val custEmail = customer?.email?.takeIf { it.isNotBlank() }
        val custContact = listOfNotNull(custPhone, custEmail).joinToString(" | ")
        if (custContact.isNotBlank()) {
            canvas.drawText("Contato: $custContact", left, y, textPaint)
            y += 13f
        }

        val custAddress = customer?.address?.takeIf { it.isNotBlank() }
        if (custAddress != null) {
            canvas.drawText("Endereço: $custAddress", left, y, textPaint)
            y += 13f
        }

        y += 4f
        drawDashedSeparator(canvas, left, right, y)
        y += 16f

        // Cabeçalho da Tabela de Itens
        textPaint.apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 9.5f
            color = Color.parseColor("#0F172A")
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("ITEM / DESCRIÇÃO", left, y, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("TOTAL", right, y, textPaint)
        canvas.drawText("QTD x UNIT", right - 70f, y, textPaint)

        y += 8f
        val linePaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 1f
        }
        canvas.drawLine(left, y, right, y, linePaint)
        y += 14f

        // Linhas de Itens
        items.forEachIndexed { index, item ->
            textPaint.apply {
                textAlign = Paint.Align.LEFT
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textSize = 9.5f
                color = Color.parseColor("#1E293B")
            }

            val truncatedName = if (item.productName.length > 24) item.productName.take(22) + "..." else item.productName
            canvas.drawText("${index + 1}. $truncatedName", left, y, textPaint)

            textPaint.apply {
                textAlign = Paint.Align.RIGHT
                color = Color.parseColor("#475569")
                textSize = 9f
            }
            val qtyUnit = "${item.quantity.toInt()} x ${item.unitPrice.toBrlCurrency()}"
            canvas.drawText(qtyUnit, right - 70f, y, textPaint)

            textPaint.apply {
                color = Color.parseColor("#0F172A")
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 9.5f
            }
            canvas.drawText(item.subtotal.toBrlCurrency(), right, y, textPaint)

            y += 18f
        }

        y += 4f
        drawDashedSeparator(canvas, left, right, y)
        y += 16f

        // Resumo Financeiro
        val subtotal = items.sumOf { it.subtotal }

        textPaint.apply {
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 10f
            color = Color.parseColor("#475569")
        }
        canvas.drawText("Subtotal", left, y, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(subtotal.toBrlCurrency(), right, y, textPaint)
        y += 16f

        if (sale.discountAmount > 0.005) {
            textPaint.apply {
                textAlign = Paint.Align.LEFT
                color = Color.parseColor("#DC2626")
            }
            canvas.drawText("Desconto Aplicado", left, y, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("-${sale.discountAmount.toBrlCurrency()}", right, y, textPaint)
            y += 16f
        }

        // TOTAL DA VENDA (Caixa destacada)
        val totalBoxRect = RectF(left, y, right, y + 36)
        val totalBoxPaint = Paint().apply {
            color = Color.parseColor("#F0FDF4")
            style = Paint.Style.FILL
        }
        val totalBorderPaint = Paint().apply {
            color = Color.parseColor("#86EFAC")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(totalBoxRect, 8f, 8f, totalBoxPaint)
        canvas.drawRoundRect(totalBoxRect, 8f, 8f, totalBorderPaint)

        textPaint.apply {
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 12f
            color = Color.parseColor("#15803D")
        }
        canvas.drawText("TOTAL DA VENDA", left + 12f, y + 23f, textPaint)

        textPaint.apply {
            textAlign = Paint.Align.RIGHT
            textSize = 15f
            color = Color.parseColor("#15803D")
        }
        canvas.drawText(sale.totalAmount.toBrlCurrency(), right - 12f, y + 24f, textPaint)
        y += 48f

        // Forma de Pagamento + Status
        textPaint.apply {
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 10f
            color = Color.parseColor("#334155")
        }
        canvas.drawText("Forma de Pagamento:", left, y, textPaint)
        textPaint.apply {
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(sale.paymentMethod, right, y, textPaint)
        y += 16f

        // Valor Pago & Pendente
        textPaint.apply {
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText("Valor Pago:", left, y, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(sale.paidAmount.toBrlCurrency(), right, y, textPaint)
        y += 16f

        val remaining = sale.remainingAmount
        if (remaining > 0.005) {
            textPaint.apply {
                textAlign = Paint.Align.LEFT
                color = Color.parseColor("#D97706")
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("Saldo Pendente:", left, y, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(remaining.toBrlCurrency(), right, y, textPaint)
            y += 16f
        }

        // Vencimento (se aplicável)
        if (sale.dueDate != null) {
            textPaint.apply {
                textAlign = Paint.Align.LEFT
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                color = if (status == SalePaymentStatus.ATRASADO) Color.parseColor("#DC2626") else Color.parseColor("#475569")
            }
            val vencimentoLabel = if (status == SalePaymentStatus.ATRASADO) "Vencimento (Atrasado!):" else "Vencimento:"
            canvas.drawText(vencimentoLabel, left, y, textPaint)
            textPaint.apply {
                textAlign = Paint.Align.RIGHT
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(sale.dueDate.toFormattedDate(), right, y, textPaint)
            y += 16f
        }

        // Observação (se existir)
        if (!sale.observation.isNullOrBlank()) {
            y += 4f
            textPaint.apply {
                textAlign = Paint.Align.LEFT
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                textSize = 9f
                color = Color.parseColor("#64748B")
            }
            canvas.drawText("Obs: ${sale.observation}", left, y, textPaint)
            y += 14f
        }

        y += 6f
        drawDashedSeparator(canvas, left, right, y)
        y += 20f

        // Rodapé de Agradecimento
        textPaint.apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 11f
            color = Color.parseColor("#0F172A")
        }
        canvas.drawText("Obrigado pela preferência!", centerX, y, textPaint)
        y += 15f

        textPaint.apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 9.5f
            color = Color.parseColor("#475569")
        }
        canvas.drawText(business.name, centerX, y, textPaint)
        y += 13f

        if (!business.cnpj.isNullOrBlank()) {
            canvas.drawText("CNPJ: ${business.cnpj}", centerX, y, textPaint)
            y += 13f
        }

        textPaint.apply {
            textSize = 8f
            color = Color.parseColor("#94A3B8")
        }
        val genTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(Date())
        canvas.drawText("Documento não fiscal para simples conferência", centerX, y, textPaint)
        y += 11f
        canvas.drawText("Gerado em $genTime", centerX, y, textPaint)

        document.finishPage(page)

        val outFile = PdfShareUtils.getCachePdfFile(context, "comprovante_venda_${sale.id.take(8)}.pdf")
        val stream = FileOutputStream(outFile)
        document.writeTo(stream)
        stream.close()
        document.close()

        return outFile
    }

    private fun drawDashedSeparator(canvas: Canvas, startX: Float, endX: Float, y: Float) {
        val paint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 1.2f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
        }
        canvas.drawLine(startX, y, endX, y, paint)
    }

    private fun drawDottedSeparator(canvas: Canvas, startX: Float, endX: Float, y: Float) {
        val paint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 1f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f)
        }
        canvas.drawLine(startX, y, endX, y, paint)
    }

    private fun loadBitmap(context: Context, logoUri: String?): Bitmap? {
        if (logoUri.isNullOrBlank()) return null
        return try {
            if (logoUri.startsWith("data:image")) {
                val base64Data = logoUri.substringAfter("base64,")
                val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else if (logoUri.startsWith("content://") || logoUri.startsWith("file://")) {
                val uri = Uri.parse(logoUri)
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                }
            } else {
                val file = File(logoUri)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
