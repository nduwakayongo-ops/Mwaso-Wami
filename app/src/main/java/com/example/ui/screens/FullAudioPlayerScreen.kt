package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AudioTrack
import com.example.data.model.PlaybackState
import com.example.data.model.RepeatMode
import com.example.service.audio.RealtimeAudioState
import com.example.ui.components.RealtimeCoverWithAudioWaves
import com.example.ui.components.RealtimeWaveformProgressBar

private val ColorNeonGreen = Color(0xFF22C55E)
private val ColorMagenta = Color(0xFFD946EF)
private val ColorCrimson = Color(0xFFEF4444)
private val BackgroundPitchBlack = Color(0xFF070709)

@Composable
fun FullAudioPlayerScreen(
    playbackState: PlaybackState,
    audioState: RealtimeAudioState,
    economyMode: Boolean,
    sleepTimerRemainingSec: Int?,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleFavorite: (AudioTrack) -> Unit,
    onOpenSleepTimer: () -> Unit,
    onShowInfo: (AudioTrack) -> Unit,
    onOpenQueueTab: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val track = playbackState.currentTrack ?: return
    var selectedTopTab by remember { mutableIntStateOf(0) } // 0: Tocando, 1: Fila

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundPitchBlack)
            .testTag("full_audio_player_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ----------------------------------------------------
            // 1. Top Bar: Back Chevron, "Tocando" & "Fila" Tabs, 3-dots Menu
            // ----------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back / Minimize Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("close_full_player_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimizar Reprodutor",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Centered Tabs: "Tocando" & "Fila" (matching screenshot)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Tocando Tab
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { selectedTopTab = 0 }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Tocando",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (selectedTopTab == 0) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 15.sp,
                                color = if (selectedTopTab == 0) Color.White else Color.White.copy(alpha = 0.5f)
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (selectedTopTab == 0) {
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(2.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(ColorMagenta, Color(0xFFA855F7))
                                        )
                                    )
                            )
                        } else {
                            Spacer(modifier = Modifier.height(2.5.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Fila Tab
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable {
                                selectedTopTab = 1
                                onOpenQueueTab()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Fila",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (selectedTopTab == 1) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 15.sp,
                                color = if (selectedTopTab == 1) Color.White else Color.White.copy(alpha = 0.5f)
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (selectedTopTab == 1) {
                            Box(
                                modifier = Modifier
                                    .width(24.dp)
                                    .height(2.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(ColorMagenta)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(2.5.dp))
                        }
                    }
                }

                // 3-dots Menu Button
                IconButton(
                    onClick = { onShowInfo(track) },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("full_player_info_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opções",
                        tint = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ----------------------------------------------------
            // 2. Real-Time Dynamic Soundwaves & Album Cover Visualizer
            // ----------------------------------------------------
            RealtimeCoverWithAudioWaves(
                artworkUri = track.artworkUri,
                audioState = audioState,
                isPlaying = playbackState.isPlaying,
                economyMode = economyMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ----------------------------------------------------
            // 3. Track Title, Artist, Album Collection Details
            // ----------------------------------------------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track.artist.ifBlank { "Artista Desconhecido" },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.album.ifBlank { "Coleção de Áudios" },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.45f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ----------------------------------------------------
            // 4. Real-Time Waveform Equalizer Seeker / Progress Bar
            // ----------------------------------------------------
            RealtimeWaveformProgressBar(
                progress = playbackState.progress,
                durationMs = playbackState.durationMs,
                currentPositionMs = playbackState.currentPositionMs,
                formattedCurrent = playbackState.formattedCurrentPosition,
                formattedDuration = playbackState.formattedDuration,
                audioState = audioState,
                isPlaying = playbackState.isPlaying,
                onSeekTo = onSeekTo,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // ----------------------------------------------------
            // 5. Playback Controls: Shuffle, Previous, Play/Pause, Next, Repeat
            // ----------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier.testTag("full_player_shuffle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Aleatório",
                        tint = if (playbackState.isShuffle) ColorNeonGreen else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Previous Button
                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier.testTag("full_player_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Música Anterior",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Large Glowing Play/Pause Button (Spotify Green Circle style from screenshot)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .shadow(
                            elevation = if (playbackState.isPlaying) 18.dp else 8.dp,
                            shape = CircleShape,
                            spotColor = ColorNeonGreen
                        )
                        .background(Color(0xFF131F17))
                        .border(2.5.dp, ColorNeonGreen, CircleShape)
                        .clickable { onTogglePlayPause() }
                        .testTag("full_player_play_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pausar" else "Reproduzir",
                        tint = ColorNeonGreen,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Next Button
                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier.testTag("full_player_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Próxima Música",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repeat Mode Button
                IconButton(
                    onClick = onCycleRepeatMode,
                    modifier = Modifier.testTag("full_player_repeat_button")
                ) {
                    val icon = when (playbackState.repeatMode) {
                        RepeatMode.ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    }
                    val tint = when (playbackState.repeatMode) {
                        RepeatMode.OFF -> Color.White.copy(alpha = 0.4f)
                        RepeatMode.ALL, RepeatMode.ONE -> ColorNeonGreen
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Repetir",
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ----------------------------------------------------
            // 6. Bottom Utility Actions (Heart, Queue, Cast, Playlist)
            // ----------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Favorite Button
                IconButton(
                    onClick = { onToggleFavorite(track) },
                    modifier = Modifier.testTag("full_player_fav_button")
                ) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (track.isFavorite) "Favorito" else "Marcar como Favorito",
                        tint = if (track.isFavorite) ColorCrimson else Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Queue Button
                IconButton(
                    onClick = onOpenQueueTab,
                    modifier = Modifier.testTag("full_player_queue_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Fila",
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Cast / Output Device Button
                IconButton(
                    onClick = onOpenSleepTimer,
                    modifier = Modifier.testTag("full_player_cast_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Cast,
                        contentDescription = "Dispositivos / Temporizador",
                        tint = if (sleepTimerRemainingSec != null) ColorNeonGreen else Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Track Details / Playlist Button
                IconButton(
                    onClick = { onShowInfo(track) },
                    modifier = Modifier.testTag("full_player_details_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatListBulleted,
                        contentDescription = "Mais opções",
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
