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

// ============================================================
// СЕКЦИЯ: Компоненты микшера / SECTION: Mixer components
// Переиспользуемые UI-элементы DJ-микшера: кроссфейдер,
// эквалайзер и анализатор спектра.
// Reusable DJ mixer UI elements: crossfader, EQ and spectrum analyzer.
// ============================================================

/**
 * Кроссфейдер — горизонтальный слайдер для плавного перехода между деками A и B.
 * Crossfader - a horizontal slider for smooth transitions between decks A and B.
 *
 * @param position Текущая позиция кроссфейдера: 0.0 = полностью дека A, 1.0 = полностью дека B, 0.5 = центр.
 *                 Current crossfader position: 0.0 = full deck A, 1.0 = full deck B, 0.5 = center.
 * @param onPositionChange Колбэк, вызываемый при перемещении слайдера пользователем.
 *                         Callback invoked when the user moves the slider.
 * @param isSyncEnabled Включена ли функция синхронизации BPM двух деков.
 *                      Whether BPM sync between the two decks is enabled.
 * @param onSyncToggle Колбэк переключения кнопки SYNC.
 *                     Callback for toggling the SYNC button.
 */
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
            // Верхний ряд: метки деков и кнопка SYNC / Top row: deck labels and SYNC button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Дека A подсвечивается красным / Deck A highlighted in red
                Text(
                    text = "DECK A",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF6B6B)
                )

                // Кнопка SYNC меняет цвет в зависимости от состояния / SYNC button changes color based on state
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

                // Дека B подсвечивается бирюзовым / Deck B highlighted in teal
                Text(
                    text = "DECK B",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4ECDC4)
                )
            }

            // Сам слайдер кроссфейдера / The crossfader slider itself
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

            // Быстрые пресеты позиций: полная A / центр / полная B
            // Quick position presets: full A / center / full B
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

/**
 * Панель эквалайзера с тремя полосами (LOW/MID/HI) для каждой из двух деков.
 * Equalizer panel with three bands (LOW/MID/HI) for each of the two decks.
 *
 * Отображает два столбца поворотных регуляторов, разделённых вертикальной линией.
 * Displays two columns of rotary knobs separated by a vertical divider line.
 *
 * @param deckAState Состояние эквалайзера деки A (low/mid/high).
 *                   EQ state of deck A (low/mid/high).
 * @param deckBState Состояние эквалайзера деки B (low/mid/high).
 *                   EQ state of deck B (low/mid/high).
 * @param onEQChangeDeckA Колбэк изменения EQ деки A; параметры — (low, mid, high).
 *                        Deck A EQ change callback; parameters are (low, mid, high).
 * @param onEQChangeDeckB Колбэк изменения EQ деки B; параметры — (low, mid, high).
 *                        Deck B EQ change callback; parameters are (low, mid, high).
 */
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
            // Столбец регуляторов деки A / Deck A knob column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text("EQ A", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6B6B))
                // Каждый регулятор обновляет только свою полосу, сохраняя остальные значения
                // Each knob updates only its own band while keeping the other values intact
                EQKnob("HI", deckAState.high) { onEQChangeDeckA(deckAState.low, deckAState.mid, it) }
                EQKnob("MID", deckAState.mid) { onEQChangeDeckA(deckAState.low, it, deckAState.high) }
                EQKnob("LOW", deckAState.low) { onEQChangeDeckA(it, deckAState.mid, deckAState.high) }
            }

            // Вертикальный разделитель между деками / Vertical divider between decks
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color.Gray.copy(alpha = 0.3f))
            )

            // Столбец регуляторов деки B / Deck B knob column
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

/**
 * Одиночный поворотный регулятор эквалайзера («крутилка»).
 * A single equalizer rotary knob ("knob").
 *
 * Реализован как вертикальный Slider, повёрнутый на -90° через graphicsLayer,
 * чтобы имитировать внешний вид аппаратного регулятора.
 * Implemented as a vertical Slider rotated by -90° via graphicsLayer
 * to mimic the look of a hardware knob.
 *
 * @param label Подпись полосы (HI / MID / LOW). Band label (HI / MID / LOW).
 * @param value Текущее значение от 0.0 до 1.0. Current value from 0.0 to 1.0.
 * @param onValueChange Колбэк изменения значения. Value change callback.
 */
@Composable
fun EQKnob(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        // Подпись полосы над регулятором / Band label above the knob
        Text(label, fontSize = 8.sp)
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(80.dp),
            contentAlignment = Alignment.Center
        ) {
            // Слайдер повёрнут на -90° вокруг центра, превращаясь в «крутилку»
            // The slider is rotated -90° around its center, turning into a "knob"
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
        // Числовое значение в процентах под регулятором / Numeric percentage below the knob
        Text("${(value * 100).toInt()}%", fontSize = 7.sp)
    }
}

/**
 * Анализатор спектра — визуализация 32 частотных полос в виде зеркальных полос.
 * Spectrum analyzer - visualization of 32 frequency bands as mirrored bars.
 *
 * Полоса рисуется симметрично относительно центра: зелёная часть вверх,
 * оранжевая вниз, что создаёт эффект «отражения».
 * Each bar is drawn symmetrically around the center: green part up,
 * orange part down, creating a "reflection" effect.
 *
 * @param getSpectrumData Лямбда, возвращающая массив амплитуд (0..1) для 32 полос.
 *                        Lambda returning an array of amplitudes (0..1) for 32 bands.
 */
@Composable
fun SpectrumAnalyzer(
    getSpectrumData: () -> FloatArray,
    modifier: Modifier = Modifier
) {
    // Локальное состояние амплитуд полос, инициализированное нулями
    // Local band amplitude state, initialized with zeros
    var bands by remember { mutableStateOf(List(32) { 0f }) }

    // Фоновая корутина опрашивает данные спектра каждые 50 мс (~20 FPS)
    // Background coroutine polls spectrum data every 50 ms (~20 FPS)
    LaunchedEffect(Unit) {
        while (true) {
            val data = getSpectrumData()
            bands = data.toList()
            kotlinx.coroutines.delay(50)
        }
    }

    Canvas(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // Ширина одной полосы и координата центральной линии
        // Width of one bar and the center line coordinate
        val barWidth = size.width / bands.size
        val centerY = size.height / 2

        bands.forEachIndexed { index, amplitude ->
            // Высота полосы пропорциональна амплитуде (максимум 80% холста)
            // Bar height is proportional to amplitude (max 80% of canvas)
            val height = amplitude * size.height * 0.8f
            val x = index * barWidth + barWidth / 4

            // Верхняя (зелёная) половина полосы / Upper (green) half of the bar
            drawLine(
                color = Color(0xFF00FF88),
                start = Offset(x, centerY - height / 2),
                end = Offset(x, centerY),
                strokeWidth = barWidth / 2
            )

            // Нижняя (оранжевая) половина — «отражение» / Lower (orange) half - "reflection"
            drawLine(
                color = Color(0xFFFF6600),
                start = Offset(x, centerY),
                end = Offset(x, centerY + height / 2),
                strokeWidth = barWidth / 2
            )
        }

        // Тонкая белая центральная линия-ось / Thin white central axis line
        drawLine(
            color = Color.White.copy(alpha = 0.3f),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 1f
        )
    }
}
