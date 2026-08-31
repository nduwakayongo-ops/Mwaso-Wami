package com.example.data.local

import com.example.data.model.AppSettings
import com.example.data.model.AudioTrack
import com.example.data.model.PlaybackHistoryItem
import com.example.data.model.SortOrder
import com.example.data.model.VideoItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

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
        }
    }

    fun getFavoriteTracks(): Flow<List<AudioTrack>> = trackDao.getFavoriteTracks()

    fun searchTracks(query: String): Flow<List<AudioTrack>> = trackDao.searchTracks(query)

    suspend fun getTrackById(id: Long): AudioTrack? = trackDao.getTrackById(id)

    suspend fun setFavorite(trackId: Long, isFavorite: Boolean) {
        trackDao.setFavorite(trackId, isFavorite)
    }

    suspend fun recordTrackPlayed(track: AudioTrack, durationPlayedMs: Long) {
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

    fun getAllVideos(): Flow<List<VideoItem>> = videoDao.getAllVideos()

    suspend fun getVideoById(id: Long): VideoItem? = videoDao.getVideoById(id)

    suspend fun updateVideoProgress(id: Long, positionMs: Long) {
        videoDao.updateVideoProgress(id, positionMs)
    }

    fun getHistory(): Flow<List<PlaybackHistoryItem>> = historyDao.getAllHistory()

    suspend fun clearHistory() = historyDao.clearHistory()

    fun getSettingsFlow(): Flow<AppSettings?> = settingsDao.getSettingsFlow()

    suspend fun getOrCreateSettings(): AppSettings {
        val existing = settingsDao.getSettings()
        if (existing != null) return existing
        val defaultSettings = AppSettings()
        settingsDao.insertSettings(defaultSettings)
        return defaultSettings
    }

    suspend fun updateSettings(settings: AppSettings) {
        settingsDao.insertSettings(settings)
    }

    // Statistics Flows
    fun getTotalTrackCount(): Flow<Int> = trackDao.getTotalTrackCount()
    fun getTotalPlaybackTimeMs(): Flow<Long?> = trackDao.getTotalPlaybackTimeMs()
    fun getTotalPlayCount(): Flow<Int?> = trackDao.getTotalPlayCount()
    fun getTopArtist(): Flow<String?> = trackDao.getTopArtist()
    fun getTopGenre(): Flow<String?> = trackDao.getTopGenre()
}
