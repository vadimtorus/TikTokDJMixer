package com.tiktokdj.mixer.engine

import kotlin.math.*

class SpectralAnalyzer {

    private val fftSize = 2048
    private val numBands = 32
    @Volatile
    private var currentSpectrum = FloatArray(numBands)

    fun analyze(frame: FloatArray): FloatArray {
        val paddedFrame = FloatArray(fftSize)
        frame.copyInto(paddedFrame, 0, 0, minOf(frame.size, fftSize))

        val windowed = applyHannWindow(paddedFrame)
        val (real, imag) = fftReal(windowed)

        val magnitudes = FloatArray(fftSize / 2)
        for (i in magnitudes.indices) {
            magnitudes[i] = sqrt(real[i] * real[i] + imag[i] * imag[i]) / fftSize
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

    private fun fftReal(input: FloatArray): Pair<FloatArray, FloatArray> {
        val n = input.size
        val real = FloatArray(n)
        val imag = FloatArray(n)
        input.copyInto(real)

        fftInPlace(real, imag)

        return Pair(real, imag)
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
