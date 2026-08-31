package com.example.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.AudioTrack
import com.example.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaScanner(private val context: Context, private val database: AppDatabase) {

    suspend fun scanAllMedia(forceRescan: Boolean = false): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        Log.d("PERF", "[PERF] MediaStore loading start")
        val audioCount = scanAudio(forceRescan)
        Log.d("PERF", "[PERF] Video thumbnails loading start")
        val videoCount = scanVideo(forceRescan)
        val elapsed = System.currentTimeMillis() - startTime
        Log.d("PERF", "[PERF] MediaStore loading complete: ${elapsed}ms (Audio: $audioCount, Video: $videoCount)")
        Pair(audioCount, videoCount)
    }

    suspend fun scanAudio(forceRescan: Boolean = false): Int = withContext(Dispatchers.IO) {
        val trackDao = database.trackDao()
        if (forceRescan) {
            trackDao.clearScannedTracks()
        }

        var totalScanned = 0
        val batch = mutableListOf<AudioTrack>()
        var currentBatchTarget = 15 // First batch is 15 items for rapid initial UI render

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.GENRE else MediaStore.Audio.Media._ID
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Faixa Desconhecida"
                    val artist = cursor.getString(artistCol) ?: "Artista Desconhecido"
                    val album = cursor.getString(albumCol) ?: "Álbum Desconhecido"
                    val duration = cursor.getLong(durationCol)
                    val dateAdded = cursor.getLong(dateAddedCol) * 1000

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    val albumArtUri = "content://media/external/audio/media/$id/albumart"

                    val genre = try {
                        val genreCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                        } else -1
                        if (genreCol >= 0) cursor.getString(genreCol) ?: "Música Local" else "Música Local"
                    } catch (e: Exception) {
                        "Música Local"
                    }

                    if (duration > 1000) { // filter out short notification sounds
                        batch.add(
                            AudioTrack(
                                id = id,
                                title = title,
                                artist = if (artist.contains("<unknown>")) "Artista Desconhecido" else artist,
                                album = if (album.contains("<unknown>")) "Álbum Desconhecido" else album,
                                durationMs = duration,
                                mediaUri = contentUri,
                                artworkUri = albumArtUri,
                                genre = genre,
                                dateAdded = dateAdded,
                                isSample = false
                            )
                        )
                        totalScanned++

                        if (batch.size >= currentBatchTarget) {
                            trackDao.insertTracks(batch.toList())
                            batch.clear()
                            currentBatchTarget = 40 // subsequent batches are 40 items
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MediaScanner", "Error querying audio MediaStore", e)
        }

        if (batch.isNotEmpty()) {
            trackDao.insertTracks(batch.toList())
            batch.clear()
        }

        totalScanned
    }

    suspend fun scanVideo(forceRescan: Boolean = false): Int = withContext(Dispatchers.IO) {
        val videoDao = database.videoDao()
        if (forceRescan) {
            videoDao.clearScannedVideos()
        }

        var totalScanned = 0
        val batch = mutableListOf<VideoItem>()
        var currentBatchTarget = 10 // First batch is 10 items for rapid initial UI render

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.RESOLUTION else MediaStore.Video.Media._ID
        )

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Vídeo Desconhecido"
                    val duration = cursor.getLong(durationCol)
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol) * 1000

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    val resolution = try {
                        val resCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            cursor.getColumnIndex(MediaStore.Video.Media.RESOLUTION)
                        } else -1
                        if (resCol >= 0) cursor.getString(resCol) ?: "HD" else "HD"
                    } catch (e: Exception) {
                        "HD"
                    }

                    batch.add(
                        VideoItem(
                            id = id,
                            title = title,
                            durationMs = duration,
                            mediaUri = contentUri,
                            thumbnailUri = contentUri,
                            resolution = resolution,
                            sizeBytes = size,
                            dateAdded = dateAdded,
                            isSample = false
                        )
                    )
                    totalScanned++

                    if (batch.size >= currentBatchTarget) {
                        videoDao.insertVideos(batch.toList())
                        batch.clear()
                        currentBatchTarget = 30
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MediaScanner", "Error querying video MediaStore", e)
        }

        if (batch.isNotEmpty()) {
            videoDao.insertVideos(batch.toList())
            batch.clear()
        }

        totalScanned
    }
}
