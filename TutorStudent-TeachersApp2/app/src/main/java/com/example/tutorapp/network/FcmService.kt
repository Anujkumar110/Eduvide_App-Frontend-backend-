package com.example.tutorapp.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.tutorapp.MainActivity
import com.example.tutorapp.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FcmService : FirebaseMessagingService() {

    private val TAG = "FcmService"

    private val PAYMENT_CHANNEL_ID = "payment_notifications"
    private val SESSION_CHANNEL_ID = "session_notifications"
    private val WALLET_CHANNEL_ID  = "wallet_notifications"
    private val GENERAL_CHANNEL_ID = "general_notifications"

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(TAG, "From: ${message.from}")
        Log.d(TAG, "Data: ${message.data}")
        Log.d(TAG, "Notification: ${message.notification?.title} / ${message.notification?.body}")

        // Data payload takes priority over notification payload
        if (message.data.isNotEmpty()) {
            handleDataPayload(message.data)
            return
        }

        message.notification?.let {
            val title = it.title ?: "EduVibe"
            val body  = it.body  ?: return@let
            if (body.isNotBlank()) showNotification(title, body, GENERAL_CHANNEL_ID)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")

        // Save locally first — even if API call fails, token is preserved
        FcmTokenManager.saveToken(this, token)

        val authToken = TokenManager.getToken(this)
        if (!authToken.isNullOrBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val resp = RetrofitClient.api.saveFcmToken(
                        token   = "Bearer $authToken",
                        request = FcmTokenRequest(token = token)
                    )
                    if (resp.isSuccessful) Log.d(TAG, " New FCM token synced to backend")
                    else Log.e(TAG, " FCM token sync failed: ${resp.code()}")
                } catch (e: Exception) {
                    Log.e(TAG, " FCM token sync error: ${e.message}")
                }
            }
        }
    }

    // ── Data payload handler ──────────────────────────────────────────────────

    private fun handleDataPayload(data: Map<String, String>) {

        val type = data["type"] ?: ""

        val (title, body, channelId) = when (type) {

            // ── Payment ───────────────────────────────────────────────────────
            "PAYMENT_INITIATED",
            "payment_initiated" -> Triple(
                "⏳ Payment Initiated",
                "₹${data["amount"] ?: "0"} payment to ${data["teacherName"] ?: "teacher"} is being processed...",
                PAYMENT_CHANNEL_ID
            )

            "PAYMENT_SUCCESS",
            "payment_success" -> Triple(
                "✅ Payment Successful",
                "₹${data["amount"] ?: "0"} successfully paid to ${data["teacherName"] ?: "teacher"}. New balance: ₹${data["newBalance"] ?: ""}",
                WALLET_CHANNEL_ID
            )

            "payment_received" -> Triple(
                "💰 Payment Received",
                "${data["studentName"] ?: "A student"} paid ₹${data["amount"] ?: "0"} for ${data["className"] ?: "a class"}",
                PAYMENT_CHANNEL_ID
            )

            // ── Slot Booking ──────────────────────────────────────────────────
            "new_booking" -> Triple(
                "📅 New Slot Booked",
                "${data["studentName"] ?: "A student"} booked ${data["slotTime"] ?: "a slot"} — ₹${data["amount"] ?: ""}",
                PAYMENT_CHANNEL_ID
            )

            "booking_confirmed" -> Triple(
                "✅ Slot Confirmed",
                "Your slot with ${data["teacherName"] ?: "Teacher"} at ${data["slotTime"] ?: ""} is confirmed",
                GENERAL_CHANNEL_ID
            )

            // ── Sessions ──────────────────────────────────────────────────────
            "session_created" -> Triple(
                "🎓 New Session Created",
                "${data["teacherName"] ?: "Your teacher"} created a session: ${data["className"] ?: ""}",
                SESSION_CHANNEL_ID
            )

            "class_starting" -> Triple(
                "⏰ Class Starting Soon",
                "${data["className"] ?: "Your class"} is about to start. Join now!",
                SESSION_CHANNEL_ID
            )

            "SESSION_STARTED",
            "session_started" -> Triple(
                "🎓 Class Started!",
                "${data["teacherName"] ?: "Teacher"} has started \"${data["className"] ?: "class"}\"! Join now!",
                SESSION_CHANNEL_ID
            )

            "SESSION_ENDED",
            "session_ended" -> Triple(
                "🏁 Class Ended",
                "\"${data["className"] ?: "Class"}\" has ended.",
                SESSION_CHANNEL_ID
            )

            // ── Enrollment & Live ─────────────────────────────────────────────
            "new_enrollment" -> Triple(
                "📚 New Enrollment",
                "${data["studentName"] ?: "A student"} enrolled in ${data["className"] ?: "your class"}",
                GENERAL_CHANNEL_ID
            )

            "teacher_live" -> Triple(
                "🔴 Teacher is Live",
                "${data["teacherName"] ?: "A teacher"} is live now — book a slot!",
                GENERAL_CHANNEL_ID
            )

            // ── Wallet ────────────────────────────────────────────────────────
            "wallet_topup_success",
            "WALLET_TOPUP_SUCCESS" -> Triple(
                "💰 Wallet Credited",
                "₹${data["amount"] ?: "0"} added to your wallet. New balance: ₹${data["newBalance"] ?: data["balance"] ?: ""}",
                WALLET_CHANNEL_ID
            )

            "WALLET_PAYMENT_DEBITED",
            "wallet_payment_sent" -> Triple(
                "💸 Payment Sent",
                "₹${data["amount"] ?: "0"} paid to teacher. New balance: ₹${data["newBalance"] ?: data["balance"] ?: ""}",
                WALLET_CHANNEL_ID
            )

            "WALLET_PAYMENT_CREDITED",
            "wallet_payment_received" -> Triple(
                "💰 Payment Received",
                "₹${data["amount"] ?: "0"} received from student. New balance: ₹${data["newBalance"] ?: data["balance"] ?: ""}",
                WALLET_CHANNEL_ID
            )

            "wallet_deposit" -> Triple(
                "💰 Wallet Credited",
                "₹${data["amount"] ?: "0"} added to your wallet. New balance: ₹${data["balance"] ?: ""}",
                WALLET_CHANNEL_ID
            )

            "wallet_withdrawal" -> Triple(
                "🏧 Withdrawal Processed",
                "₹${data["amount"] ?: "0"} withdrawn from your wallet. Remaining: ₹${data["balance"] ?: ""}",
                WALLET_CHANNEL_ID
            )

            "wallet_payment" -> Triple(
                "💳 Payment Deducted",
                "₹${data["amount"] ?: "0"} deducted for ${data["className"] ?: "class"}",
                WALLET_CHANNEL_ID
            )

            // ── Fallback ──────────────────────────────────────────────────────
            else -> {
                val t = data["title"]   ?: "EduVibe"
                val b = data["body"]    ?: data["message"] ?: ""
                if (b.isBlank()) return
                Triple(t, b, GENERAL_CHANNEL_ID)
            }
        }

        showNotification(title, body, channelId)
    }

    // ── Show notification ─────────────────────────────────────────────────────

    private fun showNotification(title: String, body: String, channelId: String) {

        createChannel(channelId)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Wallet notifications → navigate to WalletScreen on tap
            putExtra("navigate_to", if (channelId == WALLET_CHANNEL_ID) "WalletScreen" else "")
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)

        Log.d(TAG, " Notification shown [$channelId]: $title")
    }

    // ── Create notification channel (Android O+) ──────────────────────────────

    private fun createChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val (name, desc) = when (channelId) {
                PAYMENT_CHANNEL_ID -> "Payments" to "Payment & booking notifications"
                SESSION_CHANNEL_ID -> "Sessions" to "Class session notifications"
                WALLET_CHANNEL_ID  -> "Wallet"   to "Wallet deposit, payment & withdrawal notifications"
                else               -> "General"  to "General app notifications"
            }
            val channel = NotificationChannel(
                channelId,
                name,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = desc
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}