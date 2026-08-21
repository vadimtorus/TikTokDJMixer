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

/**
 * Foreground-сервис стриминга: удерживает трансляцию живой, когда приложение
 * свёрнуто, и показывает постоянное уведомление с кнопкой «Stop».
 *
 * Запускается через intent с одним из действий [ACTION_START_RTMP],
 * [ACTION_START_TIKTOK] или [ACTION_STOP]; параметры передаются в extras.
 *
 * Streaming foreground service: keeps the broadcast alive while the app is
 * backgrounded and shows an ongoing notification with a "Stop" action.
 *
 * Started via an intent carrying one of the actions [ACTION_START_RTMP],
 * [ACTION_START_TIKTOK] or [ACTION_STOP]; parameters travel in the extras.
 */
class StreamingService : Service() {

    companion object {
        /** Тег для логирования / Tag for logging */
        private const val TAG = "StreamingService"

        /** Id постоянного уведомления стрима / Ongoing stream notification id */
        private const val NOTIFICATION_ID = 1001

        /** Действие: начать RTMP-трансляцию / Action: start an RTMP broadcast */
        const val ACTION_START_RTMP = "com.tiktokdj.mixer.START_RTMP"

        /** Действие: начать TikTok-трансляцию / Action: start a TikTok broadcast */
        const val ACTION_START_TIKTOK = "com.tiktokdj.mixer.START_TIKTOK"

        /** Действие: остановить трансляцию / Action: stop the broadcast */
        const val ACTION_STOP = "com.tiktokdj.mixer.STOP_STREAM"

        /** Extra: URL RTMP-сервера / Extra: RTMP server URL */
        const val EXTRA_RTMP_URL = "rtmp_url"

        /** Extra: название трансляции / Extra: stream title */
        const val EXTRA_TITLE = "title"

        /** Extra: ключ клиента TikTok / Extra: TikTok client key */
        const val EXTRA_CLIENT_KEY = "client_key"

        /** Extra: секрет клиента TikTok / Extra: TikTok client secret */
        const val EXTRA_CLIENT_SECRET = "client_secret"
    }

    /** Активный менеджер стриминга или null, если трансляция не идёт. */
    /** Active streaming manager or null when no broadcast is running. */
    private var streamManager: StreamManager? = null

    /**
     * Область корутин сервиса (IO): запуск/остановка стрима вне главного потока.
     * Отменяется в [onDestroy].
     *
     * Service coroutine scope (IO): starts/stops the stream off the main thread.
     * Cancelled in [onDestroy].
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Сервис не предоставляет интерфейса привязки / The service offers no binding interface. */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Обрабатывает команды стриминга, приходящие через startService().
     * START_NOT_STICKY-подобное поведение не нужно: возвращаем START_STICKY,
     * чтобы система перезапустила сервис после убийства (трансляция важнее).
     *
     * Handles streaming commands delivered via startService().
     * We return START_STICKY so the system restarts the service after it is
     * killed (a live broadcast is worth restoring).
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RTMP -> {
                // Читаем параметры из extras и сразу выводим foreground-уведомление
                // (обязательное требование для foreground-сервиса).
                //
                // Read parameters from the extras and immediately promote to a
                // foreground notification (mandatory for a foreground service).
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
                // Останавливаем стрим, снимаем уведомление и гасим сервис.
                // Stop the stream, drop the notification and shut the service down.
                serviceScope.launch {
                    stopStreamingInternal()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    /**
     * Создаёт StreamManager и запускает RTMP-трансляцию в фоновой корутине.
     * При неудаче — снимает уведомление и останавливает сервис.
     *
     * Creates a StreamManager and starts the RTMP broadcast in a background
     * coroutine. On failure — removes the notification and stops the service.
     */
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

    /**
     * Создаёт StreamManager и запускает TikTok-трансляцию в фоновой корутине.
     * При неудаче — снимает уведомление и останавливает сервис.
     *
     * Creates a StreamManager and starts the TikTok broadcast in a background
     * coroutine. On failure — removes the notification and stops the service.
     */
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

    /**
     * Внутренняя остановка трансляции и освобождение менеджера.
     * Internal broadcast shutdown and manager release.
     */
    private suspend fun stopStreamingInternal() {
        streamManager?.stopStreaming()
        streamManager = null
    }

    /**
     * Собирает постоянное уведомление стрима: тап открывает MainActivity,
     * кнопка «Stop» шлёт в сервис действие [ACTION_STOP].
     *
     * Builds the ongoing stream notification: tapping opens MainActivity,
     * the "Stop" action sends [ACTION_STOP] back to this service.
     */
    private fun createNotification(title: String): Notification {
        // PendingIntent на открытие главного экрана по тапу.
        // PendingIntent that opens the main screen on tap.
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // PendingIntent на самостановку сервиса по кнопке «Stop».
        // PendingIntent that self-stops the service via the "Stop" button.
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

    /**
     * Полная зачистка: отменяем корутины и освобождаем менеджер стрима.
     * Full teardown: cancel coroutines and release the stream manager.
     */
    override fun onDestroy() {
        serviceScope.cancel()
        streamManager?.cleanup()
        streamManager = null
        super.onDestroy()
    }
}
