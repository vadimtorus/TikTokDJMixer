package com.tiktokdj.mixer.engine

import android.content.Context
import com.tiktokdj.mixer.model.MixerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

class MixerEngine(context: Context) {

    val deckA = DeckPlayer(context, "A")
    val deckB = DeckPlayer(context, "B")

    private val _mixerState = MutableStateFlow(MixerState())
    val mixerState: StateFlow<MixerState> = _mixerState.asStateFlow()

    private val bpmDetector = BPMDetector()
    private val effectsProcessor = EffectsProcessor()
    private val spectralAnalyzer = SpectralAnalyzer()

    fun initialize() {
        deckA.initialize()
        deckB.initialize()
    }

    fun setCrossfader(position: Float) {
        val clamped = position.coerceIn(0f, 1f)
        _mixerState.value = _mixerState.value.copy(crossfader = clamped)
        updateDeckVolumes()
    }

    fun setMasterVolume(volume: Float) {
        _mixerState.value = _mixerState.value.copy(
            masterVolume = volume.coerceIn(0f, 1f)
        )
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
            deckB.setPitch(pitchB)
        }
    }

    private fun updateDeckVolumes() {
        val crossfader = _mixerState.value.crossfader
        val masterVol = _mixerState.value.masterVolume

        val volA = masterVol * (1f - crossfader)
        val volB = masterVol * crossfader

        deckA.setVolume(volA)
        deckB.setVolume(volB)

        _mixerState.value = _mixerState.value.copy(
            deckA = deckA.state.value,
            deckB = deckB.state.value
        )
    }

    fun startDeckA() {
        deckA.play()
        _mixerState.value = _mixerState.value.copy(deckA = deckA.state.value)
    }

    fun stopDeckA() {
        deckA.pause()
        _mixerState.value = _mixerState.value.copy(deckA = deckA.state.value)
    }

    fun startDeckB() {
        deckB.play()
        _mixerState.value = _mixerState.value.copy(deckB = deckB.state.value)
    }

    fun stopDeckB() {
        deckB.pause()
        _mixerState.value = _mixerState.value.copy(deckB = deckB.state.value)
    }

    fun autoMix() {
        val stateA = deckA.state.value
        val stateB = deckB.state.value

        if (stateA.isPlaying && !stateB.isPlaying) {
            val remainingA = (deckA.getDurationMs() - stateA.positionMs)
            if (remainingA < 15000) {
                deckB.play()
                deckB.setVolume(_mixerState.value.masterVolume)
                crossfadeToB()
            }
        } else if (!stateA.isPlaying && stateB.isPlaying) {
            val remainingB = (deckB.getDurationMs() - stateB.positionMs)
            if (remainingB < 15000) {
                deckA.play()
                deckA.setVolume(_mixerState.value.masterVolume)
                crossfadeToA()
            }
        }
    }

    private fun crossfadeToA() {
        _mixerState.value = _mixerState.value.copy(crossfader = 0f)
        updateDeckVolumes()
    }

    private fun crossfadeToB() {
        _mixerState.value = _mixerState.value.copy(crossfader = 1f)
        updateDeckVolumes()
    }

    fun analyzeBPM(audioData: FloatArray, sampleRate: Int): Float {
        return bpmDetector.detectBPM(audioData, sampleRate)
    }

    fun getSpectralData(): FloatArray {
        return spectralAnalyzer.getSpectrum()
    }

    fun getLeftChannelLevel(): Float {
        return deckA.state.value.volume * if (deckA.isPlaying()) 1f else 0f
    }

    fun getRightChannelLevel(): Float {
        return deckB.state.value.volume * if (deckB.isPlaying()) 1f else 0f
    }

    fun release() {
        deckA.release()
        deckB.release()
    }
}
