package com.tiktokdj.mixer.streaming

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import com.tiktokdj.mixer.engine.MixerEngine
import com.tiktokdj.mixer.model.StreamConfig
import com.tiktokdj.mixer.model.StreamMethod
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StreamManager(private val context: Context) {

    companion object {
        private const val TAG = "StreamManager"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val tiktokStreamer = TikTokLiveStreamer(context)
    private val rtmpStreamer = RTMPStreamer(context)

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _currentMethod = MutableStateFlow<StreamMethod?>(null)
    val currentMethod: StateFlow<StreamMethod?> = _currentMethod.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val tiktokState: StateFlow<TikTokLiveStreamer.StreamState> = tiktokStreamer.streamState
    val rtmpState: StateFlow<RTMPStreamer.RTMPState> = rtmpStreamer.streamState

    suspend fun startTikTokStream(
        clientKey: String,
        clientSecret: String,
        title: String = "DJ Mix Live"
    ): Boolean {
        return try {
            _currentMethod.value = StreamMethod.TIKTOK_LIVE_API

            if (!tiktokStreamer.authenticate(clientKey, clientSecret)) {
                return false
            }

            if (!tiktokStreamer.initLiveStream(title)) {
                return false
            }

            if (!tiktokStreamer.startStreaming()) {
                return false
            }

            startAudioCapture(StreamMethod.TIKTOK_LIVE_API)
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

            if (!rtmpStreamer.connect(rtmpUrl)) {
                return false
            }

            if (!rtmpStreamer.startStreaming()) {
                return false
            }

            startAudioCapture(StreamMethod.RTMP)
            _isStreaming.value = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Start RTMP stream error", e)
            false
        }
    }

    private fun startAudioCapture(method: StreamMethod) {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize * 2
        )

        captureJob = scope.launch {
            val buffer = ShortArray(bufferSize / 2)
            audioRecord?.startRecording()

            while (isActive && _isStreaming.value) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val pcmData = shortsToByteArray(buffer, read)

                    when (method) {
                        StreamMethod.TIKTOK_LIVE_API -> {
                            tiktokStreamer.pushStreamData(pcmData)
                        }
                        StreamMethod.RTMP -> {
                            rtmpStreamer.sendAudioData(pcmData)
                        }
                    }
                }
            }
        }
    }

    private fun shortsToByteArray(shorts: ShortArray, count: Int): ByteArray {
        val buffer = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) {
            buffer.putShort(shorts[i])
        }
        return buffer.array()
    }

    suspend fun stopStreaming() {
        _isStreaming.value = false
        captureJob?.cancel()
        captureJob = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        when (_currentMethod.value) {
            StreamMethod.TIKTOK_LIVE_API -> tiktokStreamer.stopStreaming()
            StreamMethod.RTMP -> rtmpStreamer.disconnect()
            null -> {}
        }

        _currentMethod.value = null
        Log.d(TAG, "Streaming stopped")
    }

    fun sendMixerAudio(audioData: FloatArray) {
        if (!_isStreaming.value) return

        val pcmData = floatToShortArray(audioData)
        val byteArray = shortsToByteArray(pcmData, pcmData.size)

        when (_currentMethod.value) {
            StreamMethod.TIKTOK_LIVE_API -> {
                scope.launch { tiktokStreamer.pushStreamData(byteArray) }
            }
            StreamMethod.RTMP -> {
                rtmpStreamer.sendAudioData(byteArray)
            }
            null -> {}
        }
    }

    private fun floatToShortArray(floats: FloatArray): ShortArray {
        return ShortArray(floats.size) { i ->
            (floats[i] * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    fun cleanup() {
        scope.cancel()
        tiktokStreamer.cleanup()
        rtmpStreamer.cleanup()
    }
}
