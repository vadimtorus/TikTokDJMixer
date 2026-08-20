package com.tiktokdj.mixer.engine

import kotlin.math.*

class SpectralAnalyzer {

    private val fftSize = 2048
    private val numBands = 32
    private var currentSpectrum = FloatArray(numBands)

    fun analyze(frame: FloatArray): FloatArray {
        val paddedFrame = FloatArray(fftSize)
        frame.copyInto(paddedFrame, 0, 0, minOf(frame.size, fftSize))

        val windowed = applyHannWindow(paddedFrame)
        val fftResult = fft(windowed)

        val magnitudes = FloatArray(fftSize / 2)
        for (i in magnitudes.indices) {
            val real = fftResult[2 * i]
            val imag = fftResult[2 * i + 1]
            magnitudes[i] = sqrt(real * real + imag * imag) / fftSize
        }

        currentSpectrum = downsampleToBands(magnitudes)
        return currentSpectrum.clone()
    }

    fun getSpectrum(): FloatArray = currentSpectrum.clone()

    fun getBandFrequency(bandIndex: Int, sampleRate: Int): Float {
        val binSize = sampleRate.toFloat() / fftSize
        val binsPerBand = (fftSize / 2) / numBands
        val centerBin = bandIndex * binsPerBand + binsPerBand / 2
        return centerBin * binSize
    }

    fun getEnergyInBand(bandIndex: Int): Float {
        return if (bandIndex in currentSpectrum.indices) currentSpectrum[bandIndex] else 0f
    }

    private fun applyHannWindow(data: FloatArray): FloatArray {
        val n = data.size
        return FloatArray(n) { i ->
            val window = (0.5 * (1 - cos(2.0 * PI * i / (n - 1)))).toFloat()
            data[i] * window
        }
    }

    private fun fft(data: FloatArray): FloatArray {
        val n = data.size
        if (n == 1) return floatArrayOf(data[0], 0f)

        if (n % 2 != 0) {
            val padded = FloatArray(n + 1)
            data.copyInto(padded)
            return fft(padded)
        }

        val even = FloatArray(n / 2)
        val odd = FloatArray(n / 2)
        for (i in 0 until n / 2) {
            even[2 * i] = data[2 * i]
            even[2 * i + 1] = data[2 * i + 1]
            odd[2 * i] = data[2 * i + 2]
            odd[2 * i + 1] = data[2 * i + 3]
        }

        val evenFFT = fft(even)
        val oddFFT = fft(odd)
        val result = FloatArray(n)

        for (k in 0 until n / 2) {
            val angle = -2.0 * PI * k / n
            val cosA = cos(angle).toFloat()
            val sinA = sin(angle).toFloat()

            val tReal = cosA * oddFFT[2 * k] - sinA * oddFFT[2 * k + 1]
            val tImag = sinA * oddFFT[2 * k] + cosA * oddFFT[2 * k + 1]

            result[2 * k] = evenFFT[2 * k] + tReal
            result[2 * k + 1] = evenFFT[2 * k + 1] + tImag
            result[2 * k + n] = evenFFT[2 * k] - tReal
            result[2 * k + n + 1] = evenFFT[2 * k + 1] - tImag
        }

        return result
    }

    private fun downsampleToBands(magnitudes: FloatArray): FloatArray {
        val bands = FloatArray(numBands)
        val binsPerBand = magnitudes.size / numBands

        for (band in 0 until numBands) {
            var sum = 0f
            for (i in 0 until binsPerBand) {
                val idx = band * binsPerBand + i
                if (idx < magnitudes.size) {
                    sum += magnitudes[idx]
                }
            }
            bands[band] = sum / binsPerBand
        }

        return normalizeSpectrum(bands)
    }

    private fun normalizeSpectrum(spectrum: FloatArray): FloatArray {
        val maxVal = spectrum.maxOrNull() ?: 1f
        if (maxVal < 1e-10f) return spectrum
        return FloatArray(spectrum.size) { (spectrum[it] / maxVal).coerceIn(0f, 1f) }
    }
}
