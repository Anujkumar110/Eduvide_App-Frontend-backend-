package com.example.tutorapp

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.tutorapp.network.RetrofitClient
import com.example.tutorapp.network.TokenManager
import com.example.tutorapp.network.WebSocketManager

class SessionViewModelFactory(
    private val context: Context,
    // ✅ FIX: bahar se wsManager lo — andar naya mat banao
    private val wsManager: WebSocketManager
) : ViewModelProvider.Factory {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(SessionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SessionViewModel(
                apiService    = RetrofitClient.api,
                tokenProvider = { TokenManager.getBearerToken(context) },
                wsManager     = wsManager  // ✅ same instance
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}