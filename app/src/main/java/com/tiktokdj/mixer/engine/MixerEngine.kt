package com.tiktokdj.mixer.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.tiktokdj.mixer.model.MixerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MixerEngine(context: Context) {

    val deckA = DeckPlayer(context, "A")
    val deckB = DeckPlayer(context, "B")

    private val _mixerState = MutableStateFlow(MixerState())
    val mixerState: StateFlow<MixerState> = _mixerState.asStateFlow()

    private val bpmDetector = BPMDetector()
    val effectsProcessor = EffectsProcessor()
    private val spectralAnalyzer = SpectralAnalyzer()

    private val crossfadeHandler = Handler(Looper.getMainLooper())
    private val crossfadeRunnables = CopyOnWriteArrayList<Runnable>()

    fun initialize() {
        deckA.initialize()
        deckB.initialize()
    }

    fun setCrossfader(position: Float) {
        cancelCrossfade()
        val clamped = position.coerceIn(0f, 1f)
        _mixerState.value = _mixerState.value.copy(crossfader = clamped)
        updateDeckVolumes()
    }

    fun setMasterVolume(volume: Float) {
        _mixerState.value = _mixerState.value.copy(
            masterVolume = volume.coerceIn(0f, 1f)
        )
        updateDeckVolumes()
    }

    fun toggleSync() {
        val newState = !_mixerState.value.isSyncEnabled
        _mixerState.value = _mixerState.value.copy(isSyncEnabled = newState)
        if (newState) syncBPM()
    }

    private fun syncBPM() {
        val trackA = deckA.state.value.track ?: return
        val trackB = deckB.state.value.track ?: return

        if (trackA.bpm > 0 && trackB.bpm > 0) {
            val targetBPM = trackA.bpm
            val pitchB = targetBPM / trackB.bpm
            deckB.setSpeed(pitchB)
        }
    }

    private fun updateDeckVolumes() {
        val crossfader = _mixerState.value.crossfader
        val masterVol = _mixerState.value.masterVolume

        val angle = crossfader * PI.toFloat() / 2f
        val volA = masterVol * cos(angle)
        val volB = masterVol * sin(angle)

        deckA.setVolume(volA)
        deckB.setVolume(volB)

        _mixerState.value = _mixerState.value.copy(
            deckA = deckA.state.value,
            deckB = deckB.state.value
        )
    }

    fun togglePlayPauseDeckA() {
        deckA.togglePlayPause()
        _mixerState.value = _mixerState.value.copy(deckA = deckA.state.value)
    }

    fun togglePlayPauseDeckB() {
        deckB.togglePlayPause()
        _mixerState.value = _mixerState.value.copy(deckB = deckB.state.value)
    }

    fun autoMix() {
        val stateA = deckA.state.value
        val stateB = deckB.state.value

        if (stateA.isPlaying && !stateB.isPlaying && deckB.hasTrack()) {
            val remainingA = deckA.getDurationMs() - stateA.positionMs
            if (remainingA < 15000) {
                deckB.play()
                if (_mixerState.value.isSyncEnabled) syncBPM()
                gradualCrossfade(1f, 5000)
            }
        } else if (!stateA.isPlaying && stateB.isPlaying && deckA.hasTrack()) {
            val remainingB = deckB.getDurationMs() - stateB.positionMs
            if (remainingB < 15000) {
                deckA.play()
                if (_mixerState.value.isSyncEnabled) syncBPM()
                gradualCrossfade(0f, 5000)
            }
        }
    }

    private fun cancelCrossfade() {
        crossfadeRunnables.forEach { crossfadeHandler.removeCallbacks(it) }
        crossfadeRunnables.clear()
    }

    private fun gradualCrossfade(target: Float, durationMs: Long) {
        cancelCrossfade()
        val start = _mixerState.value.crossfader
        val steps = 50
        val stepDelay = durationMs / steps

        for (i in 1..steps) {
            val progress = i.toFloat() / steps
            val position = start + (target - start) * progress

            val runnable = Runnable {
                _mixerState.value = _mixerState.value.copy(crossfader = position)
                updateDeckVolumes()
            }
            crossfadeRunnables.add(runnable)
            crossfadeHandler.postDelayed(runnable, i * stepDelay)
        }
    }

    fun updateMixerState() {
        _mixerState.value = _mixerState.value.copy(
            deckA = deckA.state.value,
            deckB = deckB.state.value
        )
    }

    fun analyzeBPM(audioData: FloatArray, sampleRate: Int): Float {
        return bpmDetector.detectBPM(audioData, sampleRate)
    }

    fun getSpectralData(): FloatArray {
        return spectralAnalyzer.getSpectrum()
    }

    fun analyzeSpectrum(frame: FloatArray): FloatArray {
        return spectralAnalyzer.analyze(frame)
    }

    fun getLeftChannelLevel(): Float {
        return deckA.state.value.volume * if (deckA.isPlaying()) 1f else 0f
    }

    fun getRightChannelLevel(): Float {
        return deckB.state.value.volume * if (deckB.isPlaying()) 1f else 0f
    }

    fun release() {
        cancelCrossfade()
        deckA.release()
        deckB.release()
    }
}
