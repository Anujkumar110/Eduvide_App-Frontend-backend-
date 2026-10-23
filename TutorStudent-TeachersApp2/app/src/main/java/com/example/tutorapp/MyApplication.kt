package com.example.tutorapp

import android.app.Application
import android.util.Log
import com.example.tutorapp.network.FcmTokenManager
import com.example.tutorapp.network.RetrofitClient
import com.example.tutorapp.network.TokenManager
import com.google.firebase.messaging.FirebaseMessaging
import com.razorpay.Checkout

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // ✅ Razorpay preload
        Checkout.preload(applicationContext)

        // ✅ FCM token fetch + agar user logged in hai to sync bhi karo
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                FcmTokenManager.saveToken(this, token)
                Log.d("MyApplication", "✅ FCM Token ready: $token")

                // Agar already logged in hai to turant sync karo
                val authToken = TokenManager.getToken(this)
                if (!authToken.isNullOrBlank()) {
                    FcmTokenManager.syncTokenWithBackend(
                        this,
                        "Bearer $authToken",
                        RetrofitClient.api
                    )
                }
            }
            .addOnFailureListener { e ->
                Log.e("MyApplication", "❌ FCM Token fetch failed: ${e.message}")
            }
    }
}