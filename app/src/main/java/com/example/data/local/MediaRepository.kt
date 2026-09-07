package com.example.data.local

import com.example.data.model.AppSettings
import com.example.data.model.AudioTrack
import com.example.data.model.PlaybackHistoryItem
import com.example.data.model.SortOrder
import com.example.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class MediaRepository(private val database: AppDatabase) {
    val trackDao = database.trackDao()
    val videoDao = database.videoDao()
    val historyDao = database.historyDao()
    val settingsDao = database.settingsDao()

    fun getTracks(sortOrder: SortOrder): Flow<List<AudioTrack>> {
        return when (sortOrder) {
            SortOrder.TITLE_AZ -> trackDao.getAllTracksAZ()
            SortOrder.PLAY_COUNT -> trackDao.getMostPlayedTracks()
            SortOrder.RECENTLY_PLAYED -> trackDao.getRecentlyPlayedTracks()
            SortOrder.ARTIST -> trackDao.getTracksByArtist()
            SortOrder.GENRE -> trackDao.getTracksByGenre()
        }.flowOn(Dispatchers.IO)
    }

    fun getFavoriteTracks(): Flow<List<AudioTrack>> = trackDao.getFavoriteTracks().flowOn(Dispatchers.IO)

    fun searchTracks(query: String): Flow<List<AudioTrack>> = trackDao.searchTracks(query).flowOn(Dispatchers.IO)

    suspend fun getTrackById(id: Long): AudioTrack? = withContext(Dispatchers.IO) {
        trackDao.getTrackById(id)
    }

    suspend fun setFavorite(trackId: Long, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        trackDao.setFavorite(trackId, isFavorite)
    }

    suspend fun recordTrackPlayed(track: AudioTrack, durationPlayedMs: Long) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        trackDao.recordPlay(track.id, now, durationPlayedMs)
        historyDao.insertHistory(
            PlaybackHistoryItem(
                trackId = track.id,
                trackTitle = track.title,
                trackArtist = track.artist,
                playedTimestamp = now,
                durationPlayedMs = durationPlayedMs
            )
        )
    }

    fun getAllVideos(): Flow<List<VideoItem>> = videoDao.getAllVideos().flowOn(Dispatchers.IO)

    suspend fun getVideoById(id: Long): VideoItem? = withContext(Dispatchers.IO) {
        videoDao.getVideoById(id)
    }

    suspend fun updateVideoProgress(id: Long, positionMs: Long) = withContext(Dispatchers.IO) {
        videoDao.updateVideoProgress(id, positionMs)
    }

    fun getHistory(): Flow<List<PlaybackHistoryItem>> = historyDao.getAllHistory().flowOn(Dispatchers.IO)

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        historyDao.clearHistory()
    }

    fun getSettingsFlow(): Flow<AppSettings?> = settingsDao.getSettingsFlow().flowOn(Dispatchers.IO)

    suspend fun getOrCreateSettings(): AppSettings = withContext(Dispatchers.IO) {
        val existing = settingsDao.getSettings()
        if (existing != null) return@withContext existing
        val defaultSettings = AppSettings()
        settingsDao.insertSettings(defaultSettings)
        defaultSettings
    }

    suspend fun updateSettings(settings: AppSettings) = withContext(Dispatchers.IO) {
        settingsDao.insertSettings(settings)
    }

    // Statistics Flows
    fun getTotalTrackCount(): Flow<Int> = trackDao.getTotalTrackCount().flowOn(Dispatchers.IO)
    fun getTotalPlaybackTimeMs(): Flow<Long?> = trackDao.getTotalPlaybackTimeMs().flowOn(Dispatchers.IO)
    fun getTotalPlayCount(): Flow<Int?> = trackDao.getTotalPlayCount().flowOn(Dispatchers.IO)
    fun getTopArtist(): Flow<String?> = trackDao.getTopArtist().flowOn(Dispatchers.IO)
    fun getTopGenre(): Flow<String?> = trackDao.getTopGenre().flowOn(Dispatchers.IO)
}
