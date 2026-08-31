package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SortOrder {
    TITLE_AZ,
    PLAY_COUNT,
    RECENTLY_PLAYED,
    ARTIST,
    GENRE
}

enum class VideoScreenLockBehavior {
    PAUSE_VIDEO,
    CONTINUE_AUDIO,
    CONTINUE_PLAYBACK
}

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey
    val id: Int = 1,
    val digitalGainPercent: Int = 100, // 100, 150, 200, 250, 300
    val showGainWarning: Boolean = true,
    val antiClippingLimiter: Boolean = true,
    val earlyTransitionSec: Int = 8, // 0 = disabled, 3, 5, 7, 8, 10, 15 sec
    val crossfadeDurationSec: Int = 8, // 0 = disabled (OFF), 5, 6, 7, 8, 10 sec (default 8s DJ crossfade)
    val economyMode: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val videoLockBehavior: VideoScreenLockBehavior = VideoScreenLockBehavior.CONTINUE_AUDIO,
    val gesturesEnabled: Boolean = true,
    val autoPlayOnLaunch: Boolean = false,
    val defaultSortOrder: SortOrder = SortOrder.TITLE_AZ,
    val developerName: String = "Nduwa Kayongo",
    val developerWhatsApp: String = "+244 942 022 933"
)
