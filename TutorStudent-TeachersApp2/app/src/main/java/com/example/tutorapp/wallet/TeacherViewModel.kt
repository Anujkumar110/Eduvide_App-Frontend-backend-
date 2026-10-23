package com.example.tutorapp.wallet

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

class TeacherViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TeacherWalletState(
        balance = 3450.00,
        totalEarnings = 8900.00,
        transactions = listOf(
            Transaction(UUID.randomUUID().toString(), "Course Sale: Advanced Kotlin", 1200.0, TransactionType.CREDIT, "2026-04-16"),
            Transaction(UUID.randomUUID().toString(), "Withdrawal to Bank", -500.0, TransactionType.DEBIT, "2026-04-12")
        )
    ))
    val uiState: StateFlow<TeacherWalletState> = _uiState.asStateFlow()

    @RequiresApi(Build.VERSION_CODES.O)
    fun withdraw(amount: Double) {
        if (amount <= 0) { _uiState.value = _uiState.value.copy(error = "Invalid amount"); return }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)
            delay(1500)
            if (_uiState.value.balance < amount) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Insufficient balance")
                return@launch
            }
            val newTx = Transaction(
                UUID.randomUUID().toString(), "Withdrawal to Bank", amount,
                TransactionType.DEBIT, LocalDate.now().toString()
            )
            _uiState.value = _uiState.value.copy(
                balance = _uiState.value.balance - amount,
                transactions = listOf(newTx) + _uiState.value.transactions,
                isLoading = false,
                successMessage = "Withdrawal processed successfully!"
            )
        }
    }

    fun clearMessage() { _uiState.value = _uiState.value.copy(error = null, successMessage = null) }
}