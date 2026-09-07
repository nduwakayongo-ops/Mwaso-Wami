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

    val hasTelemetrySubscribers: Boolean
        get() = _playerAPcm.subscriptionCount.value > 0 || _playerBPcm.subscriptionCount.value > 0

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
    var playerTag: String = "PLAYER_A"
) : BaseAudioProcessor() {

    private var lastTelemetryTimestamp = 0L

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

        // 1. Immediately copy to output buffer so AudioTrack hardware pipeline never starves
        val outputBuffer = replaceOutputBuffer(remaining)
        val readOnlySlice = inputBuffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
        val audioStartPos = readOnlySlice.position()
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()

        val sampleRate = inputAudioFormat.sampleRate.takeIf { it > 0 } ?: 44100
        val channelCount = inputAudioFormat.channelCount.takeIf { it > 0 } ?: 2
        val encoding = inputAudioFormat.encoding
        val now = System.currentTimeMillis()

        // 2. Telemetry update throttled to ~80ms to eliminate CPU and GC pressure, only if actively monitored
        if (DjAudioMixerMonitor.hasTelemetrySubscribers && (now - lastTelemetryTimestamp >= 80L)) {
            lastTelemetryTimestamp = now
            var sumSquares = 0.0
            var maxPeak = 0f
            var count = 0
            var zeroCrossings = 0
            var prevSample = 0f

            if (encoding == C.ENCODING_PCM_16BIT) {
                readOnlySlice.position(audioStartPos)
                val shortBuffer = readOnlySlice.asShortBuffer()
                val totalShorts = shortBuffer.remaining()
                val sampleLimit = minOf(totalShorts, 512 * channelCount)
                count = sampleLimit / channelCount
                var i = 0
                while (i < sampleLimit && shortBuffer.hasRemaining()) {
                    val sample = shortBuffer.get() / 32768.0f
                    sumSquares += (sample * sample)
                    val absSample = kotlin.math.abs(sample)
                    if (absSample > maxPeak) maxPeak = absSample
                    if ((sample > 0f && prevSample <= 0f) || (sample < 0f && prevSample >= 0f)) {
                        zeroCrossings++
                    }
                    prevSample = sample
                    i++
                }
            } else if (encoding == C.ENCODING_PCM_FLOAT) {
                readOnlySlice.position(audioStartPos)
                val floatBuffer = readOnlySlice.asFloatBuffer()
                val totalFloats = floatBuffer.remaining()
                val sampleLimit = minOf(totalFloats, 512 * channelCount)
                count = sampleLimit / channelCount
                var i = 0
                while (i < sampleLimit && floatBuffer.hasRemaining()) {
                    val sample = floatBuffer.get()
                    sumSquares += (sample * sample)
                    val absSample = kotlin.math.abs(sample)
                    if (absSample > maxPeak) maxPeak = absSample
                    if ((sample > 0f && prevSample <= 0f) || (sample < 0f && prevSample >= 0f)) {
                        zeroCrossings++
                    }
                    prevSample = sample
                    i++
                }
            }

            val rms = if (count > 0) sqrt(sumSquares / count).toFloat() else 0f
            val dominantFreq = if (count > 0 && sampleRate > 0) (zeroCrossings * sampleRate.toFloat()) / (2f * count) else 0f

            DjAudioMixerMonitor.updateTelemetry(
                tag = playerTag,
                sampleCount = count,
                rms = rms,
                peak = maxPeak,
                dominantFreq = dominantFreq
            )
        }

        // 3. Feed Visualizer Engine ONLY if visualizer UI is actively subscribed
        if (visualizerEngine != null && visualizerEngine.hasSubscribers) {
            try {
                readOnlySlice.position(audioStartPos)
                visualizerEngine.processPcmBuffer(
                    buffer = readOnlySlice,
                    sampleRate = sampleRate,
                    channelCount = channelCount,
                    encoding = encoding
                )
            } catch (e: Exception) {
                // Non-fatal, protect audio pipeline
            }
        }
    }

    override fun onFlush() {
        super.onFlush()
    }

    override fun onReset() {
        super.onReset()
        DjAudioMixerMonitor.resetTelemetry(playerTag)
    }
}
