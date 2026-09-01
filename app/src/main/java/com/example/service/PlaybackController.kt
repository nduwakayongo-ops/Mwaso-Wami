package com.example.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _appSettings = MutableStateFlow(AppSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    private val _sleepTimerRemainingSec = MutableStateFlow<Int?>(null)
    val sleepTimerRemainingSec: StateFlow<Int?> = _sleepTimerRemainingSec.asStateFlow()

    private var tickerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var originalQueue = mutableListOf<AudioTrack>()

    val visualizerEngine = com.example.service.audio.RealtimeAudioVisualizerEngine()
    val audioVisualizerState: StateFlow<com.example.service.audio.RealtimeAudioState> = visualizerEngine.audioState

    val transitionManager = AudioTransitionManager(context, scope, visualizerEngine) { promotedPlayer, nextTrack, nextIndex ->
        handlePlayerPromoted(promotedPlayer, nextTrack, nextIndex)
    }

    private val playerListener: Player.Listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying && transitionManager.isCrossfadeActive) {
                // Track A ended/paused while Track B is actively crossfading in: maintain playback state and ticker
                Log.d("PlaybackController", "Primary player isPlaying false during active crossfade. Keeping playback active.")
                return
            }
            _playbackState.update { it.copy(isPlaying = isPlaying) }
            visualizerEngine.setPlaying(isPlaying)
            if (isPlaying) {
                startTicker()
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
                }
                Player.STATE_ENDED -> {
                    if (!transitionManager.isCrossfadeActive) {
                        visualizerEngine.reset()
                        handleTrackEnded()
                    } else {
                        Log.d("PlaybackController", "Primary player ended during active crossfade. Secondary player is playing and will be promoted.")
                    }
                }
                Player.STATE_IDLE -> {
                    if (!transitionManager.isCrossfadeActive) {
                        visualizerEngine.reset()
                    }
                }
                else -> {}
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.w("PlaybackController", "Player error: ${error.message}")
            _playbackState.update { it.copy(isPlaying = false) }
        }
    }

    // Primary Active Player instance
    private var activePlayer: ExoPlayer = createNewExoPlayer()

    val exoPlayer: ExoPlayer
        get() = activePlayer

    var mediaSession: MediaSession? = null

    fun getSessionPlayer(): Player {
        return QueueAwareForwardingPlayer(activePlayer)
    }

    inner class QueueAwareForwardingPlayer(player: Player) : androidx.media3.common.ForwardingPlayer(player) {
        override fun getAvailableCommands(): Player.Commands {
            val baseCommands = super.getAvailableCommands()
            val queue = _playbackState.value.queue
            val currentIndex = _playbackState.value.currentIndex
            val repeatMode = _playbackState.value.repeatMode

            val hasNext = queue.isNotEmpty() && (
                repeatMode == RepeatMode.ALL ||
                repeatMode == RepeatMode.ONE ||
                currentIndex + 1 < queue.size
            )
            val hasPrevious = queue.isNotEmpty() && (
                repeatMode == RepeatMode.ALL ||
                repeatMode == RepeatMode.ONE ||
                currentIndex > 0 ||
                currentPosition > 3000L
            )

            val builder = baseCommands.buildUpon()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_STOP)
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)

            if (hasNext) {
                builder.add(Player.COMMAND_SEEK_TO_NEXT)
                builder.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            }
            if (hasPrevious) {
                builder.add(Player.COMMAND_SEEK_TO_PREVIOUS)
                builder.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            }

            return builder.build()
        }

        override fun isCommandAvailable(command: Int): Boolean {
            return getAvailableCommands().contains(command)
        }

        override fun seekToNext() {
            skipNext()
        }

        override fun seekToNextMediaItem() {
            skipNext()
        }

        override fun seekToPrevious() {
            skipPrevious()
        }

        override fun seekToPreviousMediaItem() {
            skipPrevious()
        }

        override fun play() {
            if (!_playbackState.value.isPlaying) {
                togglePlayPause()
            }
        }

        override fun pause() {
            if (_playbackState.value.isPlaying) {
                togglePlayPause()
            }
        }
    }

    private fun createNewExoPlayer(): ExoPlayer {
        val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(com.example.service.audio.RealtimeAudioProcessor(visualizerEngine, playerTag = "PLAYER_A")))
                    .build()
            }
        }

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // handle audio focus automatically
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.volume = 1.0f
        player.addListener(playerListener)
        return player
    }

    private fun startMediaService() {
        try {
            val intent = Intent(context, MediaPlaybackService::class.java)
            context.startService(intent)
        } catch (e: Exception) {
            Log.w("PlaybackController", "Unable to start media service: ${e.message}")
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
        val currentTrack = _playbackState.value.currentTrack
        val isCurrentlyPlaying = (activePlayer.isPlaying || transitionManager.isCrossfadeActive) && currentTrack != null
        val isRepeatOne = _playbackState.value.repeatMode == RepeatMode.ONE

        if (isCurrentlyPlaying && crossfadeSec > 0 && (selectedTrack.id != currentTrack.id || isRepeatOne)) {
            _playbackState.update {
                it.copy(
                    queue = queue,
                    currentIndex = safeIndex
                )
            }
            val transitioned = transitionManager.startManualTransition(
                primaryPlayer = activePlayer,
                nextTrack = selectedTrack,
                nextIndex = safeIndex,
                crossfadeSec = crossfadeSec
            )
            if (!transitioned) {
                loadAndPlaySingleTrack(queue, safeIndex)
            }
        } else {
            _playbackState.update {
                it.copy(
                    queue = queue,
                    currentIndex = safeIndex,
                    currentTrack = selectedTrack
                )
            }
            loadAndPlaySingleTrack(queue, safeIndex)
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
        val currentTrack = _playbackState.value.currentTrack
        val isCurrentlyPlaying = (activePlayer.isPlaying || transitionManager.isCrossfadeActive) && currentTrack != null
        val isRepeatOne = _playbackState.value.repeatMode == RepeatMode.ONE

        if (isCurrentlyPlaying && crossfadeSec > 0 && (track.id != currentTrack.id || isRepeatOne)) {
            _playbackState.update {
                it.copy(
                    queue = currentQueue,
                    currentIndex = targetIndex
                )
            }
            val transitioned = transitionManager.startManualTransition(
                primaryPlayer = activePlayer,
                nextTrack = track,
                nextIndex = targetIndex,
                crossfadeSec = crossfadeSec
            )
            if (!transitioned) {
                loadAndPlaySingleTrack(currentQueue, targetIndex)
            }
        } else {
            _playbackState.update {
                it.copy(
                    queue = currentQueue,
                    currentIndex = targetIndex,
                    currentTrack = track
                )
            }
            loadAndPlaySingleTrack(currentQueue, targetIndex)
        }
    }

    private fun loadAndPlaySingleTrack(queue: List<AudioTrack>, index: Int) {
        val track = queue.getOrNull(index) ?: return
        transitionManager.resetTransition(activePlayer)

        try {
            val mediaItem = buildMediaItem(track)
            activePlayer.stop()
            activePlayer.clearMediaItems()
            activePlayer.setMediaItem(mediaItem)
            activePlayer.repeatMode = Player.REPEAT_MODE_OFF
            activePlayer.volume = 1.0f // 100% volume
            activePlayer.prepare()
            activePlayer.seekTo(0L)
            activePlayer.play()

            _playbackState.update {
                it.copy(
                    currentIndex = index,
                    currentTrack = track,
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

    fun stopPlayback() {
        transitionManager.resetTransition(activePlayer)
        activePlayer.stop()
        activePlayer.clearMediaItems()
        visualizerEngine.reset()
        _playbackState.update {
            it.copy(
                isPlaying = false,
                currentPositionMs = 0L
            )
        }
        stopTicker()
    }

    fun seekTo(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        transitionManager.resetTransition(activePlayer)
        activePlayer.seekTo(safePos)
        activePlayer.volume = 1.0f
        _playbackState.update { it.copy(currentPositionMs = safePos) }
    }

    fun skipNext() {
        val queue = _playbackState.value.queue
        if (queue.isEmpty()) return

        val nextIndex = when {
            _playbackState.value.repeatMode == RepeatMode.ONE -> _playbackState.value.currentIndex
            _playbackState.value.currentIndex + 1 < queue.size -> _playbackState.value.currentIndex + 1
            _playbackState.value.repeatMode == RepeatMode.ALL -> 0
            else -> -1
        }

        if (nextIndex < 0 || nextIndex !in queue.indices) return

        val nextTrack = queue[nextIndex]
        val crossfadeSec = _appSettings.value.crossfadeDurationSec
        val currentTrack = _playbackState.value.currentTrack
        val isCurrentlyPlaying = (activePlayer.isPlaying || transitionManager.isCrossfadeActive) && currentTrack != null

        if (isCurrentlyPlaying && crossfadeSec > 0 && (nextTrack.id != currentTrack.id || _playbackState.value.repeatMode == RepeatMode.ONE)) {
            val transitioned = transitionManager.startManualTransition(
                primaryPlayer = activePlayer,
                nextTrack = nextTrack,
                nextIndex = nextIndex,
                crossfadeSec = crossfadeSec
            )
            if (!transitioned) {
                loadAndPlaySingleTrack(queue, nextIndex)
            }
        } else {
            transitionManager.resetTransition(activePlayer)
            loadAndPlaySingleTrack(queue, nextIndex)
        }
    }

    fun skipPrevious() {
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

        if (prevIndex !in queue.indices) {
            seekTo(0)
            return
        }

        val prevTrack = queue[prevIndex]
        val crossfadeSec = _appSettings.value.crossfadeDurationSec
        val currentTrack = _playbackState.value.currentTrack
        val isCurrentlyPlaying = (activePlayer.isPlaying || transitionManager.isCrossfadeActive) && currentTrack != null

        if (isCurrentlyPlaying && crossfadeSec > 0 && (prevTrack.id != currentTrack.id || _playbackState.value.repeatMode == RepeatMode.ONE)) {
            val transitioned = transitionManager.startManualTransition(
                primaryPlayer = activePlayer,
                nextTrack = prevTrack,
                nextIndex = prevIndex,
                crossfadeSec = crossfadeSec
            )
            if (!transitioned) {
                loadAndPlaySingleTrack(queue, prevIndex)
            }
        } else {
            transitionManager.resetTransition(activePlayer)
            loadAndPlaySingleTrack(queue, prevIndex)
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
    }

    fun cycleRepeatMode() {
        val nextMode = when (_playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _playbackState.update { it.copy(repeatMode = nextMode) }
    }

    fun addToQueue(track: AudioTrack) {
        val newQueue = _playbackState.value.queue.toMutableList().apply { add(track) }
        _playbackState.update { it.copy(queue = newQueue) }
    }

    fun playNextInQueue(track: AudioTrack) {
        val queue = _playbackState.value.queue.toMutableList()
        val insertIndex = (_playbackState.value.currentIndex + 1).coerceIn(0, queue.size)
        queue.add(insertIndex, track)
        _playbackState.update { it.copy(queue = queue) }
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
        }
    }

    fun clearQueue() {
        val currentTrack = _playbackState.value.currentTrack
        val queue = if (currentTrack != null) listOf(currentTrack) else emptyList()
        _playbackState.update {
            it.copy(queue = queue, currentIndex = if (currentTrack != null) 0 else -1)
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
        val oldPlayer = activePlayer
        try {
            oldPlayer.removeListener(playerListener)
        } catch (e: Exception) {
            Log.w("PlaybackController", "Old player listener remove error: ${e.message}")
        }

        // Attach listener to promoted player and enable audio focus handling
        promotedPlayer.addListener(playerListener)
        try {
            promotedPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // handle audio focus as primary player
            )
        } catch (e: Exception) {
            Log.w("PlaybackController", "Error setting audio focus on promoted player: ${e.message}")
        }
        activePlayer = promotedPlayer

        // Update MediaSession if active
        try {
            mediaSession?.setPlayer(getSessionPlayer())
        } catch (e: Exception) {
            Log.w("PlaybackController", "Error updating MediaSession player: ${e.message}")
        }

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

        // Safely stop and release old player without affecting current playback
        try {
            oldPlayer.stop()
            oldPlayer.clearMediaItems()
            oldPlayer.release()
            Log.d("PlaybackController", "[PROMOTION] Old player instance released successfully.")
        } catch (e: Exception) {
            Log.w("PlaybackController", "Error releasing old player instance: ${e.message}")
        }
    }

    private fun handleTrackEnded() {
        if (_playbackState.value.repeatMode == RepeatMode.ONE) {
            val track = _playbackState.value.currentTrack
            if (track != null) {
                loadAndPlaySingleTrack(_playbackState.value.queue, _playbackState.value.currentIndex)
            } else {
                seekTo(0)
                activePlayer.play()
            }
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
                delay(200)
                val currentPos = activePlayer.currentPosition
                val rawDuration = activePlayer.duration
                val trackDuration = _playbackState.value.currentTrack?.durationMs ?: 0L
                val effectiveDuration = if (rawDuration > 0 && rawDuration != C.TIME_UNSET) rawDuration else trackDuration
                val buffered = activePlayer.bufferedPosition

                _playbackState.update {
                    it.copy(
                        currentPositionMs = currentPos,
                        durationMs = effectiveDuration,
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
                    durationMs = effectiveDuration,
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
