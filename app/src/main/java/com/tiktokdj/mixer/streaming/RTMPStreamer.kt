package com.tiktokdj.mixer.streaming

import android.util.Log
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
import kotlin.random.Random

class RTMPStreamer {

    companion object {
        private const val TAG = "RTMPStreamer"
        private const val RTMP_PORT = 1935
        private const val RTMP_CHUNK_SIZE = 128
    }

    private val _streamState = MutableStateFlow<RTMPState>(RTMPState.Idle)
    val streamState: StateFlow<RTMPState> = _streamState.asStateFlow()

    private var socket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private var inputStream: DataInputStream? = null
    private var streamingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var transactionId = 0
    private var rtmpUrl: String = ""
    private var chunkSize = RTMP_CHUNK_SIZE

    sealed class RTMPState {
        data object Idle : RTMPState()
        data object Connecting : RTMPState()
        data object Connected : RTMPState()
        data class Streaming(val startTime: Long = System.currentTimeMillis()) : RTMPState()
        data class Error(val message: String) : RTMPState()
        data object Disconnected : RTMPState()
    }

    suspend fun connect(url: String): Boolean {
        return try {
            _streamState.value = RTMPState.Connecting
            rtmpUrl = url

            val parsedUrl = URL(url)
            val host = parsedUrl.host
            val port = if (parsedUrl.port > 0) parsedUrl.port else RTMP_PORT
            val path = parsedUrl.path

            socket = Socket()
            socket!!.tcpNoDelay = true
            socket!!.soTimeout = 10000
            socket!!.connect(InetSocketAddress(host, port), 5000)

            outputStream = DataOutputStream(BufferedOutputStream(socket!!.getOutputStream()))
            inputStream = DataInputStream(BufferedInputStream(socket!!.getInputStream()))

            performHandshake()
            sendConnect(path, host, port)
            waitForConnectResponse()

            _streamState.value = RTMPState.Connected
            Log.d(TAG, "Connected to RTMP: $host:$port$path")
            true
        } catch (e: Exception) {
            Log.e(TAG, "RTMP connection error", e)
            _streamState.value = RTMPState.Error("Connection failed: ${e.message}")
            disconnect()
            false
        }
    }

    private suspend fun performHandshake() {
        val dos = outputStream ?: return
        val dis = inputStream ?: return

        val c0c1 = ByteArray(1537)
        c0c1[0] = 0x03
        val timestamp = (System.currentTimeMillis() / 1000).toInt()
        c0c1[1] = ((timestamp shr 24) and 0xFF).toByte()
        c0c1[2] = ((timestamp shr 16) and 0xFF).toByte()
        c0c1[3] = ((timestamp shr 8) and 0xFF).toByte()
        c0c1[4] = (timestamp and 0xFF).toByte()
        for (i in 8 until c0c1.size) {
            c0c1[i] = Random.nextInt(1, 255).toByte()
        }

        dos.write(c0c1)
        dos.flush()

        val s0s1 = ByteArray(1537)
        dis.readFully(s0s1)

        dos.write(s0s1, 1, 1536)
        dos.flush()

        val s2 = ByteArray(1536)
        dis.readFully(s2)
    }

    private suspend fun sendConnect(path: String, host: String, port: Int) {
        transactionId++
        val amf = buildAMF0 {
            writeString("connect")
            writeNumber(transactionId.toDouble())
            writeObjectStart()
            writeProperty("app", path.trimStart('/'))
            writeProperty("type", "nonprivate")
            writeProperty("flashVer", "FMLE/3.0")
            writeProperty("tcUrl", "rtmp://$host:$port$path")
            writeObjectEnd()
        }
        sendChunk(3, 0x14, 0, amf)
    }

    private suspend fun waitForConnectResponse() {
        val dis = inputStream ?: return
        val dos = outputStream ?: return

        var attempts = 0
        while (attempts < 10) {
            val basicHeader = dis.readUnsignedByte()
            val fmt = (basicHeader shr 6) and 0x03
            var csid = basicHeader and 0x3F

            if (csid == 0) csid = dis.readUnsignedByte() + 64
            else if (csid == 1) csid = dis.readUnsignedByte() + dis.readUnsignedByte() * 256 + 64

            val timestamp = dis.readUnsignedByte() shl 16 or
                    (dis.readUnsignedByte() shl 8) or
                    dis.readUnsignedByte()
            val messageLength = dis.readUnsignedByte() shl 16 or
                    (dis.readUnsignedByte() shl 8) or
                    dis.readUnsignedByte()
            val messageType = dis.readUnsignedByte()
            val messageStreamId = dis.readInt()

            val payload = ByteArray(messageLength)
            dis.readFully(payload)

            if (messageType == 0x14) {
                val amfData = ByteBuffer.wrap(payload)
                val marker = amfData.get().toInt() and 0xFF
                if (marker == 0x02) {
                    val strLen = amfData.getShort().toInt() and 0xFFFF
                    val strBytes = ByteArray(strLen)
                    amfData.get(strBytes)
                    val command = String(strBytes)
                    Log.d(TAG, "Connect response: $command")

                    if (command == "_result") {
                        amfData.get()
                        val txId = amfData.double.toInt()
                        amfData.get()
                        val properties = readAMF0Object(amfData)
                        val chunkSizeServer = properties["chunkSize"] as? Double
                        if (chunkSizeServer != null) {
                            chunkSize = chunkSizeServer.toInt()
                            setChunkSize(chunkSize)
                        }
                        return
                    } else if (command == "_error") {
                        throw IOException("RTMP connect rejected by server")
                    }
                }
            }
            attempts++
        }
        Log.w(TAG, "Connect response timeout, proceeding anyway")
    }

    private fun readAMF0Object(buffer: ByteBuffer): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        while (buffer.hasRemaining()) {
            val keyLen = buffer.short.toInt() and 0xFFFF
            if (keyLen == 0) break
            val keyBytes = ByteArray(keyLen)
            buffer.get(keyBytes)
            val key = String(keyBytes)
            val type = buffer.get().toInt() and 0xFF
            when (type) {
                0x00 -> map[key] = buffer.double
                0x01 -> map[key] = (buffer.get().toInt() == 1)
                0x02 -> {
                    val len = buffer.short.toInt() and 0xFFFF
                    val bytes = ByteArray(len)
                    buffer.get(bytes)
                    map[key] = String(bytes)
                }
                0x03 -> map[key] = readAMF0Object(buffer)
                else -> return map
            }
        }
        return map
    }

    private fun setChunkSize(size: Int) {
        transactionId++
        val amf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        amf.putInt(size)
        sendChunk(2, 0x01, 0, amf.array())
        chunkSize = size
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
                val flvTag = buildFLVAudioTag(data)
                sendChunk(4, 0x08, timestamp, flvTag)
            } catch (e: Exception) {
                Log.e(TAG, "Send audio error", e)
            }
        }
    }

    private fun buildFLVAudioTag(pcmData: ByteArray): ByteArray {
        val flvData = ByteArray(2 + pcmData.size)
        flvData[0] = 0xAF.toByte()
        flvData[1] = 0x01.toByte()
        pcmData.copyInto(flvData, 2)
        return flvData
    }

    private fun sendChunk(chunkStreamId: Int, typeId: Int, timestamp: Int, data: ByteArray) {
        val dos = outputStream ?: return

        val csid = chunkStreamId and 0x3F
        val tsField = timestamp.coerceAtMost(0xFFFFFF)

        val totalChunks = (data.size + chunkSize - 1) / chunkSize

        synchronized(dos) {
            for (chunkIndex in 0 until totalChunks) {
                val offset = chunkIndex * chunkSize
                val remaining = minOf(chunkSize, data.size - offset)

                if (chunkIndex == 0) {
                    val header = ByteBuffer.allocate(11).order(ByteOrder.BIG_ENDIAN)
                    header.put(((0 shl 6) or csid).toByte())
                    header.put(((tsField shr 16) and 0xFF).toByte())
                    header.put(((tsField shr 8) and 0xFF).toByte())
                    header.put((tsField and 0xFF).toByte())
                    header.put(((data.size shr 16) and 0xFF).toByte())
                    header.put(((data.size shr 8) and 0xFF).toByte())
                    header.put((data.size and 0xFF).toByte())
                    header.put(typeId.toByte())
                    header.putInt(0)
                    dos.write(header.array())
                } else {
                    val header = ByteBuffer.allocate(1).order(ByteOrder.BIG_ENDIAN)
                    header.put(((3 shl 6) or csid).toByte())
                    dos.write(header.array())
                }
                dos.write(data, offset, remaining)
            }
            dos.flush()
        }
    }

    suspend fun disconnect() {
        streamingJob?.cancel()
        streamingJob = null
        val dos = outputStream
        val dis = inputStream
        val sock = socket
        try {
            if (dos != null) synchronized(dos) { dos.close() }
            dis?.close()
            sock?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Disconnect error", e)
        }
        outputStream = null
        inputStream = null
        socket = null
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
            buffer.write(0x02)
            buffer.write(bytes.size shr 8)
            buffer.write(bytes.size and 0xFF)
            buffer.write(bytes)
        }

        fun writeNumber(value: Double) {
            buffer.write(0x00)
            val bb = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            bb.putDouble(value)
            buffer.write(bb.array())
        }

        fun writeObjectStart() {
            buffer.write(0x03)
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
            buffer.write(0x09)
        }

        fun toByteArray(): ByteArray = buffer.toByteArray()
    }

    private fun buildAMF0(block: AMF0Writer.() -> Unit): ByteArray {
        return AMF0Writer().apply(block).toByteArray()
    }
}
