package com.example.data.model

data class TrackUserData(
    val id: Long,
    val isFavorite: Boolean,
    val playCount: Int,
    val lastPlayed: Long,
    val totalTimePlayedMs: Long
)
