package com.example.tutorapp.teacher

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.tutorapp.teacher.Routes.TEACHER_LOGIN
import com.example.tutorapp.ui.theme.PrimaryBlue
import com.example.tutorapp.ui.theme.TextGray
import com.example.tutorapp.ui.theme.TextPrimary
import com.example.tutorapp.viewmodel.AuthViewModel


@Preview(showBackground = true, showSystemUi = true)
@Composable
fun TeacherRegisterScreenPreview() {
    val navController = rememberNavController()
    val context = LocalContext.current
    TeacherRegisterScreen(
        navController = navController,
        prefs         = context.getSharedPreferences("preview_prefs", Context.MODE_PRIVATE)
    )
}

@Composable
fun TeacherRegisterScreen(
    navController : NavHostController,
    prefs         : android.content.SharedPreferences,
    viewModel     : AuthViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var name            by remember { mutableStateOf("") }
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passVisible     by remember { mutableStateOf(false) }
    var confirmVisible  by remember { mutableStateOf(false) }
    var nameError       by remember { mutableStateOf("") }
    var emailError      by remember { mutableStateOf("") }
    var passwordError   by remember { mutableStateOf("") }
    var confirmError    by remember { mutableStateOf("") }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            val userRole = uiState.userRole ?: "teacher"
            
            prefs.edit()
                .putBoolean("teacher_logged_in", userRole == "teacher")
                .putString("name",  uiState.userName ?: name.trim())
                .putString("email", email.trim())
                .apply()

            // ✅ Register/Google Sign-In ke baad COMPLETE_PROFILE_1 pe jao
            navController.navigate(Routes.COMPLETE_PROFILE_1) {
                popUpTo(0) { inclusive = true }
            }
            viewModel.resetState()
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = PrimaryBlue,
        unfocusedBorderColor = Color(0xFFE0E0E0),
        focusedLabelColor    = PrimaryBlue,
        unfocusedLabelColor  = TextGray
    )

    fun validate(): Boolean {
        var valid = true
        if (name.isEmpty()) { nameError = "Name required"; valid = false } else nameError = ""
        if (email.isEmpty() || !email.contains("@")) { emailError = "Valid email required"; valid = false } else emailError = ""
        if (password.length < 6) { passwordError = "Min 6 characters required"; valid = false } else passwordError = ""
        if (password != confirmPassword) { confirmError = "Passwords do not match"; valid = false } else confirmError = ""
        return valid
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FF))
            .verticalScroll(rememberScrollState())
    ) {

        // ── Top gradient header ────────────────────────────────────────────────
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
                Text("Create your account", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }

        // ── White card form ────────────────────────────────────────────────────
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

                Text("TEACHER REGISTER", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Create a new account", fontSize = 13.sp, color = TextGray)

                Spacer(modifier = Modifier.height(22.dp))

                // Name
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it; nameError = "" },
                    placeholder   = { Text("Enter your name...", color = TextGray, fontSize = 13.sp) },
                    label         = { Text("Name", fontSize = 13.sp) },
                    leadingIcon   = { Icon(Icons.Default.Person, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp)) },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp),
                    colors        = fieldColors,
                    singleLine    = true,
                    isError       = nameError.isNotEmpty()
                )
                if (nameError.isNotEmpty()) Text("⚠ $nameError", fontSize = 11.sp, color = Color.Red, modifier = Modifier.padding(start = 4.dp, top = 2.dp))

                Spacer(modifier = Modifier.height(14.dp))

                // Email
                OutlinedTextField(
                    value         = email,
                    onValueChange = { email = it; emailError = "" },
                    placeholder   = { Text("Enter your email...", color = TextGray, fontSize = 13.sp) },
                    label         = { Text("Email", fontSize = 13.sp) },
                    leadingIcon   = { Icon(Icons.Default.Email, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp)) },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp),
                    colors        = fieldColors,
                    singleLine    = true,
                    isError       = emailError.isNotEmpty()
                )
                if (emailError.isNotEmpty()) Text("⚠ $emailError", fontSize = 11.sp, color = Color.Red, modifier = Modifier.padding(start = 4.dp, top = 2.dp))

                Spacer(modifier = Modifier.height(14.dp))

                // Password
                OutlinedTextField(
                    value                = password,
                    onValueChange        = { password = it; passwordError = "" },
                    placeholder          = { Text("Create password...", color = TextGray, fontSize = 13.sp) },
                    label                = { Text("Password", fontSize = 13.sp) },
                    leadingIcon          = { Icon(Icons.Default.Lock, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp)) },
                    trailingIcon         = {
                        IconButton(onClick = { passVisible = !passVisible }) {
                            Icon(if (passVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp))
                        }
                    },
                    visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier             = Modifier.fillMaxWidth(),
                    shape                = RoundedCornerShape(12.dp),
                    colors               = fieldColors,
                    singleLine           = true,
                    isError              = passwordError.isNotEmpty()
                )
                if (passwordError.isNotEmpty()) Text("⚠ $passwordError", fontSize = 11.sp, color = Color.Red, modifier = Modifier.padding(start = 4.dp, top = 2.dp))

                Spacer(modifier = Modifier.height(14.dp))

                // Confirm Password
                OutlinedTextField(
                    value                = confirmPassword,
                    onValueChange        = { confirmPassword = it; confirmError = "" },
                    placeholder          = { Text("Confirm password...", color = TextGray, fontSize = 13.sp) },
                    label                = { Text("Confirm Password", fontSize = 13.sp) },
                    leadingIcon          = { Icon(Icons.Default.Lock, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp)) },
                    trailingIcon         = {
                        IconButton(onClick = { confirmVisible = !confirmVisible }) {
                            Icon(if (confirmVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp))
                        }
                    },
                    visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier             = Modifier.fillMaxWidth(),
                    shape                = RoundedCornerShape(12.dp),
                    colors               = fieldColors,
                    singleLine           = true,
                    isError              = confirmError.isNotEmpty()
                )
                if (confirmError.isNotEmpty()) Text("⚠ $confirmError", fontSize = 11.sp, color = Color.Red, modifier = Modifier.padding(start = 4.dp, top = 2.dp))

                Spacer(modifier = Modifier.height(16.dp))

                // ── API Error message ──────────────────────────────────────────
                uiState.errorMessage?.let {
                    Text(
                        text     = "⚠ $it",
                        color    = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // ── Loading / Register button ──────────────────────────────────
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        color    = PrimaryBlue
                    )
                } else {
                    Button(
                        onClick = {
                            if (validate()) {
                                viewModel.register(
                                    name     = name,
                                    email    = email,
                                    password = password,
                                    role     = "teacher",
                                    context  = context
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape    = RoundedCornerShape(24.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("Register", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Divider(modifier = Modifier.weight(1f), color = Color(0xFFEEEEEE))
                    Text("  Or  ", fontSize = 12.sp, color = TextGray)
                    Divider(modifier = Modifier.weight(1f), color = Color(0xFFEEEEEE))
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ── Google Sign Up (authenticates via Google) ──────────────────
                OutlinedButton(
                    onClick  = { viewModel.signInWithGoogle(context, role = "teacher") },
                    enabled  = !uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape    = RoundedCornerShape(12.dp),
                    border   = BorderStroke(1.dp, Color(0xFFE0E0E0))
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
                        Text("Sign up with Google", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text("Already have an account? ", fontSize = 13.sp, color = TextGray)
                    Text(
                        text           = "Login",
                        fontSize       = 13.sp,
                        color          = PrimaryBlue,
                        fontWeight     = FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline,
                        modifier       = Modifier.clickable {
                            navController.navigate(TEACHER_LOGIN) {
                                popUpTo("RegisterScreen") { inclusive = true }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}