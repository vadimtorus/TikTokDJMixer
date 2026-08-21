package com.tiktokdj.mixer.engine

import kotlin.math.*

/**
 * Спектральный анализатор на базе БПФ (FFT) для визуализации частотного спектра.
 * FFT-based spectral analyzer for frequency spectrum visualization.
 *
 * Алгоритм: оконная функция Ханна -> БПФ -> амплитудный спектр -> усреднение по 32 полосам.
 * Algorithm: Hann window -> FFT -> magnitude spectrum -> averaging into 32 bands.
 *
 * Потокобезопасен: [currentSpectrum] помечен @Volatile.
 * Thread-safe: [currentSpectrum] is @Volatile.
 */
class SpectralAnalyzer {

    /** Размер FFT (степень двойки для efficiency). FFT size (power of two for efficiency). */
    private val fftSize = 2048

    /** Количество полос спектра для отображения в UI. Number of spectrum bands for UI display. */
    private val numBands = 32

    /** Текущий спектр (32 полосы, значения 0..1). Volatile для чтения из UI-потока.
     *  Current spectrum (32 bands, values 0..1). Volatile for UI thread reads. */
    @Volatile
    private var currentSpectrum = FloatArray(numBands)

    // ======================== ОСНОВНОЙ АНАЛИЗ / MAIN ANALYSIS ========================

    /**
     * Анализирует аудиокадр и возвращает спектрограмму из 32 полос.
     * Analyzes an audio frame and returns a 32-band spectrogram.
     *
     * @param inputPCM PCM-данные (float [-1..1]). PCM data (float [-1..1]).
     * @return массив из 32 значений [0..1], каждое — средняя амплитуда полосы.
     *         Array of 32 values [0..1], each being the average amplitude of its band.
     */
    fun analyze(inputPCM: FloatArray): FloatArray {
        // Дополняем вход до fftSize нулями (zero-padding для точности БПФ).
        // Pad input to fftSize with zeros (zero-padding for FFT accuracy).
        val paddedFrame = FloatArray(fftSize)
        inputPCM.copyInto(paddedFrame, 0, 0, minOf(inputPCM.size, fftSize))

        // Применяем оконную функцию Ханна для уменьшения утечки спектра.
        // Apply Hann window function to reduce spectral leakage.
        val windowed = applyHannWindow(paddedFrame)

        // Вычисляем БПФ и получаем комплексный спектр (real, imag).
        // Compute FFT and obtain the complex spectrum (real, imag).
        val (real, imag) = fftReal(windowed)

        // Вычисляем амплитудный спектр (модуль комплексного числа).
        // Compute magnitude spectrum (modulus of complex numbers).
        val magnitudes = FloatArray(fftSize / 2)
        for (i in magnitudes.indices) {
            magnitudes[i] = sqrt(real[i] * real[i] + imag[i] * imag[i]) / fftSize
        }

        // Усредняем по полосам и нормализуем для UI.
        // Average into bands and normalize for UI.
        currentSpectrum = downsampleToBands(magnitudes)
        return currentSpectrum.clone()
    }

    // ======================== ПУБЛИЧНЫЙ API / PUBLIC API ========================

    /** Возвращает клон текущего спектра (32 полосы). Returns a clone of the current spectrum (32 bands). */
    fun getSpectrum(): FloatArray = currentSpectrum.clone()

    /**
     * Возвращает центральную частоту полосы (Гц).
     * Returns the center frequency of a band (Hz).
     *
     * @param bandIndex индекс полосы [0..31]. Band index [0..31].
     * @param sampleRate частота дискретизации (Гц). Sample rate (Hz).
     */
    fun getBandFrequency(bandIndex: Int, sampleRate: Int): Float {
        val binSize = sampleRate.toFloat() / fftSize
        val binsPerBand = (fftSize / 2) / numBands
        val centerBin = bandIndex * binsPerBand + binsPerBand / 2
        return centerBin * binSize
    }

    /**
     * Возвращает энергию в конкретной полосе [0..1].
     * Returns the energy in a specific band [0..1].
     */
    fun getEnergyInBand(bandIndex: Int): Float {
        return if (bandIndex in currentSpectrum.indices) currentSpectrum[bandIndex] else 0f
    }

    // ======================== ВНУТРЕННИЕ МЕТОДЫ / INTERNAL ========================

    /**
     * Оконная функция Ханна: уменьшает утечку спектра на границах кадра.
     * Hann window function: reduces spectral leakage at frame boundaries.
     *
     * Формула: w(n) = 0.5 * (1 - cos(2πn / (N-1))).
     * Formula: w(n) = 0.5 * (1 - cos(2πn / (N-1))).
     */
    private fun applyHannWindow(data: FloatArray): FloatArray {
        val n = data.size
        return FloatArray(n) { i ->
            val window = (0.5 * (1 - cos(2.0 * PI * i / (n - 1)))).toFloat()
            data[i] * window
        }
    }

    /**
     * БПФ для действительного сигнала: возвращает пару (real, imag).
     * FFT for real signal: returns (real, imag) pair.
     */
    private fun fftReal(input: FloatArray): Pair<FloatArray, FloatArray> {
        val n = input.size
        val real = FloatArray(n)
        val imag = FloatArray(n)
        input.copyInto(real)

        // БПФ «in-place»: результат перезаписывает входные массивы.
        // In-place FFT: results overwrite the input arrays.
        fftInPlace(real, imag)

        return Pair(real, imag)
    }

    /**
     * БПФ «in-place» (алгоритм Кули-Тьюки, бит-реверс-порядок).
     * In-place FFT (Cooley-Tukey algorithm, bit-reversal order).
     *
     * Сложность: O(N log N). Complexity: O(N log N).
     */
    private fun fftInPlace(real: FloatArray, imag: FloatArray) {
        val n = real.size
        if (n == 0) return

        // Бит-реверс-перестановка (bit-reversal permutation).
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

        // Бабочки (butterfly operations) по.stage.
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

                    // Бабочка Кули-Тьюки / Cooley-Tukey butterfly.
                    val tReal = curReal * real[vIdx] - curImag * imag[vIdx]
                    val tImag = curReal * imag[vIdx] + curImag * real[vIdx]

                    real[vIdx] = real[uIdx] - tReal
                    imag[vIdx] = imag[uIdx] - tImag
                    real[uIdx] += tReal
                    imag[uIdx] += tImag

                    // Вращение (twiddle factor multiplication).
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

    /**
     * Усредняет амплитудный спектр по [numBands] полосам (log-подобное распределение).
     * Averages the magnitude spectrum into [numBands] bands (log-like distribution).
     */
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

    /**
     * Линейная нормализация спектра: максимум = 1.0.
     * Linear spectrum normalization: maximum = 1.0.
     */
    private fun normalizeSpectrum(spectrum: FloatArray): FloatArray {
        val maxVal = spectrum.maxOrNull() ?: 1f
        if (maxVal < 1e-10f) return spectrum
        return FloatArray(spectrum.size) { (spectrum[it] / maxVal).coerceIn(0f, 1f) }
    }
}
