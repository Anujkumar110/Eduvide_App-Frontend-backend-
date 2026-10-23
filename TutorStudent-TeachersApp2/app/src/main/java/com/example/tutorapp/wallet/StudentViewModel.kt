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

class StudentViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(StudentWalletState(
        balance = 1500.00,
        transactions = listOf(
            Transaction(UUID.randomUUID().toString(), "Course: Android Fundamentals", -450.0, TransactionType.DEBIT, "2026-04-10"),
            Transaction(UUID.randomUUID().toString(), "Wallet Top-up", 1000.0, TransactionType.CREDIT, "2026-04-05")
        )
    ))
    val uiState: StateFlow<StudentWalletState> = _uiState.asStateFlow()

    @RequiresApi(Build.VERSION_CODES.O)
    fun addMoney(amount: Double, paymentMethod: String) {
        if (amount <= 0) {
            _uiState.value = _uiState.value.copy(error = "Invalid amount")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)
            delay(1500) // Simulate network
            val newTx = Transaction(
                UUID.randomUUID().toString(), "Top-up via $paymentMethod", amount,
                TransactionType.CREDIT, LocalDate.now().toString()
            )
            _uiState.value = _uiState.value.copy(
                balance = _uiState.value.balance + amount,
                transactions = listOf(newTx) + _uiState.value.transactions,
                isLoading = false,
                successMessage = "₹$amount added successfully!"
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun purchaseCourse(coursePrice: Double) {
        if (coursePrice > _uiState.value.balance) {
            _uiState.value = _uiState.value.copy(error = "Insufficient wallet balance")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)
            delay(1200)
            val newTx = Transaction(
                UUID.randomUUID().toString(), "Course Purchase", coursePrice,
                TransactionType.DEBIT, LocalDate.now().toString()
            )
            _uiState.value = _uiState.value.copy(
                balance = _uiState.value.balance - coursePrice,
                transactions = listOf(newTx) + _uiState.value.transactions,
                isLoading = false,
                successMessage = "Course purchased successfully!"
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}