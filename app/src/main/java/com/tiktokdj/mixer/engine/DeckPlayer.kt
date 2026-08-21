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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

/**
 * DeckPlayer — движок воспроизведения одного деки DJ-микшера.
 * Инкапсулирует ExoPlayer, состояние деки, позицию воспроизведения,
 * hot-cue точки и простой 3-полосный эквалайзер.
 *
 * DeckPlayer — playback engine for a single DJ mixer deck.
 * Encapsulates ExoPlayer, deck state, playback position,
 * hot-cue points and a simple 3-band EQ.
 */
class DeckPlayer(
    private val context: Context,
    private val deckId: String
) {
    /**
     * Экземпляр ExoPlayer. Создаётся в [initialize], освобождается в [release].
     * Volatile, т.к. доступ возможен из разных потоков.
     *
     * ExoPlayer instance. Created in [initialize], released in [release].
     * Volatile because it may be accessed from multiple threads.
     */
    @Volatile
    private var exoPlayer: ExoPlayer? = null

    /**
     * Handler главного потока для периодического обновления позиции.
     *
     * Main-thread Handler used for periodic position updates.
     */
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Флаг активности цикла обновления позиции.
     * Предотвращает дублирующий post обновления в [play].
     *
     * Position-updater loop activity flag.
     * Prevents duplicate updater posts in [play].
     */
    @Volatile
    private var positionUpdaterRunning = false

    /**
     * Детектор BPM: анализирует PCM-данные и оценивает темп трека.
     * Сейчас используется как заглушка (см. TODO в [loadTrack]).
     *
     * BPM detector: analyzes PCM data and estimates the track tempo.
     * Currently used as a placeholder (see the TODO in [loadTrack]).
     */
    private val bpmDetector = BPMDetector()

    /**
     * Область корутин для фоновых задач (например, BPM-анализа при загрузке трека).
     * Отменяется в [release], чтобы не утечь после уничтожения деки.
     *
     * Coroutine scope for background jobs (e.g. BPM analysis on track load).
     * Cancelled in [release] so nothing leaks after the deck is destroyed.
     */
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Реактивное состояние деки (трек, play/pause, громкость, скорость, EQ, cue).
     *
     * Reactive deck state (track, play/pause, volume, speed, EQ, cue).
     */
    private val _state = MutableStateFlow(DeckState())
    val state: StateFlow<DeckState> = _state.asStateFlow()

    /**
     * Реактивная текущая позиция воспроизведения в миллисекундах.
     *
     * Reactive current playback position in milliseconds.
     */
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    /**
     * Данные формы волны трека (для визуализации).
     *
     * Track waveform data (for visualization).
     */
    private val _waveform = MutableStateFlow<List<Float>>(emptyList())
    val waveform: StateFlow<List<Float>> = _waveform.asStateFlow()

    /**
     * Список hot-cue точек деки. CopyOnWriteArrayList — потокобезопасное чтение/запись.
     *
     * Deck hot-cue list. CopyOnWriteArrayList gives thread-safe read/write.
     */
    private val hotCues = CopyOnWriteArrayList<HotCue>()

    /**
     * Текущий загруженный трек или null.
     *
     * Currently loaded track or null.
     */
    private var currentTrack: Track? = null

    /**
     * Периодический Runnable: каждые 50 мс синхронизирует [_position] и [_state]
     * с реальной позицией ExoPlayer. Останавливается, когда плеер не играет.
     *
     * Periodic Runnable: every 50 ms syncs [_position] and [_state]
     * with the real ExoPlayer position. Stops when the player is not playing.
     */
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

    /**
     * Создаёт и инициализирует ExoPlayer.
     * handleAudioBecomingNoisy — пауза при отключении наушников.
     *
     * Creates and initializes ExoPlayer.
     * handleAudioBecomingNoisy — pause when headphones are unplugged.
     */
    fun initialize() {
        exoPlayer = ExoPlayer.Builder(context)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    /**
     * Загружает новый трек в деку, сбрасывая позицию на 0.
     *
     * FIX: сбрасываем [positionUpdaterRunning] после removeCallbacks.
     * Раньше флаг оставался true, если трек грузился во время воспроизведения,
     * из-за чего последующий [play] не перезапускал обновление позиции
     * (позиция «замерзала»).
     *
     * Loads a new track into the deck, resetting position to 0.
     *
     * FIX: reset [positionUpdaterRunning] after removeCallbacks.
     * Previously the flag stayed true if a track was loaded while playing,
     * so a subsequent [play] never re-posted the updater
     * (the position display froze).
     */
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
        // FIX: сбрасываем позицию StateFlow на 0 для нового трека,
        // иначе UI показывает позицию предыдущего трека до первого тика апдейтера.
        // FIX: reset the position StateFlow to 0 for the new track,
        // otherwise the UI shows the previous track's position until the first updater tick.
        _position.value = 0L
        _state.value = _state.value.copy(
            track = track,
            positionMs = 0,
            isPlaying = false
        )

        // ЗАПУСК BPM-АНАЛИЗА: после установки состояния деки запускаем фоновую
        // корутину, которая должна определить темп трека и обновить его поле bpm.
        //
        // BPM ANALYSIS KICKOFF: once the deck state is set, launch a background
        // coroutine that is supposed to detect the track tempo and update its
        // bpm field through the state.
        //
        // TODO: реальный анализ через [bpmDetector].detectBPM требует доступа к
        //  PCM-данным трека, которые нельзя легко прочитать напрямую из URI
        //  (формат/кодек неизвестны до подготовки источника). Нужен обратный
        //  вызов аудио ExoPlayer (AudioProcessor / AnalyticsListener), чтобы
        //  накапливать сэмплы во время prepare/воспроизведения и скармливать их
        //  детектору. Пока публикуем заглушку 120.0 BPM — рабочее значение по
        //  умолчанию для UI и синхронизации BPM.
        //
        // TODO: real analysis via [bpmDetector].detectBPM requires access to the
        //  track's PCM data, which cannot easily be read straight from a URI
        //  (format/codec are unknown until the source is prepared). An ExoPlayer
        //  audio callback (AudioProcessor / AnalyticsListener) is needed to
        //  accumulate samples during prepare/playback and feed them to the
        //  detector. For now we publish a 120.0 BPM placeholder — a sane working
        //  default for both the UI and BPM sync.
        scope.launch {
            val detectedBpm = 120.0f // ЗАГЛУШКА / PLACEHOLDER
            _state.value = _state.value.copy(
                track = _state.value.track?.copy(bpm = detectedBpm)
            )
        }

        handler.removeCallbacks(positionUpdater)
        // FIX: обязательно сбрасываем флаг, чтобы следующий play()
        // гарантированно перезапустил цикл обновления позиции.
        // FIX: always reset the flag so the next play()
        // reliably restarts the position update loop.
        positionUpdaterRunning = false
    }

    /**
     * Запускает воспроизведение и цикл обновления позиции (если ещё не запущен).
     *
     * Starts playback and the position update loop (if not already running).
     */
    fun play() {
        if (_state.value.track == null) return
        exoPlayer?.play()
        _state.value = _state.value.copy(isPlaying = true)
        if (!positionUpdaterRunning) {
            positionUpdaterRunning = true
            handler.post(positionUpdater)
        }
    }

    /**
     * Приостанавливает воспроизведение.
     * Цикл обновления остановит себя сам на следующем тике.
     *
     * Pauses playback.
     * The update loop stops itself on its next tick.
     */
    fun pause() {
        exoPlayer?.pause()
        _state.value = _state.value.copy(isPlaying = false)
    }

    /**
     * Переключает play/pause.
     *
     * Toggles between play and pause.
     */
    fun togglePlayPause() {
        if (_state.value.isPlaying) pause() else play()
    }

    /**
     * Перемещает позицию воспроизведения.
     *
     * FIX: обновляем [_position] сразу, чтобы UI-слайдер и таймкод
     * реагировали мгновенно, не дожидаясь следующего тика апдейтера.
     *
     * Seeks the playback position.
     *
     * FIX: update [_position] immediately so the UI slider and timecode
     * react instantly instead of waiting for the next updater tick.
     */
    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        // FIX: мгновенно публикуем новую позицию в StateFlow.
        // FIX: publish the new position to the StateFlow immediately.
        _position.value = positionMs
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    /**
     * Устанавливает громкость деки (0..1) и применяет её с учётом EQ.
     *
     * Sets deck volume (0..1) and applies it together with the EQ.
     */
    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _state.value = _state.value.copy(volume = clamped)
        applyVolume()
    }

    /**
     * Устанавливает скорость/питч воспроизведения (0.5x..2.0x).
     *
     * Sets playback speed/pitch (0.5x..2.0x).
     */
    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        exoPlayer?.playbackParameters = PlaybackParameters(clamped)
        _state.value = _state.value.copy(speed = clamped)
    }

    /**
     * Устанавливает уровни 3-полосного EQ (каждый 0..1) и пересчитывает громкость.
     *
     * Sets 3-band EQ levels (each 0..1) and recomputes the effective volume.
     */
    fun setEQ(low: Float, mid: Float, high: Float) {
        val clampedLow = low.coerceIn(0f, 1f)
        val clampedMid = mid.coerceIn(0f, 1f)
        val clampedHigh = high.coerceIn(0f, 1f)

        _state.value = _state.value.copy(
            eq = EQState(low = clampedLow, mid = clampedMid, high = clampedHigh)
        )
        applyVolume()
    }

    /**
     * Применяет итоговую громкость к ExoPlayer: громкость деки × масштаб EQ.
     *
     * Applies the final volume to ExoPlayer: deck volume × EQ scale.
     */
    private fun applyVolume() {
        val s = _state.value
        val eq = s.eq
        val eqScale = (eq.low + eq.mid + eq.high) / 1.5f
        exoPlayer?.volume = s.volume * eqScale.coerceIn(0f, 1f)
    }

    /**
     * Ставит cue-точку в текущую позицию воспроизведения.
     *
     * Sets a cue point at the current playback position.
     */
    fun setCuePoint() {
        _state.value = _state.value.copy(
            isCueActive = true,
            cuePositionMs = _position.value
        )
    }

    /**
     * Возвращает воспроизведение к cue-точке, если она активна.
     *
     * Jumps back to the cue point if one is active.
     */
    fun jumpToCue() {
        val cue = _state.value.cuePositionMs
        if (_state.value.isCueActive) {
            seekTo(cue)
        }
    }

    /**
     * Добавляет/перезаписывает hot-cue с заданным id в текущей позиции.
     *
     * Adds/overwrites the hot-cue with the given id at the current position.
     */
    fun addHotCue(id: Int, color: String, label: String = "") {
        hotCues.removeAll { it.id == id }
        hotCues.add(HotCue(id, _position.value, color, label))
    }

    /**
     * Переходит к позиции hot-cue с заданным id (если он существует).
     *
     * Seeks to the hot-cue position with the given id (if it exists).
     */
    fun jumpToHotCue(id: Int) {
        hotCues.find { it.id == id }?.let {
            seekTo(it.positionMs)
        }
    }

    /**
     * Возвращает копию списка hot-cue точек.
     *
     * Returns a copy of the hot-cue list.
     */
    fun getHotCues(): List<HotCue> = hotCues.toList()

    /**
     * Текущая позиция воспроизведения в мс.
     *
     * Current playback position in ms.
     */
    fun getCurrentPositionMs(): Long = _position.value

    /**
     * Длительность трека в мс; 0, если длительность неизвестна.
     *
     * Track duration in ms; 0 if the duration is unknown.
     */
    fun getDurationMs(): Long {
        val duration = exoPlayer?.duration ?: 0L
        return if (duration == C.TIME_UNSET || duration < 0) 0L else duration
    }

    /**
     * Играет ли дека сейчас.
     *
     * Whether the deck is currently playing.
     */
    fun isPlaying(): Boolean = _state.value.isPlaying

    /**
     * Загружен ли в деку трек.
     *
     * Whether a track is loaded into the deck.
     */
    fun hasTrack(): Boolean = _state.value.track != null

    /**
     * Полностью освобождает ресурсы: останавливает цикл обновления,
     * отменяет фоновые корутины (BPM-анализ) и уничтожает ExoPlayer.
     *
     * Fully releases resources: stops the update loop, cancels background
     * coroutines (BPM analysis) and destroys the ExoPlayer.
     */
    fun release() {
        positionUpdaterRunning = false
        handler.removeCallbacks(positionUpdater)
        scope.cancel()
        exoPlayer?.release()
        exoPlayer = null
    }
}
