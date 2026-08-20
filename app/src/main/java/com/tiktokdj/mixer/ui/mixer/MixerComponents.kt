package com.tiktokdj.mixer.ui.mixer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiktokdj.mixer.model.EQState

@Composable
fun CrossfaderBar(
    position: Float,
    onPositionChange: (Float) -> Unit,
    isSyncEnabled: Boolean,
    onSyncToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DECK A",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF6B6B)
                )

                Button(
                    onClick = onSyncToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSyncEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Sync, "Sync", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SYNC", fontSize = 10.sp)
                }

                Text(
                    text = "DECK B",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4ECDC4)
                )
            }

            Slider(
                value = position,
                onValueChange = onPositionChange,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = Color(0xFF9B59B6),
                    inactiveTrackColor = Color.LightGray
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(
                    onClick = { onPositionChange(0f) },
                    modifier = Modifier.height(28.dp)
                ) { Text("A", fontSize = 10.sp) }
                OutlinedButton(
                    onClick = { onPositionChange(0.5f) },
                    modifier = Modifier.height(28.dp)
                ) { Text("MID", fontSize = 10.sp) }
                OutlinedButton(
                    onClick = { onPositionChange(1f) },
                    modifier = Modifier.height(28.dp)
                ) { Text("B", fontSize = 10.sp) }
            }
        }
    }
}

@Composable
fun EQPanel(
    deckAState: EQState,
    deckBState: EQState,
    onEQChangeDeckA: (Float, Float, Float) -> Unit,
    onEQChangeDeckB: (Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text("EQ A", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6B6B))
                EQKnob("HI", deckAState.high) { onEQChangeDeckA(deckAState.low, deckAState.mid, it) }
                EQKnob("MID", deckAState.mid) { onEQChangeDeckA(deckAState.low, it, deckAState.high) }
                EQKnob("LOW", deckAState.low) { onEQChangeDeckA(it, deckAState.mid, deckAState.high) }
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color.Gray.copy(alpha = 0.3f))
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text("EQ B", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4ECDC4))
                EQKnob("HI", deckBState.high) { onEQChangeDeckB(deckBState.low, deckBState.mid, it) }
                EQKnob("MID", deckBState.mid) { onEQChangeDeckB(deckBState.low, it, deckBState.high) }
                EQKnob("LOW", deckBState.low) { onEQChangeDeckB(it, deckBState.mid, deckBState.high) }
            }
        }
    }
}

@Composable
fun EQKnob(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        Text(label, fontSize = 8.sp)
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(80.dp),
            contentAlignment = Alignment.Center
        ) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .height(48.dp)
                    .width(80.dp)
                    .graphicsLayer {
                        rotationZ = -90f
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    },
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
            )
        }
        Text("${(value * 100).toInt()}%", fontSize = 7.sp)
    }
}

@Composable
fun SpectrumAnalyzer(
    getSpectrumData: () -> FloatArray,
    modifier: Modifier = Modifier
) {
    var bands by remember { mutableStateOf(FloatArray(32)) }

    LaunchedEffect(Unit) {
        while (true) {
            bands = getSpectrumData()
            kotlinx.coroutines.delay(50)
        }
    }

    Canvas(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        val barWidth = size.width / bands.size
        val centerY = size.height / 2

        bands.forEachIndexed { index, amplitude ->
            val height = amplitude * size.height * 0.8f
            val x = index * barWidth + barWidth / 4

            drawLine(
                color = Color(0xFF00FF88),
                start = Offset(x, centerY - height / 2),
                end = Offset(x, centerY),
                strokeWidth = barWidth / 2
            )

            drawLine(
                color = Color(0xFFFF6600),
                start = Offset(x, centerY),
                end = Offset(x, centerY + height / 2),
                strokeWidth = barWidth / 2
            )
        }

        drawLine(
            color = Color.White.copy(alpha = 0.3f),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 1f
        )
    }
}
