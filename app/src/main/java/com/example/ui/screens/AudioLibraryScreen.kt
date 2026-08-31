package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AudioTrack
import com.example.data.model.PlaybackState
import com.example.data.model.SortOrder
import com.example.ui.components.AudioTrackItem
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.TerracottaAccent

@Composable
fun AudioLibraryScreen(
    tracks: List<AudioTrack>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    selectedSortOrder: SortOrder,
    searchQuery: String,
    isScanning: Boolean,
    onSortOrderChange: (SortOrder) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onRefreshScan: () -> Unit,
    onTrackClick: (AudioTrack, List<AudioTrack>) -> Unit,
    onToggleFavorite: (AudioTrack) -> Unit,
    onPlayNext: (AudioTrack) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onShowInfo: (AudioTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(AmberPrimary, TerracottaAccent))),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_mwaso_logo),
                        contentDescription = "Mwaso Wami Logo",
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "MWASO WAMI",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            letterSpacing = 1.sp
                        ),
                        color = GoldAccent
                    )
                    Text(
                        text = "Minha Música — Côkwe",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { searchVisible = !searchVisible },
                    modifier = Modifier.testTag("toggle_search_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Pesquisar",
                        tint = if (searchVisible) AmberPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onRefreshScan,
                    enabled = !isScanning,
                    modifier = Modifier.testTag("refresh_library_button")
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = AmberPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Atualizar Biblioteca",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Search Bar (if visible)
        if (searchVisible) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Pesquisar por título, artista, álbum...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = AmberPrimary)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Limpar")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AmberPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("search_text_field")
            )
        }

        // Sorting & Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SortFilterChip(
                label = "Nome A-Z",
                selected = selectedSortOrder == SortOrder.TITLE_AZ,
                onClick = { onSortOrderChange(SortOrder.TITLE_AZ) },
                testTag = "sort_title_az"
            )
            SortFilterChip(
                label = "Mais tocadas",
                selected = selectedSortOrder == SortOrder.PLAY_COUNT,
                onClick = { onSortOrderChange(SortOrder.PLAY_COUNT) },
                testTag = "sort_play_count"
            )
            SortFilterChip(
                label = "Recentes",
                selected = selectedSortOrder == SortOrder.RECENTLY_PLAYED,
                onClick = { onSortOrderChange(SortOrder.RECENTLY_PLAYED) },
                testTag = "sort_recent"
            )
            SortFilterChip(
                label = "Por artista",
                selected = selectedSortOrder == SortOrder.ARTIST,
                onClick = { onSortOrderChange(SortOrder.ARTIST) },
                testTag = "sort_artist"
            )
            SortFilterChip(
                label = "Por género",
                selected = selectedSortOrder == SortOrder.GENRE,
                onClick = { onSortOrderChange(SortOrder.GENRE) },
                testTag = "sort_genre"
            )
        }

        // Tracks Count Subtitle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${tracks.size} faixas encontradas",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            if (isScanning) {
                Text(
                    text = "A verificar arquivos...",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        color = AmberPrimary
                    )
                )
            }
        }

        // Tracks List or Empty/Skeleton State
        if (tracks.isEmpty()) {
            if (isScanning) {
                // Skeleton loading list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("audio_tracks_skeleton_list"),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(6) {
                        TrackItemSkeleton()
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = AmberPrimary.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Nenhuma música corresponde à pesquisa" else "Nenhuma música encontrada no aparelho",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("audio_tracks_list"),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(tracks, key = { it.id }, contentType = { "audio_track" }) { track ->
                    val isCurrent = currentTrackId == track.id
                    AudioTrackItem(
                        track = track,
                        isCurrentTrack = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        onTrackClick = { onTrackClick(track, tracks) },
                        onToggleFavorite = { onToggleFavorite(track) },
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onShowInfo = { onShowInfo(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackItemSkeleton() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                )
            }
        }
    }
}

@Composable
private fun SortFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 12.sp
                )
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AmberPrimary,
            selectedLabelColor = Color.Black,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.testTag(testTag)
    )
}
