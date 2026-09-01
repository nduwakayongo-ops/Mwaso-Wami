package com.example.service.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Diagnostic data class holding instantaneous real PCM telemetry for verification.
 */
data class PcmTelemetry(
    val playerTag: String = "",
    val sampleCount: Int = 0,
    val rms: Float = 0f,
    val peak: Float = 0f,
    val dominantFrequencyHz: Float = 0f,
    val isReceivingRealPcm: Boolean = false,
    val timestampMs: Long = 0L
)

/**
 * Central DJ Mixer monitor that validates real physical audio concurrency from Player A and Player B.
 */
object DjAudioMixerMonitor {
    private val _playerAPcm = MutableStateFlow(PcmTelemetry(playerTag = "PLAYER_A"))
    val playerAPcm: StateFlow<PcmTelemetry> = _playerAPcm.asStateFlow()

    private val _playerBPcm = MutableStateFlow(PcmTelemetry(playerTag = "PLAYER_B"))
    val playerBPcm: StateFlow<PcmTelemetry> = _playerBPcm.asStateFlow()

    fun updateTelemetry(
        tag: String,
        sampleCount: Int,
        rms: Float,
        peak: Float,
        dominantFreq: Float
    ) {
        val telemetry = PcmTelemetry(
            playerTag = tag,
            sampleCount = sampleCount,
            rms = rms,
            peak = peak,
            dominantFrequencyHz = dominantFreq,
            isReceivingRealPcm = sampleCount > 0 && rms > 0.0001f,
            timestampMs = System.currentTimeMillis()
        )
        if (tag == "PLAYER_A") {
            _playerAPcm.value = telemetry
        } else if (tag == "PLAYER_B") {
            _playerBPcm.value = telemetry
        }
    }

    fun resetTelemetry(tag: String) {
        if (tag == "PLAYER_A") {
            _playerAPcm.value = PcmTelemetry(playerTag = "PLAYER_A")
        } else if (tag == "PLAYER_B") {
            _playerBPcm.value = PcmTelemetry(playerTag = "PLAYER_B")
        }
    }
}

/**
 * ExoPlayer AudioProcessor that intercepts the live PCM audio stream during playback,
 * computes exact RMS/Peak/Frequency telemetry for real acoustic validation,
 * and passes the unmodified PCM downstream to Android's AudioTrack hardware.
 */
@OptIn(UnstableApi::class)
class RealtimeAudioProcessor(
    val visualizerEngine: RealtimeAudioVisualizerEngine? = null,
    val playerTag: String = "PLAYER_A"
) : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val sampleRate = inputAudioFormat.sampleRate.takeIf { it > 0 } ?: 44100
        val channelCount = inputAudioFormat.channelCount.takeIf { it > 0 } ?: 2
        val encoding = inputAudioFormat.encoding

        // Read-only analysis for real PCM proof
        val readOnlyCopy = inputBuffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)

        var sumSquares = 0.0
        var maxPeak = 0f
        var count = 0
        var zeroCrossings = 0
        var prevSample = 0f

        if (encoding == C.ENCODING_PCM_16BIT) {
            val shortBuffer = readOnlyCopy.asShortBuffer()
            val totalShorts = shortBuffer.remaining()
            count = totalShorts / channelCount
            while (shortBuffer.hasRemaining()) {
                val sample = shortBuffer.get() / 32768.0f
                sumSquares += (sample * sample)
                val absSample = kotlin.math.abs(sample)
                if (absSample > maxPeak) maxPeak = absSample
                if ((sample > 0f && prevSample <= 0f) || (sample < 0f && prevSample >= 0f)) {
                    zeroCrossings++
                }
                prevSample = sample
            }
        } else if (encoding == C.ENCODING_PCM_FLOAT) {
            val floatBuffer = readOnlyCopy.asFloatBuffer()
            val totalFloats = floatBuffer.remaining()
            count = totalFloats / channelCount
            while (floatBuffer.hasRemaining()) {
                val sample = floatBuffer.get()
                sumSquares += (sample * sample)
                val absSample = kotlin.math.abs(sample)
                if (absSample > maxPeak) maxPeak = absSample
                if ((sample > 0f && prevSample <= 0f) || (sample < 0f && prevSample >= 0f)) {
                    zeroCrossings++
                }
                prevSample = sample
            }
        }

        val rms = if (count > 0) sqrt(sumSquares / (count * channelCount)).toFloat() else 0f
        val dominantFreq = if (count > 0) (zeroCrossings * sampleRate.toFloat()) / (2f * count * channelCount) else 0f

        // Update central real-time diagnostic monitor
        DjAudioMixerMonitor.updateTelemetry(
            tag = playerTag,
            sampleCount = count,
            rms = rms,
            peak = maxPeak,
            dominantFreq = dominantFreq
        )

        // Feed Visualizer Engine
        try {
            readOnlyCopy.rewind()
            visualizerEngine?.processPcmBuffer(
                buffer = readOnlyCopy,
                sampleRate = sampleRate,
                channelCount = channelCount,
                encoding = encoding
            )
        } catch (e: Exception) {
            // Never crash audio pipeline
        }

        // Pass audio samples down the pipeline to AudioTrack hardware unchanged
        val outputBuffer = replaceOutputBuffer(remaining)
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }

    override fun onFlush() {
        super.onFlush()
    }

    override fun onReset() {
        super.onReset()
        DjAudioMixerMonitor.resetTelemetry(playerTag)
    }
}
