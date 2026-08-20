package com.tiktokdj.mixer.ui.deck

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiktokdj.mixer.model.DeckState
import com.tiktokdj.mixer.utils.AudioUtils

@Composable
fun DeckPanel(
    deckId: String,
    state: DeckState,
    modifier: Modifier = Modifier,
    onPlayPause: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onVolumeChange: (Float) -> Unit = {},
    onPitchChange: (Float) -> Unit = {},
    onCue: () -> Unit = {},
    onHotCue: (Int) -> Unit = {}
) {
    val deckColor = when (deckId) {
        "A" -> Color(0xFFFF6B6B)
        else -> Color(0xFF4ECDC4)
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DECK $deckId",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = deckColor
                )

                if (state.track != null) {
                    Text(
                        text = "${state.track.bpm.toInt()} BPM",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            state.track?.let { track ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = track.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = track.artist,
                        fontSize = 11.sp,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } ?: Text(
                text = "No track loaded",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            WaveformDisplay(
                isPlaying = state.isPlaying,
                deckColor = deckColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = AudioUtils.formatDuration(state.positionMs),
                    fontSize = 10.sp
                )
                state.track?.let {
                    Text(
                        text = AudioUtils.formatDuration(it.durationMs),
                        fontSize = 10.sp
                    )
                }
            }

            var seekPosition by remember { mutableFloatStateOf(0f) }
            val duration = state.track?.durationMs ?: 1L

            Slider(
                value = seekPosition,
                onValueChange = { seekPosition = it },
                onValueChangeFinished = {
                    onSeek((seekPosition * duration).toLong())
                },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = deckColor,
                    activeTrackColor = deckColor
                )
            )
            LaunchedEffect(state.positionMs, duration) {
                seekPosition = if (duration > 0) state.positionMs.toFloat() / duration else 0f
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCue) {
                    Icon(Icons.Default.FiberManualRecord, "Cue", tint = MaterialTheme.colorScheme.primary)
                }

                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(deckColor)
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "Play/Pause",
                        tint = Color.White
                    )
                }

                HotCueButton(number = 1, color = Color.Red, onClick = { onHotCue(1) })
                HotCueButton(number = 2, color = Color.Green, onClick = { onHotCue(2) })
                HotCueButton(number = 3, color = Color.Blue, onClick = { onHotCue(3) })
            }

            Text("Volume", fontSize = 10.sp)
            Slider(
                value = state.volume,
                onValueChange = onVolumeChange,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = deckColor,
                    activeTrackColor = deckColor
                )
            )

            Text("Pitch", fontSize = 10.sp)
            Slider(
                value = state.pitch,
                onValueChange = onPitchChange,
                valueRange = 0.8f..1.2f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = deckColor,
                    activeTrackColor = deckColor
                )
            )
        }
    }
}

@Composable
fun HotCueButton(number: Int, color: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$number",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun WaveformDisplay(
    isPlaying: Boolean,
    deckColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveformProgress"
    )

    val cachedWaveform = remember {
        List(50) { (Math.random() * 0.4 + 0.1).toFloat() }
    }

    Canvas(modifier = modifier.background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))) {
        val barCount = cachedWaveform.size
        val barWidth = size.width / barCount
        val centerY = size.height / 2

        for (i in 0 until barCount) {
            val height = if (isPlaying) {
                val phase = (i.toFloat() / barCount + animProgress) % 1f
                val amplitude = (Math.sin(phase * Math.PI * 4).toFloat() + 1f) / 2f
                amplitude * size.height * 0.8f
            } else {
                cachedWaveform[i] * size.height * 0.3f + size.height * 0.1f
            }

            drawLine(
                color = deckColor,
                start = Offset(i * barWidth + barWidth / 2, centerY - height / 2),
                end = Offset(i * barWidth + barWidth / 2, centerY + height / 2),
                strokeWidth = barWidth * 0.6f,
                cap = StrokeCap.Round
            )
        }

        if (isPlaying) {
            drawLine(
                color = Color.White,
                start = Offset(animProgress * size.width, 0f),
                end = Offset(animProgress * size.width, size.height),
                strokeWidth = 2f
            )
        }
    }
}
