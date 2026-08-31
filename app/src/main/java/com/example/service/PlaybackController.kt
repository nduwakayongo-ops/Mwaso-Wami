package com.example.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.example.data.local.MediaRepository
import com.example.data.model.AppSettings
import com.example.data.model.AudioTrack
import com.example.data.model.PlaybackState
import com.example.data.model.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Collections

class PlaybackController private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var repository: MediaRepository? = null

    val effectsManager = AudioEffectsManager(context)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _appSettings = MutableStateFlow(AppSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    private val _sleepTimerRemainingSec = MutableStateFlow<Int?>(null)
    val sleepTimerRemainingSec: StateFlow<Int?> = _sleepTimerRemainingSec.asStateFlow()

    private var tickerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var originalQueue = mutableListOf<AudioTrack>()

    private val transitionManager = AudioTransitionManager(context, scope) { promotedPlayer, nextTrack, nextIndex ->
        handlePlayerPromoted(promotedPlayer, nextTrack, nextIndex)
    }

    private val playerListener: Player.Listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startTicker()
                effectsManager.attachToSession(activePlayer.audioSessionId)
                startMediaService()
            } else {
                stopTicker()
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> {
                    val duration = activePlayer.duration.coerceAtLeast(0L)
                    _playbackState.update {
                        it.copy(
                            durationMs = if (duration > 0) duration else (it.currentTrack?.durationMs ?: 0L),
                            bufferedPositionMs = activePlayer.bufferedPosition
                        )
                    }
                    effectsManager.attachToSession(activePlayer.audioSessionId)
                }
                Player.STATE_ENDED -> {
                    handleTrackEnded()
                }
                else -> {}
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Only handle standard ExoPlayer playlist transitions if crossfade is not active
            if (!transitionManager.isCrossfadeActive) {
                val currentIdx = activePlayer.currentMediaItemIndex
                val queue = _playbackState.value.queue
                if (currentIdx in queue.indices) {
                    val newTrack = queue[currentIdx]
                    _playbackState.update {
                        it.copy(
                            currentIndex = currentIdx,
                            currentTrack = newTrack,
                            currentPositionMs = activePlayer.currentPosition,
                            durationMs = newTrack.durationMs,
                            bufferedPositionMs = 0L
                        )
                    }
                    scope.launch {
                        repository?.recordTrackPlayed(newTrack, 1000L)
                    }
                    effectsManager.attachToSession(activePlayer.audioSessionId)
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.w("PlaybackController", "Player error: ${error.message}")
            _playbackState.update { it.copy(isPlaying = false) }
        }
    }

    // Primary Active Player instance initialized AFTER playerListener is guaranteed non-null
    private var activePlayer: ExoPlayer = createNewExoPlayer()

    val exoPlayer: ExoPlayer
        get() = activePlayer

    var mediaSession: MediaSession? = null

    private fun createNewExoPlayer(): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // handle audio focus automatically
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build().apply {
                volume = 1.0f // 100% Unity gain standard
                addListener(playerListener)
            }
    }

    private fun startMediaService() {
        try {
            val intent = Intent(context, MediaPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.w("PlaybackController", "Unable to start foreground service: ${e.message}")
        }
    }

    fun initRepository(repo: MediaRepository) {
        this.repository = repo
        scope.launch {
            repo.getSettingsFlow().collect { settings ->
                if (settings != null) {
                    _appSettings.value = settings
                }
            }
        }
    }

    fun playTrackList(tracks: List<AudioTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        originalQueue = tracks.toMutableList()
        val queue = if (_playbackState.value.isShuffle) tracks.shuffled() else tracks
        val safeIndex = startIndex.coerceIn(0, queue.size - 1)
        val selectedTrack = queue[safeIndex]

        val crossfadeSec = _appSettings.value.crossfadeDurationSec
        val isCurrentlyPlaying = activePlayer.isPlaying && _playbackState.value.currentTrack != null

        _playbackState.update {
            it.copy(
                queue = queue,
                currentIndex = safeIndex,
                currentTrack = selectedTrack
            )
        }

        if (isCurrentlyPlaying && crossfadeSec > 0) {
            val transitioned = transitionManager.startManualTransition(
                primaryPlayer = activePlayer,
                nextTrack = selectedTrack,
                nextIndex = safeIndex,
                crossfadeSec = crossfadeSec
            )
            if (!transitioned) {
                loadAndPlayPlaylist(queue, safeIndex)
            }
        } else {
            loadAndPlayPlaylist(queue, safeIndex)
        }
    }

    fun playSingleTrack(track: AudioTrack) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        val existingIndex = currentQueue.indexOfFirst { it.id == track.id }
        val targetIndex = if (existingIndex >= 0) existingIndex else {
            currentQueue.add(track)
            currentQueue.size - 1
        }

        val crossfadeSec = _appSettings.value.crossfadeDurationSec
        val isCurrentlyPlaying = activePlayer.isPlaying && _playbackState.value.currentTrack != null

        _playbackState.update {
            it.copy(
                queue = currentQueue,
                currentIndex = targetIndex,
                currentTrack = track
            )
        }

        if (isCurrentlyPlaying && crossfadeSec > 0) {
            val transitioned = transitionManager.startManualTransition(
                primaryPlayer = activePlayer,
                nextTrack = track,
                nextIndex = targetIndex,
                crossfadeSec = crossfadeSec
            )
            if (!transitioned) {
                loadAndPlayPlaylist(currentQueue, targetIndex)
            }
        } else {
            loadAndPlayPlaylist(currentQueue, targetIndex)
        }
    }

    private fun loadAndPlayPlaylist(queue: List<AudioTrack>, startIndex: Int) {
        val track = queue.getOrNull(startIndex) ?: return
        transitionManager.resetTransition(activePlayer)

        try {
            val mediaItems = queue.map { buildMediaItem(it) }
            activePlayer.setMediaItems(mediaItems, startIndex, 0L)
            activePlayer.repeatMode = when (_playbackState.value.repeatMode) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            }
            activePlayer.volume = 1.0f
            activePlayer.prepare()
            activePlayer.play()

            _playbackState.update {
                it.copy(
                    currentPositionMs = 0L,
                    durationMs = track.durationMs,
                    isPlaying = true
                )
            }

            scope.launch {
                repository?.recordTrackPlayed(track, 1000L)
            }
            startMediaService()
        } catch (e: Exception) {
            Log.e("PlaybackController", "Failed to play track ${track.title}", e)
        }
    }

    private fun buildMediaItem(track: AudioTrack): MediaItem {
        val uri = Uri.parse(track.mediaUri)

        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(track.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(track.artworkUri?.let { Uri.parse(it) })
                    .build()
            )
            .build()
    }

    fun togglePlayPause() {
        if (activePlayer.isPlaying) {
            activePlayer.pause()
            transitionManager.onPause()
        } else {
            if (_playbackState.value.currentTrack != null) {
                if (activePlayer.playbackState == Player.STATE_IDLE || activePlayer.playbackState == Player.STATE_ENDED) {
                    activePlayer.prepare()
                }
                activePlayer.play()
                transitionManager.onResume()
                startMediaService()
            } else if (_playbackState.value.queue.isNotEmpty()) {
                playTrackList(_playbackState.value.queue, 0)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        transitionManager.resetTransition(activePlayer)
        activePlayer.seekTo(safePos)
        _playbackState.update { it.copy(currentPositionMs = safePos) }
    }

    fun skipNext() {
        val queue = _playbackState.value.queue
        if (queue.isEmpty()) return

        val crossfadeSec = _appSettings.value.crossfadeDurationSec
        val nextIndex = when {
            _playbackState.value.repeatMode == RepeatMode.ONE -> _playbackState.value.currentIndex
            _playbackState.value.currentIndex + 1 < queue.size -> _playbackState.value.currentIndex + 1
            _playbackState.value.repeatMode == RepeatMode.ALL -> 0
            else -> -1
        }

        if (nextIndex < 0 || nextIndex !in queue.indices) return
        val nextTrack = queue[nextIndex]

        if (activePlayer.isPlaying && crossfadeSec > 0) {
            val transitioned = transitionManager.startManualTransition(
                primaryPlayer = activePlayer,
                nextTrack = nextTrack,
                nextIndex = nextIndex,
                crossfadeSec = crossfadeSec
            )
            if (transitioned) return
        }

        // Standard instant skip if crossfade is off
        transitionManager.resetTransition(activePlayer)
        if (nextIndex < activePlayer.mediaItemCount) {
            activePlayer.seekToDefaultPosition(nextIndex)
            activePlayer.play()
        } else {
            loadAndPlayPlaylist(queue, nextIndex)
        }
    }

    fun skipPrevious() {
        transitionManager.resetTransition(activePlayer)
        val queue = _playbackState.value.queue
        if (queue.isEmpty()) return

        if (activePlayer.currentPosition > 3000) {
            seekTo(0)
            return
        }

        val prevIndex = when {
            _playbackState.value.currentIndex - 1 >= 0 -> _playbackState.value.currentIndex - 1
            _playbackState.value.repeatMode == RepeatMode.ALL -> queue.size - 1
            else -> 0
        }

        if (prevIndex in queue.indices) {
            if (prevIndex < activePlayer.mediaItemCount) {
                activePlayer.seekToDefaultPosition(prevIndex)
                activePlayer.play()
            } else {
                loadAndPlayPlaylist(queue, prevIndex)
            }
        } else {
            seekTo(0)
        }
    }

    fun toggleShuffle() {
        val newShuffle = !_playbackState.value.isShuffle
        val currentTrack = _playbackState.value.currentTrack
        val currentQueue = _playbackState.value.queue

        val newQueue = if (newShuffle) {
            val shuffled = currentQueue.toMutableList().apply {
                if (currentTrack != null) {
                    remove(currentTrack)
                    shuffle()
                    add(0, currentTrack)
                } else {
                    shuffle()
                }
            }
            shuffled
        } else {
            originalQueue.ifEmpty { currentQueue.sortedBy { it.title } }
        }

        val newIndex = if (currentTrack != null) newQueue.indexOfFirst { it.id == currentTrack.id }.coerceAtLeast(0) else 0
        _playbackState.update {
            it.copy(isShuffle = newShuffle, queue = newQueue, currentIndex = newIndex)
        }

        if (newQueue.isNotEmpty()) {
            val currentPos = activePlayer.currentPosition
            val isPlaying = activePlayer.isPlaying
            val mediaItems = newQueue.map { buildMediaItem(it) }
            activePlayer.setMediaItems(mediaItems, newIndex, currentPos)
            if (isPlaying) {
                activePlayer.play()
            }
        }
    }

    fun cycleRepeatMode() {
        val nextMode = when (_playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _playbackState.update { it.copy(repeatMode = nextMode) }
        activePlayer.repeatMode = when (nextMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    fun addToQueue(track: AudioTrack) {
        val newQueue = _playbackState.value.queue.toMutableList().apply { add(track) }
        _playbackState.update { it.copy(queue = newQueue) }
        activePlayer.addMediaItem(buildMediaItem(track))
    }

    fun playNextInQueue(track: AudioTrack) {
        val queue = _playbackState.value.queue.toMutableList()
        val insertIndex = (_playbackState.value.currentIndex + 1).coerceIn(0, queue.size)
        queue.add(insertIndex, track)
        _playbackState.update { it.copy(queue = queue) }
        if (insertIndex in 0..activePlayer.mediaItemCount) {
            activePlayer.addMediaItem(insertIndex, buildMediaItem(track))
        } else {
            activePlayer.addMediaItem(buildMediaItem(track))
        }
    }

    fun removeFromQueue(index: Int) {
        val queue = _playbackState.value.queue.toMutableList()
        if (index in queue.indices) {
            val isCurrent = index == _playbackState.value.currentIndex
            queue.removeAt(index)
            val newCurrentIndex = if (index < _playbackState.value.currentIndex) {
                _playbackState.value.currentIndex - 1
            } else if (isCurrent) {
                index.coerceAtMost(queue.size - 1)
            } else {
                _playbackState.value.currentIndex
            }
            val currentTrack = if (queue.isNotEmpty() && newCurrentIndex >= 0) queue[newCurrentIndex] else null
            _playbackState.update {
                it.copy(queue = queue, currentIndex = newCurrentIndex, currentTrack = currentTrack)
            }
            if (index in 0 until activePlayer.mediaItemCount) {
                activePlayer.removeMediaItem(index)
            }
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val queue = _playbackState.value.queue.toMutableList()
        if (fromIndex in queue.indices && toIndex in queue.indices) {
            Collections.swap(queue, fromIndex, toIndex)
            val currentTrack = _playbackState.value.currentTrack
            val newCurrentIndex = if (currentTrack != null) queue.indexOfFirst { it.id == currentTrack.id } else 0
            _playbackState.update {
                it.copy(queue = queue, currentIndex = newCurrentIndex)
            }
            if (fromIndex in 0 until activePlayer.mediaItemCount && toIndex in 0 until activePlayer.mediaItemCount) {
                activePlayer.moveMediaItem(fromIndex, toIndex)
            }
        }
    }

    fun clearQueue() {
        val currentTrack = _playbackState.value.currentTrack
        val queue = if (currentTrack != null) listOf(currentTrack) else emptyList()
        _playbackState.update {
            it.copy(queue = queue, currentIndex = if (currentTrack != null) 0 else -1)
        }
        if (currentTrack != null) {
            val currentPos = activePlayer.currentPosition
            val isPlaying = activePlayer.isPlaying
            activePlayer.setMediaItem(buildMediaItem(currentTrack), currentPos)
            if (isPlaying) activePlayer.play()
        } else {
            activePlayer.clearMediaItems()
        }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerRemainingSec.value = null
            return
        }

        var remaining = minutes * 60
        _sleepTimerRemainingSec.value = remaining

        sleepTimerJob = scope.launch {
            while (remaining > 0) {
                delay(1000)
                remaining--
                _sleepTimerRemainingSec.value = remaining
            }
            _sleepTimerRemainingSec.value = null
            activePlayer.pause()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerRemainingSec.value = null
    }

    /**
     * PROMOTION OF PLAYER:
     * The promotedPlayer is the EXACT instance that was playing the second track.
     * We attach the listener, update the activePlayer reference, attach MediaSession,
     * and update state with its CURRENT position WITHOUT any restart or seekTo(0)!
     */
    private fun handlePlayerPromoted(promotedPlayer: ExoPlayer, nextTrack: AudioTrack, nextIndex: Int) {
        try {
            activePlayer.removeListener(playerListener)
        } catch (e: Exception) {
            Log.w("PlaybackController", "Old player listener remove error: ${e.message}")
        }

        // Attach listener to promoted player
        promotedPlayer.addListener(playerListener)
        activePlayer = promotedPlayer

        // Update MediaSession if active
        try {
            mediaSession?.setPlayer(activePlayer)
        } catch (e: Exception) {
            Log.w("PlaybackController", "Error updating MediaSession player: ${e.message}")
        }

        effectsManager.attachToSession(activePlayer.audioSessionId)

        val currentPos = activePlayer.currentPosition
        Log.d("PlaybackController", "[PROMOTION] Player promoted at position ${currentPos}ms for track: ${nextTrack.title}")

        _playbackState.update {
            it.copy(
                currentIndex = nextIndex,
                currentTrack = nextTrack,
                currentPositionMs = currentPos,
                durationMs = nextTrack.durationMs,
                isPlaying = true
            )
        }

        scope.launch {
            repository?.recordTrackPlayed(nextTrack, 1000L)
        }

        startTicker()
    }

    private fun handleTrackEnded() {
        if (_playbackState.value.repeatMode == RepeatMode.ONE) {
            seekTo(0)
            activePlayer.play()
        } else {
            val queue = _playbackState.value.queue
            val isLast = _playbackState.value.currentIndex >= queue.size - 1
            if (isLast && _playbackState.value.repeatMode == RepeatMode.OFF) {
                _playbackState.update { it.copy(isPlaying = false) }
            } else {
                skipNext()
            }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                delay(250)
                val currentPos = activePlayer.currentPosition
                val duration = activePlayer.duration.coerceAtLeast(0L)
                val buffered = activePlayer.bufferedPosition

                _playbackState.update {
                    it.copy(
                        currentPositionMs = currentPos,
                        durationMs = if (duration > 0) duration else (it.currentTrack?.durationMs ?: 0L),
                        bufferedPositionMs = buffered
                    )
                }

                // Check and trigger DJ Crossfade if within remaining window
                val settings = _appSettings.value
                val queue = _playbackState.value.queue
                val currentIndex = _playbackState.value.currentIndex
                val repeatMode = _playbackState.value.repeatMode

                transitionManager.checkAndHandleEarlyTransition(
                    primaryPlayer = activePlayer,
                    currentPosMs = currentPos,
                    durationMs = duration,
                    crossfadeSec = settings.crossfadeDurationSec,
                    currentQueue = queue,
                    currentIndex = currentIndex,
                    repeatMode = repeatMode
                )
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
    }

    companion object {
        @Volatile
        private var INSTANCE: PlaybackController? = null

        fun getInstance(context: Context): PlaybackController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val start = System.currentTimeMillis()
                    Log.d("PERF", "[PERF] PlaybackController start")
                    val instance = PlaybackController(context.applicationContext)
                    INSTANCE = instance
                    Log.d("PERF", "[PERF] PlaybackController initialized: ${System.currentTimeMillis() - start}ms")
                    instance
                }
            }
        }
    }
}
