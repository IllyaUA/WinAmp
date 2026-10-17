package com.illyaua.winamp.player

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.illyaua.winamp.MainActivity
import com.illyaua.winamp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps playback alive in the background: foreground notification with transport buttons, a media session
 * for lock screen and headset controls, and a pause when headphones are unplugged.
 * The audio itself is owned by [PlayerEngine].
 */
class PlaybackService : Service() {

    private lateinit var engine: PlayerEngine
    private lateinit var session: MediaSessionCompat
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) engine.pause()
        }
    }

    override fun onCreate() {
        super.onCreate()
        engine = PlayerEngine.get(this)
        createChannel()

        session = MediaSessionCompat(this, "WinAmp").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = engine.playOrResume()
                override fun onPause() = engine.pause()
                override fun onStop() = engine.stop()
                override fun onSkipToNext() = engine.next()
                override fun onSkipToPrevious() = engine.previous()
                override fun onSeekTo(pos: Long) = engine.seekTo(pos.toInt())
            })
            isActive = true
        }

        ContextCompat.registerReceiver(
            this,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        // startForegroundService() requires this within a few seconds of the service starting.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )

        scope.launch {
            engine.state
                .map { Triple(it.status, it.current, it.durationMs) }
                .distinctUntilChanged()
                .collect { render() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREVIOUS -> engine.previous()
            ACTION_TOGGLE -> engine.togglePause()
            ACTION_NEXT -> engine.next()
            ACTION_STOP -> engine.stop()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        try {
            unregisterReceiver(noisyReceiver)
        } catch (_: IllegalArgumentException) {
        }
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    private fun render() {
        val state = engine.state.value
        if (state.status == PlayStatus.STOPPED) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val track = state.current
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track?.title ?: "")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track?.artist ?: "")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, state.durationMs.toLong())
                .build()
        )
        val playing = state.status == PlayStatus.PLAYING
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(
                    if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    engine.position.value.toLong(),
                    if (playing) 1f else 0f,
                )
                .build()
        )
        notify(buildNotification())
    }

    @SuppressLint("MissingPermission")
    private fun notify(notification: Notification) {
        // Without the notification permission this is silently dropped, playback is unaffected.
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val state = engine.state.value
        val track = state.current
        val playing = state.status == PlayStatus.PLAYING
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_winamp)
            .setContentTitle(track?.title ?: getString(R.string.app_name))
            .setContentText(track?.artist)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(playing)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", action(ACTION_PREVIOUS))
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pause" else "Play",
                action(ACTION_TOGGLE),
            )
            .addAction(android.R.drawable.ic_media_next, "Next", action(ACTION_NEXT))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", action(ACTION_STOP))
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun action(name: String): PendingIntent = PendingIntent.getService(
        this,
        name.hashCode(),
        Intent(this, PlaybackService::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.channel_playback), NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_PREVIOUS = "com.illyaua.winamp.PREVIOUS"
        private const val ACTION_TOGGLE = "com.illyaua.winamp.TOGGLE"
        private const val ACTION_NEXT = "com.illyaua.winamp.NEXT"
        private const val ACTION_STOP = "com.illyaua.winamp.STOP"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
            } catch (_: Exception) {
                // Android refuses background starts in a few situations; the audio still plays, just without a notification.
            }
        }
    }
}
