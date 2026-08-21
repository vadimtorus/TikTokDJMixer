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

/**
 * DeckPanel — Compose-панель одного деки DJ-микшера.
 * Отображает метаданные трека, волновую форму, слайдер перемотки,
 * громкость, питч и кнопки cue/hot-cue.
 *
 * DeckPanel — Compose UI panel for a single DJ mixer deck.
 * Shows track metadata, waveform, seek slider, volume, pitch
 * and cue/hot-cue buttons.
 *
 * @param deckId   идентификатор деки ("A" или "B") / deck identifier ("A" or "B")
 * @param state    реактивное состояние деки / reactive deck state
 * @param onPlayPause  колбэк play/pause / play/pause callback
 * @param onSeek       колбэк перемотки (мс) / seek callback (ms)
 * @param onVolumeChange колбэк изменения громкости / volume change callback
 * @param onPitchChange  колбэк изменения скорости/питча / speed/pitch change callback
 * @param onCue         колбэк установки cue-точки / set-cue-point callback
 * @param onHotCue      колбэк нажатия hot-cue (номер) / hot-cue press callback (number)
 */
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
    // Цвет деки: красный для A, бирюзовый для остальных / Deck color: red for A, teal otherwise
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
            // Заголовок деки + BPM / Deck header + BPM
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

            // Название трека и исполнитель / Track title and artist
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

            // Волновая форма / Waveform display
            WaveformDisplay(
                isPlaying = state.isPlaying,
                deckColor = deckColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            )

            // Таймкоды: текущая позиция и длительность / Timecodes: current position and duration
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

            // --- Слайдер перемотки / Seek slider ---
            // FIX: добавлен флаг isSeeking. Раньше LaunchedEffect перезаписывал
            // seekPosition каждые 50 мс даже во время перетаскивания — слайдер
            // «дрался» с воспроизведением и дёргался под пальцем.
            // Теперь позиция из состояния применяется только когда юзер НЕ тянет слайдер.
            //
            // FIX: added an isSeeking flag. Previously the LaunchedEffect overwrote
            // seekPosition every 50 ms even mid-drag — the slider fought playback
            // and jittered under the user's finger. Now the state position is only
            // applied while the user is NOT dragging.
            var seekPosition by remember { mutableFloatStateOf(0f) }
            var isSeeking by remember { mutableStateOf(false) }
            val duration = state.track?.durationMs ?: 1L

            Slider(
                value = seekPosition,
                // Начало/процесс перетаскивания: помечаем активный drag /
                // Drag start/update: mark the drag as active
                onValueChange = {
                    isSeeking = true
                    seekPosition = it
                },
                // Завершение перемотки: отправляем позицию и снимаем флаг /
                // Seek finished: emit the position and clear the flag
                onValueChangeFinished = {
                    onSeek((seekPosition * duration).toLong())
                    isSeeking = false
                },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = deckColor,
                    activeTrackColor = deckColor
                )
            )
            // Синхронизация слайдера с позицией воспроизведения —
            // только когда пользователь не перетаскивает его вручную.
            //
            // Sync the slider with the playback position —
            // only while the user is not dragging it manually.
            LaunchedEffect(state.positionMs, duration) {
                if (!isSeeking) {
                    seekPosition = if (duration > 0) state.positionMs.toFloat() / duration else 0f
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Кнопки управления: Cue, Play/Pause, Hot-cue 1-3 /
            // Control buttons: Cue, Play/Pause, Hot-cues 1-3
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

            // Слайдер громкости / Volume slider
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

            // Слайдер скорости/питча (0.5x..2.0x) / Speed/pitch slider (0.5x..2.0x)
            Text("Speed", fontSize = 10.sp)
            Slider(
                value = state.speed,
                onValueChange = onPitchChange,
                valueRange = 0.5f..2.0f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = deckColor,
                    activeTrackColor = deckColor
                )
            )
        }
    }
}

/**
 * HotCueButton — круглая кнопка hot-cue с номером.
 *
 * HotCueButton — round hot-cue button showing its number.
 *
 * @param number номер hot-cue / hot-cue number
 * @param color  цвет кнопки / button color
 * @param onClick обработчик нажатия / click handler
 */
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

/**
 * WaveformDisplay — анимированная псевдо-волновая форма деки.
 * При воспроизведении бары «пляшут» по синусоиде и бежит белая линия прогресса;
 * на паузе показывается статичная случайная форма.
 *
 * WaveformDisplay — animated pseudo-waveform for the deck.
 * While playing, bars dance along a sine wave and a white progress line sweeps;
 * when paused, a static random shape is shown.
 *
 * @param isPlaying играет ли дека / whether the deck is playing
 * @param deckColor цвет деки / deck accent color
 */
@Composable
fun WaveformDisplay(
    isPlaying: Boolean,
    deckColor: Color,
    modifier: Modifier = Modifier
) {
    // Бесконечная анимация 0..1 для движения баров и линии прогресса /
    // Infinite 0..1 animation driving bar motion and the progress line
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

    // Статичные случайные высоты баров для режима паузы /
    // Static random bar heights used in paused mode
    val cachedWaveform = remember {
        List(50) { (Math.random() * 0.4 + 0.1).toFloat() }
    }

    Canvas(modifier = modifier.background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))) {
        val barCount = cachedWaveform.size
        val barWidth = size.width / barCount
        val centerY = size.height / 2

        // Отрисовка баров: анимация при игре, статика на паузе /
        // Draw bars: animated while playing, static while paused
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

        // Белая линия «играющей головки» во время воспроизведения /
        // White playhead line while playing
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
