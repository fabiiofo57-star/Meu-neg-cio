package com.example.ui.screens.auth

import android.accounts.AccountManager
import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.auth.AuthState
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SlateNavy
import com.example.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    viewModel: AppViewModel,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val savedUsers by viewModel.savedUsers.collectAsStateWithLifecycle()
    val deviceAccounts = remember { viewModel.authRepository.getDeviceGoogleAccounts() }
    var showEmailForm by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showGoogleAccountDialog by remember { mutableStateOf(false) }
    var googleEmailInput by remember {
        mutableStateOf(
            deviceAccounts.firstOrNull() ?: savedUsers.firstOrNull()?.email ?: "fabiio.FO57@gmail.com"
        )
    }

    val googleAccountChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isLoading = false
        if (result.resultCode == Activity.RESULT_OK) {
            val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!accountName.isNullOrBlank()) {
                isLoading = true
                errorMessage = null
                coroutineScope.launch {
                    val res = viewModel.authRepository.signInWithGoogleEmail(accountName)
                    isLoading = false
                    res.onSuccess {
                        onLoginSuccess()
                    }.onFailure { err ->
                        errorMessage = err.localizedMessage ?: "Erro ao entrar com a conta selecionada."
                    }
                }
            } else {
                showGoogleAccountDialog = true
            }
        } else {
            // Se o seletor do sistema foi fechado ou cancelado, abre o seletor no app
            showGoogleAccountDialog = true
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle top background gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(EmeraldPrimary.copy(alpha = 0.15f), Color.Transparent)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 28.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(EmeraldPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Acessar o Meu Negócio",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Entre para gerenciar suas vendas, estoque e finanças",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Actions Container Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Error message banner
                        if (errorMessage != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = errorMessage!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { errorMessage = null }) {
                                        Text("OK", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Google Sign-In Button (Nativo e Direto)
                        Button(
                            onClick = {
                                errorMessage = null
                                try {
                                    val intent = AccountManager.newChooseAccountIntent(
                                        null,
                                        null,
                                        arrayOf("com.google"),
                                        null,
                                        null,
                                        null,
                                        null
                                    )
                                    isLoading = true
                                    googleAccountChooserLauncher.launch(intent)
                                } catch (e: Exception) {
                                    Log.w("LoginScreen", "newChooseAccountIntent indisponível: ${e.message}")
                                    isLoading = false
                                    showGoogleAccountDialog = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("login_google_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Stylized "G" icon
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "G",
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF4285F4),
                                        fontSize = 16.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Continuar com Google",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Fast Instant / Demo access button
                        OutlinedButton(
                            onClick = {
                                isLoading = true
                                coroutineScope.launch {
                                    viewModel.authRepository.signInDemoOrLocal()
                                    isLoading = false
                                    onLoginSuccess()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("login_demo_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = EmeraldPrimary
                            )
                        ) {
                            Text(
                                text = "Entrar Imediato (Demonstração / Teste)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Divider
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f))
                            Text(
                                text = "ou e-mail",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Toggle E-mail & Password Fields
                        TextButton(
                            onClick = { showEmailForm = !showEmailForm },
                            modifier = Modifier.testTag("toggle_email_login_button")
                        ) {
                            Text(
                                text = if (showEmailForm) "Ocultar formulário de e-mail" else "Entrar com e-mail e senha",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        AnimatedVisibility(visible = showEmailForm) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text("E-mail") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Email, contentDescription = null)
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("login_email_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Senha") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Lock, contentDescription = null)
                                    },
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("login_password_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        if (email.isNotBlank() && password.isNotBlank()) {
                                            isLoading = true
                                            errorMessage = null
                                            coroutineScope.launch {
                                                val res = viewModel.authRepository.signInWithEmailPassword(email.trim(), password)
                                                isLoading = false
                                                res.onSuccess { onLoginSuccess() }
                                                    .onFailure { errorMessage = it.localizedMessage }
                                            }
                                        } else {
                                            errorMessage = "Preencha o e-mail e a senha."
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("login_submit_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                                ) {
                                    Text("Entrar com E-mail", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (isLoading) {
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = EmeraldPrimary,
                                strokeWidth = 3.dp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Footer note
                Text(
                    text = "Seus dados ficam salvos localmente e seguros no seu celular.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (showGoogleAccountDialog) {
            val allAccounts = remember(deviceAccounts, savedUsers) {
                val list = mutableListOf<Triple<String, String, String?>>()
                deviceAccounts.forEach { acc ->
                    val saved = savedUsers.find { it.email.equals(acc, ignoreCase = true) }
                    val name = saved?.name ?: acc.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() }
                    list.add(Triple(acc, name, saved?.photoUrl))
                }
                savedUsers.forEach { user ->
                    if (list.none { it.first.equals(user.email, ignoreCase = true) }) {
                        list.add(Triple(user.email, user.name, user.photoUrl))
                    }
                }
                if (list.none { it.first.equals("fabiio.FO57@gmail.com", ignoreCase = true) }) {
                    list.add(Triple("fabiio.FO57@gmail.com", "Fábio", null))
                }
                list
            }
            var showManualInput by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showGoogleAccountDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "G",
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF4285F4),
                                fontSize = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Entrar com Conta Google",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Escolha sua conta para acessar o sistema",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        if (!showManualInput) {
                            Text(
                                text = "Toque na sua conta para entrar:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            allAccounts.forEach { account ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            showGoogleAccountDialog = false
                                            isLoading = true
                                            errorMessage = null
                                            coroutineScope.launch {
                                                val res = viewModel.authRepository.signInWithGoogleEmail(
                                                    email = account.first,
                                                    displayName = account.second,
                                                    photoUrl = account.third
                                                )
                                                isLoading = false
                                                res.onSuccess {
                                                    onLoginSuccess()
                                                }.onFailure { err ->
                                                    errorMessage = err.localizedMessage ?: "Erro ao entrar com a conta"
                                                }
                                            }
                                        },
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(EmeraldPrimary.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = (account.second.firstOrNull() ?: account.first.firstOrNull() ?: 'G').uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                color = EmeraldPrimary,
                                                fontSize = 16.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = account.second,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = account.first,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                showGoogleAccountDialog = false
                                                isLoading = true
                                                errorMessage = null
                                                coroutineScope.launch {
                                                    val res = viewModel.authRepository.signInWithGoogleEmail(
                                                        email = account.first,
                                                        displayName = account.second,
                                                        photoUrl = account.third
                                                    )
                                                    isLoading = false
                                                    res.onSuccess {
                                                        onLoginSuccess()
                                                    }.onFailure { err ->
                                                        errorMessage = err.localizedMessage ?: "Erro ao entrar com a conta"
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text("Entrar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = { showManualInput = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Digitar outro e-mail Google")
                            }
                        } else {
                            Text(
                                text = "Digite o e-mail da sua conta Google para entrar:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedTextField(
                                value = googleEmailInput,
                                onValueChange = { googleEmailInput = it },
                                label = { Text("E-mail Google") },
                                placeholder = { Text("seu-email@gmail.com") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("google_email_dialog_input"),
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { showManualInput = false }
                            ) {
                                Text("Ver contas detectadas")
                            }
                        }
                    }
                },
                confirmButton = {
                    if (showManualInput) {
                        Button(
                            onClick = {
                                val clean = googleEmailInput.trim()
                                if (clean.isNotBlank() && clean.contains("@")) {
                                    showGoogleAccountDialog = false
                                    isLoading = true
                                    errorMessage = null
                                    coroutineScope.launch {
                                        val result = viewModel.authRepository.signInWithGoogleEmail(
                                            email = clean
                                        )
                                        isLoading = false
                                        result.onSuccess {
                                            onLoginSuccess()
                                        }.onFailure { e ->
                                            errorMessage = e.localizedMessage ?: "Falha ao entrar com e-mail Google."
                                        }
                                    }
                                } else {
                                    errorMessage = "Por favor, digite um e-mail válido."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("confirm_google_email_button")
                        ) {
                            Text("Entrar")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showGoogleAccountDialog = false }
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}
