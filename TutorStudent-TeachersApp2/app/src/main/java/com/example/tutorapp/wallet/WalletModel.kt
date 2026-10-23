package com.example.tutorapp.wallet

enum class TransactionType { CREDIT, DEBIT }
enum class TransactionStatus { SUCCESS, PENDING, FAILED }

data class Transaction(
    val id: String,
    val title: String,
    val amount: Double,
    val type: TransactionType,
    val date: String,
    val status: TransactionStatus = TransactionStatus.SUCCESS
)

data class StudentWalletState(
    val balance: Double = 1250.00,
    val transactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

data class TeacherWalletState(
    val balance: Double = 3450.00,
    val totalEarnings: Double = 8900.00,
    val transactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)