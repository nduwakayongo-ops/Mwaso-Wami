package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ThemeMode
import com.example.ui.MainViewModel
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.SleepTimerDialog
import com.example.ui.components.TrackDetailsDialog
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.MyApplicationTheme

enum class MainNavTab(val title: String, val icon: ImageVector) {
    AUDIO("Músicas", Icons.Default.MusicNote),
    VIDEO("Vídeos", Icons.Default.VideoLibrary),
    QUEUE_FAV("Fila & Fav", Icons.Default.QueueMusic),
    STATS("Estatísticas", Icons.Default.BarChart),
    SETTINGS("Definições", Icons.Default.Settings)
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onFirstUIRendered: () -> Unit = {}
) {
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val isSystemDark = isSystemInDarkTheme()
    val isDarkTheme = when (appSettings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemDark
    }

    LaunchedEffect(Unit) {
        onFirstUIRendered()
    }

    MyApplicationTheme(darkTheme = isDarkTheme) {
        val tracks by viewModel.tracks.collectAsStateWithLifecycle()
        val currentTrackId by viewModel.currentTrackId.collectAsStateWithLifecycle()
        val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
        val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
        val selectedSortOrder by viewModel.selectedSortOrder.collectAsStateWithLifecycle()
        val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
        val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
        val isFullPlayerOpen by viewModel.isFullPlayerOpen.collectAsStateWithLifecycle()
        val selectedVideo by viewModel.selectedVideo.collectAsStateWithLifecycle()
        val showSleepTimerDialog by viewModel.showSleepTimerDialog.collectAsStateWithLifecycle()
        val showTrackDetailsDialog by viewModel.showTrackDetailsDialog.collectAsStateWithLifecycle()
        val sleepTimerRemainingSec by viewModel.sleepTimerRemainingSec.collectAsStateWithLifecycle()

        var selectedTab by remember { mutableIntStateOf(0) }

        // Request storage / media permissions on start
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = permissions.values.any { it }
            if (granted) {
                viewModel.onPermissionGranted()
            }
        }

        LaunchedEffect(Unit) {
            val permissionsToRequest = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }

        Scaffold(
            bottomBar = {
                if (selectedVideo == null) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = AmberPrimary,
                        tonalElevation = 8.dp,
                        modifier = Modifier.testTag("main_navigation_bar")
                    ) {
                        MainNavTab.values().forEachIndexed { index, tab ->
                            val isSelected = selectedTab == index
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { selectedTab = index },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.title,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.Black,
                                    selectedTextColor = AmberPrimary,
                                    indicatorColor = AmberPrimary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Tab Content Screen
                when (selectedTab) {
                    0 -> AudioLibraryScreen(
                        tracks = tracks,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                        selectedSortOrder = selectedSortOrder,
                        searchQuery = searchQuery,
                        isScanning = isScanning,
                        onSortOrderChange = { viewModel.setSortOrder(it) },
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        onRefreshScan = { viewModel.scanMedia(forceRescan = true) },
                        onTrackClick = { track, list -> viewModel.playTrack(track, list) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onPlayNext = { viewModel.playNextInQueue(it) },
                        onAddToQueue = { viewModel.addToQueue(it) },
                        onShowInfo = { viewModel.showTrackDetails(it) }
                    )
                    1 -> {
                        val videos by viewModel.videos.collectAsStateWithLifecycle()
                        VideoLibraryScreen(
                            videos = videos,
                            isScanning = isScanning,
                            onRefreshScan = { viewModel.scanMedia(forceRescan = true) },
                            onVideoClick = { viewModel.openVideo(it) }
                        )
                    }
                    2 -> {
                        val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
                        QueueAndFavoritesScreen(
                            playbackState = playbackState,
                            favoriteTracks = favoriteTracks,
                            onTrackClick = { track, list -> viewModel.playTrack(track, list) },
                            onRemoveFromQueue = { viewModel.removeFromQueue(it) },
                            onReorderQueue = { from, to -> viewModel.reorderQueue(from, to) },
                            onClearQueue = { viewModel.clearQueue() },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onPlayNext = { viewModel.playNextInQueue(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onShowInfo = { viewModel.showTrackDetails(it) }
                        )
                    }
                    3 -> {
                        val totalTrackCount by viewModel.totalTrackCount.collectAsStateWithLifecycle()
                        val totalPlaybackTimeMs by viewModel.totalPlaybackTimeMs.collectAsStateWithLifecycle()
                        val totalPlayCount by viewModel.totalPlayCount.collectAsStateWithLifecycle()
                        val topArtist by viewModel.topArtist.collectAsStateWithLifecycle()
                        val topGenre by viewModel.topGenre.collectAsStateWithLifecycle()
                        val history by viewModel.history.collectAsStateWithLifecycle()
                        StatisticsScreen(
                            totalTrackCount = totalTrackCount,
                            totalPlaybackTimeMs = totalPlaybackTimeMs,
                            totalPlayCount = totalPlayCount,
                            topArtist = topArtist,
                            topGenre = topGenre,
                            history = history,
                            onClearHistory = { viewModel.clearHistory() }
                        )
                    }
                    4 -> SettingsAndAboutScreen(
                        appSettings = appSettings,
                        onUpdateEarlyTransition = { viewModel.updateEarlyTransition(it) },
                        onUpdateCrossfade = { viewModel.updateCrossfade(it) },
                        onUpdateEconomyMode = { viewModel.updateEconomyMode(it) },
                        onUpdateThemeMode = { viewModel.updateThemeMode(it) },
                        onUpdateVideoLockBehavior = { viewModel.updateVideoLockBehavior(it) },
                        onUpdateGesturesEnabled = { viewModel.updateGesturesEnabled(it) }
                    )
                }

                // Persistent Mini Player (Pinned above navigation bar)
                if (!isFullPlayerOpen && selectedVideo == null) {
                    MiniPlayerBar(
                        playbackState = playbackState,
                        audioVisualizerState = viewModel.audioVisualizerState,
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onSkipNext = { viewModel.skipNext() },
                        onClick = { viewModel.openFullPlayer() },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // Full Audio Player Overlay (Animated Slide Up)
                AnimatedVisibility(
                    visible = isFullPlayerOpen,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.fillMaxSize()
                ) {
                    FullAudioPlayerScreen(
                        playbackState = playbackState,
                        audioVisualizerState = viewModel.audioVisualizerState,
                        economyMode = appSettings.economyMode,
                        sleepTimerRemainingSec = sleepTimerRemainingSec,
                        onClose = { viewModel.closeFullPlayer() },
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onSeekTo = { viewModel.seekTo(it) },
                        onSkipNext = { viewModel.skipNext() },
                        onSkipPrevious = { viewModel.skipPrevious() },
                        onToggleShuffle = { viewModel.toggleShuffle() },
                        onCycleRepeatMode = { viewModel.cycleRepeatMode() },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onOpenSleepTimer = { viewModel.openSleepTimerDialog() },
                        onShowInfo = { viewModel.showTrackDetails(it) },
                        onOpenQueueTab = {
                            viewModel.closeFullPlayer()
                            selectedTab = 2
                        }
                    )
                }

                // Video Player Screen Overlay
                selectedVideo?.let { currentVideo ->
                    VideoPlayerScreen(
                        video = currentVideo,
                        gesturesEnabled = appSettings.gesturesEnabled,
                        onClose = { viewModel.closeVideo() },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Dialogs
                if (showSleepTimerDialog) {
                    SleepTimerDialog(
                        remainingSeconds = sleepTimerRemainingSec,
                        onSetTimer = { mins -> viewModel.setSleepTimer(mins) },
                        onCancelTimer = { viewModel.cancelSleepTimer() },
                        onDismiss = { viewModel.closeSleepTimerDialog() }
                    )
                }

                showTrackDetailsDialog?.let { track ->
                    TrackDetailsDialog(
                        track = track,
                        onDismiss = { viewModel.closeTrackDetails() }
                    )
                }
            }
        }
    }
}
