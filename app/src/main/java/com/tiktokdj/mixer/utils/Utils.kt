package com.tiktokdj.mixer.utils

import android.content.Context
import android.media.audiofx.AudioEffect
import android.os.Build
import android.view.View
import android.widget.SeekBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

object AudioUtils {

    fun getValidSessionId(context: Context): Int {
        return try {
            val audioSessionId = 0
            AudioEffect.queryEffects()?.let {
                if (it.isNotEmpty()) audioSessionId else -1
            } ?: -1
        } catch (e: Exception) {
            -1
        }
    }

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
    private val deckColors = mapOf(
        "A" to "#FF6B6B",
        "B" to "#4ECDC4",
        "master" to "#FFE66D",
        "cue1" to "#FF0000",
        "cue2" to "#00FF00",
        "cue3" to "#0000FF",
        "cue4" to "#FFFF00",
        "cue5" to "#FF00FF",
        "cue6" to "#00FFFF"
    )

    fun getDeckColor(deckId: String): String {
        return deckColors[deckId] ?: "#FFFFFF"
    }
}

object StringUtils {
    fun truncate(text: String, maxLength: Int): String {
        return if (text.length > maxLength) text.take(maxLength - 1) + "..." else text
    }

    fun generateId(): String {
        return UUID.randomUUID().toString().take(8)
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
