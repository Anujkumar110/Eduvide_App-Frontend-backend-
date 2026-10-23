package com.example.tutorapp.viewmodel

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tutorapp.network.FcmTokenManager
import com.example.tutorapp.network.RetrofitClient
import com.example.tutorapp.network.TokenManager
import com.example.tutorapp.repository.AuthRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AuthUiState(
    val isLoading    : Boolean = false,
    val isSuccess    : Boolean = false,
    val errorMessage : String? = null,
    val userName     : String? = null,
    val userRole     : String? = null
)

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val firebaseAuth = FirebaseAuth.getInstance()

    // ── Register ──────────────────────────────────────────────────────────────
    fun register(name: String, email: String, password: String, role: String = "student", context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.register(name, email, password, role)
                .onSuccess { response ->
                    response.data?.accessToken?.let { token ->
                        TokenManager.saveToken(context, token)
                        FcmTokenManager.syncTokenWithBackend(context, "Bearer $token", RetrofitClient.api)
                    }

                    val userRole  = response.data?.user?.role ?: role
                    val userId    = response.data?.user?._id ?: ""
                    val userName  = response.data?.user?.fullName ?: ""
                    val userEmail = response.data?.user?.email ?: ""

                    TokenManager.saveRole(context, userRole)
                    TokenManager.saveUserInfo(context, userId, userName, userEmail) // ✅ ADD

                    context.getSharedPreferences("eduvibe_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("is_logged_in", true).apply()

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSuccess = true,
                            userName  = userName,
                            userRole  = userRole
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Registration failed")
                    }
                }
        }
    }

    // ── Login (Email/Password) ────────────────────────────────────────────────
    fun login(email: String, password: String, context: Context, role: String = "student") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.login(email, password, role)
                .onSuccess { response ->
                    response.data?.accessToken?.let { token ->
                        TokenManager.saveToken(context, token)
                        FcmTokenManager.syncTokenWithBackend(context, "Bearer $token", RetrofitClient.api)
                    }

                    val userRole  = role
                    val userId    = response.data?.user?._id ?: ""
                    val userName  = response.data?.user?.fullName ?: ""
                    val userEmail = response.data?.user?.email ?: ""

                    TokenManager.saveRole(context, userRole)
                    TokenManager.saveUserInfo(context, userId, userName, userEmail) // ✅ ADD

                    context.getSharedPreferences("eduvibe_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("is_logged_in", true).apply()

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSuccess = true,
                            userName  = userName,
                            userRole  = userRole
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Login failed")
                    }
                }
        }
    }

    // ── Google Sign-In ────────────────────────────────────────────────────────
    fun signInWithGoogle(context: Context, role: String = "student") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val googleApiAvailability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
                val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
                if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                    val error = googleApiAvailability.getErrorString(resultCode)
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Google Play Services not available: $error.")
                    }
                    return@launch
                }

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(WEB_CLIENT_ID)
                    .setAutoSelectEnabled(false)
                    .setNonce(null)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val credentialManager = CredentialManager.create(context)

                var result: androidx.credentials.GetCredentialResponse? = null
                var lastException: Exception? = null
                val maxRetries = 2

                for (attempt in 1..maxRetries) {
                    try {
                        result = credentialManager.getCredential(context, request)
                        break
                    } catch (e: androidx.credentials.exceptions.GetCredentialException) {
                        lastException = e
                        if (e.message?.contains("NETWORK_ERROR") == true ||
                            e.message?.contains("ERR_NETWORK") == true) {
                            if (attempt < maxRetries) {
                                kotlinx.coroutines.delay(1000 * attempt.toLong())
                                continue
                            }
                        }
                        throw e
                    }
                }

                if (result == null) throw lastException ?: Exception("Failed to get credential after retries")

                val googleIdToken = GoogleIdTokenCredential
                    .createFrom(result.credential.data)
                    .idToken

                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val firebaseUser = firebaseAuth.signInWithCredential(firebaseCredential).await().user
                    ?: throw Exception("Firebase user null")

                val firebaseIdToken = firebaseUser.getIdToken(false).await()?.token
                    ?: throw Exception("Firebase ID token not found")

                val response = repository.firebaseAuth(firebaseIdToken, role)

                response.onSuccess { authResponse ->
                    android.util.Log.d("GoogleAuth", "✅ Backend auth successful")

                    authResponse.data?.accessToken?.let { token ->
                        TokenManager.saveToken(context, token)
                        FcmTokenManager.syncTokenWithBackend(context, "Bearer $token", RetrofitClient.api)
                    }

                    val userRole  = role
                    val userId    = authResponse.data?.user?._id ?: ""
                    val userName  = authResponse.data?.user?.fullName ?: ""
                    val userEmail = authResponse.data?.user?.email ?: ""

                    TokenManager.saveRole(context, userRole)
                    TokenManager.saveUserInfo(context, userId, userName, userEmail) // ✅ ADD

                    context.getSharedPreferences("eduvibe_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("google_user_name", userName)
                        .putString("google_user_email", userEmail)
                        .putBoolean("is_logged_in", true)
                        .putBoolean("teacher_logged_in", userRole == "teacher")
                        .putBoolean("needs_role_selection", false)
                        .apply()

                    _uiState.update {
                        it.copy(isLoading = false, isSuccess = true, userName = userName, userRole = userRole)
                    }
                }

                response.onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Backend authentication failed: ${error.message ?: "Unknown error"}")
                    }
                }

            } catch (e: androidx.credentials.exceptions.GetCredentialException) {
                val errorMsg = when {
                    e.message?.contains("NETWORK_ERROR") == true ||
                            e.message?.contains("ERR_NETWORK") == true ||
                            e.message?.contains("ERR_NAME_NOT_RESOLVED") == true ->
                        "Network error: Please check your internet connection and try again."
                    e.message?.contains("canceled") == true ||
                            e.message?.contains("CANCELLED") == true ->
                        "Google Sign-In canceled"
                    else -> "Google Sign-In failed: ${e.message}"
                }
                _uiState.update { it.copy(isLoading = false, errorMessage = errorMsg) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Something went wrong, please retry") }
            }
        }
    }

    fun resetState() {
        _uiState.update { AuthUiState() }
    }

    companion object {
        const val WEB_CLIENT_ID = "510929059416-3q5kt33tduvur5vb0qoqpspnreunmfo7.apps.googleusercontent.com"
    }
}