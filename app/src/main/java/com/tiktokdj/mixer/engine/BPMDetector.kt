package com.tiktokdj.mixer.engine

import kotlin.math.*

/**
 * Детектор темпа (BPM) по PCM-данным.
 *
 * Алгоритм / Algorithm:
 * 1. [computeOnsetStrength] — спектральный поток (spectral flux) на кадрах 1024/512:
 *    сумма положительных приращений спектра = сила «удара»;
 * 2. [computeAutocorrelation] — автокорреляция огибающей ударов в диапазоне
 *    лагов, соответствующих 60–200 BPM;
 * 3. [estimateBPMFromAutocorrelation] — лаг с максимальной корреляцией
 *    даёт первичную оценку темпа;
 * 4. [refineBPM] — локальный поиск ±2 BPM шагом 0.1: выбирается кандидат,
 *    при котором энергия между ударами минимальна (биты попадают в пики).
 *
 * Tempo (BPM) detector for PCM data.
 *
 * Algorithm / Algorithm:
 * 1. [computeOnsetStrength] — spectral flux over 1024/512 frames:
 *    the sum of positive spectral increments = onset strength;
 * 2. [computeAutocorrelation] — autocorrelation of the onset envelope across
 *    lags corresponding to 60–200 BPM;
 * 3. [estimateBPMFromAutocorrelation] — the lag with maximum correlation
 *    yields the initial tempo estimate;
 * 4. [refineBPM] — a local ±2 BPM search with a 0.1 step picks the candidate
 *    that minimizes inter-beat energy (beats land on peaks).
 */
class BPMDetector {

    /** Нижняя граница поиска темпа / Lower bound of the tempo search */
    private val minBPM = 60f

    /** Верхняя граница поиска темпа / Upper bound of the tempo search */
    private val maxBPM = 200f

    /**
     * Определяет темп трека по аудиоданным.
     * Detects the track tempo from audio data.
     *
     * @param audioData Нормализованные PCM-сэмплы [-1..1] / normalized PCM samples [-1..1]
     * @param sampleRate Частота дискретизации / sample rate
     * @return BPM с точностью до 0.1; 0, если определить не удалось /
     *         BPM accurate to 0.1; 0 when detection fails
     */
    fun detectBPM(audioData: FloatArray, sampleRate: Int): Float {
        // Пустой вход анализировать нечего.
        // Nothing to analyze on empty input.
        if (audioData.isEmpty()) return 0f

        // Шаг 1: огибающая силы ударов через спектральный поток.
        // Step 1: onset-strength envelope via spectral flux.
        val onsetStrength = computeOnsetStrength(audioData, sampleRate)
        if (onsetStrength.isEmpty()) return 0f

        // Шаги 2-3: автокорреляция огибающей и первичная оценка BPM.
        // Steps 2-3: envelope autocorrelation and the initial BPM estimate.
        val autocorrelation = computeAutocorrelation(onsetStrength, sampleRate)
        val bpm = estimateBPMFromAutocorrelation(autocorrelation, sampleRate)

        // Шаг 4: уточнение оценки локальным перебором.
        // Step 4: refine the estimate via a local search.
        return refineBPM(bpm, audioData, sampleRate)
    }

    /**
     * Считает огибающую силы ударов (onset strength) как спектральный поток:
     * для каждого кадра суммируются только ПОЛОЖИТЕЛЬНЫЕ приращения спектра —
     * так реагируем на появление энергии (атаку), игнорируя её затухание.
     *
     * Computes the onset-strength envelope as spectral flux: for each frame only
     * POSITIVE spectral increments are summed — this reacts to energy appearing
     * (attacks) while ignoring its decay.
     *
     * @param data Входные сэмплы / input samples
     * @param sampleRate Частота дискретизации / sample rate
     * @return Нормированная огибающая / normalized envelope
     */
    private fun computeOnsetStrength(data: FloatArray, sampleRate: Int): FloatArray {
        // Размер окна БПФ и шаг между кадрами (50% перекрытие).
        // FFT window size and hop between frames (50% overlap).
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

            // Спектральный поток: только положительная разница соседних спектров.
            // Spectral flux: only the positive difference of adjacent spectra.
            var flux = 0f
            for (j in spectrum.indices) {
                val diff = spectrum[j] - prevSpectrum[j]
                if (diff > 0) flux += diff
            }
            onsetStrength[i] = flux
            prevSpectrum = spectrum
        }

        // Нормируем к максимуму, чтобы автокорреляция не зависела от громкости.
        // Normalize to the peak so autocorrelation is loudness-independent.
        return normalize(onsetStrength)
    }

    /**
     * Считает амплитудный спектр кадра: дополняет нулями до степени двойки,
     * выполняет БПФ и возвращает модули первых n/2 бинов.
     *
     * Computes a frame's magnitude spectrum: zero-pads to a power of two,
     * runs the FFT and returns the magnitudes of the first n/2 bins.
     */
    private fun computeSpectrumFFT(frame: FloatArray): FloatArray {
        // Округляем размер БПФ вверх до ближайшей степени двойки.
        // Round the FFT size up to the nearest power of two.
        val n = 1 shl (32 - frame.size.countLeadingZeroBits() - 1).coerceAtLeast(1)
        val real = FloatArray(n)
        val imag = FloatArray(n)
        frame.copyInto(real, 0, 0, minOf(frame.size, n))

        fftInPlace(real, imag)

        // Модуль комплексного бина, нормированный на число точек.
        // Complex bin magnitude normalized by the point count.
        val spectrum = FloatArray(n / 2)
        for (k in spectrum.indices) {
            spectrum[k] = sqrt(real[k] * real[k] + imag[k] * imag[k]) / n
        }
        return spectrum
    }

    /**
     * Итеративное БПФ Кули-Тьюки по основанию 2 (radix-2 Cooley-Tukey):
     * сначала битреверс-перестановка индексов, затем каскады бабочек.
     * Работает in-place, без аллокаций внутри циклов.
     *
     * Iterative radix-2 Cooley-Tukey FFT: bit-reversal index permutation first,
     * then cascades of butterfly operations. Runs in-place with no allocations
     * inside the loops.
     */
    private fun fftInPlace(real: FloatArray, imag: FloatArray) {
        val n = real.size
        if (n == 0) return

        // Фаза 1: битреверс-перестановка входных индексов.
        // Phase 1: bit-reversal permutation of the input indices.
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

        // Фаза 2: каскады «бабочек» длиной len = 2, 4, 8, ... n.
        // Phase 2: butterfly cascades of length len = 2, 4, 8, ... n.
        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            // Корень из единицы: поворот на -2π/len.
            // Root of unity: rotation by -2π/len.
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

                    // «Бабочка»: комплексное умножение и сложение/вычитание.
                    // "Butterfly": complex multiply plus add/subtract.
                    val tReal = curReal * real[vIdx] - curImag * imag[vIdx]
                    val tImag = curReal * imag[vIdx] + curImag * real[vIdx]

                    real[vIdx] = real[uIdx] - tReal
                    imag[vIdx] = imag[uIdx] - tImag
                    real[uIdx] += tReal
                    imag[uIdx] += tImag

                    // Пошаговое накопление поворачивающего множителя (w^jj).
                    // Incremental accumulation of the twiddle factor (w^jj).
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
     * Нормированная автокорреляция огибающей ударов.
     * Перебираются только лаги, соответствующие диапазону [minBPM..maxBPM]:
     * lag = 60·fps / BPM, где fps — частота кадров огибающей (sampleRate/hop).
     *
     * Normalized autocorrelation of the onset envelope.
     * Only lags matching the [minBPM..maxBPM] range are evaluated:
     * lag = 60·fps / BPM, where fps is the envelope frame rate (sampleRate/hop).
     */
    private fun computeAutocorrelation(data: FloatArray, sampleRate: Int): FloatArray {
        val n = data.size
        // Частота кадров огибающей: один кадр на hopSize=512 сэмплов.
        // Envelope frame rate: one frame per hopSize=512 samples.
        val fps = sampleRate.toFloat() / 512
        val minLag = (60.0 * fps / maxBPM).toInt().coerceAtLeast(1)
        val maxLag = (60.0 * fps / minBPM).toInt().coerceAtMost(n)
        val result = FloatArray(maxLag + 1)

        // Среднее и дисперсия нужны для нормировки коэффициента корреляции в [-1..1].
        // Mean and variance normalize the correlation coefficient into [-1..1].
        val mean = data.average().toFloat()
        var varianceSum = 0f
        for (d in data) {
            val diff = d - mean
            varianceSum += diff * diff
        }
        val variance = varianceSum / n

        // Постоянный (тишина) или вырожденный сигнал — корреляции нет.
        // Constant (silence) or degenerate signal — no correlation exists.
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

    /**
     * Первичная оценка BPM: лаг автокорреляции с максимальным коэффициентом
     * переводится обратно в темп: BPM = 60·fps / lag.
     *
     * Initial BPM estimate: the autocorrelation lag with the highest coefficient
     * is converted back into tempo: BPM = 60·fps / lag.
     */
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

    /**
     * Уточнение BPM: перебор кандидатов initialBPM ± 2 с шагом 0.1.
     * Для каждого кандидата суммируется энергия сигнала в окнах,
     * выровненных по предполагаемым ударам; побеждает кандидат с
     * МАКСИМАЛЬНОЙ энергией под битами (биты совпали с реальными атаками).
     *
     * BPM refinement: candidates initialBPM ± 2 with a 0.1 step.
     * For each candidate the signal energy is summed over windows aligned to
     * the hypothesized beats; the candidate with MAXIMAL under-beat energy wins
     * (its beats matched the real attacks).
     */
    private fun refineBPM(initialBPM: Float, audioData: FloatArray, sampleRate: Int): Float {
        if (initialBPM <= 0f) return 0f

        var bestBPM = initialBPM
        var bestScore = 0f

        for (delta in -2f..2f step 0.1f) {
            val candidateBPM = initialBPM + delta
            if (candidateBPM < minBPM || candidateBPM > maxBPM) continue

            // Интервал между ударами кандидата в сэмплах.
            // The candidate's beat interval in samples.
            val beatInterval = (60.0 * sampleRate / candidateBPM).toFloat()
            var score = 0f
            var pos = 0f

            while (pos < audioData.size - beatInterval) {
                val idx = pos.toInt()
                val nextIdx = (pos + beatInterval).toInt().coerceAtMost(audioData.size - 1)

                if (idx < audioData.size && nextIdx < audioData.size) {
                    // Средняя энергия в окне между двумя ударами.
                    // Mean energy within the window between two beats.
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

        // Округляем до одного знака после запятой.
        // Round to a single decimal place.
        return (bestBPM * 10).roundToInt() / 10f
    }

    /**
     * Нормировка массива к максимуму (пик становится равным 1).
     * Тихий/нулевой сигнал возвращается без изменений.
     *
     * Peak-normalizes an array (the peak becomes 1).
     * Quiet/silent input is returned unchanged.
     */
    private fun normalize(data: FloatArray): FloatArray {
        val maxVal = data.maxOrNull() ?: 1f
        if (maxVal < 1e-10f) return data
        return FloatArray(data.size) { data[it] / maxVal }
    }
}
