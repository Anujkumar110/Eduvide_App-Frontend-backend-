package com.example.tutorapp

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.tutorapp.network.TokenManager
import com.example.tutorapp.teacher.Routes
import com.example.tutorapp.ui.theme.PrimaryBlue
import com.example.tutorapp.ui.theme.TextGray
import com.example.tutorapp.ui.theme.TextPrimary
import com.example.tutorapp.viewmodel.AuthViewModel

@Composable
fun RoleSelectionScreen(
    navController: NavHostController,
    viewModel: AuthViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("eduvibe_prefs", Context.MODE_PRIVATE)

    // Get user info from previous Google Sign-In
    val userEmail = prefs.getString("google_user_email", "") ?: ""
    val userName = prefs.getString("google_user_name", "") ?: ""

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
                .height(180.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF1A73E8), Color(0xFF4A90E2)))),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "E",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "EduVibe",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    "Choose Your Role",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }

        // ── Content Card ──────────────────────────────────────────────────────
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
                    .padding(20.dp)
            ) {
                // Welcome message
                Text(
                    "Welcome!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                
                if (userName.isNotEmpty()) {
                    Text(
                        userName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryBlue,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                if (userEmail.isNotEmpty()) {
                    Text(
                        userEmail,
                        fontSize = 13.sp,
                        color = TextGray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "How would you like to use EduVibe?",
                    fontSize = 14.sp,
                    color = TextGray,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // ── Student Role Card ─────────────────────────────────────────
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectRole(context, prefs, viewModel, "student", navController)
                        },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, Color(0xFFE0E0E0)),
                    color = Color(0xFFF8F9FF)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Student",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Discover mentors, enroll in classes,\nattend live sessions, and track progress",
                            fontSize = 13.sp,
                            color = TextGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Teacher Role Card ─────────────────────────────────────────
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectRole(context, prefs, viewModel, "teacher", navController)
                        },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, Color(0xFFE0E0E0)),
                    color = Color(0xFFF8F9FF)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Teacher",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Create classes, track students,\nconduct live sessions, manage payments",
                            fontSize = 13.sp,
                            color = TextGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // ── Info Text ─────────────────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("💡", fontSize = 20.sp)
                        Text(
                            "You can switch roles anytime by logging out and signing in again with the same Google account.",
                            fontSize = 12.sp,
                            color = Color(0xFF8D6E00)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── Loading Indicator ─────────────────────────────────────────
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = PrimaryBlue,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ── Error Message ─────────────────────────────────────────────
                uiState.errorMessage?.let { error ->
                    Text(
                        "⚠ $error",
                        fontSize = 12.sp,
                        color = Color.Red,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

private fun selectRole(
    context: Context,
    prefs: android.content.SharedPreferences,
    viewModel: AuthViewModel,
    role: String,
    navController: NavHostController
) {
    // Save role to TokenManager and SharedPreferences
    TokenManager.saveRole(context, role)
    
    // Set appropriate login flags
    prefs.edit().apply {
        putBoolean("is_logged_in", true)
        putBoolean("teacher_logged_in", role == "teacher")
        apply()
    }
    
    // Navigate based on role
    if (role == "teacher") {
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
