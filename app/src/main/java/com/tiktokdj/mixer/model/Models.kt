package com.tiktokdj.mixer.model

import kotlinx.serialization.Serializable

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
    val durationFormatted: String
        get() {
            val totalSec = durationMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return "%d:%02d".format(min, sec)
        }
}

@Serializable
data class DeckState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val volume: Float = 0.8f,
    val pitch: Float = 1.0f,
    val positionMs: Long = 0,
    val eq: EQState = EQState(),
    val isCueActive: Boolean = false,
    val cuePositionMs: Long = 0
)

@Serializable
data class EQState(
    val low: Float = 0.5f,
    val mid: Float = 0.5f,
    val high: Float = 0.5f
)

@Serializable
data class MixerState(
    val deckA: DeckState = DeckState(),
    val deckB: DeckState = DeckState(),
    val crossfader: Float = 0.5f,
    val masterVolume: Float = 0.8f,
    val isSyncEnabled: Boolean = false
)

@Serializable
data class StreamConfig(
    val method: StreamMethod = StreamMethod.RTMP,
    val rtmpUrl: String = "",
    val tiktokAccessToken: String = "",
    val bitrate: Int = 2500,
    val resolution: StreamResolution = StreamResolution.HD_720P,
    val fps: Int = 30,
    val enableMicrophone: Boolean = false,
    val microphoneVolume: Float = 0.5f
)

@Serializable
enum class StreamMethod {
    TIKTOK_LIVE_API,
    RTMP
}

@Serializable
enum class StreamResolution(val width: Int, val height: Int) {
    SD_480P(854, 480),
    HD_720P(1280, 720),
    FULL_HD_1080P(1920, 1080)
}

@Serializable
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val buildNumber: Int = 0
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        if (major != other.major) return major - other.major
        if (minor != other.minor) return minor - other.minor
        if (patch != other.patch) return patch - other.patch
        return buildNumber - other.buildNumber
    }

    fun isNewerThan(other: AppVersion) = this > other

    override fun toString() = "$major.$minor.$patch (build $buildNumber)"

    companion object {
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

@Serializable
data class UpdateInfo(
    val version: AppVersion,
    val downloadUrl: String,
    val changelog: String,
    val isRequired: Boolean = false,
    val publishedAt: Long = 0L
)

@Serializable
data class Sample(
    val id: String,
    val name: String,
    val category: SampleCategory,
    val uri: String,
    val durationMs: Long
)

@Serializable
enum class SampleCategory {
    DRUMS, BASS, SYNTH, VOCAL, EFFECT, OTHER
}

@Serializable
data class Effect(
    val id: String,
    val name: String,
    val type: EffectType,
    val intensity: Float = 0.5f
)

@Serializable
enum class EffectType {
    ECHO, REVERB, FLANGER, PHASER, FILTER_LOW_PASS, FILTER_HIGH_PASS,
    DISTORTION, BITCRUSHER, DELAY, PAN
}

@Serializable
data class HotCue(
    val id: Int,
    val positionMs: Long,
    val color: String,
    val label: String = ""
)
