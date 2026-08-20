package com.tiktokdj.mixer.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tiktokdj.mixer.TikTokDJApp
import com.tiktokdj.mixer.engine.MixerEngine
import com.tiktokdj.mixer.streaming.StreamManager
import com.tiktokdj.mixer.ui.MainActivity

class StreamingService : Service() {

    companion object {
        private const val TAG = "StreamingService"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.tiktokdj.mixer.START_STREAM"
        const val ACTION_STOP = "com.tiktokdj.mixer.STOP_STREAM"
        const val EXTRA_RTMP_URL = "rtmp_url"
        const val EXTRA_TITLE = "title"
    }

    private var streamManager: StreamManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "StreamingService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val rtmpUrl = intent.getStringExtra(EXTRA_RTMP_URL) ?: ""
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "DJ Mix Live"
                startForeground(NOTIFICATION_ID, createNotification(title))
                startStreaming(rtmpUrl, title)
            }
            ACTION_STOP -> {
                stopStreaming()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startStreaming(rtmpUrl: String, title: String) {
        streamManager = StreamManager(applicationContext)
        // Stream will be started via the manager
    }

    private fun stopStreaming() {
        streamManager?.cleanup()
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
            .addAction(android.R.drawable.ic_media_pause, "Остановить", stopIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        streamManager?.cleanup()
        super.onDestroy()
    }
}
