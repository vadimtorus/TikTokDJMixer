package com.tiktokdj.mixer.engine

import com.tiktokdj.mixer.model.Effect
import com.tiktokdj.mixer.model.EffectType
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

class EffectsProcessor {

    private val activeEffects = ConcurrentHashMap<EffectType, Effect>()
    private val delayBuffer = FloatArray(48000)
    private var delayWritePos = 0
    private val delayMaxSamples = 48000

    private val flangerBuffer = FloatArray(48000)
    private var flangerPos = 0
    private var flangerPhase = 0f

    fun process(input: FloatArray, sampleRate: Int): FloatArray {
        if (activeEffects.isEmpty()) return input

        var output = input.copyOf()
        for ((type, effect) in activeEffects) {
            output = applyEffect(output, type, effect.intensity, sampleRate)
        }
        return output
    }

    fun addEffect(effect: Effect) {
        activeEffects[effect.type] = effect
    }

    fun removeEffect(type: EffectType) {
        activeEffects.remove(type)
    }

    fun setEffectIntensity(type: EffectType, intensity: Float) {
        activeEffects[type]?.let {
            activeEffects[type] = it.copy(intensity = intensity.coerceIn(0f, 1f))
        }
    }

    fun clearEffects() {
        activeEffects.clear()
        delayWritePos = 0
        delayBuffer.fill(0f)
        flangerPos = 0
        flangerPhase = 0f
        flangerBuffer.fill(0f)
    }

    fun getActiveEffects(): List<Effect> = activeEffects.values.toList()

    fun isEffectActive(type: EffectType): Boolean = activeEffects.containsKey(type)

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

    private fun applyEcho(input: FloatArray, intensity: Float, sampleRate: Int): FloatArray {
        val delayMs = 200 + intensity * 300
        val delaySamples = (delayMs * sampleRate / 1000).toInt()
        val feedback = intensity * 0.7f
        val output = FloatArray(input.size)

        for (i in input.indices) {
            val delayedIdx = i - delaySamples
            val delayed = if (delayedIdx >= 0) output[delayedIdx] else 0f
            output[i] = input[i] + delayed * feedback
        }

        return output
    }

    private fun applyReverb(input: FloatArray, intensity: Float): FloatArray {
        val output = FloatArray(input.size)
        val delays = intArrayOf(11, 13, 17, 23, 29, 37)
        val gains = floatArrayOf(0.3f, 0.25f, 0.2f, 0.15f, 0.1f, 0.05f)

        for (i in input.indices) {
            var sample = input[i]
            for (j in delays.indices) {
                val idx = i - delays[j]
                if (idx >= 0) {
                    sample += input[idx] * gains[j] * intensity
                }
            }
            output[i] = sample.coerceIn(-1f, 1f)
        }

        return output
    }

    private fun applyFlanger(input: FloatArray, intensity: Float, sampleRate: Int): FloatArray {
        val output = FloatArray(input.size)
        val maxDelay = (intensity * 10).toInt()
        val rate = 0.5f

        for (i in input.indices) {
            flangerPos = (flangerPos + 1) % flangerBuffer.size
            flangerBuffer[flangerPos] = input[i]

            flangerPhase += rate / sampleRate
            val modDelay = ((sin(flangerPhase * 2 * PI) * 0.5 + 0.5) * maxDelay).toInt()

            val readPos = (flangerPos - modDelay + flangerBuffer.size) % flangerBuffer.size
            output[i] = (input[i] + flangerBuffer[readPos] * intensity) / 2f
        }

        return output
    }

    private fun applyPhaser(input: FloatArray, intensity: Float, sampleRate: Int): FloatArray {
        val output = input.copyOf()
        val numStages = 4
        val stages = Array(numStages) { floatArrayOf(0f, 0f) }
        val rate = 0.3f

        for (i in output.indices) {
            var sample = output[i]
            for (s in 0 until numStages) {
                val freq = 200 + sin(i * rate / sampleRate * 2 * PI + s * PI / 4) * 800 * intensity
                val f = 2 * cos(freq * 2 * PI / sampleRate)

                val tmp = sample - stages[s][1] * f * 0.5f + stages[s][0] * 0.5f
                stages[s][0] = stages[s][1]
                stages[s][1] = tmp
                sample = tmp
            }
            output[i] = (input[i] + sample * intensity) / (1 + intensity)
        }

        return output
    }

    private fun applyLowPass(input: FloatArray, intensity: Float, sampleRate: Int): FloatArray {
        val cutoff = 200 + intensity * 18000
        val rc = 1.0f / (2 * PI.toFloat() * cutoff)
        val dt = 1.0f / sampleRate
        val alpha = dt / (rc + dt)

        val output = FloatArray(input.size)
        output[0] = input[0]

        for (i in 1 until input.size) {
            output[i] = output[i - 1] + alpha * (input[i] - output[i - 1])
        }

        return output
    }

    private fun applyHighPass(input: FloatArray, intensity: Float, sampleRate: Int): FloatArray {
        val cutoff = intensity * 18000
        val rc = 1.0f / (2 * PI.toFloat() * cutoff.coerceAtLeast(1f))
        val dt = 1.0f / sampleRate
        val alpha = rc / (rc + dt)

        val output = FloatArray(input.size)
        output[0] = input[0]

        for (i in 1 until input.size) {
            output[i] = alpha * (output[i - 1] + input[i] - input[i - 1])
        }

        return output
    }

    private fun applyDistortion(input: FloatArray, intensity: Float): FloatArray {
        val gain = 1f + intensity * 10f
        return FloatArray(input.size) { i ->
            val amplified = input[i] * gain
            ((2f / PI.toFloat()) * atan(amplified * intensity)).coerceIn(-1f, 1f)
        }
    }

    private fun applyBitcrusher(input: FloatArray, intensity: Float): FloatArray {
        val bits = (16 - intensity * 12).toInt().coerceAtLeast(2)
        val levels = (1 shl bits).toFloat()

        return FloatArray(input.size) { i ->
            (floor(input[i] * levels) / levels).coerceIn(-1f, 1f)
        }
    }

    private fun applyDelay(input: FloatArray, intensity: Float, sampleRate: Int): FloatArray {
        val delayMs = 100 + intensity * 500
        val delaySamples = (delayMs * sampleRate / 1000).toInt()
        val feedback = intensity * 0.6f
        val output = FloatArray(input.size)

        for (i in input.indices) {
            val readPos = (delayWritePos - delaySamples + delayMaxSamples) % delayMaxSamples
            val delayed = delayBuffer[readPos]
            delayBuffer[delayWritePos] = input[i] + delayed * feedback
            delayWritePos = (delayWritePos + 1) % delayMaxSamples
            output[i] = (input[i] + delayed * intensity * 0.5f).coerceIn(-1f, 1f)
        }

        return output
    }

    private fun applyPan(input: FloatArray, intensity: Float): FloatArray {
        val pan = (intensity - 0.5f) * 2f
        val leftGain = sqrt((1f - pan) / 2f)
        val rightGain = sqrt((1f + pan) / 2f)

        return FloatArray(input.size) { i ->
            if (i % 2 == 0) input[i] * leftGain else input[i] * rightGain
        }
    }
}
