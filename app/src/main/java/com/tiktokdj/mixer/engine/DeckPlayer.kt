package com.tiktokdj.mixer.engine

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import kotlin.math.abs

class DeckPlayer(
    private val context: Context,
    private val deckId: String
) {
    private var exoPlayer: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(DeckState())
    val state: StateFlow<DeckState> = _state.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _waveform = MutableStateFlow<List<Float>>(emptyList())
    val waveform: StateFlow<List<Float>> = _waveform.asStateFlow()

    private val hotCues = mutableListOf<HotCue>()
    private var currentTrack: Track? = null

    private val positionUpdater = object : Runnable {
        override fun run() {
            exoPlayer?.let { player ->
                _position.value = player.currentPosition
                _state.value = _state.value.copy(
                    positionMs = player.currentPosition,
                    isPlaying = player.isPlaying
                )
            }
            handler.postDelayed(this, 50)
        }
    }

    fun initialize() {
        exoPlayer = ExoPlayer.Builder(context)
            .setHandleAudioBecomingNoisy(true)
            .build()
        handler.post(positionUpdater)
    }

    fun loadTrack(track: Track) {
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
    }

    fun play() {
        exoPlayer?.play()
        _state.value = _state.value.copy(isPlaying = true)
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
        exoPlayer?.volume = clamped
        _state.value = _state.value.copy(volume = clamped)
    }

    fun setPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        exoPlayer?.playbackParameters = PlaybackParameters(clamped)
        _state.value = _state.value.copy(pitch = clamped)
    }

    fun setEQ(low: Float, mid: Float, high: Float) {
        _state.value = _state.value.copy(
            eq = EQState(
                low = low.coerceIn(0f, 1f),
                mid = mid.coerceIn(0f, 1f),
                high = high.coerceIn(0f, 1f)
            )
        )
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
        hotCues.removeIf { it.id == id }
        hotCues.add(HotCue(id, _position.value, color, label))
    }

    fun jumpToHotCue(id: Int) {
        hotCues.find { it.id == id }?.let {
            seekTo(it.positionMs)
        }
    }

    fun getHotCues(): List<HotCue> = hotCues.toList()

    fun getCurrentPositionMs(): Long = _position.value

    fun getDurationMs(): Long = exoPlayer?.duration ?: 0L

    fun isPlaying(): Boolean = _state.value.isPlaying

    fun release() {
        handler.removeCallbacks(positionUpdater)
        exoPlayer?.release()
        exoPlayer = null
    }
}
