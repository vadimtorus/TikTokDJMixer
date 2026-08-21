package com.tiktokdj.mixer.model

import kotlinx.serialization.Serializable

/**
 * Трек музыкальной библиотеки: метаданные и ссылка на источник.
 *
 * A music-library track: metadata and a reference to its source.
 *
 * @property id Уникальный идентификатор / unique identifier
 * @property title Название трека / track title
 * @property artist Исполнитель / performing artist
 * @property uri Строка URI источника (файл/контент-провайдер) / source URI string (file/content provider)
 * @property durationMs Длительность в миллисекундах / duration in milliseconds
 * @property bpm Темп трека; 0 = не определён / track tempo; 0 = undetermined
 * @property key Музыкальная тональность / musical key
 * @property waveform Сэмплы формы волны для отрисовки / waveform samples for drawing
 */
@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val uri: String,
    val durationMs: Long,
    val bpm: Float = 0f,
    val key: String = "",
    val waveform: List<Float> = emptyList()
) {
    /**
     * Длительность в формате «M:SS» (например, «3:07»).
     * Duration formatted as "M:SS" (e.g. "3:07").
     */
    val durationFormatted: String
        get() {
            val totalSec = durationMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return "%d:%02d".format(min, sec)
        }
}

/**
 * Реактивное состояние одного деки: загруженный трек, transport,
 * громкость, питч, эквалайзер и cue-точка.
 *
 * Reactive state of a single deck: loaded track, transport,
 * volume, pitch, equalizer and cue point.
 *
 * @property track Загруженный трек или null / loaded track or null
 * @property isPlaying Играет ли дека / whether the deck is playing
 * @property volume Громкость 0..1 / volume 0..1
 * @property speed Питч (скорость) 0.5..2.0 / pitch (speed) 0.5..2.0
 * @property positionMs Текущая позиция в мс / current position in ms
 * @property eq Состояние 3-полосного EQ / 3-band EQ state
 * @property isCueActive Активна ли cue-точка / whether a cue point is active
 * @property cuePositionMs Позиция cue-точки в мс / cue point position in ms
 */
@Serializable
data class DeckState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val volume: Float = 0.8f,
    val speed: Float = 1.0f,
    val positionMs: Long = 0,
    val eq: EQState = EQState(),
    val isCueActive: Boolean = false,
    val cuePositionMs: Long = 0
)

/**
 * Состояние 3-полосного эквалайзера; каждое значение 0..1,
 * где 0.5 — нейтральная середина.
 *
 * 3-band equalizer state; every value is 0..1 with 0.5 as neutral center.
 *
 * @property low Низкие частоты / low band
 * @property mid Средние частоты / mid band
 * @property high Высокие частоты / high band
 */
@Serializable
data class EQState(
    val low: Float = 0.5f,
    val mid: Float = 0.5f,
    val high: Float = 0.5f
)

/**
 * Агрегированное состояние всего микшера: оба деки, кроссфейдер,
 * мастер-громкость и флаг синхронизации BPM.
 *
 * Aggregated state of the whole mixer: both decks, crossfader,
 * master volume and the BPM-sync flag.
 *
 * @property deckA Состояние деки A / deck A state
 * @property deckB Состояние деки B / deck B state
 * @property crossfader Позиция кроссфейдера 0..1 (0 = A, 1 = B) / crossfader position 0..1 (0 = A, 1 = B)
 * @property masterVolume Общая громкость 0..1 / master volume 0..1
 * @property isSyncEnabled Включена ли синхронизация BPM / whether BPM sync is enabled
 */
@Serializable
data class MixerState(
    val deckA: DeckState = DeckState(),
    val deckB: DeckState = DeckState(),
    val crossfader: Float = 0.5f,
    val masterVolume: Float = 0.8f,
    val isSyncEnabled: Boolean = false
)

/**
 * Способ доставки стрима: официальный TikTok Live API или прямой RTMP.
 *
 * Stream delivery method: the official TikTok Live API or plain RTMP.
 */
@Serializable
enum class StreamMethod {
    /** Официальный TikTok Live API / official TikTok Live API */
    TIKTOK_LIVE_API,

    /** Прямая RTMP-трансляция / direct RTMP broadcast */
    RTMP
}

/**
 * Разрешение стримингового видео с фиксированными размерами кадра.
 *
 * Streaming video resolution with fixed frame dimensions.
 *
 * @property width Ширина кадра / frame width
 * @property height Высота кадра / frame height
 */
@Serializable
enum class StreamResolution(val width: Int, val height: Int) {
    /** 854×480 / 854×480 */
    SD_480P(854, 480),

    /** 1280×720 / 1280×720 */
    HD_720P(1280, 720),

    /** 1920×1080 / 1920×1080 */
    FULL_HD_1080P(1920, 1080)
}

/**
 * Версия приложения с семантическим сравнением: major.minor.patch (build N).
 *
 * App version with semantic comparison: major.minor.patch (build N).
 *
 * Приоритет сравнения / comparison precedence:
 * major > minor > patch > buildNumber.
 */
@Serializable
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val buildNumber: Int = 0
) : Comparable<AppVersion> {
    /**
     * Поэлементное сравнение версий от старшей компоненты к младшей.
     * Component-wise version comparison from the most to least significant.
     */
    override fun compareTo(other: AppVersion): Int {
        if (major != other.major) return major - other.major
        if (minor != other.minor) return minor - other.minor
        if (patch != other.patch) return patch - other.patch
        return buildNumber - other.buildNumber
    }

    /** Новее ли эта версия, чем [other] / whether this version is newer than [other]. */
    fun isNewerThan(other: AppVersion) = this > other

    /** Читаемое представление «major.minor.patch (build N)» / human-readable form. */
    override fun toString() = "$major.$minor.$patch (build $buildNumber)"

    companion object {
        /**
         * Разбирает строку версии вида «1.2.3» или «1.2.3 (build 45)».
         * Отсутствующие компоненты считаются нулями; мусор — тоже 0.
         *
         * Parses a version string like "1.2.3" or "1.2.3 (build 45)".
         * Missing components default to zero; garbage also becomes 0.
         */
        fun parse(versionString: String): AppVersion {
            val parts = versionString.replace("build", "").split(".", "(", " ").filter { it.isNotBlank() }
            return AppVersion(
                major = parts.getOrElse(0) { "0" }.trim().toIntOrNull() ?: 0,
                minor = parts.getOrElse(1) { "0" }.trim().toIntOrNull() ?: 0,
                patch = parts.getOrElse(2) { "0" }.trim().toIntOrNull() ?: 0,
                buildNumber = parts.getOrElse(3) { "0" }.trim().toIntOrNull() ?: 0
            )
        }
    }
}

/**
 * Информация о доступном обновлении приложения.
 *
 * Information about an available app update.
 *
 * @property version Версия обновления / update version
 * @property downloadUrl Прямая ссылка на APK / direct APK link
 * @property changelog Список изменений / release notes
 * @property isRequired Обязательно ли обновление / whether the update is mandatory
 * @property publishedAt Время публикации (epoch ms) / publish time (epoch ms)
 */
@Serializable
data class UpdateInfo(
    val version: AppVersion,
    val downloadUrl: String,
    val changelog: String,
    val isRequired: Boolean = false,
    val publishedAt: Long = 0L
)

/**
 * Экземпляр аудиоэффекта: тип, имя и интенсивность обработки.
 *
 * An audio effect instance: type, name and processing intensity.
 *
 * @property id Идентификатор эффекта / effect identifier
 * @property name Отображаемое имя / display name
 * @property type Тип эффекта (ключ цепочки) / effect type (chain key)
 * @property intensity Интенсивность 0..1 / intensity 0..1
 */
@Serializable
data class Effect(
    val id: String,
    val name: String,
    val type: EffectType,
    val intensity: Float = 0.5f
)

/**
 * Каталог поддерживаемых аудиоэффектов DJ-микшера.
 *
 * Catalog of audio effects supported by the DJ mixer.
 */
@Serializable
enum class EffectType {
    /** Эхо / echo */
    ECHO,

    /** Реверберация / reverb */
    REVERB,

    /** Флэнжер / flanger */
    FLANGER,

    /** Фейзер / phaser */
    PHASER,

    /** Фильтр нижних частот / low-pass filter */
    FILTER_LOW_PASS,

    /** Фильтр верхних частот / high-pass filter */
    FILTER_HIGH_PASS,

    /** Дисторшн / distortion */
    DISTORTION,

    /** Биткрашер / bitcrusher */
    BITCRUSHER,

    /** Задержка / delay */
    DELAY,

    /** Панорама / panning */
    PAN
}

/**
 * Hot-cue точка деки: быстрый переход к сохранённой позиции.
 *
 * Deck hot-cue point: quick jump to a saved position.
 *
 * @property id Номер hot-cue (кнопка) / hot-cue number (button)
 * @property positionMs Сохранённая позиция в мс / saved position in ms
 * @property color Цвет маркера в UI / marker color in the UI
 * @property label Необязательная подпись / optional label
 */
@Serializable
data class HotCue(
    val id: Int,
    val positionMs: Long,
    val color: String,
    val label: String = ""
)
