package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.PlaybackHistoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY playedTimestamp DESC")
    fun getAllHistory(): Flow<List<PlaybackHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: PlaybackHistoryItem)

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()
}
