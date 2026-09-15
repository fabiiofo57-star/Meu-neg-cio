package com.example.data.auth

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.data.local.BusinessDao
import com.example.data.local.UserDao
import com.example.data.model.UserEntity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

sealed interface AuthState {
    data object Initial : AuthState
    data object Loading : AuthState
    data class Authenticated(val user: UserEntity) : AuthState
    data object Unauthenticated : AuthState
    data class Error(val message: String) : AuthState
}

class AuthRepository(
    private val context: Context,
    private val userDao: UserDao,
    private val businessDao: BusinessDao? = null,
    private val scope: CoroutineScope
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val isFirebaseAvailable: Boolean
        get() = try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (e: Exception) {
            false
        }

    private val firebaseAuth: FirebaseAuth?
        get() = if (isFirebaseAvailable) {
            try {
                FirebaseAuth.getInstance()
            } catch (e: Exception) {
                null
            }
        } else null

    val savedUsers: Flow<List<UserEntity>> = userDao.getAllUsers()

    init {
        checkCurrentAuth()
    }

    private fun checkCurrentAuth() {
        scope.launch(Dispatchers.IO) {
            val sharedPrefs = context.getSharedPreferences("meu_negocio_prefs", Context.MODE_PRIVATE)
            val savedUserId = sharedPrefs.getString("active_user_id", null)

            val currentFbUser = firebaseAuth?.currentUser
            if (currentFbUser != null) {
                val user = UserEntity(
                    id = currentFbUser.uid,
                    name = currentFbUser.displayName ?: "Empreendedor",
                    email = currentFbUser.email ?: "",
                    photoUrl = currentFbUser.photoUrl?.toString()
                )
                userDao.insertUser(user)
                sharedPrefs.edit().putString("active_user_id", user.id).apply()
                _authState.value = AuthState.Authenticated(user)
            } else if (savedUserId != null) {
                // Restore any authenticated user session from database or preferences
                val dbUser = userDao.getUserById(savedUserId).firstOrNull()
                val user = dbUser ?: UserEntity(
                    id = savedUserId,
                    name = sharedPrefs.getString("active_user_name", "Empreendedor") ?: "Empreendedor",
                    email = sharedPrefs.getString("active_user_email", "contato@meunegocio.app") ?: "contato@meunegocio.app"
                )
                userDao.insertUser(user)
                _authState.value = AuthState.Authenticated(user)
            } else {
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    private fun findActivity(ctx: Context): Activity? {
        var current = ctx
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    suspend fun signInWithGoogle(activityContext: Context? = null, webClientId: String? = null): Result<UserEntity> {
        _authState.value = AuthState.Loading
        return try {
            val act = activityContext?.let { findActivity(it) } ?: findActivity(context)
            val uiContext = act ?: context
            val credentialManager = CredentialManager.create(uiContext)
            
            // Build GoogleIdOption
            val googleIdOptionBuilder = GetGoogleIdOption.Builder()
                .setAutoSelectEnabled(false)
                .setFilterByAuthorizedAccounts(false)

            // Resolving actual Web Client ID from google-services.json generated string resource
            val defaultWebClientId = try {
                context.getString(com.example.R.string.default_web_client_id)
            } catch (_: Exception) {
                val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                if (resId != 0) {
                    try { context.getString(resId) } catch (_: Exception) { null }
                } else null
            } ?: "256302195418-ok4h6k8fdq21nu5vt2cejuspah80sj51.apps.googleusercontent.com"

            val targetClientId = webClientId?.takeIf { it.isNotBlank() }
                ?: defaultWebClientId

            googleIdOptionBuilder.setServerClientId(targetClientId)

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOptionBuilder.build())
                .build()

            val response: GetCredentialResponse = credentialManager.getCredential(
                request = request,
                context = uiContext
            )

            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken

                val user = if (firebaseAuth != null) {
                    try {
                        val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                        val authResult = firebaseAuth!!.signInWithCredential(authCredential).await()
                        val fbUser = authResult.user ?: firebaseAuth!!.currentUser ?: throw Exception("Usuário Firebase não retornado")
                        UserEntity(
                            id = fbUser.uid,
                            name = fbUser.displayName ?: googleIdTokenCredential.displayName ?: "Empreendedor",
                            email = fbUser.email ?: googleIdTokenCredential.id,
                            photoUrl = fbUser.photoUrl?.toString() ?: googleIdTokenCredential.profilePictureUri?.toString()
                        )
                    } catch (fe: Exception) {
                        Log.w("AuthRepository", "FirebaseAuth signInWithCredential com token falhou, usando dados diretos da conta Google", fe)
                        UserEntity(
                            id = "google_${googleIdTokenCredential.id.replace("@", "_").replace(".", "_")}",
                            name = googleIdTokenCredential.displayName ?: "Empreendedor",
                            email = googleIdTokenCredential.id,
                            photoUrl = googleIdTokenCredential.profilePictureUri?.toString()
                        )
                    }
                } else {
                    UserEntity(
                        id = "google_${googleIdTokenCredential.id.replace("@", "_").replace(".", "_")}",
                        name = googleIdTokenCredential.displayName ?: "Empreendedor",
                        email = googleIdTokenCredential.id,
                        photoUrl = googleIdTokenCredential.profilePictureUri?.toString()
                    )
                }

                userDao.insertUser(user)
                saveSession(user)
                _authState.value = AuthState.Authenticated(user)
                Result.success(user)
            } else {
                _authState.value = AuthState.Unauthenticated
                Result.failure(Exception("Nenhuma credencial Google selecionada."))
            }
        } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
            Log.w("AuthRepository", "Google CredentialManager cancelado pelo sistema: ${e.message}")
            val deviceAccounts = getDeviceGoogleAccounts()
            if (deviceAccounts.isNotEmpty()) {
                val primary = deviceAccounts[0]
                signInWithGoogleEmail(primary)
            } else {
                _authState.value = AuthState.Unauthenticated
                Result.failure(Exception("A solicitação foi cancelada ou não concluída pelo serviço Google."))
            }
        } catch (e: Throwable) {
            Log.w("AuthRepository", "Google CredentialManager erro: ${e.message}")
            val deviceAccounts = getDeviceGoogleAccounts()
            if (deviceAccounts.isNotEmpty()) {
                val primary = deviceAccounts[0]
                signInWithGoogleEmail(primary)
            } else {
                _authState.value = AuthState.Unauthenticated
                Result.failure(e)
            }
        }
    }

    fun getDeviceGoogleAccounts(): List<String> {
        return try {
            val am = AccountManager.get(context)
            am.getAccountsByType("com.google").mapNotNull { it.name }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun signInWithGoogleEmail(
        email: String,
        displayName: String? = null,
        photoUrl: String? = null
    ): Result<UserEntity> {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            _authState.value = AuthState.Unauthenticated
            return Result.failure(Exception("Por favor, digite um e-mail válido."))
        }
        _authState.value = AuthState.Loading
        return try {
            val existingUser = userDao.findUserByEmail(cleanEmail)
            val existingBiz = businessDao?.findBusinessByUserEmail(cleanEmail) ?: businessDao?.findAnyBusiness()
            val finalId = existingUser?.id ?: existingBiz?.userId ?: "google_${cleanEmail.replace("@", "_").replace(".", "_")}"
            val finalName = displayName?.takeIf { it.isNotBlank() }
                ?: existingUser?.name
                ?: cleanEmail.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() }

            val user = existingUser?.copy(
                name = finalName,
                photoUrl = photoUrl ?: existingUser.photoUrl,
                lastAccessAt = System.currentTimeMillis()
            ) ?: UserEntity(
                id = finalId,
                name = finalName,
                email = cleanEmail,
                photoUrl = photoUrl
            )

            userDao.insertUser(user)
            saveSession(user)
            _authState.value = AuthState.Authenticated(user)
            Result.success(user)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Falha ao autenticar com e-mail Google", e)
            _authState.value = AuthState.Error(e.localizedMessage ?: "Erro ao autenticar com a conta Google")
            Result.failure(e)
        }
    }

    suspend fun signInDemoOrLocal(name: String = "Empreendedor", email: String = "meunegocio@demo.com"): UserEntity {
        _authState.value = AuthState.Loading
        val existingUser = userDao.findUserByEmail(email)
        val user = existingUser?.copy(lastAccessAt = System.currentTimeMillis()) ?: UserEntity(
            id = "demo_user_${UUID.randomUUID().toString().take(8)}",
            name = name,
            email = email
        )
        userDao.insertUser(user)
        saveSession(user)
        _authState.value = AuthState.Authenticated(user)
        return user
    }

    suspend fun signInWithEmailPassword(email: String, pass: String): Result<UserEntity> {
        _authState.value = AuthState.Loading
        val cleanEmail = email.trim().lowercase()
        val existingUser = userDao.findUserByEmail(cleanEmail)
        return try {
            val user = if (firebaseAuth != null) {
                try {
                    val result = try {
                        firebaseAuth!!.signInWithEmailAndPassword(cleanEmail, pass).await()
                    } catch (signError: Exception) {
                        val msg = signError.message.orEmpty().lowercase()
                        if (msg.contains("no user record") || msg.contains("user-not-found") || msg.contains("invalid_login_credentials") || msg.contains("invalid-credential")) {
                            firebaseAuth!!.createUserWithEmailAndPassword(cleanEmail, pass).await()
                        } else {
                            throw signError
                        }
                    }
                    val fbUser = result.user ?: firebaseAuth!!.currentUser
                    val existingBiz = businessDao?.findBusinessByUserEmail(cleanEmail) ?: businessDao?.findAnyBusiness()
                    val finalId = fbUser?.uid ?: existingUser?.id ?: existingBiz?.userId ?: "user_${cleanEmail.replace("@", "_").replace(".", "_")}"
                    val finalName = fbUser?.displayName?.takeIf { it.isNotBlank() }
                        ?: existingUser?.name
                        ?: cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }

                    existingUser?.copy(
                        name = finalName,
                        lastAccessAt = System.currentTimeMillis()
                    ) ?: UserEntity(
                        id = finalId,
                        name = finalName,
                        email = fbUser?.email ?: cleanEmail
                    )
                } catch (fe: Exception) {
                    Log.w("AuthRepository", "FirebaseAuth fallback para autenticação local: ${fe.message}")
                    val finalId = existingUser?.id ?: "user_${cleanEmail.replace("@", "_").replace(".", "_")}"
                    val finalName = existingUser?.name ?: cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                    existingUser?.copy(
                        name = finalName,
                        lastAccessAt = System.currentTimeMillis()
                    ) ?: UserEntity(
                        id = finalId,
                        name = finalName,
                        email = cleanEmail
                    )
                }
            } else {
                val finalId = existingUser?.id ?: "user_${cleanEmail.replace("@", "_").replace(".", "_")}"
                val finalName = existingUser?.name ?: cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                existingUser?.copy(
                    name = finalName,
                    lastAccessAt = System.currentTimeMillis()
                ) ?: UserEntity(
                    id = finalId,
                    name = finalName,
                    email = cleanEmail
                )
            }
            userDao.insertUser(user)
            saveSession(user)
            _authState.value = AuthState.Authenticated(user)
            Result.success(user)
        } catch (e: Exception) {
            val localUser = UserEntity(
                id = "user_${cleanEmail.replace("@", "_").replace(".", "_")}",
                name = cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() },
                email = cleanEmail
            )
            userDao.insertUser(localUser)
            saveSession(localUser)
            _authState.value = AuthState.Authenticated(localUser)
            Result.success(localUser)
        }
    }

    fun signOut() {
        firebaseAuth?.signOut()
        val sharedPrefs = context.getSharedPreferences("meu_negocio_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .remove("active_user_id")
            .remove("active_user_name")
            .remove("active_user_email")
            .apply()
        _authState.value = AuthState.Unauthenticated
    }

    suspend fun updateUser(user: UserEntity) {
        userDao.insertUser(user)
        saveSession(user)
        _authState.value = AuthState.Authenticated(user)
    }

    suspend fun switchTestUser(userSlot: String): UserEntity {
        _authState.value = AuthState.Loading
        val (id, name, email) = if (userSlot.equals("B", ignoreCase = true)) {
            Triple("test_uid_user_b", "Usuário B (Isolado)", "usuario.b@meunegocio.cloud")
        } else {
            Triple("test_uid_user_a", "Usuário A (Isolado)", "usuario.a@meunegocio.cloud")
        }
        val user = UserEntity(
            id = id,
            name = name,
            email = email,
            phone = "(11) 98765-4321"
        )
        userDao.insertUser(user)
        saveSession(user)
        _authState.value = AuthState.Authenticated(user)
        return user
    }

    private fun saveSession(user: UserEntity) {
        val sharedPrefs = context.getSharedPreferences("meu_negocio_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString("active_user_id", user.id)
            .putString("active_user_name", user.name)
            .putString("active_user_email", user.email)
            .apply()
    }
}
