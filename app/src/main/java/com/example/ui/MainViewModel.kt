package com.example.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MwasoWamiApp
import com.example.data.local.MediaRepository
import com.example.data.model.AppSettings
import com.example.data.model.AudioTrack
import com.example.data.model.PlaybackHistoryItem
import com.example.data.model.PlaybackState
import com.example.data.model.SortOrder
import com.example.data.model.ThemeMode
import com.example.data.model.VideoItem
import com.example.data.model.VideoScreenLockBehavior
import com.example.scanner.MediaScanner
import com.example.service.PlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val database = (application as MwasoWamiApp).database
    val repository = MediaRepository(database)
    val playbackController = PlaybackController.getInstance(application)
    val mediaScanner = MediaScanner(application, database)

    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState
    val audioVisualizerState: StateFlow<com.example.service.audio.RealtimeAudioState> = playbackController.audioVisualizerState
    val appSettings: StateFlow<AppSettings> = playbackController.appSettings
    val sleepTimerRemainingSec: StateFlow<Int?> = playbackController.sleepTimerRemainingSec

    // High-efficiency discrete state flows to prevent recomposition loops on 200ms tickers
    val currentTrackId: StateFlow<Long?> = playbackController.playbackState
        .map { it.currentTrack?.id }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isPlaying: StateFlow<Boolean> = playbackController.playbackState
        .map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val currentTrack: StateFlow<AudioTrack?> = playbackController.playbackState
        .map { it.currentTrack }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _selectedSortOrder = MutableStateFlow(SortOrder.TITLE_AZ)
    val selectedSortOrder: StateFlow<SortOrder> = _selectedSortOrder.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    private val scanMutex = Mutex()
    private var hasInitialScanCompleted = false

    private val _isFullPlayerOpen = MutableStateFlow(false)
    val isFullPlayerOpen: StateFlow<Boolean> = _isFullPlayerOpen.asStateFlow()

    private val _selectedVideo = MutableStateFlow<VideoItem?>(null)
    val selectedVideo: StateFlow<VideoItem?> = _selectedVideo.asStateFlow()

    private val _showSleepTimerDialog = MutableStateFlow(false)
    val showSleepTimerDialog: StateFlow<Boolean> = _showSleepTimerDialog.asStateFlow()

    private val _showTrackDetailsDialog = MutableStateFlow<AudioTrack?>(null)
    val showTrackDetailsDialog: StateFlow<AudioTrack?> = _showTrackDetailsDialog.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val tracks: StateFlow<List<AudioTrack>> = combine(_selectedSortOrder, _searchQuery) { sort, query ->
        Pair(sort, query)
    }.flatMapLatest { (sort, query) ->
        if (query.isBlank()) {
            repository.getTracks(sort)
        } else {
            repository.searchTracks(query)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteTracks: StateFlow<List<AudioTrack>> = repository.getFavoriteTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videos: StateFlow<List<VideoItem>> = repository.getAllVideos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<PlaybackHistoryItem>> = repository.getHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalTrackCount: StateFlow<Int> = repository.getTotalTrackCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalPlaybackTimeMs: StateFlow<Long?> = repository.getTotalPlaybackTimeMs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalPlayCount: StateFlow<Int?> = repository.getTotalPlayCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val topArtist: StateFlow<String?> = repository.getTopArtist()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val topGenre: StateFlow<String?> = repository.getTopGenre()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        Log.d("PERF", "[PERF] MainViewModel start")
        playbackController.initRepository(repository)
        viewModelScope.launch(Dispatchers.IO) {
            val roomStart = System.currentTimeMillis()
            val settings = repository.getOrCreateSettings()
            _selectedSortOrder.value = settings.defaultSortOrder
            Log.d("PERF", "[PERF] Settings initialized in background: ${System.currentTimeMillis() - roomStart}ms")
            if (hasMediaPermission()) {
                scanMedia(forceRescan = false)
            }
        }
    }

    private fun hasMediaPermission(): Boolean {
        val app = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(app, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(app, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun onPermissionGranted() {
        viewModelScope.launch(Dispatchers.IO) {
            hasInitialScanCompleted = false
            scanMedia(forceRescan = false)
        }
    }

    fun scanMedia(forceRescan: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isScanning.value && !forceRescan) return@launch
            if (hasInitialScanCompleted && !forceRescan) return@launch
            scanMutex.withLock {
                if (hasInitialScanCompleted && !forceRescan) return@withLock
                _isScanning.value = true
                try {
                    mediaScanner.scanAllMedia(forceRescan)
                    hasInitialScanCompleted = true
                } finally {
                    _isScanning.value = false
                }
            }
        }
    }

    fun setSortOrder(sortOrder: SortOrder) {
        _selectedSortOrder.value = sortOrder
        viewModelScope.launch(Dispatchers.IO) {
            val current = appSettings.value
            repository.updateSettings(current.copy(defaultSortOrder = sortOrder))
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun playTrack(track: AudioTrack, trackList: List<AudioTrack>? = null) {
        if (trackList != null && trackList.isNotEmpty()) {
            val index = trackList.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            playbackController.playTrackList(trackList, index)
        } else {
            playbackController.playSingleTrack(track)
        }
    }

    fun togglePlayPause() = playbackController.togglePlayPause()
    fun stopPlayback() = playbackController.stopPlayback()
    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)
    fun skipNext() = playbackController.skipNext()
    fun skipPrevious() = playbackController.skipPrevious()
    fun toggleShuffle() = playbackController.toggleShuffle()
    fun cycleRepeatMode() = playbackController.cycleRepeatMode()
    fun addToQueue(track: AudioTrack) = playbackController.addToQueue(track)
    fun playNextInQueue(track: AudioTrack) = playbackController.playNextInQueue(track)
    fun removeFromQueue(index: Int) = playbackController.removeFromQueue(index)
    fun reorderQueue(from: Int, to: Int) = playbackController.reorderQueue(from, to)
    fun clearQueue() = playbackController.clearQueue()

    fun toggleFavorite(track: AudioTrack) {
        viewModelScope.launch(Dispatchers.IO) {
            val newFav = !track.isFavorite
            repository.setFavorite(track.id, newFav)
            playbackController.updateFavorite(track.id, newFav)
        }
    }

    fun openFullPlayer() {
        _isFullPlayerOpen.value = true
    }

    fun closeFullPlayer() {
        _isFullPlayerOpen.value = false
    }

    fun openVideo(video: VideoItem) {
        if (playbackState.value.isPlaying) {
            playbackController.togglePlayPause()
        }
        _selectedVideo.value = video
    }

    fun closeVideo() {
        _selectedVideo.value = null
    }

    // Sleep Timer
    fun openSleepTimerDialog() {
        _showSleepTimerDialog.value = true
    }

    fun closeSleepTimerDialog() {
        _showSleepTimerDialog.value = false
    }

    fun setSleepTimer(minutes: Int) {
        playbackController.setSleepTimer(minutes)
        closeSleepTimerDialog()
    }

    fun cancelSleepTimer() {
        playbackController.cancelSleepTimer()
        closeSleepTimerDialog()
    }

    // Track Details
    fun showTrackDetails(track: AudioTrack) {
        _showTrackDetailsDialog.value = track
    }

    fun closeTrackDetails() {
        _showTrackDetailsDialog.value = null
    }

    // History
    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHistory()
        }
    }

    // Settings Updates
    fun updateEarlyTransition(seconds: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = appSettings.value.copy(earlyTransitionSec = seconds)
            repository.updateSettings(updated)
        }
    }

    fun updateCrossfade(seconds: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = appSettings.value.copy(crossfadeDurationSec = seconds)
            repository.updateSettings(updated)
        }
    }

    fun updateEconomyMode(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = appSettings.value.copy(economyMode = enabled)
            repository.updateSettings(updated)
        }
    }

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = appSettings.value.copy(themeMode = mode)
            repository.updateSettings(updated)
        }
    }

    fun updateVideoLockBehavior(behavior: VideoScreenLockBehavior) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = appSettings.value.copy(videoLockBehavior = behavior)
            repository.updateSettings(updated)
        }
    }

    fun updateGesturesEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = appSettings.value.copy(gesturesEnabled = enabled)
            repository.updateSettings(updated)
        }
    }
}
