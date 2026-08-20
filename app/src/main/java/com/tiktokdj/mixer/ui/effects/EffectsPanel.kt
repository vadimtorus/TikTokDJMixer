package com.tiktokdj.mixer.ui.effects

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiktokdj.mixer.engine.MixerEngine
import com.tiktokdj.mixer.model.Effect
import com.tiktokdj.mixer.model.EffectType

@Composable
fun EffectsPanel(mixerEngine: MixerEngine) {
    val activeEffects = remember { mutableStateListOf<Effect>() }

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

        // Effects grid
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Echo, Reverb, Delay
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EffectButton(
                    effectType = EffectType.ECHO,
                    label = "Echo",
                    icon = Icons.DefaultgraphicEq,
                    activeEffects = activeEffects,
                    onToggle = { type, active, intensity ->
                        if (active) {
                            activeEffects.add(Effect(type.name, type.name, type, intensity))
                        } else {
                            activeEffects.removeAll { it.type == type }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                EffectButton(
                    effectType = EffectType.REVERB,
                    label = "Reverb",
                    icon = Icons.Default.Waves,
                    activeEffects = activeEffects,
                    onToggle = { type, active, intensity ->
                        if (active) {
                            activeEffects.add(Effect(type.name, type.name, type, intensity))
                        } else {
                            activeEffects.removeAll { it.type == type }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                EffectButton(
                    effectType = EffectType.DELAY,
                    label = "Delay",
                    icon = Icons.Default.Timer,
                    activeEffects = activeEffects,
                    onToggle = { type, active, intensity ->
                        if (active) {
                            activeEffects.add(Effect(type.name, type.name, type, intensity))
                        } else {
                            activeEffects.removeAll { it.type == type }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 2: Flanger, Phaser, Filter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EffectButton(
                    effectType = EffectType.FLANGER,
                    label = "Flanger",
                    icon = Icons.Default.SwapVert,
                    activeEffects = activeEffects,
                    onToggle = { type, active, intensity ->
                        if (active) {
                            activeEffects.add(Effect(type.name, type.name, type, intensity))
                        } else {
                            activeEffects.removeAll { it.type == type }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                EffectButton(
                    effectType = EffectType.PHASER,
                    label = "Phaser",
                    icon = Icons.Default.RotateRight,
                    activeEffects = activeEffects,
                    onToggle = { type, active, intensity ->
                        if (active) {
                            activeEffects.add(Effect(type.name, type.name, type, intensity))
                        } else {
                            activeEffects.removeAll { it.type == type }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                EffectButton(
                    effectType = EffectType.FILTER_LOW_PASS,
                    label = "LP Filter",
                    icon = Icons.Default.FilterAlt,
                    activeEffects = activeEffects,
                    onToggle = { type, active, intensity ->
                        if (active) {
                            activeEffects.add(Effect(type.name, type.name, type, intensity))
                        } else {
                            activeEffects.removeAll { it.type == type }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 3: HP Filter, Distortion, Bitcrusher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EffectButton(
                    effectType = EffectType.FILTER_HIGH_PASS,
                    label = "HP Filter",
                    icon = Icons.Default.FilterAltOff,
                    activeEffects = activeEffects,
                    onToggle = { type, active, intensity ->
                        if (active) {
                            activeEffects.add(Effect(type.name, type.name, type, intensity))
                        } else {
                            activeEffects.removeAll { it.type == type }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                EffectButton(
                    effectType = EffectType.DISTORTION,
                    label = "Distort",
                    icon = Icons.Default.Bolt,
                    activeEffects = activeEffects,
                    onToggle = { type, active, intensity ->
                        if (active) {
                            activeEffects.add(Effect(type.name, type.name, type, intensity))
                        } else {
                            activeEffects.removeAll { it.type == type }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                EffectButton(
                    effectType = EffectType.BITCRUSHER,
                    label = "Bitcrush",
                    icon = Icons.Default.Memory,
                    activeEffects = activeEffects,
                    onToggle = { type, active, intensity ->
                        if (active) {
                            activeEffects.add(Effect(type.name, type.name, type, intensity))
                        } else {
                            activeEffects.removeAll { it.type == type }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // X/Y Pad for effect modulation
        Text(
            text = "Effect Modulation",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        XYModPad(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )

        // Active effects list
        if (activeEffects.isNotEmpty()) {
            Text(
                text = "Active Effects",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            activeEffects.forEach { effect ->
                EffectChip(
                    effect = effect,
                    onRemove = { activeEffects.remove(effect) }
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
    activeEffects: List<Effect>,
    onToggle: (EffectType, Boolean, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = activeEffects.any { it.type == effectType }
    val intensity = activeEffects.find { it.type == effectType }?.intensity ?: 0.5f
    var showSlider by remember { mutableStateOf(false) }

    Card(
        onClick = {
            if (isActive) {
                onToggle(effectType, false, 0f)
            } else {
                onToggle(effectType, true, intensity)
            }
        },
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
                icon,
                label,
                tint = if (isActive)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = if (isActive)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun XYModPad(modifier: Modifier = Modifier) {
    var offsetX by remember { mutableFloatStateOf(0.5f) }
    var offsetY by remember { mutableFloatStateOf(0.5f) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        // Grid lines
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

            // Crosshair
            drawCircle(
                color = Color.Cyan.copy(alpha = 0.8f),
                radius = 12f,
                center = Offset(offsetX * size.width, offsetY * size.height)
            )
            drawCircle(
                color = Color.Cyan.copy(alpha = 0.3f),
                radius = 24f,
                center = Offset(offsetX * size.width, offsetY * size.height)
            )
        }
    }
}

@Composable
fun EffectChip(
    effect: Effect,
    onRemove: () -> Unit
) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(effect.name) },
        trailingIcon = {
            Icon(
                Icons.Default.Close,
                "Remove",
                modifier = Modifier.size(16.dp)
            )
        }
    )
}
