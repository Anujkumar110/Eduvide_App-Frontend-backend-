package com.example.tutorapp.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.*

@Composable
fun WalletBalanceCard(
    balance: Double,
    title: String = "Wallet Balance",
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit = { Icon(Icons.Default.ArrowUpward, null, tint = MaterialTheme.colorScheme.onPrimary) }
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "₹${NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(balance)}",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TransactionItem(transaction: Transaction) {
    val amountColor = if (transaction.type == TransactionType.CREDIT) Color(0xFF4CAF50) else Color(0xFFF44336)
    val prefix = if (transaction.type == TransactionType.CREDIT) "+" else "-"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(transaction.title, style = MaterialTheme.typography.bodyLarge)
            Text(transaction.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("$prefix₹${String.format("%.2f", transaction.amount)}", color = amountColor, fontWeight = FontWeight.SemiBold)
            if (transaction.status == TransactionStatus.PENDING) {
                Text("Pending", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun LoadingOverlay(isLoading: Boolean) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewComponents() {
    MaterialTheme {
        Column(Modifier.padding(16.dp)) {
            WalletBalanceCard(balance = 1250.50)
            Spacer(Modifier.height(16.dp))
            TransactionItem(Transaction("1", "Kotlin Basics Course", -299.0, TransactionType.DEBIT, "2026-04-15"))
        }
    }
}