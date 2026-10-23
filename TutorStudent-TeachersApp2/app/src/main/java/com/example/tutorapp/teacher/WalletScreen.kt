package com.example.tutorapp.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.tutorapp.network.TransactionData
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun formatTxTime(createdAt: String?): String {
    if (createdAt.isNullOrBlank()) return ""
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val date = sdf.parse(createdAt) ?: return ""
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(date)
    } catch (_: Exception) { "" }
}

private fun teacherTxLabel(type: String, isCredit: Boolean): String = when (type) {
    "PAYMENT"    -> if (isCredit) "Received from Student" else "Payment Sent"
    "ADD_MONEY"  -> "Wallet Top-Up"
    "WITHDRAWAL" -> "Withdrawal"
    "REFUND"     -> "Refund Issued"
    "TRANSFER"   -> "Transfer"
    else         -> type
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TeacherWalletScreen(
    navController: NavController,
    viewModel: TeacherWalletViewModel = viewModel()   // ✅ ViewModel inject
) {
    val context  = LocalContext.current
    val uiState  by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Initial load ──────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        viewModel.loadWallet(context)
    }

    // ── Show error in Snackbar ────────────────────────────────────────────────
    LaunchedEffect(uiState.errorMsg) {
        uiState.errorMsg?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F7FB))
        ) {

            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Text("Wallet & Earnings", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick  = { viewModel.loadWallet(context) },
                    enabled  = !uiState.isLoading
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }

            // ── Loading ───────────────────────────────────────────────────────
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            // ── Error (full-screen fallback, e.g. first load failed) ──────────
            if (uiState.errorMsg != null && uiState.transactions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(uiState.errorMsg ?: "", color = Color.Red)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.loadWallet(context) }) {
                            Text("Retry")
                        }
                    }
                }
                return@Column
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {

                // ── Balance + Earnings banner ─────────────────────────────────
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF1B5E20), Color(0xFF43A047))
                                )
                            )
                            .padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Outlined.AccountBalanceWallet,
                                contentDescription = null,
                                tint     = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Available Balance",
                                color    = Color.White.copy(alpha = 0.75f),
                                fontSize = 13.sp
                            )
                            Text(
                                "₹ ${String.format("%.2f", uiState.balance)}",
                                color      = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize   = 32.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.3f))
                            Spacer(Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "Total Earned",
                                        color    = Color.White.copy(alpha = 0.75f),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        "₹ ${String.format("%.2f", uiState.totalEarned)}",
                                        color      = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize   = 18.sp
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "Transactions",
                                        color    = Color.White.copy(alpha = 0.75f),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        "${uiState.transactions.size}",
                                        color      = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize   = 18.sp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // ── Notification note ─────────────────────────────────────────
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape  = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                tint     = Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "You'll receive a notification whenever a student pays you from their wallet.",
                                fontSize = 12.sp,
                                color    = Color(0xFF2E7D32)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // ── History header ────────────────────────────────────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            "Transaction History",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp
                        )
                        Text(
                            "${uiState.transactions.size} records",
                            fontSize = 12.sp,
                            color    = Color.Gray
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // ── Empty state ───────────────────────────────────────────────
                if (uiState.transactions.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint     = Color.LightGray,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text("No transactions yet", color = Color.Gray, fontSize = 14.sp)
                                Text(
                                    "Earnings appear here when students pay you",
                                    color    = Color.LightGray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // ── Transaction rows ──────────────────────────────────────────
                itemsIndexed(uiState.transactions) { _, tx ->
                    TeacherTransactionRow(
                        tx       = tx,
                        walletId = uiState.walletId
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Transaction row (extracted for clarity)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TeacherTransactionRow(tx: TransactionData, walletId: String) {

    // ✅ KEY: toWalletId == teacher's walletId → paise aaye hain
    val isCredit = when (tx.type) {
        "ADD_MONEY", "REFUND" -> true
        "PAYMENT"             -> tx.toWalletId == walletId
        else                  -> false
    }

    val amountColor  = if (isCredit) Color(0xFF2E7D32) else Color(0xFFC62828)
    val amountPrefix = if (isCredit) "+ ₹" else "- ₹"

    val txIcon = when (tx.type) {
        "PAYMENT"    -> Icons.Default.AccountBalanceWallet
        "ADD_MONEY"  -> Icons.Default.AddCircle
        "WITHDRAWAL" -> Icons.Default.RemoveCircle
        "REFUND"     -> Icons.Default.Replay
        else         -> Icons.Default.SwapHoriz
    }
    val iconBg = when {
        tx.type == "PAYMENT" && isCredit  -> Color(0xFF2E7D32)
        tx.type == "PAYMENT" && !isCredit -> Color(0xFFC62828)
        tx.type == "ADD_MONEY"            -> Color(0xFF1565C0)
        tx.type == "WITHDRAWAL"           -> Color(0xFFC62828)
        tx.type == "REFUND"               -> Color(0xFFE65100)
        else                              -> Color(0xFF6A1B9A)
    }

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape     = RoundedCornerShape(10.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconBg.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(txIcon, contentDescription = null, tint = iconBg, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    teacherTxLabel(tx.type, isCredit),
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp
                )
                Text(formatTxTime(tx.createdAt), fontSize = 11.sp, color = Color.Gray)
            }
            Text(
                "$amountPrefix${String.format("%.2f", tx.amount)}",
                color      = amountColor,
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp
            )
        }
    }
}