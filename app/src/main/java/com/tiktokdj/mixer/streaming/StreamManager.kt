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

class StreamManager(private val context: Context) {

    companion object {
        private const val TAG = "StreamManager"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var tiktokStreamer: TikTokLiveStreamer? = null
    private var rtmpStreamer: RTMPStreamer? = null

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _currentMethod = MutableStateFlow<StreamMethod?>(null)
    val currentMethod: StateFlow<StreamMethod?> = _currentMethod.asStateFlow()

    private val _tiktokState = MutableStateFlow<TikTokLiveStreamer.StreamState>(TikTokLiveStreamer.StreamState.Idle)
    private val _rtmpState = MutableStateFlow<RTMPStreamer.RTMPState>(RTMPStreamer.RTMPState.Idle)

    val tiktokState: StateFlow<TikTokLiveStreamer.StreamState> = _tiktokState.asStateFlow()
    val rtmpState: StateFlow<RTMPStreamer.RTMPState> = _rtmpState.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

            val captureStarted = startAudioCapture(StreamMethod.TIKTOK_LIVE_API)
            if (!captureStarted) {
                tiktokStreamer?.stopStreaming()
                return false
            }
            _isStreaming.value = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Start TikTok stream error", e)
            false
        }
    }

    suspend fun startRTMPStream(rtmpUrl: String): Boolean {
        return try {
            _currentMethod.value = StreamMethod.RTMP
            rtmpStreamer = RTMPStreamer()

            if (!rtmpStreamer!!.connect(rtmpUrl)) return false
            if (!rtmpStreamer!!.startStreaming()) return false

            val captureStarted = startAudioCapture(StreamMethod.RTMP)
            if (!captureStarted) {
                rtmpStreamer?.disconnect()
                return false
            }
            _isStreaming.value = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Start RTMP stream error", e)
            false
        }
    }

    private fun startAudioCapture(method: StreamMethod): Boolean {
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

            while (isActive && _isStreaming.value) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val pcmData = shortsToByteArray(buffer, read)
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

    private fun shortsToByteArray(shorts: ShortArray, count: Int): ByteArray {
        val buffer = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) {
            buffer.putShort(shorts[i])
        }
        return buffer.array()
    }

    suspend fun stopStreaming() {
        val method = _currentMethod.value
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

    fun sendMixerAudio(audioData: FloatArray) {
        if (!_isStreaming.value) return
        val method = _currentMethod.value ?: return

        val pcmData = floatToShortArray(audioData)
        val byteArray = shortsToByteArray(pcmData, pcmData.size)

        when (method) {
            StreamMethod.TIKTOK_LIVE_API -> {
                scope.launch { tiktokStreamer?.pushStreamData(byteArray) }
            }
            StreamMethod.RTMP -> {
                rtmpStreamer?.sendAudioData(byteArray)
            }
        }
    }

    private fun floatToShortArray(floats: FloatArray): ShortArray {
        return ShortArray(floats.size) { i ->
            (floats[i] * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

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
