package com.example.tutorapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.tutorapp.R

/**
 * Foreground Service for Screen Sharing
 * Required for Android 14+ (API 34+) to capture screen using MediaProjection
 * This service keeps the screen capture alive even when app is in background
 */
class ScreenShareService : Service() {

    private val TAG = "ScreenShareService"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "screen_share_channel"

    var mediaProjection: MediaProjection? = null
    private var mediaProjectionManager: MediaProjectionManager? = null

    companion object {
        const val ACTION_START_SCREEN_SHARE = "com.example.tutorapp.START_SCREEN_SHARE"
        const val ACTION_STOP_SCREEN_SHARE = "com.example.tutorapp.STOP_SCREEN_SHARE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"

        // Singleton instance for accessing from other classes
        var instance: ScreenShareService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        createNotificationChannel()
        Log.d(TAG, "ScreenShareService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SCREEN_SHARE -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }

                if (resultCode != -1 && data != null) {
                    startForeground(NOTIFICATION_ID, createNotification())
                    startMediaProjection(resultCode, data)
                    Log.d(TAG, "Screen sharing started with foreground service")
                } else {
                    Log.e(TAG, "Invalid result code or data for screen sharing")
                    stopSelf()
                }
            }
            ACTION_STOP_SCREEN_SHARE -> {
                Log.d(TAG, "Stopping screen share service")
                stopMediaProjection()
                stopForeground()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startMediaProjection(resultCode: Int, data: Intent) {
        try {
            mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped")
                    mediaProjection = null
                    // Stop service when MediaProjection stops
                    stopForeground()
                    stopSelf()
                }
            }, null)
            Log.d(TAG, "MediaProjection started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaProjection: ${e.message}")
            stopSelf()
        }
    }

    private fun stopMediaProjection() {
        try {
            mediaProjection?.unregisterCallback(object : MediaProjection.Callback() {})
            mediaProjection?.stop()
            mediaProjection = null
            Log.d(TAG, "MediaProjection stopped and cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaProjection: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Sharing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification shown while screen is being shared"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Sharing Active")
            .setContentText("EduVibe is sharing your screen")
            .setSmallIcon(R.drawable.ic_screen_share)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun stopForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopMediaProjection()
        instance = null
        Log.d(TAG, "ScreenShareService destroyed")
    }
}
