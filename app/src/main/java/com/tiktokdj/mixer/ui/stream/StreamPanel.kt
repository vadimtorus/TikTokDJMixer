package com.tiktokdj.mixer.ui.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiktokdj.mixer.model.StreamMethod
import com.tiktokdj.mixer.model.StreamResolution
import com.tiktokdj.mixer.streaming.StreamManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamPanel(streamManager: StreamManager) {
    var selectedMethod by remember { mutableStateOf(StreamMethod.RTMP) }
    var rtmpUrl by remember { mutableStateOf("") }
    var tiktokClientKey by remember { mutableStateOf("") }
    var tiktokClientSecret by remember { mutableStateOf("") }
    var streamTitle by remember { mutableStateOf("") }
    var selectedResolution by remember { mutableStateOf(StreamResolution.HD_720P) }
    var bitrate by remember { mutableFloatStateOf(2500f) }
    var enableMicrophone by remember { mutableStateOf(false) }

    val isStreaming by streamManager.isStreaming.collectAsState()
    val scope = rememberCoroutineScope()
    var isStarting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Stream to TikTok", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedMethod == StreamMethod.RTMP,
                onClick = { selectedMethod = StreamMethod.RTMP },
                label = { Text("RTMP") }
            )
            FilterChip(
                selected = selectedMethod == StreamMethod.TIKTOK_LIVE_API,
                onClick = { selectedMethod = StreamMethod.TIKTOK_LIVE_API },
                label = { Text("TikTok API") }
            )
        }

        OutlinedTextField(
            value = streamTitle,
            onValueChange = { streamTitle = it },
            label = { Text("Stream Title") },
            modifier = Modifier.fillMaxWidth()
        )

        if (selectedMethod == StreamMethod.RTMP) {
            OutlinedTextField(
                value = rtmpUrl,
                onValueChange = { rtmpUrl = it },
                label = { Text("RTMP URL") },
                placeholder = { Text("rtmp://server/live/stream_key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text("Resolution", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StreamResolution.entries.forEach { res ->
                    FilterChip(
                        selected = selectedResolution == res,
                        onClick = { selectedResolution = res },
                        label = {
                            Text(
                                when (res) {
                                    StreamResolution.SD_480P -> "480p"
                                    StreamResolution.HD_720P -> "720p"
                                    StreamResolution.FULL_HD_1080P -> "1080p"
                                },
                                fontSize = 11.sp
                            )
                        }
                    )
                }
            }
        }

        if (selectedMethod == StreamMethod.TIKTOK_LIVE_API) {
            OutlinedTextField(
                value = tiktokClientKey,
                onValueChange = { tiktokClientKey = it },
                label = { Text("Client Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = tiktokClientSecret,
                onValueChange = { tiktokClientSecret = it },
                label = { Text("Client Secret") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
        }

        Text("Bitrate: ${bitrate.toInt()} kbps", fontSize = 12.sp)
        Slider(value = bitrate, onValueChange = { bitrate = it }, valueRange = 500f..6000f, steps = 10)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Enable Microphone")
            Switch(checked = enableMicrophone, onCheckedChange = { enableMicrophone = it })
        }

        Spacer(modifier = Modifier.weight(1f))

        if (isStreaming) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE74C3C).copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(12.dp).background(Color.Red, RoundedCornerShape(6.dp))) {}
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LIVE", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE74C3C))
                }
            }
        }

        Button(
            onClick = {
                if (isStreaming) {
                    scope.launch {
                        streamManager.stopStreaming()
                    }
                } else {
                    isStarting = true
                    scope.launch {
                        when (selectedMethod) {
                            StreamMethod.RTMP -> {
                                streamManager.startRTMPStream(rtmpUrl)
                            }
                            StreamMethod.TIKTOK_LIVE_API -> {
                                streamManager.startTikTokStream(
                                    tiktokClientKey, tiktokClientSecret, streamTitle
                                )
                            }
                        }
                        isStarting = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !isStarting && (isStreaming || (if (selectedMethod == StreamMethod.RTMP) rtmpUrl.isNotBlank() else tiktokClientKey.isNotBlank() && tiktokClientSecret.isNotBlank())),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isStreaming) Color(0xFFE74C3C) else Color(0xFF2ECC71)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                if (isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                "Stream",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    isStarting -> "STARTING..."
                    isStreaming -> "STOP STREAM"
                    else -> "START STREAM"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
