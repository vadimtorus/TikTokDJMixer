package com.tiktokdj.mixer.engine

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import com.tiktokdj.mixer.model.DeckState
import com.tiktokdj.mixer.model.EQState
import com.tiktokdj.mixer.model.HotCue
import com.tiktokdj.mixer.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

class DeckPlayer(
    private val context: Context,
    private val deckId: String
) {
    @Volatile
    private var exoPlayer: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    @Volatile
    private var positionUpdaterRunning = false

    private val _state = MutableStateFlow(DeckState())
    val state: StateFlow<DeckState> = _state.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _waveform = MutableStateFlow<List<Float>>(emptyList())
    val waveform: StateFlow<List<Float>> = _waveform.asStateFlow()

    private val hotCues = CopyOnWriteArrayList<HotCue>()
    private var currentTrack: Track? = null

    private val positionUpdater = object : Runnable {
        override fun run() {
            exoPlayer?.let { player ->
                val pos = player.currentPosition
                _position.value = pos
                _state.value = _state.value.copy(
                    positionMs = pos,
                    isPlaying = player.isPlaying
                )
            }
            if (exoPlayer != null && _state.value.isPlaying) {
                handler.postDelayed(this, 50)
            } else {
                positionUpdaterRunning = false
            }
        }
    }

    fun initialize() {
        exoPlayer = ExoPlayer.Builder(context)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    fun loadTrack(track: Track) {
        if (exoPlayer?.isPlaying == true) {
            exoPlayer?.stop()
        }
        currentTrack = track
        val mediaItem = MediaItem.fromUri(Uri.parse(track.uri))
        exoPlayer?.apply {
            setMediaItem(mediaItem)
            prepare()
        }
        _state.value = _state.value.copy(
            track = track,
            positionMs = 0,
            isPlaying = false
        )
        handler.removeCallbacks(positionUpdater)
    }

    fun play() {
        if (_state.value.track == null) return
        exoPlayer?.play()
        _state.value = _state.value.copy(isPlaying = true)
        if (!positionUpdaterRunning) {
            positionUpdaterRunning = true
            handler.post(positionUpdater)
        }
    }

    fun pause() {
        exoPlayer?.pause()
        _state.value = _state.value.copy(isPlaying = false)
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _state.value = _state.value.copy(volume = clamped)
        applyVolume()
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        exoPlayer?.playbackParameters = PlaybackParameters(clamped)
        _state.value = _state.value.copy(speed = clamped)
    }

    fun setEQ(low: Float, mid: Float, high: Float) {
        val clampedLow = low.coerceIn(0f, 1f)
        val clampedMid = mid.coerceIn(0f, 1f)
        val clampedHigh = high.coerceIn(0f, 1f)

        _state.value = _state.value.copy(
            eq = EQState(low = clampedLow, mid = clampedMid, high = clampedHigh)
        )
        applyVolume()
    }

    private fun applyVolume() {
        val s = _state.value
        val eq = s.eq
        val eqScale = (eq.low + eq.mid + eq.high) / 1.5f
        exoPlayer?.volume = s.volume * eqScale.coerceIn(0f, 1f)
    }

    fun setCuePoint() {
        _state.value = _state.value.copy(
            isCueActive = true,
            cuePositionMs = _position.value
        )
    }

    fun jumpToCue() {
        val cue = _state.value.cuePositionMs
        if (_state.value.isCueActive) {
            seekTo(cue)
        }
    }

    fun addHotCue(id: Int, color: String, label: String = "") {
        hotCues.removeAll { it.id == id }
        hotCues.add(HotCue(id, _position.value, color, label))
    }

    fun jumpToHotCue(id: Int) {
        hotCues.find { it.id == id }?.let {
            seekTo(it.positionMs)
        }
    }

    fun getHotCues(): List<HotCue> = hotCues.toList()

    fun getCurrentPositionMs(): Long = _position.value

    fun getDurationMs(): Long {
        val duration = exoPlayer?.duration ?: 0L
        return if (duration == C.TIME_UNSET || duration < 0) 0L else duration
    }

    fun isPlaying(): Boolean = _state.value.isPlaying

    fun hasTrack(): Boolean = _state.value.track != null

    fun release() {
        positionUpdaterRunning = false
        handler.removeCallbacks(positionUpdater)
        exoPlayer?.release()
        exoPlayer = null
    }
}
