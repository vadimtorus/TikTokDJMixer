package com.tiktokdj.mixer.streaming

import android.content.Context
import android.util.Log
import com.tiktokdj.mixer.model.StreamConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RTMPStreamer(private val context: Context) {

    companion object {
        private const val TAG = "RTMPStreamer"
        private const val RTMP_PORT = 1935
        private const val CHUNK_SIZE = 4096
    }

    private val _streamState = MutableStateFlow<RTMPState>(RTMPState.Idle)
    val streamState: StateFlow<RTMPState> = _streamState.asStateFlow()

    private var socket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private var inputStream: DataInputStream? = null
    private var streamingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var transactionId = 0
    private var streamStartTime = 0L

    sealed class RTMPState {
        data object Idle : RTMPState()
        data object Connecting : RTMPState()
        data object Connected : RTMPState()
        data class Streaming(val startTime: Long = System.currentTimeMillis()) : RTMPState()
        data class Error(val message: String) : RTMPState()
        data object Disconnected : RTMPState()
    }

    suspend fun connect(rtmpUrl: String): Boolean {
        return try {
            _streamState.value = RTMPState.Connecting

            val url = URL(rtmpUrl)
            val host = url.host
            val port = if (url.port > 0) url.port else RTMP_PORT
            val path = url.path

            socket = Socket()
            socket!!.tcpNoDelay = true
            socket!!.soTimeout = 10000
            socket!!.connect(InetSocketAddress(host, port), 5000)

            outputStream = DataOutputStream(BufferedOutputStream(socket!!.getOutputStream()))
            inputStream = DataInputStream(BufferedInputStream(socket!!.getInputStream()))

            performHandshake(path)
            _streamState.value = RTMPState.Connected
            Log.d(TAG, "Connected to RTMP: $host:$port$path")
            true
        } catch (e: Exception) {
            Log.e(TAG, "RTMP connection error", e)
            _streamState.value = RTMPState.Error("Connection failed: ${e.message}")
            false
        }
    }

    private suspend fun performHandshake(path: String) {
        val dos = outputStream ?: return

        // C0 + C1
        val c0c1 = ByteArray(1536 + 1)
        c0c1[0] = 0x03 // RTMP version
        val timestamp = (System.currentTimeMillis() / 1000).toInt()
        c0c1[1] = ((timestamp shr 24) and 0xFF).toByte()
        c0c1[2] = ((timestamp shr 16) and 0xFF).toByte()
        c0c1[3] = ((timestamp shr 8) and 0xFF).toByte()
        c0c1[4] = (timestamp and 0xFF).toByte()

        // Zero
        for (i in 8..1536) c0c1[i] = 0

        withContext(Dispatchers.IO) {
            dos.write(c0c1)
            dos.flush()
        }

        // S0 + S1
        val dis = inputStream ?: return
        val s0s1 = ByteArray(1537)
        withContext(Dispatchers.IO) {
            dis.readFully(s0s1)
        }

        // C2
        val c2 = s0s1.copyOfRange(1, 1537)
        withContext(Dispatchers.IO) {
            dos.write(c2)
            dos.flush()
        }

        // Read S2
        val s2 = ByteArray(1536)
        withContext(Dispatchers.IO) {
            dis.readFully(s2)
        }

        // Send connect
        sendConnect(path)
    }

    private suspend fun sendConnect(path: String) {
        val dos = outputStream ?: return

        transactionId++
        val amf = buildAMF0 {
            writeString("connect")
            writeNumber(transactionId.toDouble())
            writeObjectStart()
            writeProperty("app", path.trimStart('/'))
            writeProperty("type", "nonprivate")
            writeProperty("flashVer", "FMLE/3.0")
            writeProperty("tcUrl", "rtmp://${outputStream.toString()}")
            writeObjectEnd()
        }

        sendChunk(3, 0x14, 0, amf)
    }

    fun startStreaming(): Boolean {
        if (_streamState.value !is RTMPState.Connected) return false

        streamingJob = scope.launch {
            _streamState.value = RTMPState.Streaming()
            Log.d(TAG, "RTMP streaming started")
        }
        return true
    }

    fun sendAudioData(data: ByteArray, timestamp: Int = 0) {
        if (_streamState.value !is RTMPState.Streaming) return

        scope.launch {
            try {
                val flvHeader = buildFLVAudioTag(data)
                sendChunk(4, 0x08, timestamp, flvHeader)
            } catch (e: Exception) {
                Log.e(TAG, "Send audio error", e)
            }
        }
    }

    fun sendVideoData(data: ByteArray, timestamp: Int = 0) {
        if (_streamState.value !is RTMPState.Streaming) return

        scope.launch {
            try {
                sendChunk(6, 0x09, timestamp, data)
            } catch (e: Exception) {
                Log.e(TAG, "Send video error", e)
            }
        }
    }

    private fun sendChunk(chunkStreamId: Int, typeId: Int, timestamp: Int, data: ByteArray) {
        val dos = outputStream ?: return

        val header = ByteBuffer.allocate(12)
            .order(ByteOrder.BIG_ENDIAN)
            .put((0x00 or (chunkStreamId and 0x3F)).toByte())
            .putInt(timestamp.coerceAtMost(0xFFFFFF))
            .putInt(data.size)
            .put(typeId.toByte())
            .putInt(0)
            .array()

        synchronized(dos) {
            dos.write(header)
            dos.write(data)
            dos.flush()
        }
    }

    private fun buildFLVAudioTag(pcmData: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(pcmData.size + 2)
            .order(ByteOrder.BIG_ENDIAN)
        buffer.put(0xAF.toByte()) // AAC, 44kHz, 16bit, stereo
        buffer.put(0x01.toByte()) // AAC raw
        buffer.put(pcmData)
        return buffer.array()
    }

    suspend fun disconnect() {
        streamingJob?.cancel()
        streamingJob = null

        try {
            outputStream?.close()
            inputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Disconnect error", e)
        }

        _streamState.value = RTMPState.Disconnected
    }

    fun isStreaming(): Boolean = _streamState.value is RTMPState.Streaming

    fun cleanup() {
        scope.cancel()
        runBlocking { disconnect() }
    }

    private class AMF0Writer {
        val buffer = ByteArrayOutputStream()

        fun writeString(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            buffer.write(0x02) // AMF0 string
            buffer.write(bytes.size shr 8)
            buffer.write(bytes.size and 0xFF)
            buffer.write(bytes)
        }

        fun writeNumber(value: Double) {
            buffer.write(0x00) // AMF0 number
            val bb = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            bb.putDouble(value)
            buffer.write(bb.array())
        }

        fun writeObjectStart() {
            buffer.write(0x03) // AMF0 object
        }

        fun writeProperty(key: String, value: String) {
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            buffer.write(keyBytes.size shr 8)
            buffer.write(keyBytes.size and 0xFF)
            buffer.write(keyBytes)
            writeString(value)
        }

        fun writeProperty(key: String, value: Double) {
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            buffer.write(keyBytes.size shr 8)
            buffer.write(keyBytes.size and 0xFF)
            buffer.write(keyBytes)
            writeNumber(value)
        }

        fun writeObjectEnd() {
            buffer.write(0x00)
            buffer.write(0x00)
            buffer.write(0x09) // Object end marker
        }

        fun toByteArray(): ByteArray = buffer.toByteArray()
    }

    private fun buildAMF0(block: AMF0Writer.() -> Unit): ByteArray {
        return AMF0Writer().apply(block).toByteArray()
    }
}
