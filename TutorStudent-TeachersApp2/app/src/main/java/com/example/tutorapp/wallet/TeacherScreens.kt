package com.example.tutorapp.wallet

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewTeacherWithdraw() {
    MaterialTheme {
        // Humne onBack ke liye ek khali lambda {} pass kiya hai
        TeacherWithdrawScreen(
            onBack = {}
            // Note: viewModel() yahan default use ho raha hai,
            // isliye UI initial state mein dikhega.
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherDashboardScreen(
    onWithdrawClick: () -> Unit,
    viewModel: TeacherViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Teacher Dashboard") }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            WalletBalanceCard(balance = state.balance, title = "Wallet Balance")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Earnings", style = MaterialTheme.typography.titleMedium)
                    Text("₹${String.format("%.2f", state.totalEarnings)}", style = MaterialTheme.typography.headlineMedium)
                }
            }
            Button(onClick = onWithdrawClick, modifier = Modifier.fillMaxWidth()) { Text("Withdraw Funds") }
            Spacer(Modifier.height(16.dp))
            Text("Recent Transactions", style = MaterialTheme.typography.titleLarge)
            LazyColumn {
                items(state.transactions) { TransactionItem(it) }
            }
        }
        LoadingOverlay(state.isLoading)
        MessageSnackbar(state.error, state.successMessage, onDismiss = viewModel::clearMessage)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherWithdrawScreen(
    onBack: () -> Unit,
    viewModel: TeacherViewModel = viewModel()
) {
    var amount by remember { mutableStateOf("") }
    val numAmount = amount.toDoubleOrNull() ?: 0.0
    val platformFee = numAmount * 0.20
    val tds = numAmount * 0.10
    val finalPayout = numAmount - platformFee - tds

    Scaffold(topBar = { TopAppBar(title = { Text("Withdraw Funds") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(androidx.compose.material.icons.Icons.Filled.ArrowBack, "Back") }
    }) }) { padding ->
        Column(Modifier.padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Withdraw Amount") },
                prefix = { Text("₹") },
                modifier = Modifier.fillMaxWidth()
            )
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Breakdown", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Platform Fee (20%)")
                        Text("- ₹${String.format("%.2f", platformFee)}")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TDS/Charges (10%)")
                        Text("- ₹${String.format("%.2f", tds)}")
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Final Payout", style = MaterialTheme.typography.bodyLarge)
                        Text("₹${String.format("%.2f", finalPayout)}", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Button(
                onClick = { viewModel.withdraw(numAmount) },
                modifier = Modifier.fillMaxWidth(),
                enabled = numAmount > 0
            ) { Text("Confirm Withdrawal") }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewTeacherDashboard() {
    MaterialTheme { TeacherDashboardScreen({}) }
}