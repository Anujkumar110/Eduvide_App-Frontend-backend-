package com.example.tutorapp.teacher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.tutorapp.ui.theme.EduBackground
import com.example.tutorapp.ui.theme.EduBlue
import com.example.tutorapp.ui.theme.EduBorder
import com.example.tutorapp.ui.theme.EduGray
import com.example.tutorapp.ui.theme.EduTextDark

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun AccountControlScreenPreview() {
    AccountControlScreen(navController = rememberNavController())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountControlScreen(navController: NavController) {

    var currentPassword by remember { mutableStateOf("") }
    var newPassword     by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var currentVisible  by remember { mutableStateOf(false) }
    var newVisible      by remember { mutableStateOf(false) }
    var confirmVisible  by remember { mutableStateOf(false) }
    var showErrors      by remember { mutableStateOf(false) }
    var savedOk         by remember { mutableStateOf(false) }

    // ── Validation ────────────────────────────────────────────────────────────
    val currentError    = showErrors && currentPassword.isBlank()
    val newError        = showErrors && newPassword.length < 6
    val confirmError    = showErrors && confirmPassword != newPassword

    fun validate(): Boolean {
        showErrors = true
        return currentPassword.isNotBlank() &&
                newPassword.length >= 6 &&
                newPassword == confirmPassword
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                title = {
                    Text(
                        text       = "Change Password",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                        color      = Color.White
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = EduBlue
                )
            )
        },
        containerColor = EduBackground
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── Success banner ────────────────────────────────────────────────
            if (savedOk) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint     = Color(0xFF43A047),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text     = "Password changed successfully!",
                        fontSize = 14.sp,
                        color    = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ── Current password ──────────────────────────────────────────────
            PasswordField(
                value         = currentPassword,
                onValueChange = { currentPassword = it; showErrors = false },
                label         = "Current Password",
                visible       = currentVisible,
                onToggle      = { currentVisible = !currentVisible },
                isError       = currentError,
                errorMessage  = "Current password is required"
            )

            // ── New password ──────────────────────────────────────────────────
            PasswordField(
                value         = newPassword,
                onValueChange = { newPassword = it; showErrors = false },
                label         = "New Password",
                visible       = newVisible,
                onToggle      = { newVisible = !newVisible },
                isError       = newError,
                errorMessage  = if (newPassword.isBlank()) "New password is required"
                else "Minimum 6 characters required"
            )

            // ── Confirm password ──────────────────────────────────────────────
            PasswordField(
                value         = confirmPassword,
                onValueChange = { confirmPassword = it; showErrors = false },
                label         = "Confirm New Password",
                visible       = confirmVisible,
                onToggle      = { confirmVisible = !confirmVisible },
                isError       = confirmError,
                errorMessage  = "Passwords do not match"
            )

            Spacer(Modifier.height(4.dp))

            // ── Save button ───────────────────────────────────────────────────
            EduButton(
                text    = "Save Password",
                onClick = {
                    if (validate()) {
                        savedOk         = true
                        currentPassword = ""
                        newPassword     = ""
                        confirmPassword = ""
                        showErrors      = false
                    }
                }
            )
        }
    }
}

// ── Password field ────────────────────────────────────────────────────────────

@Composable
private fun PasswordField(
    value:         String,
    onValueChange: (String) -> Unit,
    label:         String,
    visible:       Boolean,
    onToggle:      () -> Unit,
    isError:       Boolean = false,
    errorMessage:  String  = ""
) {
    Column {
        OutlinedTextField(
            value                = value,
            onValueChange        = onValueChange,
            label                = { Text(label, fontSize = 13.sp) },
            leadingIcon          = {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint     = if (isError) MaterialTheme.colorScheme.error else EduGray,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon         = {
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector        = if (visible) Icons.Filled.Visibility
                        else Icons.Filled.VisibilityOff,
                        contentDescription = null,
                        tint     = EduGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            visualTransformation = if (visible) VisualTransformation.None
            else PasswordVisualTransformation(),
            singleLine           = true,
            isError              = isError,
            modifier             = Modifier.fillMaxWidth(),
            shape                = RoundedCornerShape(10.dp),
            colors               = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = EduBlue,
                unfocusedBorderColor = EduBorder,
                errorBorderColor     = MaterialTheme.colorScheme.error,
                focusedLabelColor    = EduBlue,
                unfocusedLabelColor  = EduGray,
                errorLabelColor      = MaterialTheme.colorScheme.error,
                focusedTextColor     = EduTextDark,
                unfocusedTextColor   = EduTextDark,
                cursorColor          = EduBlue
            )
        )
        if (isError && errorMessage.isNotEmpty()) {
            Text(
                text     = errorMessage,
                fontSize = 11.sp,
                color    = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp, top = 3.dp)
            )
        }
    }
}