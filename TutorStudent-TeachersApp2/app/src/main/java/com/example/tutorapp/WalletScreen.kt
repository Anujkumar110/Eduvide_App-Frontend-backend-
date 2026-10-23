package com.example.tutorapp

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.tutorapp.network.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*

private fun formatWalletTime(createdAt: String?): String {
    if (createdAt.isNullOrBlank()) return ""
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val date = sdf.parse(createdAt) ?: return ""
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(date)
    } catch (_: Exception) { "" }
}

private fun txLabel(type: String): String = when (type) {
    "ADD_MONEY"  -> "Wallet Top-Up"
    "PAYMENT"    -> "Payment to Teacher"
    "TRANSFER"   -> "Transfer"
    "REFUND"     -> "Refund"
    "WITHDRAWAL" -> "Withdrawal"
    else         -> type
}

@Composable
fun WalletScreen(navController: NavHostController) {

    val context  = LocalContext.current
    val scope    = rememberCoroutineScope()
    val activity = context as? Activity

    var balance      by remember { mutableStateOf(0.0) }
    var transactions by remember { mutableStateOf<List<TransactionData>>(emptyList()) }
    var isLoading    by remember { mutableStateOf(true) }
    var errorMsg     by remember { mutableStateOf<String?>(null) }

    var showTopUpDialog by remember { mutableStateOf(false) }
    var topUpAmountText by remember { mutableStateOf("") }
    var topUpLoading    by remember { mutableStateOf(false) }
    var topUpError      by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val razorpayManager = (activity as? MainActivity)?.razorpayManager

    fun loadWallet() {
        scope.launch {
            isLoading = true
            errorMsg  = null
            val token = TokenManager.getToken(context) ?: run {
                errorMsg  = "Not logged in"
                isLoading = false
                return@launch
            }
            val bearer = "Bearer $token"
            try {
                val walletResp = RetrofitClient.api.getWallet(bearer)
                if (walletResp.isSuccessful) {
                    balance = walletResp.body()?.data?.balance ?: 0.0
                }
                val txResp = RetrofitClient.api.getWalletTransactions(bearer)
                if (txResp.isSuccessful) {
                    transactions = txResp.body()?.data ?: emptyList()
                }
            } catch (e: Exception) {
                errorMsg = e.message
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadWallet() }

    fun startTopUp() {
        val amount = topUpAmountText.toDoubleOrNull()
        if (amount == null || amount < 1) {
            topUpError = "Enter a valid amount (min ₹1)"
            return
        }
        if (razorpayManager == null) {
            topUpError = "Payment not initialised"
            return
        }
        topUpLoading = true
        topUpError   = null

        razorpayManager.startWalletTopUp(
            amount   = amount,
            callback = object : RazorpayManager.PaymentCallback {

                override fun onSuccess(paymentId: String, orderId: String, signature: String) {
                    scope.launch {
                        try {
                            val token  = TokenManager.getToken(context) ?: return@launch
                            val bearer = "Bearer $token"
                            val json = """{"razorpay_order_id":"$orderId","razorpay_payment_id":"$paymentId","razorpay_signature":"$signature","amount":$amount}"""
                            val body = json.toRequestBody("application/json".toMediaType())
                            val resp = RetrofitClient.api.topUpWalletRaw(bearer, body)
                            if (resp.isSuccessful) {
                                balance         = resp.body()?.data?.balance ?: balance
                                showTopUpDialog = false
                                topUpAmountText = ""
                                loadWallet()
                                snackbarHostState.showSnackbar("₹${amount.toInt()} added to wallet!")
                            } else {
                                topUpError = "Top-up failed: ${resp.code()}"
                            }
                        } catch (e: Exception) {
                            topUpError = e.message
                        } finally {
                            topUpLoading = false
                        }
                    }
                }

                override fun onFailure(errorCode: String, errorMessage: String) {
                    topUpLoading = false
                    topUpError   = "Payment failed: $errorMessage"
                }
            }
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F7FB))
        ) {
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
                Text("Wallet", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (errorMsg != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(errorMsg ?: "", color = Color.Red)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { loadWallet() }) { Text("Retry") }
                    }
                }
                return@Column
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.linearGradient(listOf(Color(0xFF1565C0), Color(0xFF42A5F5))))
                            .padding(24.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Available Balance", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                            Text("₹ ${String.format("%.2f", balance)}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp)
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { showTopUpDialog = true },
                                colors  = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1565C0)),
                                shape   = RoundedCornerShape(24.dp)
                            ) {
                                Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add Money", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1565C0), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Add money to your wallet via Razorpay, then pay teachers directly from your balance.", fontSize = 12.sp, color = Color(0xFF1565C0))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Transaction History", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${transactions.size} records", fontSize = 12.sp, color = Color.Gray)
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (transactions.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(56.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("No transactions yet", color = Color.Gray, fontSize = 14.sp)
                                Text("Add money to get started", color = Color.LightGray, fontSize = 12.sp)
                            }
                        }
                    }
                }

                itemsIndexed(transactions) { _, tx ->
                    val isCredit     = tx.type == "ADD_MONEY" || tx.type == "REFUND"
                    val amountColor  = if (isCredit) Color(0xFF2E7D32) else Color(0xFFC62828)
                    val amountPrefix = if (isCredit) "+ ₹" else "- ₹"
                    val txIcon = when (tx.type) {
                        "ADD_MONEY"  -> Icons.Default.AddCircle
                        "REFUND"     -> Icons.Default.Replay
                        "PAYMENT"    -> Icons.Default.Payment
                        "WITHDRAWAL" -> Icons.Default.RemoveCircle
                        else         -> Icons.Default.SwapHoriz
                    }
                    val iconBg = when (tx.type) {
                        "ADD_MONEY"  -> Color(0xFF1565C0)
                        "REFUND"     -> Color(0xFF2E7D32)
                        "PAYMENT"    -> Color(0xFFC62828)
                        "WITHDRAWAL" -> Color(0xFFE65100)
                        else         -> Color(0xFF6A1B9A)
                    }
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(iconBg.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                Icon(txIcon, contentDescription = null, tint = iconBg, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(txLabel(tx.type), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(formatWalletTime(tx.createdAt), fontSize = 11.sp, color = Color.Gray)
                            }
                            Text("$amountPrefix${String.format("%.2f", tx.amount)}", color = amountColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }

    if (showTopUpDialog) {
        AlertDialog(
            onDismissRequest = { if (!topUpLoading) showTopUpDialog = false },
            title = { Text("Add Money to Wallet", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter amount to add via Razorpay:", fontSize = 13.sp, color = Color.Gray)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = topUpAmountText, onValueChange = { topUpAmountText = it; topUpError = null }, label = { Text("Amount (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(), leadingIcon = { Text("₹", fontWeight = FontWeight.Bold) })
                    if (topUpError != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(topUpError!!, color = Color.Red, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(200, 500, 1000).forEach { preset ->
                            SuggestionChip(onClick = { topUpAmountText = preset.toString(); topUpError = null }, label = { Text("₹$preset") })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { startTopUp() }, enabled = !topUpLoading, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))) {
                    if (topUpLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Pay via Razorpay")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTopUpDialog = false }, enabled = !topUpLoading) { Text("Cancel") }
            }
        )
    }
}