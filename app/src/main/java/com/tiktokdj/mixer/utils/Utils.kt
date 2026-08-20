package com.tiktokdj.mixer.utils

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
