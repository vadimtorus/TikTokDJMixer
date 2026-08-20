package com.tiktokdj.mixer.engine

import kotlin.math.*

class BPMDetector {

    private val minBPM = 60f
    private val maxBPM = 200f

    fun detectBPM(audioData: FloatArray, sampleRate: Int): Float {
        if (audioData.isEmpty()) return 0f

        val onsetStrength = computeOnsetStrength(audioData, sampleRate)
        if (onsetStrength.isEmpty()) return 0f

        val autocorrelation = computeAutocorrelation(onsetStrength)
        val bpm = estimateBPMFromAutocorrelation(autocorrelation, sampleRate)

        return refineBPM(bpm, audioData, sampleRate)
    }

    private fun computeOnsetStrength(data: FloatArray, sampleRate: Int): FloatArray {
        val frameSize = 1024
        val hopSize = 512
        val numFrames = (data.size - frameSize) / hopSize
        if (numFrames <= 0) return floatArrayOf()

        val onsetStrength = FloatArray(numFrames)
        var prevSpectrum = FloatArray(frameSize / 2)

        for (i in 0 until numFrames) {
            val start = i * hopSize
            val frame = data.copyOfRange(start, minOf(start + frameSize, data.size))
            val spectrum = computeSpectrum(frame)

            var flux = 0f
            for (j in spectrum.indices) {
                val diff = spectrum[j] - prevSpectrum[j]
                if (diff > 0) flux += diff
            }
            onsetStrength[i] = flux
            prevSpectrum = spectrum
        }

        return normalize(onsetStrength)
    }

    private fun computeSpectrum(frame: FloatArray): FloatArray {
        val n = frame.size
        val spectrum = FloatArray(n / 2)

        for (k in 0 until n / 2) {
            var real = 0f
            var imag = 0f
            for (i in 0 until n) {
                val angle = (2.0 * PI * k * i / n).toFloat()
                real += frame[i] * cos(angle)
                imag -= frame[i] * sin(angle)
            }
            spectrum[k] = sqrt(real * real + imag * imag) / n
        }
        return spectrum
    }

    private fun computeAutocorrelation(data: FloatArray): FloatArray {
        val n = data.size
        val fps = 44100f / 512
        val minLag = (60.0 * fps / maxBPM).toInt().coerceAtLeast(1)
        val maxLag = (60.0 * fps / minBPM).toInt().coerceAtMost(n)
        val result = FloatArray(maxLag + 1)

        val mean = data.average().toFloat()
        val variance = data.map { (it - mean) * (it - mean) }.average().toFloat()

        if (variance < 1e-10f) return result

        for (lag in minLag..maxLag) {
            var sum = 0f
            for (i in 0 until n - lag) {
                sum += (data[i] - mean) * (data[i + lag] - mean)
            }
            result[lag] = sum / (variance * n)
        }
        return result
    }

    private fun estimateBPMFromAutocorrelation(
        autocorr: FloatArray,
        sampleRate: Int
    ): Float {
        val fps = sampleRate.toFloat() / 512
        var bestBPM = 0f
        var bestCorr = -1f

        val minLag = (60.0 * fps / maxBPM).toInt().coerceAtLeast(1)
        val maxLag = (60.0 * fps / minBPM).toInt().coerceAtMost(autocorr.size - 1)

        for (lag in minLag..maxLag) {
            val bpm = 60f * fps / lag
            val corr = autocorr[lag]

            if (corr > bestCorr) {
                bestCorr = corr
                bestBPM = bpm
            }
        }

        return bestBPM
    }

    private fun refineBPM(initialBPM: Float, audioData: FloatArray, sampleRate: Int): Float {
        if (initialBPM <= 0f) return 0f

        var bestBPM = initialBPM
        var bestScore = 0f

        for (delta in -2f..2f step 0.1f) {
            val candidateBPM = initialBPM + delta
            if (candidateBPM < minBPM || candidateBPM > maxBPM) continue

            val beatInterval = (60.0 * sampleRate / candidateBPM).toFloat()
            var score = 0f
            var pos = 0f

            while (pos < audioData.size - beatInterval) {
                val idx = pos.toInt()
                val nextIdx = (pos + beatInterval).toInt().coerceAtMost(audioData.size - 1)

                if (idx < audioData.size && nextIdx < audioData.size) {
                    val energy = audioData.sliceArray(idx until nextIdx)
                    val variance = energy.map { it * it }.average().toFloat()
                    score += variance
                }
                pos += beatInterval
            }

            if (score > bestScore) {
                bestScore = score
                bestBPM = candidateBPM
            }
        }

        return (bestBPM * 10).roundToInt() / 10f
    }

    private fun normalize(data: FloatArray): FloatArray {
        val maxVal = data.maxOrNull() ?: 1f
        if (maxVal < 1e-10f) return data
        return FloatArray(data.size) { data[it] / maxVal }
    }
}
