package com.example.service.audio

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Snapshot of real-time audio visualization metrics extracted directly
 * from the playing audio PCM stream.
 */
data class RealtimeAudioState(
    val waveform: FloatArray = FloatArray(64) { 0f },
    val fftBands: FloatArray = FloatArray(48) { 0f },
    val bassEnergy: Float = 0f,
    val lowMidEnergy: Float = 0f,
    val midEnergy: Float = 0f,
    val highMidEnergy: Float = 0f,
    val trebleEnergy: Float = 0f,
    val overallAmplitude: Float = 0f,
    val beatPulse: Float = 1.0f,
    val isPlaying: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RealtimeAudioState
        return isPlaying == other.isPlaying &&
                overallAmplitude == other.overallAmplitude &&
                beatPulse == other.beatPulse &&
                bassEnergy == other.bassEnergy &&
                trebleEnergy == other.trebleEnergy &&
                waveform.contentEquals(other.waveform) &&
                fftBands.contentEquals(other.fftBands)
    }

    override fun hashCode(): Int {
        var result = waveform.contentHashCode()
        result = 31 * result + fftBands.contentHashCode()
        result = 31 * result + overallAmplitude.hashCode()
        result = 31 * result + beatPulse.hashCode()
        result = 31 * result + isPlaying.hashCode()
        return result
    }
}

/**
 * High-performance, zero-allocation real-time audio analysis engine.
 * Processes real decoded PCM audio buffers from ExoPlayer in real-time.
 */
class RealtimeAudioVisualizerEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _audioState = MutableStateFlow(RealtimeAudioState())
    val audioState: StateFlow<RealtimeAudioState> = _audioState.asStateFlow()

    // Pre-allocated buffers for zero-garbage collection processing
    private val fftSize = 256
    private val halfFft = fftSize / 2
    private val numBands = 48

    private val pcmRingBuffer = FloatArray(fftSize)
    private var pcmRingIndex = 0

    private val windowedPcm = FloatArray(fftSize)
    private val realBuffer = FloatArray(fftSize)
    private val imagBuffer = FloatArray(fftSize)
    private val magnitudes = FloatArray(halfFft)

    private val rawWaveform = FloatArray(64)
    private val smoothWaveform = FloatArray(64)
    private val rawBands = FloatArray(numBands)
    private val smoothBands = FloatArray(numBands)

    // Pre-computed Hann Window table
    private val hannWindow = FloatArray(fftSize) { i ->
        (0.5f * (1.0f - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
    }

    // Beat Detection state
    private var smoothBass = 0f
    private var smoothMid = 0f
    private var smoothTreble = 0f
    private var smoothAmplitude = 0f
    private var currentBeatPulse = 1.0f
    private var bassEnergyHistory = FloatArray(20)
    private var historyIndex = 0
    private var lastBeatTimestampMs = 0L

    @Volatile
    private var isPlaying = false
    private var lastEmitTimestamp = 0L

    fun setPlaying(playing: Boolean) {
        isPlaying = playing
        if (!playing) {
            // Decay smoothly to zero when paused/stopped
            currentBeatPulse = 1.0f
            smoothAmplitude *= 0.3f
            smoothBass *= 0.3f
            smoothMid *= 0.3f
            smoothTreble *= 0.3f
            for (i in smoothBands.indices) {
                smoothBands[i] *= 0.3f
            }
            for (i in smoothWaveform.indices) {
                smoothWaveform[i] *= 0.3f
            }
            emitState()
        }
    }

    fun reset() {
        isPlaying = false
        currentBeatPulse = 1.0f
        smoothAmplitude = 0f
        smoothBass = 0f
        smoothMid = 0f
        smoothTreble = 0f
        smoothBands.fill(0f)
        smoothWaveform.fill(0f)
        _audioState.value = RealtimeAudioState(isPlaying = false)
    }

    /**
     * Process raw PCM byte buffer coming from ExoPlayer AudioProcessor.
     */
    @Synchronized
    fun processPcmBuffer(
        buffer: ByteBuffer,
        sampleRate: Int,
        channelCount: Int,
        encoding: Int
    ) {
        if (!isPlaying) {
            isPlaying = true
        }

        val remaining = buffer.remaining()
        if (remaining <= 0) return

        val channels = channelCount.coerceAtLeast(1)
        var sumSquares = 0.0
        var sampleCount = 0

        // Read 16-bit PCM samples
        val shortBuffer = buffer.asShortBuffer()
        val numShorts = shortBuffer.remaining()

        var i = 0
        while (i < numShorts) {
            // Mix down multi-channel to mono float [-1.0f, 1.0f]
            var monoSample = 0f
            for (c in 0 until channels) {
                if (i < numShorts) {
                    val s = shortBuffer.get(i)
                    monoSample += (s / 32768.0f)
                    i++
                }
            }
            monoSample /= channels

            // Push into PCM Ring Buffer for FFT and waveform extraction
            pcmRingBuffer[pcmRingIndex] = monoSample
            pcmRingIndex = (pcmRingIndex + 1) % fftSize

            sumSquares += (monoSample * monoSample)
            sampleCount++
        }

        // Calculate instantaneous RMS Amplitude
        val instantRms = if (sampleCount > 0) sqrt(sumSquares / sampleCount).toFloat().coerceIn(0f, 1f) else 0f
        smoothAmplitude = smoothAmplitude * 0.4f + instantRms * 0.6f

        // Throttle emission to ~50-60 Hz (every 18ms) to ensure smooth 60fps rendering without CPU waste
        val now = System.currentTimeMillis()
        if (now - lastEmitTimestamp < 18) {
            return
        }
        lastEmitTimestamp = now

        // 1. Extract Instantaneous Waveform (64 points sampled across the ring buffer)
        val step = fftSize / 64
        for (w in 0 until 64) {
            val idx = (pcmRingIndex + w * step) % fftSize
            val rawVal = pcmRingBuffer[idx]
            smoothWaveform[w] = smoothWaveform[w] * 0.35f + rawVal * 0.65f
        }

        // 2. Perform Fast Fourier Transform (FFT) on Hann-windowed PCM
        for (f in 0 until fftSize) {
            val idx = (pcmRingIndex + f) % fftSize
            windowedPcm[f] = pcmRingBuffer[idx] * hannWindow[f]
            realBuffer[f] = windowedPcm[f]
            imagBuffer[f] = 0f
        }

        computeInPlaceFft(realBuffer, imagBuffer, fftSize)

        // 3. Compute Magnitudes for half-spectrum
        for (f in 0 until halfFft) {
            val r = realBuffer[f]
            val im = imagBuffer[f]
            val mag = sqrt(r * r + im * im) * 2.0f
            magnitudes[f] = mag.coerceIn(0f, 1.5f)
        }

        // 4. Map FFT spectrum into 48 logarithmic bands & energy groups
        var bassSum = 0f
        var bassCount = 0
        var lowMidSum = 0f
        var lowMidCount = 0
        var midSum = 0f
        var midCount = 0
        var highMidSum = 0f
        var highMidCount = 0
        var trebleSum = 0f
        var trebleCount = 0

        for (b in 0 until numBands) {
            // Logarithmic index mapping (more resolution in bass/mids)
            val startFreqBin = (Math.pow((b.toDouble() / numBands), 1.6) * (halfFft - 2)).toInt().coerceIn(0, halfFft - 2)
            val endFreqBin = (Math.pow(((b + 1.0) / numBands), 1.6) * (halfFft - 1)).toInt().coerceIn(startFreqBin + 1, halfFft)

            var bandEnergy = 0f
            var count = 0
            for (bin in startFreqBin until endFreqBin) {
                bandEnergy += magnitudes[bin]
                count++
            }
            val avg = if (count > 0) bandEnergy / count else 0f
            // Apply slight frequency compensation curve (boost treble / upper mids)
            val weight = 1.0f + (b.toFloat() / numBands) * 1.5f
            val bandVal = (avg * weight * 1.8f).coerceIn(0f, 1f)

            rawBands[b] = bandVal
            // Smooth band value with attack and decay
            val smoothing = if (bandVal > smoothBands[b]) 0.75f else 0.45f
            smoothBands[b] = smoothBands[b] * (1f - smoothing) + bandVal * smoothing

            // Group into Bass, Low-Mid, Mid, High-Mid, Treble
            when {
                b < 8 -> {
                    bassSum += bandVal
                    bassCount++
                }
                b < 16 -> {
                    lowMidSum += bandVal
                    lowMidCount++
                }
                b < 28 -> {
                    midSum += bandVal
                    midCount++
                }
                b < 38 -> {
                    highMidSum += bandVal
                    highMidCount++
                }
                else -> {
                    trebleSum += bandVal
                    trebleCount++
                }
            }
        }

        val rawBass = if (bassCount > 0) bassSum / bassCount else 0f
        val rawLowMid = if (lowMidCount > 0) lowMidSum / lowMidCount else 0f
        val rawMid = if (midCount > 0) midSum / midCount else 0f
        val rawHighMid = if (highMidCount > 0) highMidSum / highMidCount else 0f
        val rawTreble = if (trebleCount > 0) trebleSum / trebleCount else 0f

        smoothBass = smoothBass * 0.3f + rawBass * 0.7f
        smoothMid = smoothMid * 0.35f + rawMid * 0.65f
        smoothTreble = smoothTreble * 0.4f + rawTreble * 0.6f

        // 5. Dynamic Beat Detection based on sub-bass / bass energy flux
        val instantBassEnergy = (rawBass * 0.7f + rawLowMid * 0.3f).coerceIn(0f, 1f)
        bassEnergyHistory[historyIndex] = instantBassEnergy
        historyIndex = (historyIndex + 1) % bassEnergyHistory.size

        var avgHistoryEnergy = 0f
        for (v in bassEnergyHistory) {
            avgHistoryEnergy += v
        }
        avgHistoryEnergy /= bassEnergyHistory.size

        val beatThreshold = (avgHistoryEnergy * 1.35f).coerceAtLeast(0.18f)
        if (instantBassEnergy > beatThreshold && (now - lastBeatTimestampMs > 240)) {
            // Beat hit! Pulse up to 1.045
            currentBeatPulse = 1.045f
            lastBeatTimestampMs = now
        } else {
            // Smooth decay back to 1.0
            currentBeatPulse = currentBeatPulse * 0.88f + 1.0f * 0.12f
        }

        emitState(rawLowMid, rawHighMid)
    }

    private fun emitState(lowMid: Float = 0f, highMid: Float = 0f) {
        _audioState.value = RealtimeAudioState(
            waveform = smoothWaveform.copyOf(),
            fftBands = smoothBands.copyOf(),
            bassEnergy = smoothBass,
            lowMidEnergy = lowMid,
            midEnergy = smoothMid,
            highMidEnergy = highMid,
            trebleEnergy = smoothTreble,
            overallAmplitude = smoothAmplitude,
            beatPulse = currentBeatPulse,
            isPlaying = isPlaying
        )
    }

    /**
     * Highly optimized in-place Cooley-Tukey Radix-2 FFT algorithm.
     */
    private fun computeInPlaceFft(real: FloatArray, imag: FloatArray, n: Int) {
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR
                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val angle = -2.0 * PI / len
            val wStepR = cos(angle).toFloat()
            val wStepI = sin(angle).toFloat()

            var i = 0
            while (i < n) {
                var wR = 1.0f
                var wI = 0.0f
                for (k in 0 until halfLen) {
                    val pos = i + k
                    val partner = pos + halfLen

                    val tR = wR * real[partner] - wI * imag[partner]
                    val tI = wR * imag[partner] + wI * real[partner]

                    real[partner] = real[pos] - tR
                    imag[partner] = imag[pos] - tI
                    real[pos] = real[pos] + tR
                    imag[pos] = imag[pos] + tI

                    val nextWR = wR * wStepR - wI * wStepI
                    val nextWI = wR * wStepI + wI * wStepR
                    wR = nextWR
                    wI = nextWI
                }
                i += len
            }
            len = len shl 1
        }
    }
}
