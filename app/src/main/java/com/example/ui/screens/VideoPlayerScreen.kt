package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.VideoItem
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.TerracottaAccent
import kotlinx.coroutines.delay

enum class VideoRepeatMode {
    OFF,        // Transição sequencial para o próximo vídeo
    REPEAT_ONE, // Repete o vídeo atual continuamente
    REPEAT_ALL  // Repete a lista toda em loop contínuo
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    video: VideoItem,
    playlist: List<VideoItem> = emptyList(),
    gesturesEnabled: Boolean,
    onNextVideo: (() -> Unit)? = null,
    onPreviousVideo: (() -> Unit)? = null,
    onSelectVideo: ((VideoItem) -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Android System Back Button Handler
    BackHandler {
        onClose()
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val uri = Uri.parse(video.mediaUri)
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }

    var isLocked by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(video.durationMs) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(VideoRepeatMode.OFF) }

    var gestureFeedbackText by remember { mutableStateOf<String?>(null) }
    var gestureFeedbackIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val effectivePlaylist = remember(playlist, video) {
        if (playlist.isNotEmpty()) playlist else listOf(video)
    }
    val currentIndex = remember(effectivePlaylist, video) {
        effectivePlaylist.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
    }
    val hasNext = currentIndex < effectivePlaylist.size - 1 || repeatMode == VideoRepeatMode.REPEAT_ALL
    val hasPrevious = currentIndex > 0 || repeatMode == VideoRepeatMode.REPEAT_ALL

    val currentRepeatMode by rememberUpdatedState(repeatMode)
    val currentOnNext by rememberUpdatedState(onNextVideo)
    val currentOnPrevious by rememberUpdatedState(onPreviousVideo)
    val currentOnSelect by rememberUpdatedState(onSelectVideo)

    val handleNext: () -> Unit = {
        if (currentIndex < effectivePlaylist.size - 1) {
            currentOnNext?.invoke()
        } else if (currentRepeatMode == VideoRepeatMode.REPEAT_ALL && effectivePlaylist.isNotEmpty()) {
            currentOnSelect?.invoke(effectivePlaylist.first())
        }
    }

    val handlePrevious: () -> Unit = {
        if (currentPositionMs > 3000L) {
            exoPlayer.seekTo(0)
        } else if (currentIndex > 0) {
            currentOnPrevious?.invoke()
        } else if (currentRepeatMode == VideoRepeatMode.REPEAT_ALL && effectivePlaylist.isNotEmpty()) {
            currentOnSelect?.invoke(effectivePlaylist.last())
        } else {
            exoPlayer.seekTo(0)
        }
    }

    // React to video changes (e.g. Next / Previous transition)
    LaunchedEffect(video.id) {
        val uri = Uri.parse(video.mediaUri)
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        currentPositionMs = 0L
        durationMs = video.durationMs
    }

    // Keep screen on during video playback
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            exoPlayer.release()
        }
    }

    // Configure ExoPlayer Repeat Mode
    LaunchedEffect(repeatMode) {
        exoPlayer.repeatMode = if (repeatMode == VideoRepeatMode.REPEAT_ONE) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
    }

    // Player position ticker and state listener
    LaunchedEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val d = exoPlayer.duration
                    if (d > 0) durationMs = d
                } else if (state == Player.STATE_ENDED) {
                    when (currentRepeatMode) {
                        VideoRepeatMode.REPEAT_ONE -> {
                            exoPlayer.seekTo(0)
                            exoPlayer.play()
                        }
                        VideoRepeatMode.REPEAT_ALL -> {
                            if (currentIndex < effectivePlaylist.size - 1) {
                                gestureFeedbackText = "Próximo vídeo..."
                                gestureFeedbackIcon = Icons.Default.SkipNext
                                currentOnNext?.invoke()
                            } else if (effectivePlaylist.isNotEmpty()) {
                                gestureFeedbackText = "Reiniciando lista..."
                                gestureFeedbackIcon = Icons.Default.Repeat
                                currentOnSelect?.invoke(effectivePlaylist.first())
                            }
                        }
                        VideoRepeatMode.OFF -> {
                            if (currentIndex < effectivePlaylist.size - 1) {
                                gestureFeedbackText = "Próximo vídeo..."
                                gestureFeedbackIcon = Icons.Default.SkipNext
                                currentOnNext?.invoke()
                            } else {
                                isPlaying = false
                            }
                        }
                    }
                }
            }
        }
        exoPlayer.addListener(listener)

        while (true) {
            currentPositionMs = exoPlayer.currentPosition
            delay(500)
        }
    }

    // Auto-hide controls after 4.5 seconds
    LaunchedEffect(showControls, isLocked) {
        if (showControls && !isLocked) {
            delay(4500)
            showControls = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("video_player_container")
            .pointerInput(isLocked, gesturesEnabled) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                    },
                    onDoubleTap = { offset ->
                        if (!isLocked && gesturesEnabled) {
                            val isRightSide = offset.x > size.width / 2
                            if (isRightSide) {
                                exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(durationMs))
                                gestureFeedbackText = "+10s"
                                gestureFeedbackIcon = Icons.Default.FastForward
                            } else {
                                exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                                gestureFeedbackText = "-10s"
                                gestureFeedbackIcon = Icons.Default.FastRewind
                            }
                        }
                    }
                )
            }
            .pointerInput(isLocked, gesturesEnabled) {
                if (!isLocked && gesturesEnabled) {
                    detectVerticalDragGestures { change, dragAmount ->
                        val isRightSide = change.position.x > size.width / 2
                        if (isRightSide) {
                            // Volume control
                            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val delta = if (dragAmount < 0) 1 else -1
                            val newVol = (currentVol + delta).coerceIn(0, maxVol)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                            gestureFeedbackText = "Volume: ${(newVol * 100) / maxVol}%"
                            gestureFeedbackIcon = Icons.Default.VolumeUp
                        } else {
                            // Brightness control
                            val currentBrightness = activity?.window?.attributes?.screenBrightness ?: 0.5f
                            val newBrightness = (currentBrightness - dragAmount / 400f).coerceIn(0.05f, 1.0f)
                            activity?.window?.attributes = activity?.window?.attributes?.apply {
                                screenBrightness = newBrightness
                            }
                            gestureFeedbackText = "Brilho: ${(newBrightness * 100).toInt()}%"
                            gestureFeedbackIcon = Icons.Default.BrightnessMedium
                        }
                    }
                }
            }
    ) {
        // Media3 ExoPlayer Video View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    this.resizeMode = resizeMode
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture Feedback HUD Toast
        LaunchedEffect(gestureFeedbackText) {
            if (gestureFeedbackText != null) {
                delay(1300)
                gestureFeedbackText = null
                gestureFeedbackIcon = null
            }
        }

        if (gestureFeedbackText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    gestureFeedbackIcon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = gestureFeedbackText ?: "",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        }

        // 🔒 Locked Floating Badge (When screen is locked)
        if (isLocked) {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(TerracottaAccent)
                        .clickable { isLocked = false }
                        .testTag("unlock_video_screen_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = "Desbloquear Tela",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Full Controls Overlay (When not locked and showControls is true)
        AnimatedVisibility(
            visible = showControls && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Control Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("video_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 1
                        )
                        if (effectivePlaylist.size > 1) {
                            Text(
                                text = "Vídeo ${currentIndex + 1} de ${effectivePlaylist.size}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    // 🔁 Repeat Mode Button
                    IconButton(
                        onClick = {
                            repeatMode = when (repeatMode) {
                                VideoRepeatMode.OFF -> VideoRepeatMode.REPEAT_ONE
                                VideoRepeatMode.REPEAT_ONE -> VideoRepeatMode.REPEAT_ALL
                                VideoRepeatMode.REPEAT_ALL -> VideoRepeatMode.OFF
                            }
                            when (repeatMode) {
                                VideoRepeatMode.OFF -> {
                                    gestureFeedbackText = "Repetição: Desativada (Próximo automático)"
                                    gestureFeedbackIcon = Icons.Default.Repeat
                                }
                                VideoRepeatMode.REPEAT_ONE -> {
                                    gestureFeedbackText = "Repetição: Este vídeo"
                                    gestureFeedbackIcon = Icons.Default.RepeatOne
                                }
                                VideoRepeatMode.REPEAT_ALL -> {
                                    gestureFeedbackText = "Repetição: Toda a lista"
                                    gestureFeedbackIcon = Icons.Default.Repeat
                                }
                            }
                        },
                        modifier = Modifier.testTag("video_repeat_button")
                    ) {
                        val (icon, tint) = when (repeatMode) {
                            VideoRepeatMode.OFF -> Pair(Icons.Default.Repeat, Color.White.copy(alpha = 0.6f))
                            VideoRepeatMode.REPEAT_ONE -> Pair(Icons.Default.RepeatOne, AmberPrimary)
                            VideoRepeatMode.REPEAT_ALL -> Pair(Icons.Default.Repeat, AmberPrimary)
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = "Modo de Repetição",
                            tint = tint
                        )
                    }

                    // 🔒 Lock Button
                    IconButton(
                        onClick = { isLocked = true },
                        modifier = Modifier.testTag("lock_video_screen_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Bloquear Controles",
                            tint = GoldAccent
                        )
                    }

                    // Aspect Ratio Button
                    IconButton(
                        onClick = {
                            resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            } else {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        modifier = Modifier.testTag("video_aspect_ratio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitScreen,
                            contentDescription = "Ajustar Proporção",
                            tint = Color.White
                        )
                    }

                    // Speed Menu
                    Box {
                        IconButton(
                            onClick = { showSpeedMenu = true },
                            modifier = Modifier.testTag("video_speed_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Velocidade",
                                tint = Color.White
                            )
                        }

                        DropdownMenu(
                            expanded = showSpeedMenu,
                            onDismissRequest = { showSpeedMenu = false }
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text("${speed}x") },
                                    onClick = {
                                        playbackSpeed = speed
                                        exoPlayer.playbackParameters = PlaybackParameters(speed)
                                        showSpeedMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Center Play/Pause & Skip Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ⏮ Previous Video Button
                    IconButton(
                        onClick = handlePrevious,
                        enabled = hasPrevious || currentPositionMs > 3000L,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("video_previous_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Vídeo Anterior",
                            tint = if (hasPrevious || currentPositionMs > 3000L) Color.White else Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // -10s Rewind
                    IconButton(
                        onClick = {
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "-10s",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Play/Pause Button
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(AmberPrimary)
                            .clickable {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                        exoPlayer.seekTo(0)
                                    }
                                    exoPlayer.play()
                                }
                            }
                            .testTag("video_play_pause_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // +10s Fast Forward
                    IconButton(
                        onClick = {
                            exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(durationMs))
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "+10s",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // ⏭ Next Video Button
                    IconButton(
                        onClick = handleNext,
                        enabled = hasNext,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("video_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Próximo Vídeo",
                            tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Bottom Timeline & Timestamps
                Column(modifier = Modifier.fillMaxWidth()) {
                    val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

                    Slider(
                        value = progress,
                        onValueChange = {
                            val newPos = (it * durationMs).toLong()
                            exoPlayer.seekTo(newPos)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = GoldAccent,
                            activeTrackColor = AmberPrimary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_timeline_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatMs(currentPositionMs),
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White)
                        )
                        Text(
                            text = formatMs(durationMs),
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White)
                        )
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
