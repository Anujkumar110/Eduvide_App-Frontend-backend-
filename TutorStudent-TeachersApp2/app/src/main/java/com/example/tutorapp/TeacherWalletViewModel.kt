package com.example.tutorapp.teacher

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tutorapp.network.RetrofitClient
import com.example.tutorapp.network.TokenManager
import com.example.tutorapp.network.TransactionData
import com.example.tutorapp.network.WalletData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// UI State
// ─────────────────────────────────────────────────────────────────────────────

data class TeacherWalletUiState(
    val isLoading: Boolean = false,
    val errorMsg: String? = null,

    // Wallet
    val balance: Double = 0.0,
    val walletId: String = "",           // teacher ka _id — credit check ke liye

    // Transactions
    val transactions: List<TransactionData> = emptyList(),
    val totalEarned: Double = 0.0,       // sirf PAYMENT type jisme toWalletId == walletId
)

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class TeacherWalletViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TeacherWalletUiState())
    val uiState: StateFlow<TeacherWalletUiState> = _uiState.asStateFlow()

    // ── Public: load / refresh ────────────────────────────────────────────────

    fun loadWallet(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMsg = null) }

            val token = TokenManager.getToken(context)
            if (token == null) {
                _uiState.update { it.copy(isLoading = false, errorMsg = "Not logged in") }
                return@launch
            }

            val bearer = "Bearer $token"

            try {
                // ── 1. Fetch wallet ──────────────────────────────────────────
                val walletResp = RetrofitClient.api.getWallet(bearer)
                val walletData: WalletData? = if (walletResp.isSuccessful) {
                    walletResp.body()?.data
                } else {
                    android.util.Log.e("TeacherWalletVM", "getWallet failed: ${walletResp.code()} ${walletResp.message()}")
                    null
                }

                val balance  = walletData?.balance ?: 0.0
                val walletId = walletData?.id      ?: ""

                android.util.Log.d("TeacherWalletVM", "walletId=$walletId | balance=$balance")

                // ── 2. Fetch transactions ────────────────────────────────────
                val txResp = RetrofitClient.api.getWalletTransactions(bearer)
                val txList: List<TransactionData> = if (txResp.isSuccessful) {
                    txResp.body()?.data ?: emptyList()
                } else {
                    android.util.Log.e("TeacherWalletVM", "getWalletTransactions failed: ${txResp.code()}")
                    emptyList()
                }

                android.util.Log.d("TeacherWalletVM", "total tx fetched: ${txList.size}")

                // ── 3. Compute totalEarned ───────────────────────────────────
                // KEY: PAYMENT type + toWalletId == teacher's walletId → credit
                val totalEarned = if (walletId.isNotBlank()) {
                    txList
                        .filter { tx -> tx.type == "PAYMENT" && tx.toWalletId == walletId }
                        .sumOf { it.amount }
                } else {
                    0.0
                }

                android.util.Log.d("TeacherWalletVM", "totalEarned=$totalEarned")

                _uiState.update {
                    it.copy(
                        isLoading    = false,
                        balance      = balance,
                        walletId     = walletId,
                        transactions = txList,
                        totalEarned  = totalEarned,
                        errorMsg     = null
                    )
                }

            } catch (e: Exception) {
                android.util.Log.e("TeacherWalletVM", "Exception: ${e.message}", e)
                _uiState.update {
                    it.copy(isLoading = false, errorMsg = e.message ?: "Unknown error")
                }
            }
        }
    }

    // ── Public: clear error (e.g. after Snackbar shown) ──────────────────────

    fun clearError() {
        _uiState.update { it.copy(errorMsg = null) }
    }
}