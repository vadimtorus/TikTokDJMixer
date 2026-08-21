package com.tiktokdj.mixer.engine

import com.tiktokdj.mixer.model.Effect
import com.tiktokdj.mixer.model.EffectType
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * Процессор аудиоэффектов DJ-микшера.
 *
 * Применяет цепочку активных эффектов последовательно к входному сигналу:
 * эхо, реверберация, флэнжер, фейзер, ФНЧ/ФВЧ, дисторшн, биткрашер,
 * задержка и панорама.
 *
 * Потокобезопасность: доступ к карте эффектов и ко всем разделяемым
 * DSP-буферам синхронизируется монитором [dspLock].
 *
 * Audio effects processor for the DJ mixer.
 *
 * Applies the chain of active effects sequentially to the input signal:
 * echo, reverb, flanger, phaser, low/high-pass filters, distortion,
 * bitcrusher, delay and panning.
 *
 * Thread safety: access to the effect map and all shared DSP buffers is
 * synchronized by the [dspLock] monitor.
 */
class EffectsProcessor {

    // ======================== СОСТОЯНИЕ / STATE ========================

    /** Активные эффекты, ключ — тип эффекта. Active effects keyed by effect type. */
    private val activeEffects = ConcurrentHashMap<EffectType, Effect>()

    /** Монитор для сериализации доступа к DSP-состоянию. Monitor serializing DSP state access. */
    private val dspLock = Any()

    /** Кольцевой буфер задержки (~2 с при 48 кГц). Delay ring buffer (~2 s at 48 kHz). */
    private var delayBuffer = FloatArray(96000)

    /** Индекс записи в буфер задержки. Write index into the delay buffer. */
    private var delayWritePos = 0

    /** Кольцевой буфер флэнжера. Flanger ring buffer. */
    private var flangerBuffer = FloatArray(96000)

    /** Индекс записи во флэнжер. Flanger write index. */
    private var flangerPos = 0

    /** Фаза LFO флэнжера, радианы. Flanger LFO phase, radians. */
    private var flangerPhase = 0f

    /**
     * Состояние четырёх каскадов фейзера: stages[s][0]/stages[s][1] —
     * два предыдущих отсчёта all-pass фильтра каскада s.
     *
     * State of the four phaser stages: stages[s][0]/stages[s][1] hold the
     * two previous samples of stage s's all-pass filter.
     */
    private var phaserStages = Array(4) { floatArrayOf(0f, 0f) }

    // ======================== ПУБЛИЧНЫЙ API / PUBLIC API ========================

    /**
     * Обрабатывает входной сигнал, последовательно применяя все активные эффекты.
     * Processes the input signal by applying all active effects in sequence.
     *
     * @param input входные PCM-отсчёты; input PCM samples
     * @param sampleRate частота дискретизации, Гц; sample rate in Hz
     * @return обработанные отсчёты; processed samples
     */
    fun process(input: FloatArray, sampleRate: Int): FloatArray {
        // Защита от пустого входа: нечего обрабатывать, возвращаем как есть.
        // Guard against empty input: nothing to process, return as is.
        if (input.isEmpty()) return input

        var output = input.copyOf()
        synchronized(dspLock) {
            for ((type, effect) in activeEffects) {
                output = applyEffect(output, type, effect.intensity, sampleRate)
            }
        }
        return output
    }

    /** Добавляет или заменяет эффект в цепочке. Adds or replaces an effect in the chain. */
    fun addEffect(effect: Effect) {
        synchronized(dspLock) {
            activeEffects[effect.type] = effect
        }
    }

    /** Удаляет эффект заданного типа. Removes the effect of the given type. */
    fun removeEffect(type: EffectType) {
        synchronized(dspLock) {
            activeEffects.remove(type)
        }
    }

    /**
     * Изменяет интенсивность эффекта, ограничивая значение диапазоном 0..1.
     * Changes an effect's intensity, clamping the value to the 0..1 range.
     */
    fun setEffectIntensity(type: EffectType, intensity: Float) {
        synchronized(dspLock) {
            activeEffects[type]?.let {
                activeEffects[type] = it.copy(intensity = intensity.coerceIn(0f, 1f))
            }
        }
    }

    /**
     * Полный сброс: удаляет эффекты и очищает буферы/фазы,
     * чтобы «хвосты» эффектов не просачивались в следующий запуск.
     *
     * Full reset: removes effects and clears buffers/phases so stale
     * effect tails cannot leak into the next run.
     */
    fun clearEffects() {
        synchronized(dspLock) {
            activeEffects.clear()
            delayWritePos = 0
            delayBuffer.fill(0f)
            flangerPos = 0
            flangerPhase = 0f
            flangerBuffer.fill(0f)
            phaserStages = Array(4) { floatArrayOf(0f, 0f) }
        }
    }

    /** Снимок списка активных эффектов. Snapshot of the active effects list. */
    fun getActiveEffects(): List<Effect> = activeEffects.values.toList()

    /** Есть ли активный эффект данного типа. Whether an effect of this type is active. */
    fun isEffectActive(type: EffectType): Boolean = activeEffects.containsKey(type)

    // ======================== ДИСПЕТЧЕР ЭФФЕКТОВ / EFFECT DISPATCH ========================

    /**
     * Выбирает и вызывает реализацию эффекта по его типу.
     * Selects and invokes the effect implementation for the given type.
     */
    private fun applyEffect(
        input: FloatArray,
        type: EffectType,
        intensity: Float,
        sampleRate: Int
    ): FloatArray {
        return when (type) {
            EffectType.ECHO -> applyEcho(input, intensity, sampleRate)
            EffectType.REVERB -> applyReverb(input, intensity)
            EffectType.FLANGER -> applyFlanger(input, intensity, sampleRate)
            EffectType.PHASER -> applyPhaser(input, intensity, sampleRate)
            EffectType.FILTER_LOW_PASS -> applyLowPass(input, intensity, sampleRate)
            EffectType.FILTER_HIGH_PASS -> applyHighPass(input, intensity, sampleRate)
            EffectType.DISTORTION -> applyDistortion(input, intensity)
            EffectType.BITCRUSHER -> applyBitcrusher(input, intensity)
            EffectType.DELAY -> applyDelay(input, intensity, sampleRate)
            EffectType.PAN -> applyPan(input, intensity)
        }
    }

    // ======================== РЕАЛИЗАЦИИ ЭФФЕКТОВ / EFFECT IMPLEMENTATIONS ========================

    /**
     * ЭХО: вход плюс затухающие копии самого себя через обратную связь.
     * ECHO: input plus decaying copies of itself via feedback.
     */
    private fun applyEcho(input: FloatArray, intensity: Float, sampleRate: Int): FloatArray {
        // Время задержки растёт с интенсивностью: 200–500 мс.
        // Delay time grows with intensity: 200–500 ms.
        val delayMs = 200 + intensity * 300
        val delaySamples = (delayMs * sampleRate / 1000).toInt()
        // Глубина обратной связи (до 0.7) определяет число слышимых повторов.
        // Feedback depth (up to 0.7) determines how many repeats stay audible.
        val feedback = intensity * 0.7f
        val output = FloatArray(input.size)

        for (i in input.indices) {
            val delayedIdx = i - delaySamples
            // НАМЕРЕННО: читаем из output, а не из input — обратная связь снимается
            // с уже построенного задержанного выходного сигнала, поэтому каждый
            // повтор содержит предыдущие повторы (классическое затухающее эхо).
            // INTENTIONAL: we read from output, not input — feedback taps the
            // already-built delayed output signal, so each repeat carries the
            // previous repeats (classic decaying echo).
            val delayed = if (delayedIdx >= 0) output[delayedIdx] else 0f
            output[i] = input[i] + delayed * feedback
        }

        return output
    }

    /**
     * РЕВЕРБЕРАЦИЯ: смесь входа с шестью короткими «ранними отражениями».
     * REVERB: mix of the input with six short "early reflections".
     */
    private fun applyReverb(input: FloatArray, intensity: Float): FloatArray {
        val output = FloatArray(input.size)
        // Задержки отражений (в отсчётах) и их убывающие веса.
        // Reflection delays (in samples) and their decreasing weights.
        val delays = intArrayOf(11, 13, 17, 23, 29, 37)
        val gains = floatArrayOf(0.3f, 0.25f, 0.2f, 0.15f, 0.1f, 0.05f)

        for (i in input.indices) {
            var sample = input[i]
            // Добавляем вклад каждого отражения, которое уже успело «дойти».
            // Add the contribution of every reflection that has already "arrived".
            for (j in delays.indices) {
                val idx = i - delays[j]
                if (idx >= 0) {
                    sample += input[idx] * gains[j] * intensity
                }
            }
            // Ограничиваем сумму диапазоном [-1, 1] против клиппинга.
            // Clamp the sum to [-1, 1] to prevent clipping.
            output[i] = sample.coerceIn(-1f, 1f)
        }

        return output
    }

    /**
     * ФЛЭНЖЕР: короткая задержка, модулируемая синусоидальным LFO,
     * подмешивается к сухому сигналу (эффект «реактивного самолёта»).
     *
     * FLANGER: a short delay modulated by a sine LFO is blended into the
     * dry signal (the classic "jet plane" whoosh).
     */
    private fun applyFlanger(input: FloatArray, intensity: Float, sampleRate: Int): FloatArray {
        val output = FloatArray(input.size)
        // Глубина модуляции задержки: 0–10 отсчётов.
        // Delay modulation depth: 0–10 samples.
        val maxDelay = (intensity * 10).toInt()
        // Частота LFO: 0.5 Гц. LFO frequency: 0.5 Hz.
        val rate = 0.5f

        for (i in input.indices) {
            // Пишем отсчёт в кольцевой буфер.
            // Push the sample into the ring buffer.
            flangerPos = (flangerPos + 1) % flangerBuffer.size
            flangerBuffer[flangerPos] = input[i]

            // Продвигаем фазу LFO и заворачиваем её на границе 2π.
            // Advance the LFO phase and wrap it at the 2π boundary.
            flangerPhase += rate / sampleRate
            if (flangerPhase > 2 * PI.toFloat()) flangerPhase -= (2 * PI).toFloat()
            // Мгновенная задержка: синус LFO, отображённый в 0..maxDelay.
            // Instantaneous delay: sine LFO mapped into 0..maxDelay.
            val modDelay = ((sin(flangerPhase * 2 * PI) * 0.5 + 0.5) * maxDelay).toInt()

            // Читаем «отстающий» отсчёт и усредняем его с оригиналом.
            // Read the lagging sample and average it with the original.
            val readPos = (flangerPos - modDelay + flangerBuffer.size) % flangerBuffer.size
            output[i] = (input[i] + flangerBuffer[readPos] * intensity) / 2f
        }

        return output
    }

    /**
     * ФЕЙЗЕР: цепочка all-pass фильтров с LFO-модуляцией частоты;
     * сдвинутый по фазе сигнал подмешивается к сухому.
     *
     * PHASER: a chain of all-pass filters with LFO-modulated frequency;
     * the phase-shifted signal is blended back into the dry one.
     */
    private fun applyPhaser(input: FloatArray, intensity: Float, sampleRate: Int): FloatArray {
        val output = input.copyOf()
        val numStages = phaserStages.size
        // Частота LFO: 0.3 Гц. LFO frequency: 0.3 Hz.
        val rate = 0.3f
        // ИСПРАВЛЕНО (BUG FIX): 2π как Float. Голая константа PI (Double)
        // продвигала angle/freq/f до Double, после чего присваивание Double
        // во Float-поля phaserStages не компилировалось.
        // FIXED (BUG FIX): 2π as Float. The bare PI constant (Double) promoted
        // angle/freq/f to Double, and assigning Double into the Float
        // phaserStages slots failed to compile.
        val twoPi = (2 * PI).toFloat()

        for (i in output.indices) {
            var sample = output[i]
            for (s in 0 until numStages) {
                // Все вычисления строго во Float: PI.toFloat() и .toFloat() у
                // результатов sin/cos не дают типам подняться до Double.
                // All math strictly in Float: PI.toFloat() and .toFloat() on the
                // sin/cos results keep every type from being promoted to Double.
                val angle = rate * i / sampleRate * twoPi + s * (PI.toFloat() / 4f)
                val freq = 200f + sin(angle).toFloat() * 800f * intensity
                val f = 2f * cos(twoPi * freq / sampleRate).toFloat()

                // Разностное уравнение all-pass фильтра текущего каскада.
                // Difference equation of the current stage's all-pass filter.
                val tmp = sample - phaserStages[s][1] * f * 0.5f + phaserStages[s][0] * 0.5f
                // Сдвигаем двухотсчётную историю каскада.
                // Shift the stage's two-sample history.
                phaserStages[s][0] = phaserStages[s][1]
                phaserStages[s][1] = tmp
                sample = tmp
            }
            // Нормированное смешивание сухого и обработанного сигналов.
            // Normalized blend of dry and processed signals.
            output[i] = (input[i] + sample * intensity) / (1f + intensity)
        }

        return output
    }

    /**
     * ФИЛЬТР НИЖНИХ ЧАСТОТ: RC-фильтр первого порядка; частота среза
     * опускается с ростом интенсивности (18200 → 200 Гц).
     *
     * LOW-PASS FILTER: first-order RC filter; the cutoff drops as
     * intensity rises (18200 → 200 Hz).
     */
    private fun applyLowPass(input: FloatArray, intensity: Float, sampleRate: Int): FloatArray {
        // Защита от пустого массива: ниже происходит обращение к элементу [0].
        // Guard against an empty array: element [0] is accessed below.
        if (input.isEmpty()) return input

        // Частота среза. Cutoff frequency.
        val cutoff = 18200f - intensity * 18000f
        // Постоянная времени RC и период дискретизации.
        // RC time constant and sampling period.
        val rc = 1.0f / (2 * PI.toFloat() * cutoff)
        val dt = 1.0f / sampleRate
        // Коэффициент сглаживания фильтра. Filter smoothing coefficient.
        val alpha = dt / (rc + dt)

        val output = FloatArray(input.size)
        output[0] = input[0]

        // Рекурсия фильтра: y[n] = y[n−1] + α·(x[n] − y[n−1]).
        // Filter recursion: y[n] = y[n−1] + α·(x[n] − y[n−1]).
        for (i in 1 until input.size) {
            output[i] = output[i - 1] + alpha * (input[i] - output[i - 1])
        }

        return output
    }

    /**
     * ФИЛЬТР ВЕРХНИХ ЧАСТОТ: первый порядок; частота среза растёт
     * с интенсивностью (0 → 18000 Гц, минимум 1 Гц).
     *
     * HIGH-PASS FILTER: first order; the cutoff rises with intensity
     * (0 → 18000 Hz, minimum 1 Hz).
     */
    private fun applyHighPass(input: FloatArray, intensity: Float, sampleRate: Int): FloatArray {
        // Защита от пустого массива: ниже происходит обращение к элементу [0].
        // Guard against an empty array: element [0] is accessed below.
        if (input.isEmpty()) return input

        // Частота среза (не ниже 1 Гц, чтобы избежать деления на ноль).
        // Cutoff frequency (at least 1 Hz to avoid division by zero).
        val cutoff = intensity * 18000
        val rc = 1.0f / (2 * PI.toFloat() * cutoff.coerceAtLeast(1f))
        val dt = 1.0f / sampleRate
        val alpha = rc / (rc + dt)

        val output = FloatArray(input.size)
        output[0] = input[0]

        // Разностное уравнение ФВЧ: y[n] = α·(y[n−1] + x[n] − x[n−1]).
        // High-pass difference equation: y[n] = α·(y[n−1] + x[n] − x[n−1]).
        for (i in 1 until input.size) {
            output[i] = alpha * (output[i - 1] + input[i] - input[i - 1])
        }

        return output
    }

    /**
     * ДИСТОРШН: арктангенс-вэйвшейпинг с мягким ограничением и
     * кроссфейдом между чистым и перегруженным звуком.
     *
     * DISTORTION: arctangent waveshaping with soft clipping and a
     * crossfade between clean and overdriven sound.
     */
    private fun applyDistortion(input: FloatArray, intensity: Float): FloatArray {
        // Предварительное усиление: 1×…11×. Pre-drive gain: 1×…11×.
        val gain = 1f + intensity * 10f
        // Доля искажённого сигнала (минимум 0.01 — полностью чистый звук не нужен).
        // Share of the distorted signal (min 0.01 — a fully clean sound is never wanted).
        val blend = intensity.coerceAtLeast(0.01f)
        return FloatArray(input.size) { i ->
            val amplified = input[i] * gain
            // Мягкая нелинейная передаточная характеристика (tanh-подобная).
            // Soft nonlinear transfer curve (tanh-like).
            val distorted = ((2f / PI.toFloat()) * atan(amplified)).coerceIn(-1f, 1f)
            input[i] * (1f - blend) + distorted * blend
        }
    }

    /**
     * БИТКРАШЕР: переквантование амплитуды с 16 до 2 бит,
     * дающее характерную «зернистую» ступенчатость.
     *
     * BITCRUSHER: requantizes amplitude from 16 down to 2 bits,
     * producing the characteristic gritty stair-stepping.
     */
    private fun applyBitcrusher(input: FloatArray, intensity: Float): FloatArray {
        // Глубина квантования: чем выше интенсивность, тем меньше бит.
        // Quantization depth: the higher the intensity, the fewer bits.
        val bits = (16 - intensity * 12).toInt().coerceAtLeast(2)
        // Число доступных уровней амплитуды. Number of available amplitude levels.
        val levels = (1 shl bits).toFloat()

        return FloatArray(input.size) { i ->
            // Привязываем отсчёт к ближайшему уровню квантования.
            // Snap the sample to the nearest quantization level.
            (floor(input[i] * levels) / levels).coerceIn(-1f, 1f)
        }
    }

    /**
     * ЗАДЕРЖКА: кольцевой буфер с обратной связью; в отличие от эха,
     * хвост сохраняется между последовательными вызовами [process].
     *
     * DELAY: ring buffer with feedback; unlike echo, its tail persists
     * across consecutive [process] calls.
     */
    private fun applyDelay(input: FloatArray, intensity: Float, sampleRate: Int): FloatArray {
        // Время задержки: 100–600 мс. Delay time: 100–600 ms.
        val delayMs = 100 + intensity * 500
        val delaySamples = (delayMs * sampleRate / 1000).toInt().coerceAtMost(delayBuffer.size - 1)
        val feedback = intensity * 0.6f
        val output = FloatArray(input.size)

        for (i in input.indices) {
            // Читаем отстающий отсчёт из кольцевого буфера.
            // Read the lagging sample from the ring buffer.
            val readPos = (delayWritePos - delaySamples + delayBuffer.size) % delayBuffer.size
            val delayed = delayBuffer[readPos]
            // В буфер возвращается вход плюс ослабленная обратная связь — так растёт хвост.
            // Input plus attenuated feedback goes back into the buffer — this grows the tail.
            delayBuffer[delayWritePos] = input[i] + delayed * feedback
            delayWritePos = (delayWritePos + 1) % delayBuffer.size
            output[i] = (input[i] + delayed * intensity * 0.5f).coerceIn(-1f, 1f)
        }

        return output
    }

    /**
     * ПАНОРАМА: равномощностное распределение чередующегося стерео
     * (чётные отсчёты — левый канал, нечётные — правый).
     *
     * PAN: equal-power panning of interleaved stereo (even samples are
     * the left channel, odd samples the right).
     */
    private fun applyPan(input: FloatArray, intensity: Float): FloatArray {
        // Отображаем интенсивность 0..1 в панораму −1..1 (L → R).
        // Map intensity 0..1 onto pan −1..1 (L → R).
        val pan = (intensity - 0.5f) * 2f
        // Равномощностные коэффициенты усиления каналов.
        // Equal-power channel gains.
        val leftGain = sqrt((1f - pan) / 2f)
        val rightGain = sqrt((1f + pan) / 2f)

        return FloatArray(input.size) { i ->
            if (i % 2 == 0) input[i] * leftGain else input[i] * rightGain
        }
    }
}
