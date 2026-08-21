package com.tiktokdj.mixer.streaming

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.tiktokdj.mixer.model.StreamMethod
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Менеджер потоковой передачи аудио для TikTok Live API и RTMP.
 * Управляет захватом звука с микрофона, конвертацией PCM и отправкой данных активному стримеру.
 *
 * Audio streaming manager for TikTok Live API and RTMP.
 * Handles microphone capture, PCM conversion, and dispatching data to the active streamer.
 *
 * Использование / Usage:
 * 1. [startTikTokStream] или / or [startRTMPStream]
 * 2. [sendMixerAudio] — микс с диджей-пульта / DJ mixer output
 * 3. [stopStreaming] или / or [cleanup]
 */
class StreamManager(
    private val context: Context,

    /**
     * Процессор аудиоэффектов (опционально). Подключается извне (например,
     * из движка микшера) и применяется к каждому захваченному кадру
     * ДО конвертации в PCM-байты и отправки в эфир.
     *
     * Optional audio effects processor. Attached externally (e.g. from the
     * mixer engine) and applied to every captured frame BEFORE conversion
     * to PCM bytes and going on air.
     */
    var effectsProcessor: com.tiktokdj.mixer.engine.EffectsProcessor? = null,

    /**
     * Спектральный анализатор (опционально). Кормится захваченным сигналом
     * в цикле захвата, чтобы UI мог визуализировать спектр стрима.
     *
     * Optional spectral analyzer. Fed the captured signal inside the capture
     * loop so the UI can visualize the stream's spectrum.
     */
    var spectralAnalyzer: com.tiktokdj.mixer.engine.SpectralAnalyzer? = null
) {

    // ============================================================
    // Константы / Constants
    // ============================================================

    companion object {
        /** Тег для логирования / Tag for logging */
        private const val TAG = "StreamManager"

        /** Частота дискретизации (Гц) / Sample rate (Hz) */
        private const val SAMPLE_RATE = 44100

        /** Стерео вход / Stereo input */
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO

        /** 16-битный PCM / 16-bit PCM */
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    // ============================================================
    // Стримеры / Streamers
    // ============================================================

    /** Стример TikTok Live API / TikTok Live API streamer */
    private var tiktokStreamer: TikTokLiveStreamer? = null

    /** RTMP-стример / RTMP streamer */
    private var rtmpStreamer: RTMPStreamer? = null

    // ============================================================
    // Состояние / State
    // ============================================================

    /**
     * Внутренний флаг активности трансляции.
     * ВАЖНО: должен выставляться в `true` ДО запуска корутины захвата,
     * иначе цикл `while (_isStreaming.value)` может завершиться мгновенно (гонка состояний).
     *
     * Internal live-activity flag.
     * IMPORTANT: must be set to `true` BEFORE launching the capture coroutine,
     * otherwise the `while (_isStreaming.value)` loop may exit instantly (race condition).
     */
    private val _isStreaming = MutableStateFlow(false)

    /** Публичный флаг активности трансляции / Public live-activity flag */
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    /** Внутренний текущий метод стриминга / Internal current streaming method */
    private val _currentMethod = MutableStateFlow<StreamMethod?>(null)

    /** Публичный текущий метод стриминга / Public current streaming method */
    val currentMethod: StateFlow<StreamMethod?> = _currentMethod.asStateFlow()

    /** Внутреннее состояние TikTok-стримера / Internal TikTok streamer state */
    private val _tiktokState = MutableStateFlow<TikTokLiveStreamer.StreamState>(TikTokLiveStreamer.StreamState.Idle)

    /** Внутреннее состояние RTMP-стримера / Internal RTMP streamer state */
    private val _rtmpState = MutableStateFlow<RTMPStreamer.RTMPState>(RTMPStreamer.RTMPState.Idle)

    /** Публичное состояние TikTok-стримера / Public TikTok streamer state */
    val tiktokState: StateFlow<TikTokLiveStreamer.StreamState> = _tiktokState.asStateFlow()

    /** Публичное состояние RTMP-стримера / Public RTMP streamer state */
    val rtmpState: StateFlow<RTMPStreamer.RTMPState> = _rtmpState.asStateFlow()

    // ============================================================
    // Аудио-захват / Audio capture
    // ============================================================

    /** Экземпляр AudioRecord для чтения с микрофона / AudioRecord instance for mic input */
    private var audioRecord: AudioRecord? = null

    /** Корутина цикла захвата / Capture-loop coroutine */
    private var captureJob: Job? = null

    /** Область корутин для фоновых операций / Coroutine scope for background work */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * ThreadLocal ByteBuffer для конвертации Short -> Byte (little-endian).
     * Потокобезопасно: у каждого потока свой буфер.
     *
     * ThreadLocal ByteBuffer for Short -> Byte conversion (little-endian).
     * Thread-safe: each thread gets its own buffer.
     */
    private val byteBufferThreadLocal = object : ThreadLocal<ByteBuffer>() {
        override fun initialValue(): ByteBuffer =
            ByteBuffer.allocate(SAMPLE_RATE * 2).order(ByteOrder.LITTLE_ENDIAN)
    }

    // ============================================================
    // Запуск трансляций / Stream startup
    // ============================================================

    /**
     * Запускает трансляцию через TikTok Live API.
     * Starts a stream via the TikTok Live API.
     *
     * @param clientKey Ключ клиента TikTok / TikTok client key
     * @param clientSecret Секрет клиента TikTok / TikTok client secret
     * @param title Название трансляции / Stream title
     * @return true при успехе / true on success
     */
    suspend fun startTikTokStream(
        clientKey: String,
        clientSecret: String,
        title: String = "DJ Mix Live"
    ): Boolean {
        return try {
            _currentMethod.value = StreamMethod.TIKTOK_LIVE_API
            tiktokStreamer = TikTokLiveStreamer(context)

            if (!tiktokStreamer!!.authenticate(clientKey, clientSecret)) return false
            if (!tiktokStreamer!!.initLiveStream(title)) return false
            if (!tiktokStreamer!!.startStreaming()) return false

            // ИСПРАВЛЕНИЕ (гонка _isStreaming): флаг выставляется ДО запуска захвата.
            // Раньше он ставился после startAudioCapture(), из-за чего цикл
            // `while (isActive && _isStreaming.value)` мог выйти сразу же,
            // не прочитав ни одного байта аудио.
            //
            // FIX (_isStreaming race): the flag is set BEFORE starting capture.
            // Previously it was set after startAudioCapture(), so the loop
            // `while (isActive && _isStreaming.value)` could exit instantly
            // without reading any audio bytes.
            _isStreaming.value = true

            val captureStarted = startAudioCapture(StreamMethod.TIKTOK_LIVE_API)
            if (!captureStarted) {
                // Откат: сбрасываем флаг и останавливаем стример
                // Rollback: reset flag and stop the streamer
                _isStreaming.value = false
                tiktokStreamer?.stopStreaming()
                return false
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Start TikTok stream error", e)
            // Гарантируем сброс флага при любом исключении
            // Guarantee flag reset on any exception
            _isStreaming.value = false
            false
        }
    }

    /**
     * Запускает трансляцию через RTMP.
     * Starts a stream via RTMP.
     *
     * @param rtmpUrl URL RTMP-сервера / RTMP server URL
     * @return true при успехе / true on success
     */
    suspend fun startRTMPStream(rtmpUrl: String): Boolean {
        return try {
            _currentMethod.value = StreamMethod.RTMP
            rtmpStreamer = RTMPStreamer()

            if (!rtmpStreamer!!.connect(rtmpUrl)) return false
            if (!rtmpStreamer!!.startStreaming()) return false

            // ИСПРАВЛЕНИЕ (гонка _isStreaming): см. комментарий в startTikTokStream —
            // флаг должен быть установлен до запуска цикла захвата.
            //
            // FIX (_isStreaming race): see comment in startTikTokStream —
            // the flag must be set before the capture loop starts.
            _isStreaming.value = true

            val captureStarted = startAudioCapture(StreamMethod.RTMP)
            if (!captureStarted) {
                // Откат: сбрасываем флаг и отключаемся от сервера
                // Rollback: reset flag and disconnect from the server
                _isStreaming.value = false
                rtmpStreamer?.disconnect()
                return false
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Start RTMP stream error", e)
            // Гарантируем сброс флага при любом исключении
            // Guarantee flag reset on any exception
            _isStreaming.value = false
            false
        }
    }

    // ============================================================
    // Цикл захвата / Capture loop
    // ============================================================

    /**
     * Инициализирует AudioRecord и запускает фоновый цикл чтения микрофона.
     * Вызывать ПОСЛЕ установки `_isStreaming.value = true`.
     *
     * Initializes AudioRecord and starts the background mic-reading loop.
     * Call AFTER setting `_isStreaming.value = true`.
     *
     * Конвейер обработки каждого кадра / Per-frame processing pipeline:
     * mic (Short) -> float [-1..1] -> effects -> spectrum analysis -> PCM bytes -> streamer.
     *
     * @param method Метод доставки данных / Data delivery method
     * @return true если захват запущен / true if capture started
     */
    private fun startAudioCapture(method: StreamMethod): Boolean {
        // Минимально допустимый размер буфера для данной конфигурации
        // Minimum allowed buffer size for this configuration
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize <= 0) {
            Log.e(TAG, "Invalid buffer size: $bufferSize")
            return false
        }

        audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Microphone permission not granted", e)
            null
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            audioRecord?.release()
            audioRecord = null
            return false
        }

        captureJob = scope.launch {
            val buffer = ShortArray(bufferSize / 2)
            audioRecord?.startRecording()

            // Цикл работает, пока корутина активна и трансляция не остановлена.
            // Loop runs while the coroutine is active and streaming hasn't stopped.
            while (isActive && _isStreaming.value) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    // ШАГ 1: нормализуем 16-битные сэмплы во float [-1..1].
                    // DSP-обработка (эффекты, спектральный анализ) работает во float,
                    // поэтому сначала приводим целочисленный PCM к нормализованному виду.
                    //
                    // STEP 1: normalize the 16-bit samples into float [-1..1].
                    // DSP processing (effects, spectral analysis) operates on floats,
                    // so we first bring the integer PCM into normalized form.
                    val floatData = FloatArray(read) { i -> buffer[i] / Short.MAX_VALUE.toFloat() }

                    // ШАГ 2: применяем цепочку активных аудиоэффектов (эхо, фильтры,
                    // дисторшн и т.д.), если процессор подключён. Если процессора нет
                    // (null) — сигнал проходит без изменений.
                    //
                    // STEP 2: apply the chain of active audio effects (echo, filters,
                    // distortion, etc.) if a processor is attached. With no processor
                    // (null) the signal passes through untouched.
                    val processed = effectsProcessor?.process(floatData, SAMPLE_RATE) ?: floatData

                    // ШАГ 3: обновляем спектр для визуализации (32 полосы),
                    // если анализатор подключён. Анализируем входной (до эффектов)
                    // сигнал, чтобы спектр отражал исходный микрофонный звук.
                    //
                    // STEP 3: refresh the visualization spectrum (32 bands) if an
                    // analyzer is attached. We analyze the pre-effects input so the
                    // spectrum reflects the raw microphone signal.
                    spectralAnalyzer?.analyze(floatData)

                    // ШАГ 4: конвертируем ОБРАБОТАННЫЕ сэмплы обратно в 16-битные
                    // little-endian байты PCM и отправляем активному стримеру.
                    //
                    // STEP 4: convert the PROCESSED samples back into 16-bit
                    // little-endian PCM bytes and dispatch them to the active streamer.
                    val pcmData = shortsToByteArray(floatToShortArray(processed), read)
                    when (method) {
                        StreamMethod.TIKTOK_LIVE_API -> {
                            tiktokStreamer?.pushStreamData(pcmData)
                        }
                        StreamMethod.RTMP -> {
                            rtmpStreamer?.sendAudioData(pcmData)
                        }
                    }
                }
            }
        }
        return true
    }

    // ============================================================
    // Остановка / Shutdown
    // ============================================================

    /**
     * Полностью останавливает трансляцию: цикл захвата, микрофон и активного стримера.
     * Fully stops the stream: capture loop, microphone, and the active streamer.
     */
    suspend fun stopStreaming() {
        val method = _currentMethod.value

        // Сначала сбрасываем флаг — это условие выхода цикла захвата
        // Reset the flag first — it's the capture loop's exit condition
        _isStreaming.value = false

        captureJob?.cancelAndJoin()
        captureJob = null

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord stop error", e)
        }
        audioRecord?.release()
        audioRecord = null

        when (method) {
            StreamMethod.TIKTOK_LIVE_API -> tiktokStreamer?.stopStreaming()
            StreamMethod.RTMP -> rtmpStreamer?.disconnect()
            null -> {}
        }
        _currentMethod.value = null

        Log.d(TAG, "Streaming stopped")
    }

    // ============================================================
    // Отправка микса / Mixer audio dispatch
    // ============================================================

    /**
     * Отправляет аудиоданные с диджей-пульта в активную трансляцию.
     * Sends DJ-mixer audio data into the active stream.
     *
     * ИСПРАВЛЕНИЕ (порядок пакетов): функция теперь suspend и вызывает
     * [TikTokLiveStreamer.pushStreamData] напрямую вместо `scope.launch` на каждый чанк.
     * Отдельная корутина на чанк могла выполняться не по порядку и ломать поток.
     * Прямой вызов гарантирует строгую последовательность отправки.
     *
     * FIX (packet ordering): the function is now suspend and calls
     * [TikTokLiveStreamer.pushStreamData] directly instead of `scope.launch` per chunk.
     * A separate coroutine per chunk could execute out of order and corrupt the stream.
     * A direct call guarantees strict sequential sending.
     *
     * @param audioData PCM-сэмплы float [-1..1] / float PCM samples [-1..1]
     */
    suspend fun sendMixerAudio(audioData: FloatArray) {
        if (!_isStreaming.value) return
        val method = _currentMethod.value ?: return

        val pcmData = floatToShortArray(audioData)
        val byteArray = shortsToByteArray(pcmData, pcmData.size)

        when (method) {
            StreamMethod.TIKTOK_LIVE_API -> {
                // Прямой suspend-вызов: без лишней корутины, порядок сохранён
                // Direct suspend call: no extra coroutine, order preserved
                tiktokStreamer?.pushStreamData(byteArray)
            }
            StreamMethod.RTMP -> {
                rtmpStreamer?.sendAudioData(byteArray)
            }
        }
    }

    // ============================================================
    // Конвертация / Conversion helpers
    // ============================================================

    /**
     * Конвертирует ShortArray в ByteArray (little-endian) через ThreadLocal-буфер.
     * Converts a ShortArray to a ByteArray (little-endian) via a ThreadLocal buffer.
     *
     * @param shorts Входные сэмплы / Input samples
     * @param count Кол-во валидных сэмплов / Number of valid samples
     * @return Байты PCM / PCM bytes
     */
    private fun shortsToByteArray(shorts: ShortArray, count: Int): ByteArray {
        val buffer = byteBufferThreadLocal.get()!!
        buffer.clear()
        for (i in 0 until count) {
            buffer.putShort(shorts[i])
        }
        val result = ByteArray(count * 2)
        buffer.flip()
        buffer.get(result)
        return result
    }

    /**
     * Конвертирует float [-1..1] в 16-битный PCM с ограничением диапазона.
     * Converts float [-1..1] to 16-bit PCM with range clamping.
     *
     * Используется и в [sendMixerAudio], и в цикле захвата после эффектов.
     * Used both by [sendMixerAudio] and by the capture loop after effects.
     *
     * @param floats Входные сэмплы / Input samples
     * @return 16-битные сэмплы / 16-bit samples
     */
    private fun floatToShortArray(floats: FloatArray): ShortArray {
        return ShortArray(floats.size) { i ->
            (floats[i] * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    // ============================================================
    // Освобождение ресурсов / Resource cleanup
    // ============================================================

    /**
     * Освобождает все ресурсы менеджера. Вызывается из onDestroy.
     * Releases all manager resources. Called from onDestroy.
     *
     * ОГРАНИЧЕНИЕ: здесь используется runBlocking, потому что onDestroy
     * не является suspend-функцией. Это БЛОКИРУЕТ поток вызывающего
     * (обычно main) на время остановки трансляции. Для UI-отзывчивости
     * предпочтительнее вызывать stopStreaming() заранее из lifecycleScope,
     * а cleanup() оставить как последний страховочный вариант.
     *
     * LIMITATION: runBlocking is used here because onDestroy is not a
     * suspend function. This BLOCKS the caller thread (usually main)
     * for the duration of the shutdown. For UI responsiveness prefer
     * calling stopStreaming() earlier from lifecycleScope and keep
     * cleanup() as a final safety net.
     */
    fun cleanup() {
        runBlocking {
            if (_isStreaming.value) {
                stopStreaming()
            }
        }
        tiktokStreamer?.cleanup()
        rtmpStreamer?.cleanup()
        scope.cancel()
    }
}
