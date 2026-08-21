package com.tiktokdj.mixer.utils

import java.text.SimpleDateFormat
import java.util.*

/**
 * Утилиты для аудио- и форматных преобразований DJ-микшера.
 *
 * Audio and formatting helpers for the DJ mixer.
 */
object AudioUtils {

    /**
     * Форматирует длительность в миллисекундах как «M:SS» (например, «3:07»).
     * Formats a duration in milliseconds as "M:SS" (e.g. "3:07").
     *
     * @param durationMs Длительность в мс / duration in ms
     * @return Строка вида «минуты:секунды» / "minutes:seconds" string
     */
    fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    /**
     * Форматирует BPM с одним знаком после запятой;
     * для неопределённого темпа (<= 0) возвращает прочерк «---».
     *
     * Formats BPM with one decimal place; an undetermined tempo (<= 0)
     * renders as the "---" placeholder.
     *
     * @param bpm Темп трека / track tempo
     * @return Отформатированный BPM или «---» / formatted BPM or "---"
     */
    fun formatBPM(bpm: Float): String {
        return if (bpm > 0) "%.1f".format(bpm) else "---"
    }

    /**
     * Переводит громкость из децибел в линейный масштаб: linear = 10^(dB/20).
     * Например, 0 dB -> 1.0, -6 dB -> ~0.5, +20 dB -> 10.0.
     *
     * Converts gain from decibels to the linear scale: linear = 10^(dB/20).
     * E.g. 0 dB -> 1.0, -6 dB -> ~0.5, +20 dB -> 10.0.
     *
     * @param db Уровень в дБ / level in dB
     * @return Линейный коэффициент усиления / linear gain factor
     */
    fun dbToLinear(db: Float): Float {
        return Math.pow(10.0, db / 20.0).toFloat()
    }

    /**
     * Переводит линейный коэффициент усиления в децибелы: dB = 20·log10(linear).
     * Значения <= 0 отображаются в -80 дБ (практическая тишина),
     * чтобы избежать log10 от нуля/отрицательных чисел.
     *
     * Converts a linear gain factor to decibels: dB = 20·log10(linear).
     * Values <= 0 map to -80 dB (practical silence) to avoid
     * log10 of zero/negative numbers.
     *
     * @param linear Линейный коэффициент / linear gain factor
     * @return Уровень в дБ / level in dB
     */
    fun linearToDb(linear: Float): Float {
        if (linear <= 0) return -80f
        return (20 * Math.log10(linear.toDouble())).toFloat()
    }
}

/**
 * Утилиты цветовой схемы интерфейса микшера.
 *
 * Color-scheme helpers for the mixer UI.
 */
object ColorUtils {

    /**
     * Возвращает фирменный цвет деки по её идентификатору:
     * A — коралловый, B — бирюзовый, master — жёлтый, остальное — белый.
     *
     * Returns the signature deck color for its id:
     * A — coral, B — teal, master — yellow, anything else — white.
     *
     * @param deckId Идентификатор деки («A», «B», «master») / deck id ("A", "B", "master")
     * @return HEX-строка цвета / color HEX string
     */
    fun getDeckColor(deckId: String): String {
        return when (deckId) {
            "A" -> "#FF6B6B"
            "B" -> "#4ECDC4"
            "master" -> "#FFE66D"
            else -> "#FFFFFF"
        }
    }
}
