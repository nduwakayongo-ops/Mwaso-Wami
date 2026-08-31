package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.VideoItem
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM video_items ORDER BY title ASC")
    fun getAllVideos(): Flow<List<VideoItem>>

    @Query("SELECT * FROM video_items WHERE id = :id LIMIT 1")
    suspend fun getVideoById(id: Long): VideoItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoItem>)

    @Update
    suspend fun updateVideo(video: VideoItem)

    @Query("UPDATE video_items SET lastPositionMs = :positionMs, playCount = playCount + 1 WHERE id = :id")
    suspend fun updateVideoProgress(id: Long, positionMs: Long)

    @Query("DELETE FROM video_items")
    suspend fun clearScannedVideos()

    @Query("SELECT COUNT(*) FROM video_items")
    fun getTotalVideoCount(): Flow<Int>
}
