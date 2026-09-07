package com.example.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.util.Size
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import com.example.MainActivity
import com.example.R
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(UnstableApi::class)
class MediaPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sessionActivityPendingIntent: PendingIntent? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var isForegroundActive: Boolean = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        isStartingOrRunning = true
        Log.i(LOG_TAG, "MWASO_SERVICE_CREATED")

        createNotificationChannel()
        registerScreenReceiver()

        val controller = PlaybackController.getInstance(this)
        sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Custom notification provider strictly configured for 3-button media style notification
        val notificationProvider = MwasoMediaNotificationProvider()
        setMediaNotificationProvider(notificationProvider)

        val session = MediaSession.Builder(this, controller.getSessionPlayer())
            .setSessionActivity(sessionActivityPendingIntent!!)
            .setCallback(MediaSessionCallback())
            .build()

        addSession(session)
        mediaSession = session
        controller.mediaSession = session
        Log.i(LOG_TAG, "MWASO_SESSION_CREATED")

        // Listen to playback state changes ONLY for discrete, meaningful state changes
        // (Track change, Play/Pause toggle, or DJ Crossfade start/end).
        // NEVER update on 200ms position ticks to eliminate high CPU usage and background crashes!
        serviceScope.launch {
            controller.playbackState
                .map { state ->
                    Triple(
                        state.currentTrack?.id,
                        state.isPlaying,
                        controller.transitionManager.isCrossfadeActive
                    )
                }
                .distinctUntilChanged()
                .collect { (_, isPlaying, isCrossfade) ->
                    val active = isPlaying || isCrossfade
                    if (active) {
                        acquireWakeLock()
                    } else {
                        releaseWakeLock()
                    }
                    mediaSession?.let { session ->
                        try {
                            onUpdateNotification(session, active)
                        } catch (e: Exception) {
                            Log.w(LOG_TAG, "Error updating notification from state collect: ${e.message}", e)
                        }
                    }
                }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(LOG_TAG, "MWASO_SERVICE_STARTED")
        if (intent?.action == ACTION_STOP) {
            stopPlaybackService()
            return START_NOT_STICKY
        }

        val controller = PlaybackController.getInstance(this)
        val isPlaying = controller.playbackState.value.isPlaying ||
                controller.transitionManager.isCrossfadeActive
        if (isPlaying) {
            acquireWakeLock()
        }

        // Satisfy the Android foreground service watchdog timer immediately
        mediaSession?.let { session ->
            try {
                onUpdateNotification(session, isPlaying)
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Error updating notification in onStartCommand: ${e.message}", e)
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val controller = PlaybackController.getInstance(this)
        val isPlaying = session.player.isPlaying ||
                controller.playbackState.value.isPlaying ||
                controller.transitionManager.isCrossfadeActive

        if (isPlaying && !isForegroundActive) {
            isForegroundActive = true
            Log.i(LOG_TAG, "MWASO_NOTIFICATION_STARTED")
        } else if (!isPlaying && isForegroundActive && !startInForegroundRequired) {
            isForegroundActive = false
            Log.i(LOG_TAG, "MWASO_NOTIFICATION_STOPPED")
        }

        try {
            super.onUpdateNotification(session, isPlaying || startInForegroundRequired)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Error in super.onUpdateNotification: ${e.message}", e)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val controller = PlaybackController.getInstance(this)
        val isPlaying = controller.playbackState.value.isPlaying ||
                controller.transitionManager.isCrossfadeActive
        if (!isPlaying) {
            stopPlaybackService()
        }
        // If playing, keep playing seamlessly in background
    }

    private fun stopPlaybackService() {
        releaseWakeLock()
        isForegroundActive = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MwasoWami:PlaybackWakeLock")?.apply {
                setReferenceCounted(false)
                acquire(2 * 3600 * 1000L) // 2 hours max safe timeout
            }
            Log.d(LOG_TAG, "MWASO_WAKELOCK_ACQUIRED")
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Could not acquire wake lock: ${e.message}", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(LOG_TAG, "MWASO_WAKELOCK_RELEASED")
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Error releasing wake lock: ${e.message}", e)
        }
        wakeLock = null
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val controller = PlaybackController.getInstance(this@MediaPlaybackService)
                val isPlaying = controller.playbackState.value.isPlaying ||
                        controller.transitionManager.isCrossfadeActive

                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        controller.visualizerEngine.setScreenVisible(false)
                        if (isPlaying) {
                            Log.i(LOG_TAG, "MWASO_SCREEN_OFF_KEEPING_PLAYBACK")
                        }
                    }
                    Intent.ACTION_SCREEN_ON,
                    Intent.ACTION_USER_PRESENT -> {
                        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                        if (km?.isKeyguardLocked == true && isPlaying) {
                            Log.i(LOG_TAG, "MWASO_LOCK_SCREEN_KEEPING_PLAYBACK")
                        }
                    }
                }
            }
        }
        try {
            registerReceiver(screenReceiver, filter)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Error registering screen receiver: ${e.message}")
        }
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Error unregistering screen receiver: ${e.message}", e)
            }
            screenReceiver = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MWASO WAMI Playback",
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

    private inner class MwasoMediaNotificationProvider : MediaNotification.Provider {
        override fun createNotification(
            mediaSession: MediaSession,
            customLayout: ImmutableList<CommandButton>,
            actionFactory: MediaNotification.ActionFactory,
            onNotificationChangedCallback: MediaNotification.Provider.Callback
        ): MediaNotification {
            val controller = PlaybackController.getInstance(this@MediaPlaybackService)
            val track = controller.playbackState.value.currentTrack
            val isPlaying = mediaSession.player.isPlaying ||
                    controller.playbackState.value.isPlaying ||
                    controller.transitionManager.isCrossfadeActive

            val title = track?.title ?: mediaSession.player.mediaMetadata.title?.toString() ?: "MWASO WAMI"
            val artist = track?.artist ?: mediaSession.player.mediaMetadata.artist?.toString() ?: ""

            val builder = NotificationCompat.Builder(this@MediaPlaybackService, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(artist)
                .setSubText("MWASO WAMI")
                .setSmallIcon(R.drawable.ic_notification_note)
                .setOngoing(isPlaying)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setShowWhen(false)

            sessionActivityPendingIntent?.let {
                builder.setContentIntent(it)
            }

            // Real artwork from MediaStore/file if available; neutral placeholder if absent (never fake artwork)
            try {
                val cachedArt = ArtworkCache.getCachedBitmap(track?.artworkUri)
                if (cachedArt != null) {
                    builder.setLargeIcon(cachedArt)
                } else if (!track?.artworkUri.isNullOrBlank()) {
                    val artUri = track?.artworkUri
                    serviceScope.launch {
                        try {
                            val loaded = withContext(Dispatchers.IO) {
                                ArtworkCache.loadBitmap(this@MediaPlaybackService, artUri)
                            }
                            if (loaded != null) {
                                val currentTrackUri = controller.playbackState.value.currentTrack?.artworkUri
                                if (currentTrackUri == artUri) {
                                    builder.setLargeIcon(loaded)
                                    try {
                                        onNotificationChangedCallback.onNotificationChanged(
                                            MediaNotification(NOTIFICATION_ID, builder.build())
                                        )
                                    } catch (e: Exception) {
                                        Log.w(LOG_TAG, "Callback failed, falling back to NotificationManager: ${e.message}", e)
                                        val nm = getSystemService(NotificationManager::class.java)
                                        nm?.notify(NOTIFICATION_ID, builder.build())
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            Log.w(LOG_TAG, "Non-fatal artwork load error: ${e.message}")
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w(LOG_TAG, "Non-fatal artwork sync error: ${e.message}")
            }

            // EXACTLY 3 ACTIONS: Previous, Play/Pause, Next
            try {
                // 1. Previous
                val prevIcon = IconCompat.createWithResource(this@MediaPlaybackService, android.R.drawable.ic_media_previous)
                val prevAction = actionFactory.createMediaAction(
                    mediaSession,
                    prevIcon,
                    "Anterior",
                    Player.COMMAND_SEEK_TO_PREVIOUS
                )
                if (prevAction != null) builder.addAction(prevAction)

                // 2. Play/Pause
                val playPauseIcon = IconCompat.createWithResource(
                    this@MediaPlaybackService,
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                )
                val playPauseTitle = if (isPlaying) "Pausar" else "Reproduzir"
                val playPauseAction = actionFactory.createMediaAction(
                    mediaSession,
                    playPauseIcon,
                    playPauseTitle,
                    Player.COMMAND_PLAY_PAUSE
                )
                if (playPauseAction != null) builder.addAction(playPauseAction)

                // 3. Next
                val nextIcon = IconCompat.createWithResource(this@MediaPlaybackService, android.R.drawable.ic_media_next)
                val nextAction = actionFactory.createMediaAction(
                    mediaSession,
                    nextIcon,
                    "Seguinte",
                    Player.COMMAND_SEEK_TO_NEXT
                )
                if (nextAction != null) builder.addAction(nextAction)

                // MediaStyle: strictly display Previous (0), Play/Pause (1), Next (2) in compact view
                builder.setStyle(
                    MediaStyleNotificationHelper.MediaStyle(mediaSession)
                        .setShowActionsInCompactView(0, 1, 2)
                )
            } catch (e: Throwable) {
                Log.w(LOG_TAG, "Non-fatal action building error: ${e.message}")
            }

            return MediaNotification(NOTIFICATION_ID, builder.build())
        }

        override fun handleCustomCommand(
            session: MediaSession,
            action: String,
            extras: Bundle
        ): Boolean = false
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
                    KeyEvent.KEYCODE_MEDIA_STOP -> {
                        playbackController.stopPlayback()
                        return true
                    }
                }
            }
            return super.onMediaButtonEvent(session, controllerInfo, intent)
        }
    }

    override fun onDestroy() {
        isRunning = false
        isStartingOrRunning = false
        releaseWakeLock()
        unregisterScreenReceiver()
        serviceScope.cancel()
        val controller = PlaybackController.getInstance(this)
        controller.mediaSession = null
        mediaSession?.run {
            removeSession(this)
            release()
            mediaSession = null
        }
        Log.i(LOG_TAG, "MWASO_SERVICE_DESTROYED")
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "mwaso_wami_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val LOG_TAG = "MWASO_MEDIA"

        @Volatile
        var isRunning: Boolean = false
            internal set

        @Volatile
        var isStartingOrRunning: Boolean = false
            internal set
    }
}

/**
 * Lightweight in-memory cache for real track artwork decoded from MediaStore/storage.
 * Does not invent artificial images and consumes minimal memory (~350KB max per bitmap).
 */
internal object ArtworkCache {
    private var lastUri: String? = null
    private var cachedBitmap: Bitmap? = null

    fun getCachedBitmap(uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        return if (uriString == lastUri) cachedBitmap else null
    }

    fun loadBitmap(context: Context, uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) {
            lastUri = null
            cachedBitmap = null
            return null
        }
        if (uriString == lastUri && cachedBitmap != null) {
            return cachedBitmap
        }

        return try {
            val uri = Uri.parse(uriString)
            val scheme = uri.scheme
            if (scheme != "content" && scheme != "file" && scheme != "android.resource") {
                return null
            }
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    context.contentResolver.loadThumbnail(uri, Size(300, 300), null)
                } catch (e: Exception) {
                    Log.d(MediaPlaybackService.LOG_TAG, "loadThumbnail fallback for $uri: ${e.message}")
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }

            if (bitmap != null) {
                val scaled = if (bitmap.width > 300 || bitmap.height > 300) {
                    Bitmap.createScaledBitmap(bitmap, 300, 300, true)
                } else {
                    bitmap
                }
                lastUri = uriString
                cachedBitmap = scaled
                scaled
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(MediaPlaybackService.LOG_TAG, "Error decoding artwork from $uriString: ${e.message}", e)
            null
        }
    }
}
