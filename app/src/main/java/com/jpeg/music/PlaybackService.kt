package com.jpeg.music

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
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

object Engine {
    private var st: PlayerState? = null
    fun get(c: Context): PlayerState = st ?: PlayerState(c.applicationContext).also { st = it }
}

class PlaybackService : Service() {
    companion object {
        @Volatile var running = false
        const val CH = "jpeg_playback"
        const val ID = 7
    }

    private lateinit var st: PlayerState
    private lateinit var session: MediaSession
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { if (st.playing) st.toggle() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        st = Engine.get(this)
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CH, "Playback", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        session = MediaSession(this, "JPEG")
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { if (!st.playing) st.toggle() }
            override fun onPause() { if (st.playing) st.toggle() }
            override fun onSkipToNext() { st.next() }
            override fun onSkipToPrevious() { st.prev() }
            override fun onSeekTo(pos: Long) { st.seek(pos.toInt()) }
            override fun onStop() { st.stop(); stopSelf() }
        })
        session.isActive = true
        st.onChange = { refreshUi() }
        registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        val n = build(null)
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(ID, n)
        refreshUi()
    }

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        when (i?.action) {
            "prev" -> st.prev()
            "toggle" -> st.toggle()
            "next" -> st.next()
            "stop" -> { st.stop(); stopSelf() }
        }
        refreshUi()
        return START_NOT_STICKY
    }

    private fun pi(a: String, code: Int) = PendingIntent.getService(this, code,
        Intent(this, PlaybackService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun act(icon: Int, label: String, a: String, code: Int) =
        Notification.Action.Builder(Icon.createWithResource(this, icon), label, pi(a, code)).build()

    private fun build(art: Bitmap?): Notification {
        val s = st.current
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CH) else Notification.Builder(this)
        b.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(s?.title ?: "JPEG")
            .setContentText(s?.artist ?: "")
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE))
            .addAction(act(android.R.drawable.ic_media_previous, "Previous", "prev", 1))
            .addAction(act(if (st.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (st.playing) "Pause" else "Play", "toggle", 2))
            .addAction(act(android.R.drawable.ic_media_next, "Next", "next", 3))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
        if (art != null) b.setLargeIcon(art)
        return b.build()
    }

    private fun scaled(b: Bitmap): Bitmap {
        val m = maxOf(b.width, b.height)
        if (m <= 512) return b
        val f = 512f / m
        return Bitmap.createScaledBitmap(b, (b.width * f).toInt(), (b.height * f).toInt(), true)
    }

    private fun refreshUi() {
        val s = st.current
        var bmp: Bitmap? = null
        if (s != null) {
            if (ArtCache.map.containsKey(s.id)) {
                bmp = ArtCache.map[s.id]?.asAndroidBitmap()?.let { scaled(it) }
            } else {
                scope.launch {
                    ArtCache.map[s.id] = loadArt(this@PlaybackService, s)
                    if (st.current?.id == s.id) refreshUi()
                }
            }
        }
        val mb = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, s?.title ?: "")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, s?.artist ?: "")
            .putString(MediaMetadata.METADATA_KEY_ALBUM, s?.album ?: "")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, (s?.duration ?: 0).toLong())
        if (bmp != null) {
            mb.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bmp)
            mb.putBitmap(MediaMetadata.METADATA_KEY_ART, bmp)
        }
        session.setMetadata(mb.build())
        val speed = if (st.pure) 1f else st.rpm / 33.3f
        session.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP)
            .setState(if (st.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, st.pos.toLong(), if (st.playing) speed else 0f)
            .build())
        try { getSystemService(NotificationManager::class.java).notify(ID, build(bmp)) } catch (e: Exception) { }
    }

    override fun onDestroy() {
        running = false
        st.onChange = null
        try { unregisterReceiver(noisy) } catch (e: Exception) { }
        session.isActive = false
        session.release()
        scope.cancel()
        super.onDestroy()
    }
}
