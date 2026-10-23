package com.example.tutorapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.tutorapp.network.GenericResponse
import com.example.tutorapp.network.RetrofitClient
import com.example.tutorapp.repository.safeCall
import com.example.tutorapp.ui.theme.*
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// ══════════════════════════════════════════════════════════════════════════════
// REQUEST MODELS —  @JsonClass zaroori hai Moshi ke liye
// ══════════════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class ForgotPasswordRequest(
    @Json(name = "email") val email: String
)

@JsonClass(generateAdapter = true)
data class ResetPasswordRequest(
    @Json(name = "token")    val token: String,
    @Json(name = "password") val password: String
)

// ══════════════════════════════════════════════════════════════════════════════
// API SERVICE
// ══════════════════════════════════════════════════════════════════════════════

interface ResetApiService {
    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): Response<GenericResponse>

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): Response<GenericResponse>
}

// ══════════════════════════════════════════════════════════════════════════════
// UI STATE
// ══════════════════════════════════════════════════════════════════════════════

data class ResetUiState(
    val isLoading      : Boolean = false,
    val step           : Int     = 1,  // 1=email, 2=token+password, 3=success
    val errorMessage   : String? = null,
    val successMessage : String? = null
)

// ══════════════════════════════════════════════════════════════════════════════
// VIEWMODEL
// ══════════════════════════════════════════════════════════════════════════════

class ResetCredentialsViewModel : ViewModel() {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val api by lazy {
        Retrofit.Builder()
            .baseUrl("${RetrofitClient.MEDIA_BASE}/api/v1/")
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(MoshiConverterFactory.create(moshi))  // moshi instance pass karo
            .build()
            .create(ResetApiService::class.java)
    }

    private val _uiState = MutableStateFlow(ResetUiState())
    val uiState: StateFlow<ResetUiState> = _uiState.asStateFlow()

    // ── Step 1: Email bhejo ───────────────────────────────────────────────────
    fun sendForgotPasswordEmail(email: String) {
        if (email.isEmpty() || !email.contains("@")) {
            _uiState.update { it.copy(errorMessage = "enter the valid email") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            safeCall { api.forgotPassword(ForgotPasswordRequest(email)) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading      = false,
                            step           = 2,
                            successMessage = "check your email reset token is sended."
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }

    // ── Step 2: Token + New Password ──────────────────────────────────────────
    fun resetPassword(token: String, password: String, confirmPassword: String) {
        when {
            token.isEmpty() -> {
                _uiState.update { it.copy(errorMessage = "Token enter that comes by email") }
                return
            }
            password.length < 6 -> {
                _uiState.update { it.copy(errorMessage = "Password at leat 6 char") }
                return
            }
            password != confirmPassword -> {
                _uiState.update { it.copy(errorMessage = "Passwords not matches") }
                return
            }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            safeCall { api.resetPassword(ResetPasswordRequest(token.trim(), password)) }
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, step = 3) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}

// ══════════════════════════════════════════════════════════════════════════════
// SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun ResetCredentialsScreen(navController: NavHostController) {

    val viewModel: ResetCredentialsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    var email           by remember { mutableStateOf("") }
    var token           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passVisible     by remember { mutableStateOf(false) }
    var confirmVisible  by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = PrimaryBlue,
        unfocusedBorderColor = Color(0xFFDDDDDD),
        focusedLabelColor    = PrimaryBlue,
        unfocusedLabelColor  = TextGray
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBg)
            .verticalScroll(rememberScrollState())
    ) {

        // ── Top Bar ───────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardWhite)
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text(
                text = when (uiState.step) {
                    1    -> "Reset Password"
                    2    -> "Enter Token"
                    else -> "Success"
                },
                fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = TextPrimary, modifier = Modifier.align(Alignment.Center)
            )
        }

        if (uiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = PrimaryBlue)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {

            when (uiState.step) {

                // ── STEP 1: Email Enter Karo ──────────────────────────────────
                1 -> {
                    Text("Password Reset", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "enter the register email token will we send to it.",
                        fontSize = 13.sp, color = TextGray
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Email", fontSize = 13.sp, color = TextGray, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value         = email,
                        onValueChange = { email = it; viewModel.clearError() },
                        placeholder   = { Text("Enter your email", color = TextGray, fontSize = 13.sp) },
                        leadingIcon   = { Icon(Icons.Default.Email, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp)) },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        singleLine    = true,
                        colors        = fieldColors
                    )

                    uiState.errorMessage?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("⚠ $it", fontSize = 12.sp, color = Color.Red)
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick  = { viewModel.sendForgotPasswordEmail(email) },
                        enabled  = !uiState.isLoading,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Send Reset Link", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                // ── STEP 2: Token + New Password ──────────────────────────────
                2 -> {
                    uiState.successMessage?.let {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFE8F5E9), RoundedCornerShape(10.dp))
                                .padding(14.dp)
                        ) {
                            Text("✓ $it", fontSize = 13.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    Text("enter the new password", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "enter the email token.",
                        fontSize = 13.sp, color = TextGray
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // Token
                    Text("Reset Token", fontSize = 13.sp, color = TextGray, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value         = token,
                        onValueChange = { token = it; viewModel.clearError() },
                        placeholder   = { Text("paste the email token", color = TextGray, fontSize = 13.sp) },
                        leadingIcon   = { Icon(Icons.Default.Key, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp)) },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        singleLine    = false,
                        maxLines      = 3,
                        colors        = fieldColors
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // New Password
                    Text("New Password", fontSize = 13.sp, color = TextGray, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value                = password,
                        onValueChange        = { password = it; viewModel.clearError() },
                        placeholder          = { Text("Enter new password", color = TextGray, fontSize = 13.sp) },
                        leadingIcon          = { Icon(Icons.Default.Lock, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp)) },
                        trailingIcon         = {
                            IconButton(onClick = { passVisible = !passVisible }) {
                                Icon(
                                    if (passVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier             = Modifier.fillMaxWidth(),
                        shape                = RoundedCornerShape(12.dp),
                        singleLine           = true,
                        colors               = fieldColors
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Confirm Password
                    Text("Confirm Password", fontSize = 13.sp, color = TextGray, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value                = confirmPassword,
                        onValueChange        = { confirmPassword = it; viewModel.clearError() },
                        placeholder          = { Text("Re-enter new password", color = TextGray, fontSize = 13.sp) },
                        leadingIcon          = { Icon(Icons.Default.Lock, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp)) },
                        trailingIcon         = {
                            IconButton(onClick = { confirmVisible = !confirmVisible }) {
                                Icon(
                                    if (confirmVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier             = Modifier.fillMaxWidth(),
                        shape                = RoundedCornerShape(12.dp),
                        singleLine           = true,
                        colors               = fieldColors
                    )

                    uiState.errorMessage?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("⚠ $it", fontSize = 12.sp, color = Color.Red)
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick  = { viewModel.resetPassword(token, password, confirmPassword) },
                        enabled  = !uiState.isLoading,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Reset Password", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                // ── STEP 3: Success ───────────────────────────────────────────
                3 -> {
                    Spacer(modifier = Modifier.height(60.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .background(Color(0xFFE8F5E9), RoundedCornerShape(45.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint     = Color(0xFF2E7D32),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Password Reset done!", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("enter new password.", fontSize = 14.sp, color = TextGray)
                        Spacer(modifier = Modifier.height(40.dp))
                        Button(
                            onClick  = {
                                navController.navigate("LoginScreen") {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text("Login ", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}