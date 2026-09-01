package com.example.service

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.AudioTrack
import com.example.data.model.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class CrossfadeState {
    IDLE,
    PREPARING_NEXT,
    CROSSFADE_ACTIVE,
    COMPLETED
}

/**
 * DJ Crossfade Engine:
 * Real equal-power audio crossfade between two simultaneous ExoPlayer instances
 * during the last 5-8 seconds of a track.
 *
 * Guarantees:
 * 1. Track 1 fades out (1.0 -> 0.0) while Track 2 starts ONCE at 00:00 and fades in (0.0 -> 1.0).
 * 2. Track 2 NEVER receives seekTo(0) after starting.
 * 3. On completion, old player is released and the secondary player is promoted seamlessly.
 * 4. Repeat ONE, Repeat ALL, and Repeat OFF are fully supported.
 */
class AudioTransitionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val visualizerEngine: com.example.service.audio.RealtimeAudioVisualizerEngine? = null,
    private val onTransitionComplete: (promotedPlayer: ExoPlayer, nextTrack: AudioTrack, nextIndex: Int) -> Unit
) {
    private val _crossfadeState = MutableStateFlow(CrossfadeState.IDLE)
    val crossfadeState: StateFlow<CrossfadeState> = _crossfadeState.asStateFlow()

    private var crossfadeJob: Job? = null
    private var secondaryPlayer: ExoPlayer? = null
    private var currentTargetTrack: AudioTrack? = null
    private var currentTargetIndex: Int = -1

    val isCrossfadeActive: Boolean
        get() = _crossfadeState.value == CrossfadeState.CROSSFADE_ACTIVE || _crossfadeState.value == CrossfadeState.PREPARING_NEXT

    val secondaryTrackId: Long?
        get() = currentTargetTrack?.id

    /**
     * Called by position ticker to evaluate and trigger the DJ crossfade.
     */
    fun checkAndHandleEarlyTransition(
        primaryPlayer: ExoPlayer,
        currentPosMs: Long,
        durationMs: Long,
        crossfadeSec: Int,
        currentQueue: List<AudioTrack>,
        currentIndex: Int,
        repeatMode: RepeatMode
    ) {
        // Only trigger when crossfade is configured (5s, 6s, 7s, 8s) and currently IDLE
        if (crossfadeSec <= 0) return
        if (_crossfadeState.value != CrossfadeState.IDLE) return
        if (!primaryPlayer.isPlaying) return

        val currentTrack = currentQueue.getOrNull(currentIndex) ?: return
        val currentDurationMs = if (durationMs > 0 && durationMs != C.TIME_UNSET) durationMs else currentTrack.durationMs
        if (currentDurationMs <= 2000L) return

        val nextIndex = when {
            repeatMode == RepeatMode.ONE -> currentIndex
            currentIndex + 1 < currentQueue.size -> currentIndex + 1
            repeatMode == RepeatMode.ALL && currentQueue.isNotEmpty() -> 0
            else -> -1
        }

        if (nextIndex !in currentQueue.indices) return
        val nextTrack = currentQueue[nextIndex]
        val nextDurationMs = if (nextTrack.durationMs > 0) nextTrack.durationMs else currentDurationMs

        // Short track protection: effectiveCrossfade = min(configuredCrossfade, durationCurrent / 2, durationNext / 2)
        val configuredCrossfadeMs = crossfadeSec * 1000L
        val maxAllowedForTrack1 = currentDurationMs / 2
        val maxAllowedForTrack2 = (if (nextDurationMs > 0) nextDurationMs else currentDurationMs) / 2
        val effectiveCrossfadeMs = minOf(configuredCrossfadeMs, maxAllowedForTrack1, maxAllowedForTrack2).coerceAtLeast(1000L)

        val remainingMs = currentDurationMs - currentPosMs

        // Check if within the crossfade window
        if (remainingMs in 1..effectiveCrossfadeMs) {
            Log.d("AudioTransitionManager", "[CROSSFADE DJ] Triggered! Remaining=${remainingMs}ms, window=${effectiveCrossfadeMs}ms")
            Log.d("AudioTransitionManager", "[CROSSFADE DJ] Track 1: '${currentTrack.title}' -> Track 2: '${nextTrack.title}'")

            startDjCrossfade(
                primaryPlayer = primaryPlayer,
                nextTrack = nextTrack,
                nextIndex = nextIndex,
                crossfadeDurationMs = remainingMs.coerceAtLeast(1000L)
            )
        }
    }

    /**
     * Manual user skip with crossfade if configured.
     */
    fun startManualTransition(
        primaryPlayer: ExoPlayer,
        nextTrack: AudioTrack,
        nextIndex: Int,
        crossfadeSec: Int
    ): Boolean {
        if (crossfadeSec <= 0) {
            resetTransition(primaryPlayer)
            return false
        }

        // If secondary player is already crossfading into this exact track, promote immediately!
        if (isCrossfadeActive && secondaryPlayer != null && currentTargetTrack?.id == nextTrack.id) {
            Log.d("AudioTransitionManager", "[CROSSFADE DJ] Promoting existing secondary player for '${nextTrack.title}'")
            promoteSecondaryPlayerImmediately(primaryPlayer, nextTrack, nextIndex)
            return true
        }

        val configuredMs = crossfadeSec * 1000L
        val maxAllowedForNext = if (nextTrack.durationMs > 2000L) nextTrack.durationMs / 2 else configuredMs
        val crossfadeDurationMs = minOf(configuredMs, maxAllowedForNext).coerceAtLeast(1000L)
        Log.d("AudioTransitionManager", "[CROSSFADE DJ] Starting manual crossfade to '${nextTrack.title}' over ${crossfadeDurationMs}ms")

        startDjCrossfade(
            primaryPlayer = primaryPlayer,
            nextTrack = nextTrack,
            nextIndex = nextIndex,
            crossfadeDurationMs = crossfadeDurationMs
        )
        return true
    }

    private fun startDjCrossfade(
        primaryPlayer: ExoPlayer,
        nextTrack: AudioTrack,
        nextIndex: Int,
        crossfadeDurationMs: Long
    ) {
        _crossfadeState.value = CrossfadeState.PREPARING_NEXT
        currentTargetTrack = nextTrack
        currentTargetIndex = nextIndex
        crossfadeJob?.cancel()

        try {
            // Clean up any lingering secondary player
            releaseSecondaryPlayer()

            // 1. Create Secondary ExoPlayer instance with dedicated AudioProcessor instance (Tagged PLAYER_B)
            val builder = if (visualizerEngine != null) {
                val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
                    override fun buildAudioSink(
                        context: Context,
                        enableFloatOutput: Boolean,
                        enableAudioTrackPlaybackParams: Boolean
                    ): androidx.media3.exoplayer.audio.AudioSink {
                        return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                            .setAudioProcessors(arrayOf(com.example.service.audio.RealtimeAudioProcessor(visualizerEngine, playerTag = "PLAYER_B")))
                            .build()
                    }
                }
                ExoPlayer.Builder(context, renderersFactory)
            } else {
                ExoPlayer.Builder(context)
            }

            val nextPlayer = builder
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    false // Don't steal audio focus from primary during simultaneous mix
                )
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(nextTrack.mediaUri))
                .setMediaId(nextTrack.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(nextTrack.title)
                        .setArtist(nextTrack.artist)
                        .setAlbumTitle(nextTrack.album)
                        .setArtworkUri(nextTrack.artworkUri?.let { Uri.parse(it) })
                        .build()
                )
                .build()

            // 2. Prepare & start secondary player at 0.0f volume (starts at 00:00 ONCE)
            nextPlayer.setMediaItem(mediaItem)
            nextPlayer.volume = 0.0f
            nextPlayer.prepare()
            nextPlayer.seekTo(0L) // Start at 00:00 exactly once
            nextPlayer.playWhenReady = true
            nextPlayer.play()
            secondaryPlayer = nextPlayer

            _crossfadeState.value = CrossfadeState.CROSSFADE_ACTIVE

            Log.d(
                "AudioTransitionManager",
                "[CROSSFADE_STARTED] A position = ${primaryPlayer.currentPosition} ms | A duration = ${primaryPlayer.duration} ms | A volume = ${primaryPlayer.volume} | B position = ${nextPlayer.currentPosition} ms | B volume = ${nextPlayer.volume} | A.isPlaying = ${primaryPlayer.isPlaying} | B.isPlaying = ${nextPlayer.isPlaying}"
            )

            // 3. Smooth simultaneous crossfade coroutine (Equal-Power DJ curve)
            crossfadeJob = scope.launch(Dispatchers.Main) {
                val startTime = System.currentTimeMillis()
                val stepIntervalMs = 25L
                var lastLoggedSec = -1

                while (_crossfadeState.value == CrossfadeState.CROSSFADE_ACTIVE) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val progress = (elapsed.toFloat() / crossfadeDurationMs).coerceIn(0f, 1f)

                    // Equal-power crossfade curve: cos/sin prevents volume dips in center
                    val oldGain = cos(progress * PI / 2.0).toFloat().coerceIn(0f, 1f)
                    val newGain = sin(progress * PI / 2.0).toFloat().coerceIn(0f, 1f)

                    primaryPlayer.volume = oldGain
                    secondaryPlayer?.volume = newGain

                    val currentElapsedSec = (elapsed / 1000).toInt()
                    if (currentElapsedSec != lastLoggedSec) {
                        lastLoggedSec = currentElapsedSec
                        Log.d(
                            "AudioTransitionManager",
                            "[CROSSFADE_PROGRESS +${elapsed}ms] A pos: ${primaryPlayer.currentPosition}ms (vol: ${"%.2f".format(oldGain)}) | B pos: ${secondaryPlayer?.currentPosition}ms (vol: ${"%.2f".format(newGain)}) | A.isPlaying: ${primaryPlayer.isPlaying} | B.isPlaying: ${secondaryPlayer?.isPlaying}"
                        )
                    }

                    if (progress >= 1.0f) {
                        break
                    }
                    delay(stepIntervalMs)
                }

                // 4. Crossfade completion & Player Promotion
                primaryPlayer.volume = 0.0f

                val promotedPlayer = secondaryPlayer
                if (promotedPlayer != null) {
                    promotedPlayer.volume = 1.0f
                    secondaryPlayer = null
                    _crossfadeState.value = CrossfadeState.COMPLETED
                    currentTargetTrack = null
                    currentTargetIndex = -1

                    Log.d("AudioTransitionManager", "[CROSSFADE_COMPLETED] Promoting Track 2 at position ${promotedPlayer.currentPosition}ms. Track 1 released.")

                    // Promote the playing instance without ANY seekTo(0) reset!
                    onTransitionComplete(promotedPlayer, nextTrack, nextIndex)
                    _crossfadeState.value = CrossfadeState.IDLE
                } else {
                    _crossfadeState.value = CrossfadeState.IDLE
                    currentTargetTrack = null
                    currentTargetIndex = -1
                }
            }
        } catch (e: Exception) {
            Log.e("AudioTransitionManager", "Error in startDjCrossfade: ${e.message}", e)
            resetTransition(primaryPlayer)
        }
    }

    private fun promoteSecondaryPlayerImmediately(
        primaryPlayer: ExoPlayer,
        nextTrack: AudioTrack,
        nextIndex: Int
    ) {
        crossfadeJob?.cancel()
        crossfadeJob = null

        try {
            primaryPlayer.stop()
            primaryPlayer.clearMediaItems()
            primaryPlayer.release()
        } catch (e: Exception) {
            Log.w("AudioTransitionManager", "Error releasing old primary player: ${e.message}")
        }

        val promotedPlayer = secondaryPlayer
        if (promotedPlayer != null) {
            promotedPlayer.volume = 1.0f
            secondaryPlayer = null
            _crossfadeState.value = CrossfadeState.COMPLETED
            currentTargetTrack = null
            currentTargetIndex = -1

            onTransitionComplete(promotedPlayer, nextTrack, nextIndex)
            _crossfadeState.value = CrossfadeState.IDLE
        } else {
            resetTransition(primaryPlayer)
        }
    }

    fun onPause() {
        secondaryPlayer?.pause()
    }

    fun onResume() {
        if (isCrossfadeActive) {
            secondaryPlayer?.play()
        }
    }

    fun resetTransition(primaryPlayer: ExoPlayer? = null) {
        crossfadeJob?.cancel()
        crossfadeJob = null
        releaseSecondaryPlayer()
        _crossfadeState.value = CrossfadeState.IDLE
        currentTargetTrack = null
        currentTargetIndex = -1
        primaryPlayer?.volume = 1.0f
    }

    private fun releaseSecondaryPlayer() {
        try {
            secondaryPlayer?.stop()
            secondaryPlayer?.clearMediaItems()
            secondaryPlayer?.release()
        } catch (e: Exception) {
            Log.w("AudioTransitionManager", "Error releasing secondary player: ${e.message}")
        } finally {
            secondaryPlayer = null
        }
    }
}
