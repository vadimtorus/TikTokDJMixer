package com.tiktokdj.mixer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class TikTokDJApp : Application() {

    companion object {
        const val CHANNEL_ID_STREAMING = "streaming_channel"
        const val CHANNEL_ID_UPDATE = "update_channel"
        lateinit var instance: TikTokDJApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val streamingChannel = NotificationChannel(
                CHANNEL_ID_STREAMING,
                "Стриминг",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Уведомления о активном стриме"
            }

            val updateChannel = NotificationChannel(
                CHANNEL_ID_UPDATE,
                "Обновления",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Уведомления об обновлениях приложения"
            }

            manager.createNotificationChannel(streamingChannel)
            manager.createNotificationChannel(updateChannel)
        }
    }
}
