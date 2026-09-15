package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.auth.AuthState
import com.example.ui.screens.MainAppScreen
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.business.BusinessSetupScreen
import com.example.ui.screens.onboarding.OnboardingScreen
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: AppViewModel = viewModel()
            val themeState by viewModel.themeState.collectAsStateWithLifecycle()
            MyApplicationTheme(themeState = themeState) {
                MeuNegocioApp(viewModel = viewModel)
            }
        }
    }
}

enum class NavigationTarget {
    ONBOARDING,
    LOGIN,
    LOADING,
    BUSINESS_SETUP,
    MAIN
}

@Composable
fun MeuNegocioApp(
    viewModel: AppViewModel = viewModel()
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("meu_negocio_prefs", Context.MODE_PRIVATE) }
    var hasSeenOnboarding by remember {
        mutableStateOf(sharedPrefs.getBoolean("has_seen_onboarding", false))
    }

    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val currentBusiness by viewModel.currentBusiness.collectAsStateWithLifecycle()
    val isInitialSyncLoading by viewModel.isInitialSyncLoading.collectAsStateWithLifecycle()
    val isCheckingBusiness by viewModel.isCheckingBusiness.collectAsStateWithLifecycle()

    val currentTarget = when {
        !hasSeenOnboarding -> NavigationTarget.ONBOARDING
        authState is AuthState.Initial || authState is AuthState.Loading -> NavigationTarget.LOADING
        authState !is AuthState.Authenticated -> NavigationTarget.LOGIN
        isCheckingBusiness || isInitialSyncLoading -> NavigationTarget.LOADING
        currentBusiness == null -> NavigationTarget.BUSINESS_SETUP
        else -> NavigationTarget.MAIN
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        AnimatedContent(
            targetState = currentTarget,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "app_navigation"
        ) { target ->
            when (target) {
                NavigationTarget.ONBOARDING -> {
                    OnboardingScreen(
                        onStartClick = {
                            sharedPrefs.edit().putBoolean("has_seen_onboarding", true).apply()
                            hasSeenOnboarding = true
                        }
                    )
                }

                NavigationTarget.LOGIN -> {
                    LoginScreen(
                        viewModel = viewModel,
                        onLoginSuccess = {
                            // Auth state updates automatically
                        }
                    )
                }

                NavigationTarget.LOADING -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = EmeraldPrimary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Sincronizando seus dados da nuvem...",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                            Text(
                                text = "Restaurando catálogo, clientes e vendas",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            androidx.compose.material3.TextButton(
                                onClick = { viewModel.dismissInitialSyncLoading() }
                            ) {
                                Text("Continuar agora", color = EmeraldPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            }
                        }
                    }
                }

                NavigationTarget.BUSINESS_SETUP -> {
                    BusinessSetupScreen(
                        viewModel = viewModel,
                        onSetupCompleted = {
                            // Current business will update reactively and navigate to MAIN
                        }
                    )
                }

                NavigationTarget.MAIN -> {
                    currentBusiness?.let { business ->
                        MainAppScreen(
                            viewModel = viewModel,
                            business = business,
                            onLogout = {
                                // Handled by authRepository.signOut() which triggers navigation to LOGIN
                            }
                        )
                    } ?: Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = EmeraldPrimary)
                    }
                }
            }
        }
    }
}
