package com.example.tutorapp.network

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object FcmTokenManager {

    private const val PREFS_NAME = "fcm_prefs"
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val TAG = "FcmTokenManager"

    // ── Save token locally ─────────────────────────────────────────────────────
    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FCM_TOKEN, token)
            .apply()
        Log.d(TAG, "💾 FCM token saved locally")
    }

    // ── Get saved token ────────────────────────────────────────────────────────
    fun getSavedToken(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FCM_TOKEN, null)
    }

    // ── Fetch fresh token from Firebase and save locally ───────────────────────
    fun fetchAndSaveToken(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                saveToken(context, token)
                Log.d(TAG, "🔥 Fresh FCM token fetched: $token")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to fetch FCM token: ${e.message}")
            }
        }
    }

    // ── Sync saved token with backend ──────────────────────────────────────────
    fun syncTokenWithBackend(
        context: Context,
        bearerToken: String,
        api: ApiService
    ) {
        val fcmToken = getSavedToken(context)

        if (fcmToken.isNullOrBlank()) {
            Log.d(TAG, "⚠️ No local FCM token — fetching fresh then syncing")
            // Fetch fresh token first, then sync
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val freshToken = FirebaseMessaging.getInstance().token.await()
                    saveToken(context, freshToken)
                    sendToBackend(api, bearerToken, freshToken)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Could not get fresh FCM token: ${e.message}")
                }
            }
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            sendToBackend(api, bearerToken, fcmToken)
        }
    }

    // ── Internal: POST token to backend ───────────────────────────────────────
    private suspend fun sendToBackend(api: ApiService, bearerToken: String, fcmToken: String) {
        try {
            val response = api.saveFcmToken(
                token = bearerToken,
                request = FcmTokenRequest(token = fcmToken)
            )
            if (response.isSuccessful) {
                Log.d(TAG, "✅ FCM token synced with backend")
            } else {
                Log.e(TAG, "❌ Sync failed: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Sync exception: ${e.message}")
        }
    }
}