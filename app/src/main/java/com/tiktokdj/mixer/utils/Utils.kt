package com.tiktokdj.mixer.utils

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

object AudioUtils {

    fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    fun formatBPM(bpm: Float): String {
        return if (bpm > 0) "%.1f".format(bpm) else "---"
    }

    fun dbToLinear(db: Float): Float {
        return Math.pow(10.0, db / 20.0).toFloat()
    }

    fun linearToDb(linear: Float): Float {
        if (linear <= 0) return -80f
        return (20 * Math.log10(linear.toDouble())).toFloat()
    }
}

object ColorUtils {
    fun getDeckColor(deckId: String): String {
        return when (deckId) {
            "A" -> "#FF6B6B"
            "B" -> "#4ECDC4"
            "master" -> "#FFE66D"
            else -> "#FFFFFF"
        }
    }
}

class PreferencesManager(context: Context) {
    private val prefs = context.getSharedPreferences("tiktok_dj_prefs", Context.MODE_PRIVATE)

    var lastRtmpUrl: String
        get() = prefs.getString("last_rtmp_url", "") ?: ""
        set(value) = prefs.edit().putString("last_rtmp_url", value).apply()

    var masterVolume: Float
        get() = prefs.getFloat("master_volume", 0.8f)
        set(value) = prefs.edit().putFloat("master_volume", value).apply()

    var enableSync: Boolean
        get() = prefs.getBoolean("enable_sync", false)
        set(value) = prefs.edit().putBoolean("enable_sync", value).apply()

    var streamBitrate: Int
        get() = prefs.getInt("stream_bitrate", 2500)
        set(value) = prefs.edit().putInt("stream_bitrate", value).apply()

    var autoUpdateEnabled: Boolean
        get() = prefs.getBoolean("auto_update", true)
        set(value) = prefs.edit().putBoolean("auto_update", value).apply()

    var githubOwner: String
        get() = prefs.getString("github_owner", "") ?: ""
        set(value) = prefs.edit().putString("github_owner", value).apply()

    var githubRepo: String
        get() = prefs.getString("github_repo", "") ?: ""
        set(value) = prefs.edit().putString("github_repo", value).apply()
}
