package com.example.tutorapp

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.tutorapp.teacher.Routes
import com.example.tutorapp.ui.theme.PrimaryBlue
import com.example.tutorapp.ui.theme.TextGray
import com.example.tutorapp.ui.theme.TextPrimary
import com.example.tutorapp.viewmodel.AuthViewModel

@Composable
fun LoginScreen(navController: NavHostController) {

    val viewModel: AuthViewModel = viewModel()
    val uiState  by viewModel.uiState.collectAsState()
    val context  = LocalContext.current

    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var emailError      by remember { mutableStateOf("") }
    var passwordError   by remember { mutableStateOf("") }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = PrimaryBlue,
        unfocusedBorderColor = Color(0xFFE0E0E0),
        focusedLabelColor    = PrimaryBlue,
        unfocusedLabelColor  = TextGray
    )

    // Login/Google success → Navigate based on role
    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            val prefs = context.getSharedPreferences("eduvibe_prefs", Context.MODE_PRIVATE)
            val userRole = uiState.userRole

            // Navigate based on role
            if (userRole == "teacher") {
                navController.navigate(Routes.MAIN) {
                    popUpTo(0) { inclusive = true }
                }
            } else {
                navController.navigate("MentorScreen") {
                    popUpTo(0) { inclusive = true }
                }
            }
            viewModel.resetState()
        }
    }

    fun validate(): Boolean {
        var valid = true
        if (email.isEmpty() || !email.contains("@")) {
            emailError = "Valid email required"; valid = false
        } else emailError = ""
        if (password.length < 6) {
            passwordError = "Min 6 characters required"; valid = false
        } else passwordError = ""
        return valid
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FF))
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF1A73E8), Color(0xFF4A90E2)))),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("E", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("EduVibe", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("Online Learning Made Fun", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }

        // ── Card ──────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .offset(y = (-24).dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Text("LOGIN", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Login to continue learning", fontSize = 13.sp, color = TextGray)
                Spacer(modifier = Modifier.height(22.dp))

                // ── Email Field ───────────────────────────────────────────────
                OutlinedTextField(
                    value         = email,
                    onValueChange = { email = it; emailError = "" },
                    placeholder   = { Text("Enter your email...", color = TextGray, fontSize = 13.sp) },
                    label         = { Text("Email", fontSize = 13.sp) },
                    leadingIcon   = {
                        Icon(Icons.Default.Email, contentDescription = null,
                            tint = TextGray, modifier = Modifier.size(18.dp))
                    },
                    modifier   = Modifier.fillMaxWidth(),
                    shape      = RoundedCornerShape(12.dp),
                    colors     = fieldColors,
                    singleLine = true,
                    isError    = emailError.isNotEmpty()
                )
                if (emailError.isNotEmpty())
                    Text("⚠ $emailError", fontSize = 11.sp, color = Color.Red,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp))

                Spacer(modifier = Modifier.height(14.dp))

                // ── Password Field ────────────────────────────────────────────
                OutlinedTextField(
                    value                = password,
                    onValueChange        = { password = it; passwordError = "" },
                    placeholder          = { Text("Enter your password...", color = TextGray, fontSize = 13.sp) },
                    label                = { Text("Password", fontSize = 13.sp) },
                    leadingIcon          = {
                        Icon(Icons.Default.Lock, contentDescription = null,
                            tint = TextGray, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.Visibility
                                else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint     = TextGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    modifier   = Modifier.fillMaxWidth(),
                    shape      = RoundedCornerShape(12.dp),
                    colors     = fieldColors,
                    singleLine = true,
                    isError    = passwordError.isNotEmpty()
                )
                if (passwordError.isNotEmpty())
                    Text("⚠ $passwordError", fontSize = 11.sp, color = Color.Red,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp))

                // ── API Error ─────────────────────────────────────────────────
                uiState.errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("⚠ $it", fontSize = 12.sp, color = Color.Red,
                        modifier = Modifier.padding(start = 4.dp))
                }

                Spacer(modifier = Modifier.height(22.dp))

                // ── Login Button ──────────────────────────────────────────────
                Button(
                    onClick  = { if (validate()) viewModel.login(email, password, context) },
                    enabled  = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape    = RoundedCornerShape(24.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    if (uiState.isLoading)
                        CircularProgressIndicator(color = Color.White,
                            modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else
                        Text("Login  →", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Divider ───────────────────────────────────────────────────
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Divider(modifier = Modifier.weight(1f), color = Color(0xFFEEEEEE))
                    Text("  Or  ", fontSize = 12.sp, color = TextGray)
                    Divider(modifier = Modifier.weight(1f), color = Color(0xFFEEEEEE))
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ── Google Sign-In Button ✅ Role = student ──────────────
                OutlinedButton(
                    onClick  = { viewModel.signInWithGoogle(context, role = "student") },
                    enabled  = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape    = RoundedCornerShape(12.dp),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            color       = PrimaryBlue,
                            modifier    = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("G", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4285F4))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign in with Google", fontSize = 14.sp,
                            color = TextPrimary, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text("By signing up, you agree to our ", fontSize = 11.sp, color = TextGray)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text("terms of use", fontSize = 11.sp, color = PrimaryBlue,
                        textDecoration = TextDecoration.Underline)
                    Text("  &  ", fontSize = 11.sp, color = TextGray)
                    Text("privacy policy", fontSize = 11.sp, color = PrimaryBlue,
                        textDecoration = TextDecoration.Underline)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text("If you don't have an account, please ", fontSize = 13.sp, color = TextGray)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text(
                        text           = "Register",
                        fontSize       = 13.sp,
                        color          = PrimaryBlue,
                        fontWeight     = FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline,
                        modifier       = Modifier.clickable { navController.navigate("RegisterScreen") }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}