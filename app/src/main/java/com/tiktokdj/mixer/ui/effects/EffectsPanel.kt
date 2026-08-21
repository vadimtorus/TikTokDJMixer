package com.tiktokdj.mixer.ui.effects

import androidx.compose.foundation.layout.*
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

// ============================================================
// СЕКЦИЯ: Панель эффектов / SECTION: Effects panel
// Экран управления аудиоэффектами: сетка кнопок включения
// и список активных эффектов.
// Audio effects control screen: a grid of toggle buttons
// and a list of active effects.
// ============================================================

/**
 * Главная панель эффектов — отображает сетку из 9 кнопок эффектов
 * (эхо, реверб, дилей, флэнжер, фейзер, ФНЧ, ФВЧ, дисторшн, биткрашер)
 * и чипы активных эффектов.
 * Main effects panel - displays a grid of 9 effect buttons
 * (echo, reverb, delay, flanger, phaser, LP filter, HP filter, distortion, bitcrusher)
 * and chips of active effects.
 *
 * Состояние активных эффектов хранится локально и синхронизируется
 * с процессором эффектов микшерного движка.
 * Active effects state is stored locally and synchronized
 * with the mixer engine's effects processor.
 *
 * @param mixerEngine Ссылка на микшерный движок для добавления/удаления эффектов.
 *                    Reference to the mixer engine for adding/removing effects.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectsPanel(mixerEngine: MixerEngine) {
    // Наблюдаемый список активных типов эффектов / Observable list of active effect types
    val activeEffects = remember { mutableStateListOf<EffectType>() }

    // Функция переключения эффекта: добавляет или удаляет его в движке.
    // Toggle function: adds or removes the effect in the engine.
    // remember гарантирует стабильную ссылку между рекомпозициями.
    // remember guarantees a stable reference across recompositions.
    val toggleEffect = remember<(EffectType) -> Unit> {
        { type: EffectType ->
            if (activeEffects.contains(type)) {
                // Эффект уже активен — удаляем его / Effect already active - remove it
                activeEffects.remove(type)
                mixerEngine.effectsProcessor.removeEffect(type)
            } else {
                // Эффект неактивен — добавляем с интенсивностью по умолчанию 0.5
                // Effect inactive - add it with default intensity of 0.5
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
        // Заголовок экрана / Screen title
        Text(
            text = "Effects",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(8.dp)
        )

        // Сетка кнопок эффектов: 3 ряда по 3 кнопки / Effect button grid: 3 rows of 3 buttons
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

        // Список активных эффектов в виде кликабельных чипов
        // List of active effects as clickable chips
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

/**
 * Кнопка одного эффекта — карточка с иконкой и подписью.
 * A single effect button - a card with an icon and a label.
 *
 * Визуально подсвечивается (цветом primary), когда эффект активен.
 * Visually highlighted (with the primary color) when the effect is active.
 *
 * @param effectType Тип эффекта, за который отвечает кнопка. The effect type this button controls.
 * @param label Текстовая подпись кнопки. Text label of the button.
 * @param icon Иконка эффекта. The effect icon.
 * @param activeEffects Текущий список активных эффектов для определения состояния.
 *                      Current list of active effects used to determine the state.
 * @param onToggle Колбэк переключения эффекта. Effect toggle callback.
 */
@Composable
fun EffectButton(
    effectType: EffectType,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    activeEffects: List<EffectType>,
    onToggle: (EffectType) -> Unit,
    modifier: Modifier = Modifier
) {
    // Эффект считается активным, если присутствует в списке
    // An effect is considered active if present in the list
    val isActive = activeEffects.contains(effectType)

    Card(
        onClick = { onToggle(effectType) },
        modifier = modifier,
        colors = CardDefaults.cardColors(
            // Активный эффект подсвечивается цветом темы / Active effect is highlighted with the theme color
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
            // Цвет иконки зависит от состояния активности / Icon color depends on the active state
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
