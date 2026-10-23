package com.example.tutorapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Foreground service required for screen capture on Android 10+.
 * This service ONLY manages the foreground notification.
 * MediaProjection is created directly by ScreenCapturerAndroid in WebRTCClient —
 * creating it here AND there causes a crash (double projection from same intent).
 */
class ScreenShareService : Service() {

    companion object {
        private const val TAG = "ScreenShareService"

        const val ACTION_START_SCREEN_SHARE = "ACTION_START_SCREEN_SHARE"
        const val ACTION_STOP_SCREEN_SHARE  = "ACTION_STOP_SCREEN_SHARE"

        // resultCode + data stored here so WebRTCClient can use them after service starts
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_DATA        = "EXTRA_DATA"

        private const val NOTIFICATION_ID      = 1001
        private const val NOTIFICATION_CHANNEL = "screen_share_channel"

        var instance: ScreenShareService? = null
            private set

        // WebRTCClient inhe use karega ScreenCapturerAndroid banane ke liye
        var pendingResultCode: Int?    = null
        var pendingData:       Intent? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        Log.d(TAG, "ScreenShareService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SCREEN_SHARE -> {
                Log.d(TAG, "Starting screen share foreground service")
                startForeground(NOTIFICATION_ID, buildNotification())

                // Store resultCode + data — WebRTCClient inhe directly use karega
                // Yahan getMediaProjection() BILKUL MAT KARO
                pendingResultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                pendingData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }

                Log.d(TAG, "Foreground started — resultCode=$pendingResultCode, data=${pendingData != null}")
            }
            ACTION_STOP_SCREEN_SHARE -> {
                Log.d(TAG, "Stopping screen share foreground service")
                pendingResultCode = null
                pendingData       = null
                stopForegroundCompat()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        pendingResultCode = null
        pendingData       = null
        instance          = null
        Log.d(TAG, "ScreenShareService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL,
                "Screen Share",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used while screen sharing is active"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
            .setContentTitle("Screen Sharing Active")
            .setContentText("Your screen is being shared in the class")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
}