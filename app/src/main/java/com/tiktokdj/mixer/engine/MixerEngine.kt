package com.tiktokdj.mixer.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tiktokdj.mixer.model.MixerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ядро микшера: два деки, кроссфейдер, синхронизация BPM, авто-микш и анализ спектра.
 * Mixer core: two decks, crossfader, BPM sync, auto-mix and spectrum analysis.
 *
 * Состояние публикуется через [mixerState] (StateFlow) для наблюдения из UI.
 * State is exposed via [mixerState] (StateFlow) for UI observation.
 */
class MixerEngine(context: Context) {

    companion object {
        private const val TAG = "MixerEngine"

        // Допустимый диапазон питча (скорости воспроизведения) дека.
        // Allowed pitch (playback speed) range of a deck.
        const val MIN_PITCH = 0.5f
        const val MAX_PITCH = 2.0f

        // Порог остатка трека для запуска авто-микша, мс.
        // Remaining-track threshold for starting auto-mix, ms.
        private const val AUTOMIX_THRESHOLD_MS = 15000L

        // Длительность плавного кроссфейда, мс.
        // Duration of the gradual crossfade, ms.
        private const val CROSSFADE_DURATION_MS = 5000L
    }

    val deckA = DeckPlayer(context, "A")
    val deckB = DeckPlayer(context, "B")

    private val _mixerState = MutableStateFlow(MixerState())
    val mixerState: StateFlow<MixerState> = _mixerState.asStateFlow()

    private val bpmDetector = BPMDetector()
    val effectsProcessor = EffectsProcessor()

    /**
     * Публичный доступ: анализатор передаётся в StreamManager, чтобы спектр
     * стримингового сигнала был доступен для визуализации.
     *
     * Public access: the analyzer is handed to StreamManager so the stream
     * signal's spectrum is available for visualization.
     */
    val spectralAnalyzer = SpectralAnalyzer()

    // Обработчик для пошагового кроссфейда в главном потоке.
    // Handler for step-by-step crossfade on the main thread.
    private val crossfadeHandler = Handler(Looper.getMainLooper())
    private val crossfadeRunnables = CopyOnWriteArrayList<Runnable>()

    /** Инициализация обоих деков / Initialize both decks. */
    fun initialize() {
        deckA.initialize()
        deckB.initialize()
    }

    /**
     * Устанавливает позицию кроссфейдера (0 = дека A, 1 = дека B).
     * Sets the crossfader position (0 = deck A, 1 = deck B).
     */
    fun setCrossfader(position: Float) {
        cancelCrossfade()
        val clamped = position.coerceIn(0f, 1f)
        _mixerState.value = _mixerState.value.copy(crossfader = clamped)
        updateDeckVolumes()
    }

    /**
     * Устанавливает общую громкость мастера (0..1).
     * Sets the master volume (0..1).
     */
    fun setMasterVolume(volume: Float) {
        _mixerState.value = _mixerState.value.copy(
            masterVolume = volume.coerceIn(0f, 1f)
        )
        updateDeckVolumes()
    }

    /**
     * Включает/выключает режим синхронизации BPM; при включении подгоняет темп деки B к деке A.
     * Toggles BPM sync mode; when enabled, matches deck B tempo to deck A.
     */
    fun toggleSync() {
        val newState = !_mixerState.value.isSyncEnabled
        _mixerState.value = _mixerState.value.copy(isSyncEnabled = newState)
        if (newState) syncBPM()
    }

    /**
     * Подгоняет скорость деки B так, чтобы её BPM совпал с BPM деки A.
     * Adjusts deck B speed so its BPM matches deck A's BPM.
     *
     * ИСПРАВЛЕНО: раньше коэффициент `targetBPM / trackB.bpm` мог выйти за пределы,
     * поддерживаемые [DeckPlayer.setSpeed] (например, 150/70 ≈ 2.14), и setSpeed молча
     * обрезал его — реальный темп не совпадал с целевым. Теперь значение явно
     * ограничивается диапазоном [MIN_PITCH, MAX_PITCH], а выход за пределы логируется.
     *
     * FIXED: the raw ratio `targetBPM / trackB.bpm` could exceed the range supported by
     * [DeckPlayer.setSpeed] (e.g. 150/70 ≈ 2.14) and setSpeed silently clamped it, so the
     * actual tempo drifted from the target. The value is now explicitly clamped to
     * [MIN_PITCH, MAX_PITCH] and out-of-range cases are logged as a warning.
     */
    private fun syncBPM() {
        val trackA = deckA.state.value.track ?: return
        val trackB = deckB.state.value.track ?: return

        if (trackA.bpm > 0 && trackB.bpm > 0) {
            val targetBPM = trackA.bpm
            val rawPitch = targetBPM / trackB.bpm

            // Ограничиваем коэффициент физически поддерживаемым диапазоном.
            // Clamp the ratio to the physically supported range.
            val clampedPitch = rawPitch.coerceIn(MIN_PITCH, MAX_PITCH)

            if (clampedPitch != rawPitch) {
                Log.w(
                    TAG,
                    "Питч вне диапазона, ограничен: ${"%.3f".format(rawPitch)} -> " +
                        "${"%.3f".format(clampedPitch)} " +
                        "(A=${trackA.bpm} BPM, B=${trackB.bpm} BPM) / " +
                        "Pitch out of range, clamped"
                )
            }

            deckB.setSpeed(clampedPitch)
        }
    }

    /**
     * Пересчитывает громкости деков по позиции кроссфейдера (равномерный закон по мощности).
     * Recomputes deck volumes from the crossfader position (constant-power law).
     */
    private fun updateDeckVolumes() {
        val crossfader = _mixerState.value.crossfader
        val masterVol = _mixerState.value.masterVolume

        // Косинусный/синусный закон: суммарная громкость остаётся постоянной.
        // Cosine/sine law: total perceived loudness stays constant.
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

    /** Play/Pause деки A / Deck A play-pause toggle. */
    fun togglePlayPauseDeckA() {
        deckA.togglePlayPause()
        _mixerState.value = _mixerState.value.copy(deckA = deckA.state.value)
    }

    /** Play/Pause деки B / Deck B play-pause toggle. */
    fun togglePlayPauseDeckB() {
        deckB.togglePlayPause()
        _mixerState.value = _mixerState.value.copy(deckB = deckB.state.value)
    }

    /**
     * Авто-микш: если играющий трек скоро закончится (< 15 c), запускает второй дек
     * и делает плавный переход на него.
     * Auto-mix: when the playing track is about to end (< 15 s), starts the idle deck
     * and gradually crossfades to it.
     */
    fun autoMix() {
        val stateA = deckA.state.value
        val stateB = deckB.state.value

        if (stateA.isPlaying && !stateB.isPlaying && deckB.hasTrack()) {
            val remainingA = deckA.getDurationMs() - stateA.positionMs
            if (remainingA < AUTOMIX_THRESHOLD_MS) {
                deckB.play()
                if (_mixerState.value.isSyncEnabled) syncBPM()
                gradualCrossfade(1f, CROSSFADE_DURATION_MS)
            }
        } else if (!stateA.isPlaying && stateB.isPlaying && deckA.hasTrack()) {
            val remainingB = deckB.getDurationMs() - stateB.positionMs
            if (remainingB < AUTOMIX_THRESHOLD_MS) {
                deckA.play()
                if (_mixerState.value.isSyncEnabled) syncBPM()
                gradualCrossfade(0f, CROSSFADE_DURATION_MS)
            }
        }
    }

    /** Отменяет все запланированные шаги кроссфейда / Cancels all scheduled crossfade steps. */
    private fun cancelCrossfade() {
        crossfadeRunnables.forEach { crossfadeHandler.removeCallbacks(it) }
        crossfadeRunnables.clear()
    }

    /**
     * Плавный кроссфейдер к целевой позиции за [durationMs] миллисекунд (50 шагов).
     * Gradual crossfade to the target position over [durationMs] milliseconds (50 steps).
     */
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

    /** Принудительно обновляет состояние микшера / Force-refreshes the mixer state. */
    fun updateMixerState() {
        _mixerState.value = _mixerState.value.copy(
            deckA = deckA.state.value,
            deckB = deckB.state.value
        )
    }

    /** Определяет BPM по аудиоданным / Detects BPM from audio data. */
    fun analyzeBPM(audioData: FloatArray, sampleRate: Int): Float {
        return bpmDetector.detectBPM(audioData, sampleRate)
    }

    /** Возвращает последний спектр / Returns the latest spectrum snapshot. */
    fun getSpectralData(): FloatArray {
        return spectralAnalyzer.getSpectrum()
    }

    /** Анализирует кадр и возвращает спектр / Analyzes a frame and returns the spectrum. */
    fun analyzeSpectrum(frame: FloatArray): FloatArray {
        return spectralAnalyzer.analyze(frame)
    }

    /** Уровень левого канала (дека A) / Left channel level (deck A). */
    fun getLeftChannelLevel(): Float {
        return deckA.state.value.volume * if (deckA.isPlaying()) 1f else 0f
    }

    /** Уровень правого канала (дека B) / Right channel level (deck B). */
    fun getRightChannelLevel(): Float {
        return deckB.state.value.volume * if (deckB.isPlaying()) 1f else 0f
    }

    /** Освобождает ресурсы движка / Releases engine resources. */
    fun release() {
        cancelCrossfade()
        deckA.release()
        deckB.release()
    }
}
