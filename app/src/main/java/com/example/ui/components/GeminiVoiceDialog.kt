package com.example.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ai.GeminiVoiceAction
import com.example.data.ai.VoiceActionType
import com.example.data.ai.VoicePaymentMethod
import com.example.data.model.SalePaymentStatus
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.WarningAmber
import java.util.Locale

@Composable
fun GeminiVoiceDialog(
    isOpen: Boolean,
    isProcessing: Boolean,
    result: GeminiVoiceAction?,
    errorMessage: String?,
    isSpeaking: Boolean = false,
    onSpeakText: (String) -> Unit = {},
    onStopSpeaking: () -> Unit = {},
    onDismiss: () -> Unit,
    onSubmitSpokenText: (String) -> Unit,
    onConfirmAction: (GeminiVoiceAction) -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    var textInput by remember { mutableStateOf("") }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val spokenText = activityResult.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                textInput = spokenText
                onSubmitSpokenText(spokenText)
            }
        }
    }

    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("pt", "BR"))
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale sua dúvida, ideia ou o que deseja lançar")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Reconhecimento de voz não suportado neste dispositivo. Use a digitação abaixo.", Toast.LENGTH_LONG).show()
        }
    }

    // Animação de pulso para o microfone
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isProcessing) 1.15f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(26.dp))
                .testTag("gemini_voice_dialog"),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header com o nome solicitado: "Sua assistente pessoal de negócios"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        ) {
                            Box(
                                modifier = Modifier.padding(9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sua assistente pessoal de negócios",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Responde por voz, tira dúvidas, dá ideias e preenche campos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Indicador sonoro caso esteja falando
                AnimatedVisibility(visible = isSpeaking) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = EmeraldPrimary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Sua assistente está falando...",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = EmeraldPrimary
                                )
                            }

                            Text(
                                text = "Parar Áudio",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { onStopSpeaking() }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Se houver resultado analisado pela IA, mostra o card com os campos ou ideias
                if (result != null && result.actionType != VoiceActionType.UNKNOWN) {
                    VoiceActionResultCard(
                        action = result,
                        isSpeaking = isSpeaking,
                        onSpeak = { text -> onSpeakText(text) },
                        onStopSpeaking = onStopSpeaking,
                        onConfirm = {
                            onConfirmAction(result)
                            onDismiss()
                        },
                        onRetry = {
                            textInput = ""
                            startListening()
                        }
                    )
                } else {
                    // Modo de Escuta / Pergunta
                    Text(
                        text = "Pergunte ideias, peça dicas de vendas ou fale suas vendas e despesas para preenchimento!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Botão Central de Microfone
                    Box(
                        modifier = Modifier
                            .scale(if (isProcessing) pulseScale else 1f)
                            .size(86.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                    )
                                )
                            )
                            .clickable(enabled = !isProcessing) { startListening() }
                            .testTag("gemini_mic_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(42.dp),
                                strokeWidth = 3.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Falar",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isProcessing) "Sua assistente está pensando na resposta..." else "Toque no microfone para falar",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isProcessing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    )

                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = ExpenseRed,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Campo de Texto Alternativo (digitação de perguntas, ideias ou dados)
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gemini_voice_text_input"),
                        label = { Text("Ou digite qualquer pergunta, ideia ou venda...") },
                        placeholder = { Text("Ex: Como atrair clientes? / Vendi 2 bolos por R$ 20 no Pix") },
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = {
                            if (textInput.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        onSubmitSpokenText(textInput)
                                    },
                                    enabled = !isProcessing
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "Enviar",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        singleLine = false,
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sugestões Rápidas: Ideias de Negócios e Preenchimento
                    Text(
                        text = "Exemplos do que você pode perguntar ou pedir:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VoiceExampleChip(
                            label = "💡 Ideias para atrair mais clientes hoje",
                            onClick = {
                                textInput = "Quais são boas ideias para atrair mais clientes hoje?"
                                onSubmitSpokenText(textInput)
                            }
                        )
                        VoiceExampleChip(
                            label = "🏷️ Ideias de promoção para o fim de semana",
                            onClick = {
                                textInput = "Me dê ideias criativas de promoções para o fim de semana"
                                onSubmitSpokenText(textInput)
                            }
                        )
                        VoiceExampleChip(
                            label = "💰 Como calcular meu lucro de forma segura?",
                            onClick = {
                                textInput = "Como calcular minha margem de lucro com segurança?"
                                onSubmitSpokenText(textInput)
                            }
                        )
                        VoiceExampleChip(
                            label = "🛒 Vendi 2 Cafés por 10 reais no Pix para o Pedro",
                            onClick = {
                                textInput = "Vendi 2 Cafés por 10 reais no Pix para o Pedro"
                                onSubmitSpokenText(textInput)
                            }
                        )
                        VoiceExampleChip(
                            label = "⏳ Venda fiado 1 bolo 35 reais dona Maria",
                            onClick = {
                                textInput = "Venda fiado 1 bolo 35 reais dona Maria"
                                onSubmitSpokenText(textInput)
                            }
                        )
                        VoiceExampleChip(
                            label = "📦 Cadastrar produto Camiseta Polo valor 50 reais estoque 20",
                            onClick = {
                                textInput = "Cadastrar produto Camiseta Polo valor 50 reais estoque 20"
                                onSubmitSpokenText(textInput)
                            }
                        )
                        VoiceExampleChip(
                            label = "💸 Despesa de 120 reais conta de luz no dinheiro",
                            onClick = {
                                textInput = "Despesa de 120 reais conta de luz no dinheiro"
                                onSubmitSpokenText(textInput)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceExampleChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/**
 * Card que mostra tudo o que a IA identificou e preencheu automaticamente nos campos.
 */
@Composable
private fun VoiceActionResultCard(
    action: GeminiVoiceAction,
    isSpeaking: Boolean = false,
    onSpeak: (String) -> Unit = {},
    onStopSpeaking: () -> Unit = {},
    onConfirm: () -> Unit,
    onRetry: () -> Unit
) {
    val (actionTitle, actionIcon, actionColor) = when (action.actionType) {
        VoiceActionType.SALE -> Triple(
            if (action.paymentMethod == VoicePaymentMethod.A_PRAZO || action.paymentStatus == SalePaymentStatus.PENDENTE) "Venda a Prazo Reconhecida" else "Nova Venda Reconhecida",
            Icons.Default.PointOfSale,
            if (action.paymentMethod == VoicePaymentMethod.A_PRAZO) WarningAmber else ProfitGreen
        )
        VoiceActionType.PRODUCT -> Triple("Novo Produto Reconhecido", Icons.Default.ShoppingBag, EmeraldPrimary)
        VoiceActionType.EXPENSE -> Triple("Nova Despesa Reconhecida", Icons.Default.ReceiptLong, ExpenseRed)
        VoiceActionType.ADVICE -> Triple("Ideia & Orientação de Negócios", Icons.Default.Lightbulb, EmeraldPrimary)
        VoiceActionType.UNKNOWN -> Triple("Ação não identificada", Icons.Default.AutoAwesome, InfoBlue)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = actionColor.copy(alpha = 0.08f)),
        border = BorderStroke(1.5.dp, actionColor.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Cabeçalho da ação reconhecida
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(actionColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = actionIcon,
                        contentDescription = null,
                        tint = actionColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = actionTitle,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = actionColor
                    )
                    Text(
                        text = "Texto falado: \"${action.rawSpokenText}\"",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = actionColor.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (action.actionType == VoiceActionType.ADVICE) "✨ Resposta da sua Assistente:" else "✨ Separação Inteligente dos Campos:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Detalhamento dos campos preenchidos conforme o tipo
            when (action.actionType) {
                VoiceActionType.SALE -> {
                    // Bloco 1: CLIENTE
                    SeparatedFieldBlock(
                        icon = Icons.Default.Person,
                        label = "CLIENTE",
                        value = action.customerName ?: "Consumidor Final",
                        highlight = action.customerName != null,
                        subtitle = if (action.customerName != null) "Separado do produto e do valor com sucesso" else "Venda avulsa / À vista"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Bloco 2: PRODUTO / ITEM
                    action.items.forEach { item ->
                        val qtyText = if (item.quantity % 1.0 == 0.0) "${item.quantity.toInt()} ${item.unit}" else "${item.quantity} ${item.unit}"
                        SeparatedFieldBlock(
                            icon = Icons.Default.ShoppingBag,
                            label = "PRODUTO / ITEM VENDIDO",
                            value = item.productName,
                            badge = qtyText,
                            subtitle = if (item.unitPrice > 0) "Preço unitário: R$ ${"%.2f".format(item.unitPrice)}" else null
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Bloco 3: VALOR E PAGAMENTO
                    val total = action.totalAmount ?: action.items.sumOf { it.quantity * it.unitPrice }
                    val isAPrazo = action.paymentMethod == VoicePaymentMethod.A_PRAZO || action.paymentStatus == SalePaymentStatus.PENDENTE
                    val statusText = if (isAPrazo) "Pendente (A Receber)" else "Pago (Quitado)"

                    SeparatedFieldBlock(
                        icon = Icons.Default.Payment,
                        label = "VALOR TOTAL E PAGAMENTO",
                        value = "R$ ${"%.2f".format(total)}",
                        badge = action.paymentMethod.label,
                        subtitle = "Status: $statusText${if (isAPrazo && action.daysUntilDue != null) " • Vence em ${action.daysUntilDue} dias" else ""}",
                        valueColor = if (isAPrazo) WarningAmber else ProfitGreen
                    )
                }

                VoiceActionType.PRODUCT -> {
                    SeparatedFieldBlock(
                        icon = Icons.Default.ShoppingBag,
                        label = "NOME DO PRODUTO",
                        value = action.productName ?: "Mercadoria",
                        badge = action.productCategory ?: "Geral"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SeparatedFieldBlock(
                        icon = Icons.Default.Payment,
                        label = "PREÇO DE VENDA",
                        value = "R$ ${"%.2f".format(action.productPrice ?: 0.0)}",
                        subtitle = "Custo: R$ ${"%.2f".format(action.productCostPrice ?: 0.0)}",
                        valueColor = ProfitGreen
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SeparatedFieldBlock(
                        icon = Icons.Default.Inventory2,
                        label = "ESTOQUE INICIAL",
                        value = "${(action.productStock ?: 10.0).toInt()} unidades"
                    )
                }

                VoiceActionType.EXPENSE -> {
                    SeparatedFieldBlock(
                        icon = Icons.Default.ReceiptLong,
                        label = "DESCRIÇÃO DA DESPESA",
                        value = action.expenseDescription ?: "Despesa",
                        badge = action.expenseCategory ?: "Geral"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SeparatedFieldBlock(
                        icon = Icons.Default.Payment,
                        label = "VALOR GASTO",
                        value = "R$ ${"%.2f".format(action.expenseAmount ?: 0.0)}",
                        badge = action.expensePaymentMethod.label,
                        valueColor = ExpenseRed
                    )
                }

                VoiceActionType.ADVICE -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = action.adviceAnswer ?: action.summary,
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                VoiceActionType.UNKNOWN -> {}
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Botão para Ouvir / Pausar a voz da assistente
            val textToSpeak = action.spokenResponse ?: action.adviceAnswer ?: action.summary
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSpeaking) EmeraldPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, if (isSpeaking) EmeraldPrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (isSpeaking) onStopSpeaking() else onSpeak(textToSpeak)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (isSpeaking) "Parar de falar" else "Ouvir resposta por voz",
                        tint = if (isSpeaking) EmeraldPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSpeaking) "Pausar Leitura de Voz" else "🔊 Ouvir Resposta por Voz",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isSpeaking) EmeraldPrimary else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Botões de Confirmação
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (action.actionType == VoiceActionType.ADVICE) "Outra Pergunta" else "Falar de Novo")
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("btn_confirm_voice_action"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = actionColor)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (action.actionType == VoiceActionType.ADVICE) "Entendido" else "Confirmar e Salvar",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SeparatedFieldBlock(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    badge: String? = null,
    subtitle: String? = null,
    highlight: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = valueColor
                    )
                    if (!badge.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
