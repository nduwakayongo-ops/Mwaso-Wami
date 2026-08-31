package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audio_tracks",
    indices = [
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["album"]),
        Index(value = ["genre"]),
        Index(value = ["playCount"]),
        Index(value = ["lastPlayed"]),
        Index(value = ["isFavorite"]),
        Index(value = ["dateAdded"])
    ]
)
data class AudioTrack(
    @PrimaryKey
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val mediaUri: String,
    val artworkUri: String? = null,
    val genre: String = "Côkwe / World",
    val year: Int = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val totalTimePlayedMs: Long = 0,
    val isFavorite: Boolean = false,
    val isSample: Boolean = false
) {
    val formattedDuration: String
        get() {
            val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }
}
