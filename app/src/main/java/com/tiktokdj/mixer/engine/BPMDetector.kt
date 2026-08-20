package com.tiktokdj.mixer.engine

import kotlin.math.*

class BPMDetector {

    private val minBPM = 60f
    private val maxBPM = 200f

    fun detectBPM(audioData: FloatArray, sampleRate: Int): Float {
        if (audioData.isEmpty()) return 0f

        val onsetStrength = computeOnsetStrength(audioData, sampleRate)
        if (onsetStrength.isEmpty()) return 0f

        val autocorrelation = computeAutocorrelation(onsetStrength, sampleRate)
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
            val spectrum = computeSpectrumFFT(frame)

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

    private fun computeSpectrumFFT(frame: FloatArray): FloatArray {
        val n = 1 shl (32 - frame.size.countLeadingZeroBits() - 1).coerceAtLeast(1)
        val real = FloatArray(n)
        val imag = FloatArray(n)
        frame.copyInto(real, 0, 0, minOf(frame.size, n))

        fftInPlace(real, imag)

        val spectrum = FloatArray(n / 2)
        for (k in spectrum.indices) {
            spectrum[k] = sqrt(real[k] * real[k] + imag[k] * imag[k]) / n
        }
        return spectrum
    }

    private fun fftInPlace(real: FloatArray, imag: FloatArray) {
        val n = real.size
        if (n == 0) return

        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                var temp = real[i]; real[i] = real[j]; real[j] = temp
                temp = imag[i]; imag[i] = imag[j]; imag[j] = temp
            }
            var k = n / 2
            while (k <= j) {
                j -= k
                k /= 2
            }
            j += k
        }

        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val angle = -2.0 * PI / len
            val wReal = cos(angle).toFloat()
            val wImag = sin(angle).toFloat()

            var i = 0
            while (i < n) {
                var curReal = 1.0f
                var curImag = 0.0f

                for (jj in 0 until halfLen) {
                    val uIdx = i + jj
                    val vIdx = i + jj + halfLen

                    val tReal = curReal * real[vIdx] - curImag * imag[vIdx]
                    val tImag = curReal * imag[vIdx] + curImag * real[vIdx]

                    real[vIdx] = real[uIdx] - tReal
                    imag[vIdx] = imag[uIdx] - tImag
                    real[uIdx] += tReal
                    imag[uIdx] += tImag

                    val newReal = curReal * wReal - curImag * wImag
                    val newImag = curReal * wImag + curImag * wReal
                    curReal = newReal
                    curImag = newImag
                }
                i += len
            }
            len *= 2
        }
    }

    private fun computeAutocorrelation(data: FloatArray, sampleRate: Int): FloatArray {
        val n = data.size
        val fps = sampleRate.toFloat() / 512
        val minLag = (60.0 * fps / maxBPM).toInt().coerceAtLeast(1)
        val maxLag = (60.0 * fps / minBPM).toInt().coerceAtMost(n)
        val result = FloatArray(maxLag + 1)

        val mean = data.average().toFloat()
        var varianceSum = 0f
        for (d in data) {
            val diff = d - mean
            varianceSum += diff * diff
        }
        val variance = varianceSum / n

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
                    var varianceSum = 0f
                    for (k in idx until nextIdx) {
                        val v = audioData[k]
                        varianceSum += v * v
                    }
                    score += varianceSum / (nextIdx - idx).coerceAtLeast(1)
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
