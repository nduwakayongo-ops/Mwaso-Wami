package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "video_items",
    indices = [
        Index(value = ["title"]),
        Index(value = ["dateAdded"]),
        Index(value = ["playCount"])
    ]
)
data class VideoItem(
    @PrimaryKey
    val id: Long,
    val title: String,
    val durationMs: Long,
    val mediaUri: String,
    val thumbnailUri: String? = null,
    val resolution: String = "HD",
    val sizeBytes: Long = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastPositionMs: Long = 0,
    val playCount: Int = 0,
    val isSample: Boolean = false
) {
    val formattedDuration: String
        get() {
            val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }

    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return ""
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1000) {
                String.format("%.1f GB", mb / 1024.0)
            } else {
                String.format("%.1f MB", mb)
            }
        }
}
