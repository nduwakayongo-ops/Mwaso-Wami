package com.example.data.model

enum class RepeatMode {
    OFF, ALL, ONE
}

data class PlaybackState(
    val currentTrack: AudioTrack? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffle: Boolean = false,
    val queue: List<AudioTrack> = emptyList(),
    val currentIndex: Int = -1,
    val playbackSpeed: Float = 1.0f,
    val digitalGainFactor: Float = 1.0f, // 1.0 = 100%, up to 3.0 = 300%
    val isAudioTransitionActive: Boolean = false
) {
    val progress: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val remainingMs: Long
        get() = (durationMs - currentPositionMs).coerceAtLeast(0L)

    val formattedCurrentPosition: String
        get() = formatTime(currentPositionMs)

    val formattedDuration: String
        get() = formatTime(durationMs)

    val formattedRemaining: String
        get() = "-" + formatTime(remainingMs)

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }
}
