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
import kotlinx.coroutines.launch

class AudioTransitionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onTransitionComplete: (promotedPlayer: ExoPlayer, nextTrack: AudioTrack, nextIndex: Int) -> Unit
) {
    private var crossfadeJob: Job? = null
    private var secondaryPlayer: ExoPlayer? = null
    private var isCrossfading = false
    private var crossfadeStarted = false
    private var currentTargetTrack: AudioTrack? = null
    private var currentTargetIndex: Int = -1

    val isCrossfadeActive: Boolean
        get() = isCrossfading

    val secondaryTrackId: Long?
        get() = currentTargetTrack?.id

    fun checkAndHandleEarlyTransition(
        primaryPlayer: ExoPlayer,
        currentPosMs: Long,
        durationMs: Long,
        crossfadeSec: Int,
        currentQueue: List<AudioTrack>,
        currentIndex: Int,
        repeatMode: RepeatMode
    ) {
        // Validation guards: Need valid duration and crossfade duration > 0 (OFF = 0)
        if (durationMs <= 4000L || isCrossfading || crossfadeStarted) return
        if (crossfadeSec <= 0) return // Crossfade is OFF

        val remainingMs = durationMs - currentPosMs
        val crossfadeMs = crossfadeSec * 1000L

        // Trigger when remaining time enters the crossfade window
        if (remainingMs in 1..crossfadeMs) {
            val nextIndex = when {
                repeatMode == RepeatMode.ONE -> currentIndex
                currentIndex + 1 < currentQueue.size -> currentIndex + 1
                repeatMode == RepeatMode.ALL && currentQueue.isNotEmpty() -> 0
                else -> -1
            }

            if (nextIndex in currentQueue.indices) {
                val currentTrack = currentQueue.getOrNull(currentIndex)
                val nextTrack = currentQueue[nextIndex]

                Log.d("AudioTransitionManager", "[CROSSFADE] remaining=${remainingMs}ms")
                Log.d("AudioTransitionManager", "[CROSSFADE] Current track: ${currentTrack?.title} -> Next track: ${nextTrack.title}")

                startDjCrossfade(
                    primaryPlayer = primaryPlayer,
                    nextTrack = nextTrack,
                    nextIndex = nextIndex,
                    crossfadeMs = remainingMs.coerceAtLeast(1000L)
                )
            }
        }
    }

    fun startManualTransition(
        primaryPlayer: ExoPlayer,
        nextTrack: AudioTrack,
        nextIndex: Int,
        crossfadeSec: Int
    ): Boolean {
        if (crossfadeSec <= 0) {
            resetTransition(primaryPlayer)
            return false // Crossfade is OFF, perform instant switch
        }

        // If the secondary player is ALREADY playing this exact track (from early transition), promote immediately!
        if (isCrossfading && secondaryPlayer != null && currentTargetTrack?.id == nextTrack.id) {
            Log.d("AudioTransitionManager", "[CROSSFADE] Promoting existing secondaryPlayer for track: ${nextTrack.title}")
            promoteSecondaryPlayerImmediately(primaryPlayer, nextTrack, nextIndex)
            return true
        }

        // Otherwise, start a smooth manual crossfade with a comfortable duration
        val crossfadeMs = (crossfadeSec * 1000L).coerceIn(1500L, 4000L)
        Log.d("AudioTransitionManager", "[CROSSFADE] Starting manual crossfade to: ${nextTrack.title} over ${crossfadeMs}ms")

        startDjCrossfade(
            primaryPlayer = primaryPlayer,
            nextTrack = nextTrack,
            nextIndex = nextIndex,
            crossfadeMs = crossfadeMs
        )
        return true
    }

    private fun startDjCrossfade(
        primaryPlayer: ExoPlayer,
        nextTrack: AudioTrack,
        nextIndex: Int,
        crossfadeMs: Long
    ) {
        crossfadeStarted = true
        isCrossfading = true
        currentTargetTrack = nextTrack
        currentTargetIndex = nextIndex
        crossfadeJob?.cancel()

        try {
            // Clean up any stale secondary player if exists
            releaseSecondaryPlayer()

            // 1. Create Secondary ExoPlayer instance
            val nextPlayer = ExoPlayer.Builder(context)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    false // Don't steal audio focus from primary
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
                        .build()
                )
                .build()

            // 2. Prepare & start secondary player at 0.0f volume
            nextPlayer.setMediaItem(mediaItem)
            nextPlayer.volume = 0.0f
            nextPlayer.prepare()
            nextPlayer.play()
            secondaryPlayer = nextPlayer

            Log.d("AudioTransitionManager", "[CROSSFADE] secondaryPlayer START")
            Log.d("AudioTransitionManager", "[CROSSFADE] primaryVolume=1.00 secondaryVolume=0.00")

            // 3. Smooth simultaneous crossfade coroutine
            crossfadeJob = scope.launch(Dispatchers.Main) {
                val startTime = System.currentTimeMillis()
                val stepIntervalMs = 40L
                var lastLogQuarter = 0

                while (isCrossfading) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val progress = (elapsed.toFloat() / crossfadeMs).coerceIn(0f, 1f)

                    // Smooth-step curve: t * t * (3 - 2 * t) for natural transition
                    val smooth = progress * progress * (3f - 2f * progress)
                    val outgoingVolume = (1.0f - smooth).coerceIn(0.0f, 1.0f)
                    val incomingVolume = smooth.coerceIn(0.0f, 1.0f)

                    primaryPlayer.volume = outgoingVolume
                    secondaryPlayer?.volume = incomingVolume

                    val currentQuarter = (progress * 4).toInt()
                    if (currentQuarter > lastLogQuarter && currentQuarter in 1..3) {
                        lastLogQuarter = currentQuarter
                        Log.d("AudioTransitionManager", "[CROSSFADE] primaryVolume=${String.format("%.2f", outgoingVolume)} secondaryVolume=${String.format("%.2f", incomingVolume)}")
                    }

                    if (progress >= 1.0f) {
                        break
                    }
                    delay(stepIntervalMs)
                }

                // 4. Crossfade completion & Player Promotion
                Log.d("AudioTransitionManager", "[CROSSFADE] primaryVolume=0.00 secondaryVolume=1.00")
                Log.d("AudioTransitionManager", "[CROSSFADE] transition COMPLETE")

                // Stop and release old primary player
                try {
                    primaryPlayer.stop()
                    primaryPlayer.release()
                    Log.d("AudioTransitionManager", "[CROSSFADE] oldPlayer RELEASED")
                } catch (e: Exception) {
                    Log.w("AudioTransitionManager", "Error releasing old primary player: ${e.message}")
                }

                val promotedPlayer = secondaryPlayer
                if (promotedPlayer != null) {
                    promotedPlayer.volume = 1.0f
                    secondaryPlayer = null
                    isCrossfading = false
                    crossfadeStarted = false
                    currentTargetTrack = null
                    currentTargetIndex = -1

                    // Promote the EXACT playing instance without restarting position!
                    onTransitionComplete(promotedPlayer, nextTrack, nextIndex)
                } else {
                    isCrossfading = false
                    crossfadeStarted = false
                    currentTargetTrack = null
                    currentTargetIndex = -1
                }
            }
        } catch (e: Exception) {
            Log.e("AudioTransitionManager", "Error starting DJ crossfade: ${e.message}", e)
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
            primaryPlayer.release()
            Log.d("AudioTransitionManager", "[CROSSFADE] oldPlayer RELEASED immediately")
        } catch (e: Exception) {
            Log.w("AudioTransitionManager", "Error releasing old player: ${e.message}")
        }

        val promotedPlayer = secondaryPlayer
        if (promotedPlayer != null) {
            promotedPlayer.volume = 1.0f
            secondaryPlayer = null
            isCrossfading = false
            crossfadeStarted = false
            currentTargetTrack = null
            currentTargetIndex = -1

            onTransitionComplete(promotedPlayer, nextTrack, nextIndex)
        } else {
            resetTransition(primaryPlayer)
        }
    }

    fun onPause() {
        secondaryPlayer?.pause()
    }

    fun onResume() {
        if (isCrossfading) {
            secondaryPlayer?.play()
        }
    }

    fun resetTransition(primaryPlayer: ExoPlayer? = null) {
        crossfadeJob?.cancel()
        crossfadeJob = null
        releaseSecondaryPlayer()
        isCrossfading = false
        crossfadeStarted = false
        currentTargetTrack = null
        currentTargetIndex = -1
        primaryPlayer?.volume = 1.0f
    }

    private fun releaseSecondaryPlayer() {
        try {
            secondaryPlayer?.stop()
            secondaryPlayer?.release()
        } catch (e: Exception) {
            Log.w("AudioTransitionManager", "Error releasing secondary player: ${e.message}")
        } finally {
            secondaryPlayer = null
        }
    }
}
