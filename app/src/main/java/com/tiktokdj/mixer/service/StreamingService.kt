package com.tiktokdj.mixer.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tiktokdj.mixer.TikTokDJApp
import com.tiktokdj.mixer.streaming.StreamManager
import com.tiktokdj.mixer.ui.MainActivity
import kotlinx.coroutines.*

class StreamingService : Service() {

    companion object {
        private const val TAG = "StreamingService"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START_RTMP = "com.tiktokdj.mixer.START_RTMP"
        const val ACTION_START_TIKTOK = "com.tiktokdj.mixer.START_TIKTOK"
        const val ACTION_STOP = "com.tiktokdj.mixer.STOP_STREAM"
        const val EXTRA_RTMP_URL = "rtmp_url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_CLIENT_KEY = "client_key"
        const val EXTRA_CLIENT_SECRET = "client_secret"
    }

    private var streamManager: StreamManager? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RTMP -> {
                val rtmpUrl = intent.getStringExtra(EXTRA_RTMP_URL) ?: ""
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "DJ Mix Live"
                startForeground(NOTIFICATION_ID, createNotification(title))
                startRtmpStream(rtmpUrl)
            }
            ACTION_START_TIKTOK -> {
                val clientKey = intent.getStringExtra(EXTRA_CLIENT_KEY) ?: ""
                val clientSecret = intent.getStringExtra(EXTRA_CLIENT_SECRET) ?: ""
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "DJ Mix Live"
                startForeground(NOTIFICATION_ID, createNotification(title))
                startTikTokStream(clientKey, clientSecret, title)
            }
            ACTION_STOP -> {
                serviceScope.launch {
                    stopStreamingInternal()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startRtmpStream(rtmpUrl: String) {
        streamManager = StreamManager(applicationContext)
        val manager = streamManager ?: return

        serviceScope.launch {
            val started = manager.startRTMPStream(rtmpUrl)
            if (!started) {
                Log.e(TAG, "Failed to start RTMP stream")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun startTikTokStream(clientKey: String, clientSecret: String, title: String) {
        streamManager = StreamManager(applicationContext)
        val manager = streamManager ?: return

        serviceScope.launch {
            val started = manager.startTikTokStream(clientKey, clientSecret, title)
            if (!started) {
                Log.e(TAG, "Failed to start TikTok stream")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun stopStreamingInternal() {
        streamManager?.stopStreaming()
        streamManager = null
    }

    private fun createNotification(title: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, StreamingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, TikTokDJApp.CHANNEL_ID_STREAMING)
            .setContentTitle("DJ Mix Live")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        streamManager?.cleanup()
        streamManager = null
        super.onDestroy()
    }
}
