package com.example.ui.screens.more

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.ProfitGreen
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.random.Random

// Cor verde característica do WhatsApp
val WhatsAppGreen = Color(0xFF25D366)
val WhatsAppDarkGreen = Color(0xFF128C7E)

data class WhatsAppStatusInfo(
    val status: String = "disconnected", // disconnected, connecting, connected
    val phoneNumber: String = "",
    val pairingCode: String? = null,
    val pairingExpiresAt: Long = 0L,
    val isConnected: Boolean = false
)

@Composable
fun WhatsAppIntegrationDialog(
    userId: String,
    businessName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var statusInfo by remember { mutableStateOf(WhatsAppStatusInfo()) }
    var inputPhone by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isGeneratingCode by remember { mutableStateOf(false) }
    var generatedCode by remember { mutableStateOf<String?>(null) }
    var showCommandsHelp by remember { mutableStateOf(false) }

    // Carrega status atual do Firestore
    LaunchedEffect(userId) {
        if (userId.isNotBlank()) {
            try {
                withContext(Dispatchers.IO) {
                    val db = FirebaseFirestore.getInstance()
                    val snapshot = db.collection("users").document(userId).collection("whatsapp").document("info").get().await()
                    if (snapshot.exists()) {
                        val status = snapshot.getString("status") ?: "disconnected"
                        val phone = snapshot.getString("phoneNumber") ?: ""
                        val code = snapshot.getString("pairingCode")
                        val expires = snapshot.getLong("pairingExpiresAt") ?: 0L
                        val isConnected = status == "connected"
                        statusInfo = WhatsAppStatusInfo(
                            status = status,
                            phoneNumber = phone,
                            pairingCode = code,
                            pairingExpiresAt = expires,
                            isConnected = isConnected
                        )
                        if (!code.isNullOrBlank() && expires > System.currentTimeMillis()) {
                            generatedCode = code
                        }
                        if (phone.isNotBlank()) {
                            inputPhone = phone
                        }
                    }
                }
            } catch (e: Exception) {
                // Silencioso se estiver offline
            } finally {
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("dialog_whatsapp_integration"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(scrollState)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(WhatsAppGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = WhatsAppDarkGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "WhatsApp Business",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Assistente Inteligente & IA",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = WhatsAppGreen)
                    }
                } else {
                    // Status Badge Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (statusInfo.isConnected) ProfitGreen.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (statusInfo.isConnected) ProfitGreen else Color.Gray)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (statusInfo.isConnected) "Conectado ao WhatsApp" else "Não Conectado",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (statusInfo.isConnected) ProfitGreen else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (statusInfo.isConnected && statusInfo.phoneNumber.isNotBlank()) {
                                        Text(
                                            text = "Número: ${statusInfo.phoneNumber}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            text = "Vincule seu número para registrar vendas por voz e texto",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (statusInfo.isConnected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ProfitGreen)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!statusInfo.isConnected) {
                        // Fluxo de Conexão com Código
                        Text(
                            text = "1. Digite seu número de WhatsApp",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = inputPhone,
                            onValueChange = { inputPhone = it },
                            label = { Text("Número com DDD") },
                            placeholder = { Text("Ex: 11988887777") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (inputPhone.trim().length >= 10 && userId.isNotBlank()) {
                                    isGeneratingCode = true
                                    scope.launch {
                                        try {
                                            val clean = inputPhone.replace(Regex("\\D"), "")
                                            val randomDigits = Random.nextInt(1000, 9999)
                                            val code = "MN-$randomDigits"
                                            val expires = System.currentTimeMillis() + 10 * 60 * 1000

                                            withContext(Dispatchers.IO) {
                                                val db = FirebaseFirestore.getInstance()
                                                val data: Map<String, Any> = hashMapOf(
                                                    "phoneNumber" to clean,
                                                    "pairingCode" to code,
                                                    "pairingExpiresAt" to expires,
                                                    "status" to "connecting",
                                                    "updatedAt" to System.currentTimeMillis()
                                                )
                                                db.collection("users").document(userId).collection("whatsapp").document("info").set(data).await()
                                            }

                                            generatedCode = code
                                            statusInfo = statusInfo.copy(
                                                phoneNumber = clean,
                                                pairingCode = code,
                                                pairingExpiresAt = expires,
                                                status = "connecting"
                                            )
                                            Toast.makeText(context, "Código gerado com sucesso!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Erro ao gerar código: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isGeneratingCode = false
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "Informe um número de telefone válido com DDD", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isGeneratingCode && inputPhone.trim().length >= 10,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppDarkGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isGeneratingCode) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Gerar Código de Pareamento")
                            }
                        }

                        // Se o código foi gerado, exibe a instrução com o código em destaque
                        if (generatedCode != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = WhatsAppGreen.copy(alpha = 0.08f)),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, WhatsAppGreen.copy(alpha = 0.4f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "2. Envie este código no WhatsApp",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = WhatsAppDarkGreen
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Envie uma mensagem com o código abaixo para o número oficial do seu negócio no WhatsApp:",
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, WhatsAppGreen.copy(alpha = 0.6f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = generatedCode ?: "",
                                                style = MaterialTheme.typography.headlineMedium.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = WhatsAppDarkGreen,
                                                    letterSpacing = 2.sp
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            IconButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    val clip = ClipData.newPlainText("Código WhatsApp", generatedCode)
                                                    clipboard.setPrimaryClip(clip)
                                                    Toast.makeText(context, "Código copiado!", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar Código", tint = WhatsAppDarkGreen)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "⏳ Válido por 10 minutos",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        // Já conectado: opção de desconectar
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            val db = FirebaseFirestore.getInstance()
                                            val updates: Map<String, Any?> = hashMapOf(
                                                "status" to "disconnected",
                                                "pairingCode" to null
                                            )
                                            db.collection("users").document(userId).collection("whatsapp").document("info")
                                                .update(updates).await()
                                        }
                                        statusInfo = statusInfo.copy(status = "disconnected", isConnected = false)
                                        Toast.makeText(context, "WhatsApp desconectado com sucesso", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Erro ao desconectar: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ExpenseRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Desconectar Este Número")
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Seção de Ajuda e Comandos Suportados
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCommandsHelp = !showCommandsHelp }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = WhatsAppDarkGreen, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "O que posso pedir no WhatsApp?",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Text(
                                text = if (showCommandsHelp) "Ocultar" else "Ver Exemplos",
                                style = MaterialTheme.typography.labelMedium,
                                color = WhatsAppDarkGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    AnimatedVisibility(visible = showCommandsHelp) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            CommandExampleItem(
                                category = "🛍️ Vendas",
                                example = "Venda pro Carlos: 2 camisas polo de 50 reais, Pix",
                                description = "Registra a venda, dá baixa no estoque e calcula o total."
                            )
                            CommandExampleItem(
                                category = "💸 Despesas",
                                example = "Registra uma despesa de 150 reais de combustível",
                                description = "Adiciona a despesa na sua contabilidade do mês."
                            )
                            CommandExampleItem(
                                category = "📦 Estoque",
                                example = "Tenho quantas camisas no estoque?",
                                description = "Informa o saldo e avisa se atingiu o estoque mínimo."
                            )
                            CommandExampleItem(
                                category = "📊 Consultas",
                                example = "Quanto vendi hoje? / Qual meu lucro deste mês?",
                                description = "Devolve o resumo consolidado com ticket médio."
                            )
                            CommandExampleItem(
                                category = "📄 Documentos em PDF",
                                example = "Me manda o comprovante da última venda",
                                description = "Gera o comprovante e entrega o PDF direto no chat!"
                            )
                            CommandExampleItem(
                                category = "🎙️ Mensagens de Áudio",
                                example = "Envie um áudio falando qualquer um dos comandos acima!",
                                description = "O Google Gemini transcreve e entende sua voz com precisão."
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Concluir")
                }
            }
        }
    }
}

@Composable
private fun CommandExampleItem(
    category: String,
    example: String,
    description: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = category, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = WhatsAppDarkGreen)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "💬 \"$example\"",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
