package com.tiktokdj.mixer.streaming

import android.content.Context
import android.os.Build
import android.util.Log
import com.tiktokdj.mixer.model.StreamConfig
import com.tiktokdj.mixer.model.StreamMethod
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class TikTokLiveStreamer(private val context: Context) {

    companion object {
        private const val TAG = "TikTokLive"
        private const val TIKTOK_AUTH_URL = "https://open.tiktokapis.com/v2/oauth/token/"
        private const val TIKTOK_LIVE_INIT_URL = "https://open.tiktokapis.com/v2/live/init/"
        private const val TIKTOK_LIVE_FINISH_URL = "https://open.tiktokapis.com/v2/live/finish/"
        private const val TIKTOK_LIVE_PUSH_URL = "https://open.tiktokapis.com/v2/live/push/"
    }

    private val _streamState = MutableStateFlow<StreamState>(StreamState.Idle)
    val streamState: StateFlow<StreamState> = _streamState.asStateFlow()

    private var streamServerUrl: String = ""
    private var streamKey: String = ""
    private var accessToken: String = ""
    private var roomId: String = ""
    private var scopeKey: String = ""

    private var streamingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    sealed class StreamState {
        data object Idle : StreamState()
        data object Initializing : StreamState()
        data class Connected(val serverUrl: String, val roomId: String) : StreamState()
        data class Streaming(val startTime: Long = System.currentTimeMillis()) : StreamState()
        data class Error(val message: String) : StreamState()
        data object Finished : StreamState()
    }

    suspend fun authenticate(clientKey: String, clientSecret: String): Boolean {
        return try {
            _streamState.value = StreamState.Initializing

            val params = "client_key=$clientKey&client_secret=$clientSecret&grant_type=client_credentials"
            val connection = URL(TIKTOK_AUTH_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true

            OutputStreamWriter(connection.outputStream).use { it.write(params) }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val token = extractJsonField(response, "access_token")
                if (token != null) {
                    accessToken = token
                    Log.d(TAG, "Authenticated successfully")
                    true
                } else {
                    _streamState.value = StreamState.Error("Failed to get access token")
                    false
                }
            } else {
                val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                _streamState.value = StreamState.Error("Auth failed: ${connection.responseCode} - $error")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Authentication error", e)
            _streamState.value = StreamState.Error("Auth error: ${e.message}")
            false
        }
    }

    suspend fun initLiveStream(
        title: String = "DJ Mix Live",
        quality: String = "720p",
        region: String = ""
    ): Boolean {
        return try {
            _streamState.value = StreamState.Initializing

            val params = buildString {
                append("title=$title")
                append("&quality=$quality")
                if (region.isNotEmpty()) append("&region=$region")
            }

            val connection = URL(TIKTOK_LIVE_INIT_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val body = """{"title":"$title","quality":"$quality"}"""
            OutputStreamWriter(connection.outputStream).use { it.write(body) }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                streamServerUrl = extractJsonField(response, "server_url") ?: ""
                streamKey = extractJsonField(response, "stream_key") ?: ""
                roomId = extractJsonField(response, "room_id") ?: ""

                if (streamServerUrl.isNotEmpty() && streamKey.isNotEmpty()) {
                    _streamState.value = StreamState.Connected(streamServerUrl, roomId)
                    Log.d(TAG, "Live stream initialized: room=$roomId")
                    true
                } else {
                    _streamState.value = StreamState.Error("Failed to get stream URL")
                    false
                }
            } else {
                val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown"
                _streamState.value = StreamState.Error("Init failed: $error")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Init live stream error", e)
            _streamState.value = StreamState.Error("Init error: ${e.message}")
            false
        }
    }

    suspend fun pushStreamData(audioData: ByteArray): Boolean {
        return try {
            val url = URL("$TIKTOK_LIVE_PUSH_URL?room_id=$roomId")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "audio/aac")
            connection.doOutput = true

            connection.outputStream.use { it.write(audioData) }
            connection.responseCode == 200
        } catch (e: Exception) {
            Log.e(TAG, "Push stream data error", e)
            false
        }
    }

    fun startStreaming(): Boolean {
        if (_streamState.value !is StreamState.Connected) return false

        streamingJob = scope.launch {
            _streamState.value = StreamState.Streaming()
            Log.d(TAG, "Streaming started")
        }
        return true
    }

    suspend fun stopStreaming(): Boolean {
        streamingJob?.cancel()
        streamingJob = null

        return try {
            val connection = URL(TIKTOK_LIVE_FINISH_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val body = """{"room_id":"$roomId"}"""
            OutputStreamWriter(connection.outputStream).use { it.write(body) }

            _streamState.value = StreamState.Finished
            Log.d(TAG, "Streaming stopped")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Stop streaming error", e)
            _streamState.value = StreamState.Error("Stop error: ${e.message}")
            false
        }
    }

    fun getStreamUrl(): String = "$streamServerUrl/$streamKey"

    fun isStreaming(): Boolean = _streamState.value is StreamState.Streaming

    fun cleanup() {
        scope.cancel()
    }

    private fun extractJsonField(json: String, field: String): String? {
        val pattern = """"$field"\s*:\s*"([^"]+)"""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
    }
}
