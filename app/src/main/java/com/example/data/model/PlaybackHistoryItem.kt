package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_history",
    indices = [
        Index(value = ["playedTimestamp"]),
        Index(value = ["trackId"])
    ]
)
data class PlaybackHistoryItem(
    @PrimaryKey(autoGenerate = true)
    val historyId: Long = 0,
    val trackId: Long,
    val trackTitle: String,
    val trackArtist: String,
    val playedTimestamp: Long = System.currentTimeMillis(),
    val durationPlayedMs: Long = 0
)
