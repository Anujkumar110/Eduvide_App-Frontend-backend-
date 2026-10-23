package com.example.tutorapp.wallet

import androidx.compose.foundation.clickable
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
import java.util.*


@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewStudentWalletSimple() {
    MaterialTheme {
        // Hum khali lambdas {} pass kar rahe hain callbacks ke liye
        StudentWalletScreen(
            onAddMoneyClick = {},
            onCoursePurchaseClick = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentWalletScreen(
    onAddMoneyClick: () -> Unit,
    onCoursePurchaseClick: () -> Unit,
    viewModel: StudentViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Student Wallet") }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            WalletBalanceCard(balance = state.balance)
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onAddMoneyClick, modifier = Modifier.weight(1f)) { Text("Add Money") }
                Button(onClick = onCoursePurchaseClick, modifier = Modifier.weight(1f)) { Text("Buy Course") }
            }
            Spacer(Modifier.height(24.dp))
            Text("Recent Transactions", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            if (state.transactions.isEmpty()) {
                Text("No transactions yet", Modifier.align(Alignment.CenterHorizontally))
            } else {
                LazyColumn {
                    items(state.transactions) { TransactionItem(it) }
                }
            }
        }
        LoadingOverlay(state.isLoading)
        MessageSnackbar(state.error, state.successMessage, onDismiss = viewModel::clearMessage)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMoneyScreen(
    onBack: () -> Unit,
    viewModel: StudentViewModel = viewModel()
) {
    var amount by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf("UPI") }
    val methods = listOf("Razorpay", "UPI", "Net Banking")

    Scaffold(topBar = { TopAppBar(title = { Text("Add Money") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(androidx.compose.material.icons.Icons.Filled.ArrowBack, "Back") }
    }) }) { padding ->
        Column(Modifier.padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount") },
                prefix = { Text("₹") },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Payment Method", style = MaterialTheme.typography.titleMedium)
            methods.forEach { method ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { selectedMethod = method }) {
                    RadioButton(selected = method == selectedMethod, onClick = { selectedMethod = method })
                    Spacer(Modifier.width(8.dp))
                    Text(method)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.addMoney(amount.toDoubleOrNull() ?: 0.0, selectedMethod) },
                modifier = Modifier.fillMaxWidth(),
                enabled = amount.isNotEmpty()
            ) { Text("Pay Now") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursePurchaseScreen(
    coursePrice: Double = 299.0,
    onBack: () -> Unit,
    viewModel: StudentViewModel = viewModel()
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Course Purchase") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(androidx.compose.material.icons.Icons.Filled.ArrowBack, "Back") }
    }) }) { padding ->
        Column(Modifier.padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Mastering Jetpack Compose", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("Price: ₹${String.format("%.2f", coursePrice)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(onClick = { viewModel.purchaseCourse(coursePrice) }, modifier = Modifier.fillMaxWidth()) {
                Text("Pay with Wallet")
            }
        }
    }
}

@Composable
fun MessageSnackbar(error: String?, success: String?, onDismiss: () -> Unit) {
    if (error != null || success != null) {
        Snackbar(
            action = { TextButton(onClick = onDismiss) { Text("OK") } },
            containerColor = if (error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (error != null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Text(error ?: success ?: "")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewStudentWallet() {
    MaterialTheme { StudentWalletScreen({}, {}) }
}