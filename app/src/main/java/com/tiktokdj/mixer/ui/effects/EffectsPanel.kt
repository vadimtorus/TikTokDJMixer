package com.tiktokdj.mixer.ui.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiktokdj.mixer.engine.MixerEngine
import com.tiktokdj.mixer.model.Effect
import com.tiktokdj.mixer.model.EffectType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectsPanel(mixerEngine: MixerEngine) {
    val activeEffects = remember { mutableStateListOf<EffectType>() }

    val toggleEffect = remember<(EffectType) -> Unit> {
        { type: EffectType ->
            if (activeEffects.contains(type)) {
                activeEffects.remove(type)
                mixerEngine.effectsProcessor.removeEffect(type)
            } else {
                activeEffects.add(type)
                mixerEngine.effectsProcessor.addEffect(
                    Effect(type.name, type.name, type, 0.5f)
                )
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Effects",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(8.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EffectButton(EffectType.ECHO, "Echo", Icons.Default.GraphicEq, activeEffects, toggleEffect, Modifier.weight(1f))
                EffectButton(EffectType.REVERB, "Reverb", Icons.Default.Waves, activeEffects, toggleEffect, Modifier.weight(1f))
                EffectButton(EffectType.DELAY, "Delay", Icons.Default.Timer, activeEffects, toggleEffect, Modifier.weight(1f))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EffectButton(EffectType.FLANGER, "Flanger", Icons.Default.SwapVert, activeEffects, toggleEffect, Modifier.weight(1f))
                EffectButton(EffectType.PHASER, "Phaser", Icons.Default.RotateRight, activeEffects, toggleEffect, Modifier.weight(1f))
                EffectButton(EffectType.FILTER_LOW_PASS, "LP Filter", Icons.Default.FilterAlt, activeEffects, toggleEffect, Modifier.weight(1f))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EffectButton(EffectType.FILTER_HIGH_PASS, "HP Filter", Icons.Default.FilterAltOff, activeEffects, toggleEffect, Modifier.weight(1f))
                EffectButton(EffectType.DISTORTION, "Distort", Icons.Default.Bolt, activeEffects, toggleEffect, Modifier.weight(1f))
                EffectButton(EffectType.BITCRUSHER, "Bitcrush", Icons.Default.Memory, activeEffects, toggleEffect, Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("Effect Modulation", fontSize = 14.sp, fontWeight = FontWeight.Medium)

        XYModPad(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            onModulationChange = { x, y ->
                // Apply to active effects
            }
        )

        if (activeEffects.isNotEmpty()) {
            Text("Active Effects", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            activeEffects.forEach { type ->
                InputChip(
                    selected = true,
                    onClick = { toggleEffect(type) },
                    label = { Text(type.name) },
                    trailingIcon = {
                        Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(16.dp))
                    }
                )
            }
        }
    }
}

@Composable
fun EffectButton(
    effectType: EffectType,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    activeEffects: List<EffectType>,
    onToggle: (EffectType) -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = activeEffects.contains(effectType)

    Card(
        onClick = { onToggle(effectType) },
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon, label,
                tint = if (isActive) MaterialTheme.colorScheme.onPrimary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = if (isActive) MaterialTheme.colorScheme.onPrimary
                         else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun XYModPad(
    modifier: Modifier = Modifier,
    onModulationChange: (Float, Float) -> Unit = { _, _ -> }
) {
    var offsetX by remember { mutableFloatStateOf(0.5f) }
    var offsetY by remember { mutableFloatStateOf(0.5f) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A2E))
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    offsetX = (change.position.x / size.width).coerceIn(0f, 1f)
                    offsetY = (change.position.y / size.height).coerceIn(0f, 1f)
                    onModulationChange(offsetX, offsetY)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (i in 1..4) {
                drawLine(
                    color = Color.White.copy(alpha = 0.1f),
                    start = Offset(size.width * i / 5, 0f),
                    end = Offset(size.width * i / 5, size.height)
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.1f),
                    start = Offset(0f, size.height * i / 5),
                    end = Offset(size.width, size.height * i / 5)
                )
            }

            drawCircle(
                color = Color.Cyan.copy(alpha = 0.3f),
                radius = 24f,
                center = Offset(offsetX * size.width, offsetY * size.height)
            )
            drawCircle(
                color = Color.Cyan.copy(alpha = 0.8f),
                radius = 12f,
                center = Offset(offsetX * size.width, offsetY * size.height)
            )
        }
    }
}
