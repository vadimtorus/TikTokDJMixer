package com.tiktokdj.mixer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/**
 * Класс приложения: создаёт каналы уведомлений и хранит глобальный
 * синглтон [instance] для доступа к контексту из не-Android классов.
 *
 * Application class: creates the notification channels and holds a global
 * [instance] singleton for context access from non-Android classes.
 */
class TikTokDJApp : Application() {

    companion object {
        /** Id канала уведомлений стриминга / Streaming notifications channel id */
        const val CHANNEL_ID_STREAMING = "streaming_channel"

        /** Id канала уведомлений об обновлениях / Update notifications channel id */
        const val CHANNEL_ID_UPDATE = "update_channel"

        /**
         * Глобальный экземпляр приложения (синглтон).
         * Global application instance (singleton).
         */
        lateinit var instance: TikTokDJApp
            private set
    }

    /**
     * Точка входа процесса: фиксируем синглтон и создаём каналы уведомлений.
     * Process entry point: capture the singleton and create notification channels.
     */
    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    /**
     * Создаёт два канала уведомлений (требуется с Android 8.0 / API 26):
     * - стриминг: низкая важность — без звука/вибрации, чтобы не мешать миксу;
     * - обновления: обычная важность — пользователь должен их заметить.
     *
     * Creates the two notification channels (required since Android 8.0 / API 26):
     * - streaming: low importance — silent, so it never disturbs the mix;
     * - updates: default importance — the user should notice them.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Канал постоянного уведомления активной трансляции.
            // Channel for the ongoing live-broadcast notification.
            val streamingChannel = NotificationChannel(
                CHANNEL_ID_STREAMING,
                getString(R.string.channel_streaming),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_streaming_desc)
            }

            // Канал уведомлений о доступных обновлениях приложения.
            // Channel for available app-update notifications.
            val updateChannel = NotificationChannel(
                CHANNEL_ID_UPDATE,
                getString(R.string.channel_updates),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.channel_updates_desc)
            }

            manager.createNotificationChannel(streamingChannel)
            manager.createNotificationChannel(updateChannel)
        }
    }
}
