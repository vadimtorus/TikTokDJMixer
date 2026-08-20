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
                getString(R.string.channel_streaming),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_streaming_desc)
            }

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
