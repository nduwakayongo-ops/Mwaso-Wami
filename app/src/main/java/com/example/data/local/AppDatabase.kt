package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.AppSettings
import com.example.data.model.AudioTrack
import com.example.data.model.PlaybackHistoryItem
import com.example.data.model.VideoItem

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create indexes on audio_tracks
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_tracks_title` ON `audio_tracks` (`title`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_tracks_artist` ON `audio_tracks` (`artist`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_tracks_album` ON `audio_tracks` (`album`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_tracks_genre` ON `audio_tracks` (`genre`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_tracks_playCount` ON `audio_tracks` (`playCount`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_tracks_lastPlayed` ON `audio_tracks` (`lastPlayed`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_tracks_isFavorite` ON `audio_tracks` (`isFavorite`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_tracks_dateAdded` ON `audio_tracks` (`dateAdded`)")

        // Create indexes on video_items
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_video_items_title` ON `video_items` (`title`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_video_items_dateAdded` ON `video_items` (`dateAdded`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_video_items_playCount` ON `video_items` (`playCount`)")

        // Create indexes on playback_history
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_history_playedTimestamp` ON `playback_history` (`playedTimestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_history_trackId` ON `playback_history` (`trackId`)")
    }
}

@Database(
    entities = [
        AudioTrack::class,
        VideoItem::class,
        PlaybackHistoryItem::class,
        AppSettings::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun videoDao(): VideoDao
    abstract fun historyDao(): HistoryDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mwaso_wami_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

