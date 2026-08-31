package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AudioTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM audio_tracks ORDER BY title ASC")
    fun getAllTracksAZ(): Flow<List<AudioTrack>>

    @Query("SELECT * FROM audio_tracks ORDER BY playCount DESC, title ASC")
    fun getMostPlayedTracks(): Flow<List<AudioTrack>>

    @Query("SELECT * FROM audio_tracks WHERE lastPlayed > 0 ORDER BY lastPlayed DESC")
    fun getRecentlyPlayedTracks(): Flow<List<AudioTrack>>

    @Query("SELECT * FROM audio_tracks ORDER BY artist ASC, title ASC")
    fun getTracksByArtist(): Flow<List<AudioTrack>>

    @Query("SELECT * FROM audio_tracks ORDER BY genre ASC, title ASC")
    fun getTracksByGenre(): Flow<List<AudioTrack>>

    @Query("SELECT * FROM audio_tracks WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteTracks(): Flow<List<AudioTrack>>

    @Query("SELECT * FROM audio_tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: Long): AudioTrack?

    @Query("SELECT * FROM audio_tracks WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%' OR genre LIKE '%' || :query || '%'")
    fun searchTracks(query: String): Flow<List<AudioTrack>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<AudioTrack>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: AudioTrack)

    @Update
    suspend fun updateTrack(track: AudioTrack)

    @Query("UPDATE audio_tracks SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE audio_tracks SET playCount = playCount + 1, lastPlayed = :timestamp, totalTimePlayedMs = totalTimePlayedMs + :durationPlayedMs WHERE id = :id")
    suspend fun recordPlay(id: Long, timestamp: Long = System.currentTimeMillis(), durationPlayedMs: Long)

    @Query("DELETE FROM audio_tracks")
    suspend fun clearScannedTracks()

    @Query("SELECT COUNT(*) FROM audio_tracks")
    fun getTotalTrackCount(): Flow<Int>

    @Query("SELECT SUM(totalTimePlayedMs) FROM audio_tracks")
    fun getTotalPlaybackTimeMs(): Flow<Long?>

    @Query("SELECT SUM(playCount) FROM audio_tracks")
    fun getTotalPlayCount(): Flow<Int?>

    @Query("SELECT artist FROM audio_tracks GROUP BY artist ORDER BY SUM(playCount) DESC LIMIT 1")
    fun getTopArtist(): Flow<String?>

    @Query("SELECT genre FROM audio_tracks GROUP BY genre ORDER BY SUM(playCount) DESC LIMIT 1")
    fun getTopGenre(): Flow<String?>
}
