package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.KeyEvent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@androidx.annotation.OptIn(UnstableApi::class)
class MediaPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val controller = PlaybackController.getInstance(this)
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Configure notification provider for lockscreen and floating notification bar
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setNotificationId(NOTIFICATION_ID)
            .build().apply {
                setSmallIcon(R.drawable.ic_mwaso_logo)
            }
        setMediaNotificationProvider(notificationProvider)

        val session = MediaSession.Builder(this, controller.getSessionPlayer())
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(MediaSessionCallback())
            .build()

        mediaSession = session
        controller.mediaSession = session
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mwaso Wami Reprodução",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles de reprodução na barra de notificações e tela de bloqueio"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableLights(false)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val playbackController = PlaybackController.getInstance(this@MediaPlaybackService)
            val currentTrack = playbackController.playbackState.value.currentTrack
            if (currentTrack != null) {
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(currentTrack.mediaUri))
                    .setMediaId(currentTrack.id.toString())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(currentTrack.title)
                            .setArtist(currentTrack.artist)
                            .setAlbumTitle(currentTrack.album)
                            .setArtworkUri(currentTrack.artworkUri?.let { Uri.parse(it) })
                            .build()
                    )
                    .build()
                val itemsWithStart = MediaSession.MediaItemsWithStartPosition(
                    listOf(mediaItem),
                    0,
                    playbackController.playbackState.value.currentPositionMs
                )
                return Futures.immediateFuture(itemsWithStart)
            }
            return super.onPlaybackResumption(mediaSession, controller)
        }

        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }
            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                val playbackController = PlaybackController.getInstance(this@MediaPlaybackService)
                when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        playbackController.skipNext()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        playbackController.skipPrevious()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY,
                    KeyEvent.KEYCODE_MEDIA_PAUSE,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    KeyEvent.KEYCODE_HEADSETHOOK -> {
                        playbackController.togglePlayPause()
                        return true
                    }
                }
            }
            return super.onMediaButtonEvent(session, controllerInfo, intent)
        }
    }

    override fun onDestroy() {
        val controller = PlaybackController.getInstance(this)
        controller.mediaSession = null
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "mwaso_wami_playback_channel"
        const val NOTIFICATION_ID = 1001
    }
}
