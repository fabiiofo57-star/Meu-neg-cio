package com.example.ui.screens.sales

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.BusinessEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.SalePaymentStatus
import com.example.data.model.SaleWithItems
import com.example.ui.components.BusinessAvatar
import com.example.ui.components.toBrlCurrency
import com.example.ui.components.toFormattedDate
import com.example.ui.components.toFormattedDateTime
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.WarningAmber
import com.example.util.pdf.PdfShareUtils
import com.example.util.pdf.ReceiptPdfGenerator

@Composable
fun SaleReceiptDialog(
    business: BusinessEntity,
    saleWithItems: SaleWithItems,
    customer: CustomerEntity? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sale = saleWithItems.sale
    val items = saleWithItems.items
    val status = sale.effectiveStatus
    val remaining = sale.remainingAmount

    // Pré-computa o arquivo PDF em background quando necessário
    fun getOrGeneratePdf() = ReceiptPdfGenerator.generateReceiptPdf(
        context = context,
        business = business,
        saleWithItems = saleWithItems,
        customer = customer
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header com Título e Fechar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.ReceiptLong,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Comprovante de Venda",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Documento comercial não fiscal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("btn_close_receipt")) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Área rolável do Comprovante (Visual de Cupom Premium)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFAFAFA),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        // Topo: Logo + Nome do Negócio
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            BusinessAvatar(
                                logoUri = business.logoUri,
                                businessName = business.name,
                                size = 48.dp,
                                showBorder = true
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = business.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp
                                ),
                                color = Color(0xFF0F172A),
                                textAlign = TextAlign.Center
                            )

                            if (!business.razaoSocial.isNullOrBlank()) {
                                Text(
                                    text = business.razaoSocial,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF475569),
                                    textAlign = TextAlign.Center
                                )
                            }

                            if (!business.cnpj.isNullOrBlank()) {
                                Text(
                                    text = "CNPJ: ${business.cnpj}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color(0xFF475569),
                                    textAlign = TextAlign.Center
                                )
                            }

                            val contact = listOfNotNull(
                                business.phone.takeIf { it.isNotBlank() }?.let { "Tel: $it" },
                                business.email?.takeIf { it.isNotBlank() }
                            ).joinToString(" • ")
                            if (contact.isNotBlank()) {
                                Text(
                                    text = contact,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B),
                                    textAlign = TextAlign.Center
                                )
                            }

                            val fullAddress = listOfNotNull(
                                business.address?.takeIf { it.isNotBlank() },
                                listOfNotNull(
                                    business.city.takeIf { it.isNotBlank() },
                                    business.state?.takeIf { it.isNotBlank() }
                                ).joinToString("/").takeIf { it.isNotBlank() },
                                business.cep?.takeIf { it.isNotBlank() }?.let { "CEP: $it" }
                            ).joinToString(" - ")

                            if (fullAddress.isNotBlank()) {
                                Text(
                                    text = fullAddress,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFFCBD5E1), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Título do documento e identificador
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFEFF6FF),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "COMPROVANTE DE VENDA",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                ),
                                color = Color(0xFF1D4ED8),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 5.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Identificador: #VND-${sale.id.take(8).uppercase()}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF334155)
                            )
                            Text(
                                text = sale.date.toFormattedDateTime(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Cliente
                        Text(
                            text = "DADOS DO CLIENTE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        val custName = customer?.name ?: sale.customerName ?: "Consumidor Final"
                        Text(
                            text = "Cliente: $custName",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF334155)
                        )

                        if (!customer?.cpfCnpj.isNullOrBlank()) {
                            Text(
                                text = "CPF/CNPJ: ${customer.cpfCnpj}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B)
                            )
                        }

                        val custContact = listOfNotNull(
                            customer?.phone?.takeIf { it.isNotBlank() },
                            customer?.email?.takeIf { it.isNotBlank() }
                        ).joinToString(" | ")
                        if (custContact.isNotBlank()) {
                            Text(
                                text = "Contato: $custContact",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B)
                            )
                        }

                        if (!customer?.address.isNullOrBlank()) {
                            Text(
                                text = "Endereço: ${customer.address}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFFCBD5E1), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Tabela de itens
                        Text(
                            text = "ITENS DA VENDA",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        items.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${index + 1}. ${item.productName}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = "${item.quantity.toInt()} un x ${item.unitPrice.toBrlCurrency()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                Text(
                                    text = item.subtotal.toBrlCurrency(),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFFCBD5E1), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Resumo financeiro
                        val subtotal = items.sumOf { it.subtotal }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Subtotal:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF475569))
                            Text(subtotal.toBrlCurrency(), style = MaterialTheme.typography.bodySmall, color = Color(0xFF475569))
                        }

                        if (sale.discountAmount > 0.005) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Desconto:", style = MaterialTheme.typography.bodySmall, color = ExpenseRed)
                                Text("-${sale.discountAmount.toBrlCurrency()}", style = MaterialTheme.typography.bodySmall, color = ExpenseRed)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // TOTAL DA VENDA
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF0FDF4),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF86EFAC), RoundedCornerShape(10.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TOTAL DA VENDA",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color(0xFF15803D)
                                )
                                Text(
                                    text = sale.totalAmount.toBrlCurrency(),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    ),
                                    color = Color(0xFF15803D)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Forma de Pagamento e Status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Forma de Pagamento:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF475569))
                            Text(
                                sale.paymentMethod,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF0F172A)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Valor Pago:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF475569))
                            Text(
                                sale.paidAmount.toBrlCurrency(),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = ProfitGreen
                            )
                        }

                        if (remaining > 0.005) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Saldo Pendente:", style = MaterialTheme.typography.bodySmall, color = WarningAmber)
                                Text(
                                    remaining.toBrlCurrency(),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = WarningAmber
                                )
                            }
                        }

                        if (sale.dueDate != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    if (status == SalePaymentStatus.ATRASADO) "Vencimento (Atrasado!):" else "Vencimento:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (status == SalePaymentStatus.ATRASADO) ExpenseRed else Color(0xFF475569)
                                )
                                Text(
                                    sale.dueDate.toFormattedDate(),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (status == SalePaymentStatus.ATRASADO) ExpenseRed else Color(0xFF0F172A)
                                )
                            }
                        }

                        if (!sale.observation.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Obs: ${sale.observation}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color(0xFFCBD5E1), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Rodapé
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Obrigado pela preferência!",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = business.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF475569)
                            )
                            if (!business.cnpj.isNullOrBlank()) {
                                Text(
                                    text = "CNPJ: ${business.cnpj}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Documento comercial não fiscal para simples conferência",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color(0xFF94A3B8),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Barra de Ações (Compartilhar, Salvar, Imprimir)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Compartilhar (WhatsApp, Telegram, etc.)
                    Button(
                        onClick = {
                            val pdfFile = getOrGeneratePdf()
                            PdfShareUtils.sharePdf(
                                context = context,
                                file = pdfFile,
                                title = "Comprovante de Venda #VND-${sale.id.take(8).uppercase()}"
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(44.dp)
                            .testTag("btn_share_receipt")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Compartilhar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Salvar no Aparelho (Downloads)
                    OutlinedButton(
                        onClick = {
                            val pdfFile = getOrGeneratePdf()
                            val filename = "Comprovante_Venda_${sale.id.take(8).uppercase()}.pdf"
                            PdfShareUtils.saveToDownloads(context, pdfFile, filename)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("btn_save_receipt")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Salvar", fontSize = 13.sp)
                    }

                    // Imprimir
                    IconButton(
                        onClick = {
                            val pdfFile = getOrGeneratePdf()
                            val jobName = "Comprovante_Venda_${sale.id.take(8).uppercase()}"
                            PdfShareUtils.printPdf(context, pdfFile, jobName)
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .testTag("btn_print_receipt")
                    ) {
                        Icon(
                            Icons.Default.Print,
                            contentDescription = "Imprimir comprovante",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
