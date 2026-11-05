package com.jpeg.music

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt

// ───────────── Look (customisation state) ─────────────

object Look {
    var accent by mutableStateOf(Color(0xFFE9ECEA))
    var style by mutableIntStateOf(0)
    var backdrop by mutableStateOf(true)
    var gyro by mutableStateOf(true)
    var gyroInvert by mutableStateOf(false)
}

val Bodies = listOf(Color(0xFF0E0E0E), Color(0xFF8E1B16), Color(0xFF12233F), Color(0xFF3A3A3A))
val Accents = listOf(Color(0xFFE9ECEA), Color(0xFF7CC242), Color(0xFFFF8A3D), Color(0xFFFF5C93), Color(0xFF4DD0E1), Color(0xFFB388FF))
private val Dark = Color(0xFF0B0B0C)
private val Panel = Color(0xFF1A1A1C)
const val FAV = "Favorites"

// ───────────── Data ─────────────

data class Song(val id: Long, val title: String, val artist: String, val album: String, val duration: Int, val uri: android.net.Uri,
                val name: String = "", val mime: String = "",
                val artUrl: String = "", val live: Boolean = false, val online: Boolean = false, val info: String = "")

data class Quality(val lossless: Boolean, val hires: Boolean, val label: String, val bits: Int, val rate: Int, val kbps: Int) {
    fun tag() = if (hires) "HI-RES" else if (lossless) "LOSSLESS" else ""
    fun detail(): String = buildString {
        append(label)
        if (bits > 0) append(" · ${bits}-bit")
        if (rate > 0) append(if (rate % 1000 == 0) " · ${rate / 1000} kHz" else " · %.1f kHz".format(rate / 1000f))
        if (!lossless && kbps > 0) append(" · $kbps kbps")
    }
}

private val LOSSLESS_EXT = setOf("flac", "wav", "alac", "aiff", "aif", "ape", "wv")
fun Song.isLossless(): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in LOSSLESS_EXT || mime.contains("flac") || mime.contains("wav") || mime.contains("aiff") || mime.contains("alac")
}

suspend fun analyze(ctx: Context, song: Song): Quality = withContext(Dispatchers.IO) {
    var mime = ""; var rate = 0; var bits = 0; var kbps = 0
    try {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, song.uri, null)
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (m.startsWith("audio/")) {
                    mime = m
                    try { if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE) } catch (e: Exception) { }
                    try { if (f.containsKey(MediaFormat.KEY_BIT_RATE)) kbps = f.getInteger(MediaFormat.KEY_BIT_RATE) / 1000 } catch (e: Exception) { }
                    try { if (f.containsKey("bits-per-sample")) bits = f.getInteger("bits-per-sample") } catch (e: Exception) { }
                    break
                }
            }
        } finally { ex.release() }
    } catch (e: Exception) { }
    try {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, song.uri)
            if (Build.VERSION.SDK_INT >= 31) {
                rate = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: rate
                bits = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull() ?: bits
            }
            if (kbps == 0) kbps = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0) / 1000
        } finally { r.release() }
    } catch (e: Exception) { }
    val ext = song.name.substringAfterLast('.', "").lowercase()
    val lossless = listOf("flac", "wav", "raw", "alac", "aiff", "ape", "wavpack").any { mime.contains(it) } || (mime.isEmpty() && song.isLossless())
    val label = when {
        mime.contains("flac") -> "FLAC"
        mime.contains("wav") || mime.contains("raw") -> "WAV"
        mime.contains("alac") -> "ALAC"
        mime.contains("aiff") -> "AIFF"
        mime.contains("mpeg") -> "MP3"
        mime.contains("mp4a") || mime.contains("aac") -> "AAC"
        mime.contains("vorbis") -> "OGG"
        mime.contains("opus") -> "OPUS"
        else -> ext.uppercase()
    }
    Quality(lossless, lossless && (rate > 48000 || bits >= 24), label, bits, rate, kbps)
}

object ArtCache { val map = HashMap<Long, ImageBitmap?>() }

suspend fun loadArt(ctx: Context, s: Song): ImageBitmap? = withContext(Dispatchers.IO) {
    if (s.online) return@withContext Net.fetchImage(s.artUrl)
    try {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, s.uri)
            val bytes = r.embeddedPicture
            if (bytes != null) {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                var sample = 1
                while (o.outWidth / sample > 800) sample *= 2
                val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o2)
                if (bmp != null) return@withContext bmp.asImageBitmap()
            }
        } finally { r.release() }
        if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.loadThumbnail(s.uri, Size(600, 600), null).asImageBitmap() else null
    } catch (e: Exception) { null }
}

@Composable
fun rememberArt(s: Song?): ImageBitmap? {
    val ctx = LocalContext.current
    val st = produceState<ImageBitmap?>(if (s != null) ArtCache.map[s.id] else null, s?.id) {
        if (s == null) { value = null } else {
            if (!ArtCache.map.containsKey(s.id)) ArtCache.map[s.id] = loadArt(ctx, s)
            value = ArtCache.map[s.id]
        }
    }
    return st.value
}

// ───────────── Player ─────────────

class PlayerState(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("jpeg", Context.MODE_PRIVATE)
    var songs by mutableStateOf(listOf<Song>())
    var queue by mutableStateOf(listOf<Song>())
    var index by mutableIntStateOf(0)
    var playing by mutableStateOf(false)
    var pos by mutableIntStateOf(0)
    var shuffle by mutableStateOf(prefs.getBoolean("shuffle", false))
    var repeat by mutableStateOf(prefs.getBoolean("repeat", false))
    var rpm by mutableFloatStateOf(prefs.getFloat("rpm", 33.3f))
    var scanned by mutableStateOf(false)
    var eqLevels by mutableStateOf((prefs.getString("eq", "0,0,0,0,0") ?: "0,0,0,0,0").split(",").map { it.toIntOrNull() ?: 0 })
    var eqMin by mutableIntStateOf(-1500)
    var eqMax by mutableIntStateOf(1500)
    var bass by mutableIntStateOf(prefs.getInt("bass", 0))
    var sleepAt by mutableLongStateOf(0L)
    var tick by mutableLongStateOf(0L)
    var playlists by mutableStateOf(loadPl())
    val quality = mutableStateMapOf<Long, Quality>()
    var pure by mutableStateOf(prefs.getBoolean("pure", false))
    var loading by mutableStateOf(false)
    var showLyrics by mutableStateOf(false)
    var lyric by mutableStateOf<LyricData?>(null)
    var error by mutableStateOf<String?>(null)
    private var pending: MediaPlayer? = null
    private var mp: MediaPlayer? = null
    private var eq: Equalizer? = null
    private var bb: BassBoost? = null

    init {
        Look.accent = Color(prefs.getInt("accent", Look.accent.toArgb()))
        Look.style = prefs.getInt("style", 0)
        Look.backdrop = prefs.getBoolean("backdrop", true)
        Look.gyro = prefs.getBoolean("gyro", true)
        Look.gyroInvert = prefs.getBoolean("gyroInv", false)
    }

    val current: Song? get() = queue.getOrNull(index)

    // ── playlists
    private fun loadPl(): Map<String, List<Long>> {
        val m = LinkedHashMap<String, List<Long>>()
        m[FAV] = emptyList()
        (prefs.getString("pl", "") ?: "").split("\n").filter { it.isNotBlank() }.forEach { line ->
            val p = line.split("\t")
            m[p[0]] = (p.getOrNull(1) ?: "").split(",").mapNotNull { it.toLongOrNull() }
        }
        return m
    }
    private fun savePl() {
        prefs.edit().putString("pl", playlists.entries.joinToString("\n") { it.key + "\t" + it.value.joinToString(",") }).apply()
    }
    fun createPl(name: String) { val n = name.trim(); if (n.isEmpty() || playlists.containsKey(n)) return; playlists = playlists + (n to emptyList()); savePl() }
    fun deletePl(name: String) { if (name == FAV) return; playlists = playlists - name; savePl() }
    fun addTo(name: String, id: Long) { val l = playlists[name] ?: return; if (id in l) return; playlists = playlists + (name to (l + id)); savePl() }
    fun removeFrom(name: String, id: Long) { val l = playlists[name] ?: return; playlists = playlists + (name to (l - id)); savePl() }
    fun isFav(id: Long) = playlists[FAV]?.contains(id) == true
    fun toggleFav(id: Long) { if (isFav(id)) removeFrom(FAV, id) else addTo(FAV, id) }
    fun songsOf(name: String): List<Song> { val ids = playlists[name] ?: return emptyList(); return ids.mapNotNull { id -> songs.firstOrNull { it.id == id } } }

    // ── library
    suspend fun scan() {
        val list = withContext(Dispatchers.IO) {
            val out = ArrayList<Song>()
            val proj = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.MIME_TYPE)
            try {
                ctx.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 30000", null,
                    "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        val artist = c.getString(2) ?: "Unknown artist"
                        out.add(Song(id, c.getString(1) ?: "Unknown", if (artist == "<unknown>") "Unknown artist" else artist,
                            c.getString(3) ?: "", c.getInt(4),
                            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id), c.getString(5) ?: "", c.getString(6) ?: ""))
                    }
                }
            } catch (e: Exception) { }
            out
        }
        songs = list
        scanned = true
        if (queue.isEmpty()) queue = list
        if (index >= queue.size) index = 0
    }

    // ── lyrics
    suspend fun loadLyrics(s: Song) {
        if (s.live || lyric?.songId == s.id) return
        val d = Lyrics.load(ctx, s)
        if (current?.id == s.id) lyric = d
    }
    fun retryLyrics(s: Song) { Lyrics.forget(ctx, s.id); lyric = null }

    // ── quality
    suspend fun ensureQ(s: Song) {
        if (quality.containsKey(s.id)) return
        quality[s.id] = if (s.online) Quality(false, false, s.info.ifBlank { "STREAM" }, 0, 0, 0) else analyze(ctx, s)
    }
    suspend fun analyzeAll() {
        for (s in songs) {
            if (quality.containsKey(s.id)) continue
            val ext = s.name.substringAfterLast('.', "").lowercase()
            if (s.isLossless() || ext == "m4a") quality[s.id] = analyze(ctx, s)
        }
    }
    fun updatePure(b: Boolean) {
        pure = b; prefs.edit().putBoolean("pure", b).apply()
        val m = mp ?: return
        if (b) releaseFx() else attachFx(m.audioSessionId)
        if (playing) applySpeed()
    }

    // ── fx
    private fun releaseFx() { try { eq?.release() } catch (e: Exception) { }; try { bb?.release() } catch (e: Exception) { }; eq = null; bb = null }
    private fun attachFx(sid: Int) {
        releaseFx()
        if (pure) return
        try {
            val e = Equalizer(0, sid)
            e.enabled = true
            eqMin = e.bandLevelRange[0].toInt(); eqMax = e.bandLevelRange[1].toInt()
            for (b in 0 until min(e.numberOfBands.toInt(), eqLevels.size)) e.setBandLevel(b.toShort(), eqLevels[b].coerceIn(eqMin, eqMax).toShort())
            eq = e
        } catch (e: Exception) { }
        try { val b = BassBoost(0, sid); b.enabled = true; b.setStrength(bass.toShort()); bb = b } catch (e: Exception) { }
    }
    fun setEq(band: Int, level: Int) {
        eqLevels = eqLevels.toMutableList().also { it[band] = level }
        try { eq?.setBandLevel(band.toShort(), level.coerceIn(eqMin, eqMax).toShort()) } catch (e: Exception) { }
        prefs.edit().putString("eq", eqLevels.joinToString(",")).apply()
    }
    fun setEqAll(l: List<Int>) { l.forEachIndexed { i, v -> setEq(i, v.coerceIn(eqMin, eqMax)) } }
    fun updateBass(v: Int) { bass = v; try { bb?.setStrength(v.toShort()) } catch (e: Exception) { }; prefs.edit().putInt("bass", v).apply() }

    // ── look
    fun setAccent(c: Color) { Look.accent = c; prefs.edit().putInt("accent", c.toArgb()).apply() }
    fun setStyle(i: Int) { Look.style = i; prefs.edit().putInt("style", i).apply() }
    fun updateGyro(b: Boolean) { Look.gyro = b; prefs.edit().putBoolean("gyro", b).apply(); if (b) Tilt.start(ctx) else Tilt.stop() }
    fun updateGyroInvert(b: Boolean) { Look.gyroInvert = b; prefs.edit().putBoolean("gyroInv", b).apply() }
    fun setBackdrop(b: Boolean) { Look.backdrop = b; prefs.edit().putBoolean("backdrop", b).apply() }
    fun updateShuffle(b: Boolean) { shuffle = b; prefs.edit().putBoolean("shuffle", b).apply() }
    fun updateRepeat(b: Boolean) { repeat = b; prefs.edit().putBoolean("repeat", b).apply() }

    // ── playback
    var onChange: (() -> Unit)? = null
    var crossfade by mutableIntStateOf(prefs.getInt("cf", 0))
    private var fadeOut: MediaPlayer? = null
    private var fadeStart = 0L
    private var fadeMs = 1L
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var resumeOnFocus = false
    private val h = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable { override fun run() { refresh(); if (playing) h.postDelayed(this, 100) } }
    private val focus = AudioManager.OnAudioFocusChangeListener { c ->
        when (c) {
            AudioManager.AUDIOFOCUS_LOSS -> { resumeOnFocus = false; if (playing) toggle() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { if (playing) { resumeOnFocus = true; toggle() } }
            AudioManager.AUDIOFOCUS_GAIN -> { if (resumeOnFocus) { resumeOnFocus = false; if (!playing) toggle() } }
            else -> { }
        }
    }

    private fun changed() { onChange?.invoke() }
    fun updateCrossfade(sec: Int) { crossfade = sec; prefs.edit().putInt("cf", sec).apply() }

    private fun ensureService() {
        if (PlaybackService.running) return
        try {
            val i = Intent(ctx, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        } catch (e: Exception) { }
    }

    @Suppress("DEPRECATION")
    private fun focusOn() { try { am.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) } catch (e: Exception) { } }

    private fun applySpeed() {
        val m = mp ?: return
        if (current?.live == true) return
        try { val r = if (pure) 1f else rpm / 33.3f; m.playbackParams = PlaybackParams().setSpeed(r).setPitch(r) } catch (e: Exception) { }
    }
    fun setSpeed(v: Float) { rpm = v; prefs.edit().putFloat("rpm", v).apply(); if (playing) applySpeed(); changed() }

    private fun newPlayer(s: Song, async: Boolean): MediaPlayer {
        val m = MediaPlayer()
        m.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        m.setWakeMode(ctx, PowerManager.PARTIAL_WAKE_LOCK)
        m.setDataSource(ctx, s.uri)
        m.setOnCompletionListener { p -> onDone(p) }
        m.setOnErrorListener { p, _, _ ->
            if (p === pending || p === mp) { loading = false; playing = false; error = "Can't play this one. Check your connection."; changed() }
            true
        }
        if (!async) m.prepare()
        return m
    }
    private fun onDone(p: MediaPlayer) { if (p !== mp) return; if (repeat) play(index) else next() }

    fun playQueue(list: List<Song>, i: Int) { if (list.isEmpty()) return; queue = list; play(i) }

    private fun startPlayer(m: MediaPlayer) {
        attachFx(m.audioSessionId)
        focusOn()
        m.start()
        mp = m
        loading = false
        playing = true
        pos = 0
        if (rpm != 33.3f) applySpeed()
        ensureService()
        h.removeCallbacks(ticker); h.post(ticker)
        changed()
    }

    fun play(i: Int) {
        if (queue.isEmpty()) return
        release()
        index = i.coerceIn(0, queue.size - 1)
        error = null
        val s = queue[index]
        try {
            if (s.online) {
                loading = true; playing = false; pos = 0
                val m = newPlayer(s, true)
                pending = m
                m.setOnPreparedListener { p -> if (p === pending) { pending = null; startPlayer(p) } }
                m.prepareAsync()
                ensureService()
                changed()
            } else startPlayer(newPlayer(s, false))
        } catch (e: Exception) { playing = false; loading = false; changed() }
    }

    private fun crossfadeTo(n: Int) {
        try {
            val old = mp
            val m = newPlayer(queue[n], false)
            m.setVolume(0f, 0f)
            attachFx(m.audioSessionId)
            m.start()
            fadeOut?.let { try { it.release() } catch (e: Exception) { } }
            old?.setOnCompletionListener(null)
            fadeOut = old
            mp = m
            index = n
            fadeStart = SystemClock.elapsedRealtime()
            fadeMs = crossfade * 1000L
            playing = true
            pos = 0
            if (rpm != 33.3f) applySpeed()
            changed()
        } catch (e: Exception) { play(n) }
    }

    private fun finishFade() {
        fadeOut?.let { try { it.release() } catch (e: Exception) { } }
        fadeOut = null
        try { mp?.setVolume(1f, 1f) } catch (e: Exception) { }
    }

    fun playNext(s: Song) {
        if (queue.isEmpty()) { queue = listOf(s); return }
        val l = queue.toMutableList(); l.add(min(index + 1, l.size), s); queue = l
    }

    fun toggle() {
        if (loading) return
        val m = mp
        if (m == null) { if (queue.isEmpty()) queue = songs; play(index); return }
        if (playing) {
            finishFade()
            m.pause(); playing = false
        } else {
            focusOn(); m.start(); playing = true
            if (rpm != 33.3f) applySpeed()
            h.removeCallbacks(ticker); h.post(ticker)
        }
        changed()
    }

    fun stop() {
        finishFade()
        mp?.let { try { it.pause(); it.seekTo(0) } catch (e: Exception) { } }
        playing = false; pos = 0
        changed()
    }

    private fun computeNext(): Int =
        if (shuffle && queue.size > 1) { var r: Int; do { r = queue.indices.random() } while (r == index); r } else (index + 1) % queue.size

    private fun go(n: Int) { if (crossfade > 0 && playing && mp != null && !queue[n].online) crossfadeTo(n) else play(n) }

    fun next() { if (queue.isEmpty()) return; go(computeNext()) }
    fun prev() {
        if (queue.isEmpty()) return
        if (pos > 3000) { seek(0); return }
        go((index - 1 + queue.size) % queue.size)
    }

    fun seek(ms: Int) { try { mp?.seekTo(ms); pos = ms } catch (e: Exception) { }; changed() }

    fun refresh() {
        tick = SystemClock.elapsedRealtime()
        if (sleepAt > 0 && tick >= sleepAt) {
            sleepAt = 0; finishFade()
            mp?.let { try { it.pause() } catch (e: Exception) { } }
            playing = false; changed()
        }
        val m = mp ?: return
        if (!playing) return
        try { pos = m.currentPosition } catch (e: Exception) { return }
        val f = fadeOut
        if (f != null) {
            val t = ((tick - fadeStart) / fadeMs.toFloat()).coerceIn(0f, 1f)
            val vo = cos(t * PI / 2).toFloat(); val vn = sin(t * PI / 2).toFloat()
            try { f.setVolume(vo, vo); m.setVolume(vn, vn) } catch (e: Exception) { }
            if (t >= 1f) finishFade()
        } else if (crossfade > 0 && queue.isNotEmpty()) {
            try {
                val dur = m.duration
                val ratio = if (pure) 1f else rpm / 33.3f
                if (dur > crossfade * 2000 && dur - pos <= crossfade * 1000 * ratio) { val n = if (repeat) index else computeNext(); if (!queue[n].online) crossfadeTo(n) }
            } catch (e: Exception) { }
        }
    }

    fun setSleep(min: Int) { sleepAt = if (min == 0) 0L else SystemClock.elapsedRealtime() + min * 60_000L }

    fun release() {
        h.removeCallbacks(ticker)
        try { pending?.release() } catch (e: Exception) { }
        pending = null
        loading = false
        try { mp?.release() } catch (e: Exception) { }
        mp = null
        try { fadeOut?.release() } catch (e: Exception) { }
        fadeOut = null
        releaseFx()
    }
}

// ───────────── Activity ─────────────

class MainActivity : ComponentActivity() {
    private val st by lazy { Engine.get(applicationContext) }
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28)
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
        setContent { App(st) }
    }
    override fun onResume() { super.onResume(); if (Look.gyro) Tilt.start(applicationContext) }
    override fun onPause() { Tilt.stop(); super.onPause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
    }
}

// ───────────── Shared UI bits ─────────────

fun View.tap(kind: Int = HapticFeedbackConstants.KEYBOARD_TAP) { performHapticFeedback(kind) }
fun fmt(ms: Int) = "%02d:%02d".format(ms / 60000, (ms / 1000) % 60)

@Composable
fun Modifier.plain(onClick: () -> Unit): Modifier =
    this.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)

private val G = mapOf(
    '0' to "111101101101111", '1' to "010110010010111", '2' to "111001111100111",
    '3' to "111001111001111", '4' to "101101111001001", '5' to "111100111001111",
    '6' to "111100111101111", '7' to "111001001001001", '8' to "111101111101111",
    '9' to "111101111001111", ':' to "000010000010000", '.' to "000000000000010", ' ' to "000000000000000"
)

@Composable
fun DotText(text: String, dot: Float = 5f, color: Color = Color.White) {
    val w = text.map { if (it == ':' || it == '.') 2 else 4 }.sum()
    Canvas(Modifier.size((w * dot * 2.2f).dp, (5 * dot * 2.2f).dp)) {
        val step = this.size.height / 5f
        var x = 0f
        for (c in text) {
            val g = G[c] ?: G[' ']!!
            for (i in 0 until 15) if (g[i] == '1')
                drawCircle(color, step * 0.38f, Offset(x + (i % 3) * step + step / 2, (i / 3) * step + step / 2))
            x += step * if (c == ':' || c == '.') 2 else 4
        }
    }
}

@Composable
fun Vinyl(dia: Dp, art: ImageBitmap?, angle: Float = 0f, sheen: Boolean = false) {
    Canvas(Modifier.size(dia).graphicsLayer { rotationZ = angle }) {
        val c = center
        val r = this.size.minDimension / 2
        drawCircle(Bodies[Look.style], r, c)
        for (i in 1..16) drawCircle(Color(0x1FFFFFFF), r * (0.58f + i * 0.025f), c, style = Stroke(1f))
        if (sheen) {
            // light glints that stay fixed in the room while the record spins and the phone tilts
            val th = -angle * (PI.toFloat() / 180f)
            val sx = Tilt.x * cos(th) - Tilt.y * sin(th)
            val sy = Tilt.x * sin(th) + Tilt.y * cos(th)
            val mag = minOf(1f, hypot(Tilt.x, Tilt.y))
            val a = 0.07f + 0.25f * mag
            val p1 = Offset(c.x + sx * r * 0.7f, c.y + sy * r * 0.7f)
            val p2 = Offset(c.x - sx * r * 0.7f, c.y - sy * r * 0.7f)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = a), Color.Transparent), center = p1, radius = r * 0.85f), r, c)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = a * 0.6f), Color.Transparent), center = p2, radius = r * 0.7f), r, c)
        }
        val lr = r * 0.56f
        if (art != null) {
            val side = min(art.width, art.height)
            val path = Path().apply { addOval(Rect(c, lr)) }
            clipPath(path) {
                drawImage(art,
                    srcOffset = IntOffset((art.width - side) / 2, (art.height - side) / 2), srcSize = IntSize(side, side),
                    dstOffset = IntOffset((c.x - lr).roundToInt(), (c.y - lr).roundToInt()),
                    dstSize = IntSize((lr * 2).roundToInt(), (lr * 2).roundToInt()))
            }
        } else drawCircle(Look.accent, lr, c)
        drawCircle(Color(0x66000000), lr, c, style = Stroke(3f))
        drawCircle(Bodies[Look.style], r * 0.04f, c)
    }
}

@Composable
fun Icon(kind: Int, tint: Color = Color.White, sz: Dp = 22.dp) {
    Canvas(Modifier.size(sz)) {
        val w = this.size.width; val h = this.size.height
        val p = Path()
        when (kind) {
            0 -> { p.moveTo(w * .22f, h * .1f); p.lineTo(w * .92f, h * .5f); p.lineTo(w * .22f, h * .9f); p.close() }
            1 -> { p.addRect(Rect(w * .18f, h * .12f, w * .42f, h * .88f)); p.addRect(Rect(w * .58f, h * .12f, w * .82f, h * .88f)) }
            2 -> { p.moveTo(w * .1f, h * .15f); p.lineTo(w * .72f, h * .5f); p.lineTo(w * .1f, h * .85f); p.close(); p.addRect(Rect(w * .78f, h * .15f, w * .92f, h * .85f)) }
            else -> { p.moveTo(w * .9f, h * .15f); p.lineTo(w * .28f, h * .5f); p.lineTo(w * .9f, h * .85f); p.close(); p.addRect(Rect(w * .08f, h * .15f, w * .22f, h * .85f)) }
        }
        drawPath(p, tint)
    }
}

private val DarkClay = Color(0xFF3B3B42)
private val Ink = Color(0xFF121216)
private val Light2 = Color(0xFFD8D8DE)

@Composable
fun Modifier.bounce(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.9f else 1f, spring(0.42f, 650f), label = "press")
    return this.graphicsLayer { scaleX = sc; scaleY = sc }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

@Composable
fun Pill(t: String, selected: Boolean = false, onClick: () -> Unit) {
    val v = LocalView.current
    val r by animateDpAsState(if (selected) 24.dp else 12.dp, spring(0.5f, 500f), label = "shape")
    Box(Modifier.clip(RoundedCornerShape(r)).background(if (selected) Look.accent else Color(0x1FFFFFFF))
        .bounce { v.tap(); onClick() }.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(t, color = if (selected) Ink else Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
    }
}

@Composable
fun RoundBtn(size: Dp, bg: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(bg).bounce(onClick), contentAlignment = Alignment.Center) { content() }
}

fun DrawScope.glyph(kind: Int, color: Color, u: Float) {
    val sw = u * 0.055f
    val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        val p = Path(); p.moveTo(x1 * u, y1 * u); p.lineTo(x2 * u, y2 * u); p.lineTo(x3 * u, y3 * u); p.close()
        drawPath(p, color)
    }
    fun bar(l: Float, t: Float, r: Float, b: Float) =
        drawRoundRect(color, Offset(l * u, t * u), Size((r - l) * u, (b - t) * u), CornerRadius(u * 0.02f))
    when (kind) {
        0 -> tri(.40f, .30f, .72f, .50f, .40f, .70f)
        1 -> { bar(.35f, .30f, .46f, .70f); bar(.54f, .30f, .65f, .70f) }
        2 -> { tri(.30f, .32f, .62f, .50f, .30f, .68f); bar(.66f, .32f, .72f, .68f) }
        3 -> { tri(.70f, .32f, .38f, .50f, .70f, .68f); bar(.28f, .32f, .34f, .68f) }
        4 -> {
            val p = Path(); p.moveTo(.26f * u, .36f * u); p.lineTo(.46f * u, .36f * u); p.lineTo(.58f * u, .64f * u); p.lineTo(.66f * u, .64f * u)
            drawPath(p, color, style = st)
            val q = Path(); q.moveTo(.26f * u, .64f * u); q.lineTo(.46f * u, .64f * u); q.lineTo(.58f * u, .36f * u); q.lineTo(.66f * u, .36f * u)
            drawPath(q, color, style = st)
            tri(.66f, .58f, .77f, .64f, .66f, .70f); tri(.66f, .30f, .77f, .36f, .66f, .42f)
        }
        5 -> {
            drawRoundRect(color, Offset(.27f * u, .36f * u), Size(.46f * u, .30f * u), CornerRadius(.1f * u), style = st)
            tri(.56f, .27f, .68f, .36f, .56f, .45f)
        }
        6 -> {
            val p = Path()
            p.moveTo(.5f * u, .70f * u)
            p.cubicTo(.16f * u, .50f * u, .28f * u, .24f * u, .5f * u, .38f * u)
            p.cubicTo(.72f * u, .24f * u, .84f * u, .50f * u, .5f * u, .70f * u)
            p.close(); drawPath(p, color)
        }
        7 -> { bar(.28f, .33f, .72f, .39f); bar(.28f, .47f, .60f, .53f); bar(.28f, .61f, .68f, .67f) }
        8 -> { bar(.28f, .46f, .38f, .70f); bar(.45f, .30f, .55f, .70f); bar(.62f, .38f, .72f, .70f) }
        9 -> {
            drawCircle(color, .22f * u, Offset(.5f * u, .5f * u), style = st)
            drawOval(color, Offset(.41f * u, .28f * u), Size(.18f * u, .44f * u), style = st)
            drawLine(color, Offset(.28f * u, .5f * u), Offset(.72f * u, .5f * u), strokeWidth = sw, cap = StrokeCap.Round)
        }
        10 -> {
            val soft = color.copy(alpha = color.alpha * 0.7f)
            drawCircle(color, .075f * u, Offset(.40f * u, .38f * u))
            drawCircle(soft, .075f * u, Offset(.60f * u, .38f * u))
            drawArc(color, 180f, 180f, true, Offset(.27f * u, .52f * u), Size(.26f * u, .26f * u))
            drawArc(soft, 180f, 180f, true, Offset(.47f * u, .52f * u), Size(.26f * u, .26f * u))
        }
        11 -> {
            for (y in listOf(.36f, .50f, .64f)) drawLine(color, Offset(.28f * u, y * u), Offset(.72f * u, y * u), strokeWidth = sw, cap = StrokeCap.Round)
            drawCircle(color, .06f * u, Offset(.42f * u, .36f * u)); drawCircle(color, .06f * u, Offset(.58f * u, .50f * u)); drawCircle(color, .06f * u, Offset(.46f * u, .64f * u))
        }
        else -> {
            drawCircle(color, .22f * u, Offset(.5f * u, .5f * u), style = st)
            drawCircle(color, .05f * u, Offset(.5f * u, .5f * u))
        }
    }
}

/** Clay-style 3D icon tile: drop shadow, gradient body, rim light, gloss and an embossed glyph. */
@Composable
fun Icon3D(kind: Int, sz: Dp = 40.dp, base: Color = Look.accent, ink: Color = Ink, round: Boolean = false) {
    Canvas(Modifier.size(sz)) {
        val w = this.size.minDimension
        val rad = if (round) w / 2 else w * 0.32f
        val body = w * 0.9f
        val off = w * 0.03f
        drawRoundRect(Color(0x66000000), Offset(off + w * 0.02f, off + w * 0.08f), Size(body, body), CornerRadius(rad))
        drawRoundRect(Brush.verticalGradient(listOf(lerp(base, Color.White, 0.30f), base, lerp(base, Color.Black, 0.38f))),
            Offset(off, off), Size(body, body), CornerRadius(rad))
        drawRoundRect(Brush.verticalGradient(listOf(Color(0x88FFFFFF), Color(0x00FFFFFF), Color(0x22000000))),
            Offset(off, off), Size(body, body), CornerRadius(rad), style = Stroke(w * 0.025f))
        drawOval(Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x00FFFFFF))), Offset(w * 0.16f, off + w * 0.03f), Size(w * 0.68f, w * 0.34f))
        translate(left = off, top = off) {
            val emboss = if (ink.luminance() > 0.5f) Color(0x88000000) else Color(0x66FFFFFF)
            translate(0f, w * 0.022f) { glyph(kind, emboss, body) }
            glyph(kind, ink, body)
        }
    }
}

@Composable
fun ClayButton(kind: Int, sz: Dp, base: Color, ink: Color, onClick: () -> Unit) {
    Box(Modifier.size(sz).bounce(onClick), contentAlignment = Alignment.Center) { Icon3D(kind, sz, base, ink, round = true) }
}

fun Modifier.frost(radius: Dp = 28.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.clip(shape)
        .background(Color(0x4D000000))
        .background(Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x12FFFFFF))))
        .border(1.dp, Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x14FFFFFF))), shape)
}

@Composable
fun Floating(modifier: Modifier = Modifier, radius: Dp = 28.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.frost(radius).padding(16.dp), content = content)
}

@Composable
fun FloatingMenu(title: String, items: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xAA000000)).plain { onDismiss() }, contentAlignment = Alignment.BottomCenter) {
        Floating(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 100.dp).fillMaxWidth().plain { }) {
            Text(title, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val v = LocalView.current
            items.forEach { (label, act) ->
                Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.fillMaxWidth()
                    .clickable { v.tap(HapticFeedbackConstants.LONG_PRESS); act(); onDismiss() }.padding(vertical = 13.dp))
            }
        }
    }
}

@Composable
fun NameDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Color(0xAA000000)).imePadding().plain { onDismiss() }, contentAlignment = Alignment.Center) {
        Floating(Modifier.padding(24.dp).fillMaxWidth().plain { }) {
            Text("NEW PLAYLIST", color = Color.White, fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            BasicTextField(text, { text = it.take(24).filter { c -> c != '\t' && c != '\n' } },
                textStyle = TextStyle(color = Color.White, fontSize = 18.sp), cursorBrush = SolidColor(Look.accent), singleLine = true,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x66000000)).padding(14.dp))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Pill("CANCEL") { onDismiss() }; Pill("CREATE", true) { onCreate(text); onDismiss() } }
        }
    }
}

// ───────────── App ─────────────

@Composable
fun App(st: PlayerState) {
    val ctx = LocalContext.current
    val social = remember { SocialHub.get(ctx) }
    val imeOn = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var tab by remember { mutableIntStateOf(0) }
    var angle by remember { mutableFloatStateOf(0f) }
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LaunchedEffect(Unit) { if (!granted) launcher.launch(perm) }
    LaunchedEffect(granted) { if (granted) st.scan() }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(granted) {
        if (granted && Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(st.songs) { st.analyzeAll() }
    LaunchedEffect(st.current?.id, st.playing, social.me) {
        if (social.signedIn) {
            delay(1200)
            social.setNow(st.current?.title ?: "", st.current?.artist ?: "", st.playing && st.current != null)
        }
    }
    LaunchedEffect(Unit) { while (true) { delay(150); st.refresh() } }
    LaunchedEffect(st.playing) {
        if (st.playing) {
            var last = 0L
            while (true) withFrameNanos { t ->
                if (last != 0L) angle = (angle + (t - last) / 1e9f * st.rpm * 6f) % 360f
                last = t
            }
        }
    }

    val art = rememberArt(st.current)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Look.accent.copy(alpha = 0.28f).compositeOver(Dark), Dark)))) {
        if (art != null && Look.backdrop) Image(art, null, Modifier.fillMaxSize().graphicsLayer { translationX = Tilt.x * -18.dp.toPx(); translationY = Tilt.y * -18.dp.toPx(); scaleX = 1.15f; scaleY = 1.15f }.blur(70.dp).alpha(if (Build.VERSION.SDK_INT >= 31) 0.65f else 0.3f), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).padding(top = 28.dp, bottom = if (imeOn) 0.dp else if (tab == 0) 96.dp else 160.dp)) {
            if (!granted) Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                Text("Allow access to your music", color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.height(14.dp))
                Pill("GRANT ACCESS", true) { launcher.launch(perm) }
            } else when (tab) {
                0 -> PlayerTab(st, art, angle)
                1 -> LibraryTab(st) { tab = 0 }
                2 -> OnlineTab(st) { tab = 0 }
                3 -> FriendsTab(social, st)
                else -> StudioTab(st)
            }
        }
        if (!imeOn) Column(Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 18.dp)) {
            if (tab != 0 && st.current != null) { MiniPlayer(st, art, angle) { tab = 0 }; Spacer(Modifier.height(10.dp)) }
            NavBar(tab) { tab = it }
        }
    }
}

@Composable
fun NavBar(tab: Int, onTab: (Int) -> Unit) {
    val v = LocalView.current
    val items = listOf("Player" to 12, "Library" to 8, "Online" to 9, "Friends" to 10, "Studio" to 11)
    Row(Modifier.fillMaxWidth().frost(36.dp).padding(6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { i, (label, kind) ->
            val sel = tab == i
            Row(Modifier.clip(RoundedCornerShape(30.dp)).background(if (sel) Color(0x33FFFFFF) else Color.Transparent)
                .animateContentSize(spring(0.7f, 500f)).bounce { v.tap(); onTab(i) }.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon3D(kind, 38.dp, if (sel) Look.accent else DarkClay, if (sel) Ink else Light2)
                if (sel) Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp, end = 8.dp))
            }
        }
    }
}

@Composable
fun MiniPlayer(st: PlayerState, art: ImageBitmap?, angle: Float, open: () -> Unit) {
    val v = LocalView.current
    val s = st.current ?: return
    Row(Modifier.fillMaxWidth().frost(30.dp).clickable { open() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Vinyl(46.dp, art, angle)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.artist, color = Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RoundBtn(38.dp, Color.Transparent, { v.tap(); st.prev() }) { Icon(3, sz = 18.dp) }
        RoundBtn(42.dp, Look.accent, { v.tap(); st.toggle() }) { Icon(if (st.playing) 1 else 0, Color.Black, 18.dp) }
        RoundBtn(38.dp, Color.Transparent, { v.tap(); st.next() }) { Icon(2, sz = 18.dp) }
    }
}

// ───────────── Player ─────────────

@Composable
fun PlayerTab(st: PlayerState, art: ImageBitmap?, angle: Float) {
    val view = LocalView.current
    val song = st.current
    val n = st.queue.size
    val nextSong = if (n > 1) st.queue[(st.index + 1) % n] else null
    val next2 = if (n > 2) st.queue[(st.index + 2) % n] else null
    val artN = rememberArt(nextSong)
    val artN2 = rememberArt(next2)
    val enter = remember { Animatable(0f) }
    LaunchedEffect(st.index, st.queue.size) { enter.snapTo(1f); enter.animateTo(0f, spring(0.62f, Spring.StiffnessLow)) }
    var lastSec by remember { mutableIntStateOf(0) }
    LaunchedEffect(song?.id) { song?.let { st.ensureQ(it) } }
    LaunchedEffect(song?.id, st.showLyrics, st.lyric == null) { if (st.showLyrics) song?.let { st.loadLyrics(it) } }

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            val fav = song != null && st.isFav(song.id)
            if (song != null && !song.online) ClayButton(6, 44.dp, if (fav) Look.accent else DarkClay, if (fav) Ink else Light2) {
                view.tap(HapticFeedbackConstants.LONG_PRESS); st.toggleFav(song.id)
            } else Spacer(Modifier.size(44.dp))
            DotText(fmt(st.pos), 4f, Look.accent)
            if (song != null && !song.live) ClayButton(7, 44.dp, if (st.showLyrics) Look.accent else DarkClay, if (st.showLyrics) Ink else Light2) {
                st.showLyrics = !st.showLyrics
            } else Spacer(Modifier.size(44.dp))
        }
        if (song == null) {
            Spacer(Modifier.weight(1f))
            Text(if (st.scanned) "No songs found" else "Scanning…", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Spacer(Modifier.weight(1f))
            return@Column
        }

        if (st.showLyrics && !song.live) LyricsPanel(st, Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp))
        else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()
            .pointerInput(st) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -90f) { view.tap(HapticFeedbackConstants.LONG_PRESS); st.next() }
                        else if (total > 90f) { view.tap(HapticFeedbackConstants.LONG_PRESS); st.prev() }
                    },
                    onVerticalDrag = { _, d -> total += d })
            }) {
            val dia = minOf(maxWidth, maxHeight - 56.dp)
            Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp)) {
                if (next2 != null) Box(Modifier.offset(y = 76.dp).graphicsLayer { translationX = Tilt.x * 56.dp.toPx(); translationY = Tilt.y * 30.dp.toPx() }) { Vinyl(dia, artN2, 40f) }
                if (nextSong != null) Box(Modifier.offset(y = 38.dp).graphicsLayer { translationX = Tilt.x * 38.dp.toPx(); translationY = Tilt.y * 20.dp.toPx() }) { Vinyl(dia, artN, 110f) }
                Box(Modifier.graphicsLayer {
                    translationY = enter.value * 260f + Tilt.y * 10.dp.toPx()
                    translationX = Tilt.x * 20.dp.toPx()
                    rotationY = Tilt.x * 14f
                    rotationX = -Tilt.y * 14f
                    cameraDistance = 14f * density
                    alpha = 1f - enter.value * 0.7f
                    val sc = 1f - enter.value * 0.12f; scaleX = sc; scaleY = sc
                }.plain { view.tap(); st.toggle() }) { Vinyl(dia, art, angle, sheen = true) }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
        Text(song.artist, color = Color(0xFFB5B5BD), fontSize = 15.sp, maxLines = 1, modifier = Modifier.fillMaxWidth())
        if (st.loading) Text("Buffering…", color = Look.accent, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        st.error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) }
        st.quality[song.id]?.let { q ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (q.tag().isNotEmpty()) { Badge(q); Spacer(Modifier.width(8.dp)) }
                Text(q.detail(), color = Color(0xFF9A9AA2), fontSize = 11.sp)
            }
        }

        val dur = maxOf(song.duration, 1)
        if (song.live) Text("● Live radio", color = Look.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp))
        else {
            Slider(value = st.pos.coerceIn(0, dur).toFloat(), onValueChange = {
                val sec = (it / 1000).toInt()
                if (sec != lastSec) { lastSec = sec; view.tap(HapticFeedbackConstants.CLOCK_TICK) }
                st.seek(it.toInt())
            }, valueRange = 0f..dur.toFloat(),
                colors = SliderDefaults.colors(thumbColor = Look.accent, activeTrackColor = Look.accent, inactiveTrackColor = Color(0x33FFFFFF)))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(fmt(st.pos), color = Color(0xFF9A9AA2), fontSize = 11.sp)
                Text(fmt(song.duration), color = Color(0xFF9A9AA2), fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            ClayButton(4, 46.dp, if (st.shuffle) Look.accent else DarkClay, if (st.shuffle) Ink else Light2) { st.updateShuffle(!st.shuffle) }
            ClayButton(3, 58.dp, DarkClay, Light2) { view.tap(HapticFeedbackConstants.LONG_PRESS); st.prev() }
            ClayButton(if (st.playing) 1 else 0, 84.dp, Look.accent, Ink) { view.tap(HapticFeedbackConstants.LONG_PRESS); st.toggle() }
            ClayButton(2, 58.dp, DarkClay, Light2) { view.tap(HapticFeedbackConstants.LONG_PRESS); st.next() }
            ClayButton(5, 46.dp, if (st.repeat) Look.accent else DarkClay, if (st.repeat) Ink else Light2) { st.updateRepeat(!st.repeat) }
        }
    }
}

// ───────────── Library & playlists ─────────────

@Composable
fun Badge(q: Quality) {
    val t = q.tag()
    if (t.isEmpty()) return
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if (q.hires) Color(0xFFFFC857) else Look.accent).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(t, color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Default)
    }
}

@Composable
fun SongRow(s: Song, active: Boolean, q: Quality?, onPlay: () -> Unit, onMore: (() -> Unit)?) {
    val a = rememberArt(s)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(24.dp))
        .background(if (active) Look.accent.copy(alpha = 0.16f) else Color.Transparent).clickable { onPlay() }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Vinyl(50.dp, a)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, color = if (active) Look.accent else Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (q != null && q.tag().isNotEmpty()) { Badge(q); Spacer(Modifier.width(6.dp)) }
                Text(s.artist + " · " + (if (s.live) "LIVE" else fmt(s.duration)), color = Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onMore != null) Box(Modifier.size(40.dp).clip(CircleShape).clickable { onMore() }, contentAlignment = Alignment.Center) {
            Text("•••", color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable
fun LibraryTab(st: PlayerState, openPlayer: () -> Unit) {
    val view = LocalView.current
    var sub by remember { mutableIntStateOf(0) }
    var openPl by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<Song?>(null) }
    var newDialog by remember { mutableStateOf(false) }
    var qf by remember { mutableIntStateOf(0) }
    val shown = st.songs.filter { x ->
        when (qf) { 1 -> st.quality[x.id]?.lossless ?: x.isLossless(); 2 -> st.quality[x.id]?.hires == true; else -> true }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
                Text(if (sub == 1 && openPl != null) openPl!! else "Library", color = Color.White, fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold, fontSize = 32.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${st.songs.size} songs · ${st.playlists.size} playlists", color = Color.Gray, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                if (sub == 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("ALL ${st.songs.size}", qf == 0) { qf = 0 }
                        Pill("LOSSLESS ${st.quality.values.count { it.lossless }}", qf == 1) { qf = 1 }
                        Pill("HI-RES ${st.quality.values.count { it.hires }}", qf == 2) { qf = 2 }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("ALL SONGS", sub == 0) { sub = 0; openPl = null }
                    Pill("PLAYLISTS", sub == 1) { sub = 1; openPl = null }
                    if (sub == 0) Pill("PLAY ALL") { st.playQueue(shown, 0); openPlayer() }
                    if (sub == 1 && openPl == null) Pill("+ NEW") { newDialog = true }
                    if (sub == 1 && openPl != null) Pill("PLAY") { val l = st.songsOf(openPl!!); if (l.isNotEmpty()) { st.playQueue(l, 0); openPlayer() } }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (sub == 0) {
                if (st.songs.isEmpty()) Text(if (st.scanned) "No music found on this device." else "Scanning…", color = Color.Gray, modifier = Modifier.padding(20.dp))
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(shown, key = { _, s -> s.id }) { i, s ->
                        SongRow(s, st.current?.id == s.id, st.quality[s.id], { view.tap(HapticFeedbackConstants.LONG_PRESS); st.playQueue(shown, i); openPlayer() }, { menu = s })
                    }
                }
            } else if (openPl == null) {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(st.playlists.keys.toList(), key = { _, k -> k }) { _, name ->
                        val list = st.songsOf(name)
                        val a = rememberArt(list.firstOrNull())
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).frost(24.dp)
                            .background(Color.Transparent).clickable { view.tap(); openPl = name }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Vinyl(56.dp, a)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (name == FAV) "♥ $name" else name, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${list.size} songs", color = Color.Gray, fontSize = 11.sp)
                            }
                            RoundBtn(40.dp, Look.accent, { if (list.isNotEmpty()) { view.tap(HapticFeedbackConstants.LONG_PRESS); st.playQueue(list, 0); openPlayer() } }) { Icon(0, Color.Black, 16.dp) }
                            if (name != FAV) {
                                Spacer(Modifier.width(6.dp))
                                RoundBtn(40.dp, Color(0x2EFFFFFF), { view.tap(HapticFeedbackConstants.LONG_PRESS); st.deletePl(name) }) { Text("✕", color = Color.White, fontSize = 13.sp) }
                            }
                        }
                    }
                }
            } else {
                val name = openPl!!
                val list = st.songsOf(name)
                if (list.isEmpty()) Text("Empty. Tap ••• on any song and choose Add to $name.", color = Color.Gray, modifier = Modifier.padding(20.dp))
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                        SongRow(s, st.current?.id == s.id, st.quality[s.id], { view.tap(HapticFeedbackConstants.LONG_PRESS); st.playQueue(list, i); openPlayer() }, { menu = s })
                    }
                }
            }
        }
        menu?.let { s ->
            val items = ArrayList<Pair<String, () -> Unit>>()
            items.add("Play next" to { st.playNext(s) })
            items.add((if (st.isFav(s.id)) "♥ Remove from Favorites" else "♡ Add to Favorites") to { st.toggleFav(s.id) })
            st.playlists.keys.filter { it != FAV }.forEach { p -> items.add("Add to $p" to { st.addTo(p, s.id) }) }
            if (sub == 1 && openPl != null && openPl != FAV) items.add("Remove from ${openPl}" to { st.removeFrom(openPl!!, s.id) })
            FloatingMenu(s.title, items) { menu = null }
        }
        if (newDialog) NameDialog({ st.createPl(it) }) { newDialog = false }
    }
}

// ───────────── Studio (speed, EQ, looks, sleep) ─────────────

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Floating(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
fun Swatch(c: Color, selected: Boolean, onClick: () -> Unit) {
    val v = LocalView.current
    Box(Modifier.size(42.dp).clip(CircleShape).background(c).border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color(0x44FFFFFF), CircleShape)
        .clickable { v.tap(); onClick() })
}

@Composable
fun StudioTab(st: PlayerState) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("Studio", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Section("Appearance") {
            Text("Accent colour", color = Color.White, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Accents.forEach { c -> Swatch(c, Look.accent == c) { st.setAccent(c) } } }
            Spacer(Modifier.height(14.dp))
            Text("Vinyl colour", color = Color.White, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Bodies.forEachIndexed { i, c -> Swatch(c, Look.style == i) { st.setStyle(i) } } }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("GYRO TILT", Look.gyro) { st.updateGyro(!Look.gyro) }
                Pill("INVERT TILT", Look.gyroInvert) { st.updateGyroInvert(!Look.gyroInvert) }
            }
            Spacer(Modifier.height(8.dp))
            Pill("ALBUM ART BACKDROP", Look.backdrop) { st.setBackdrop(!Look.backdrop) }
        }
        Section("Turntable speed") {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0x66000000)).padding(vertical = 22.dp), horizontalArrangement = Arrangement.Center) {
                DotText(if (st.rpm < 40f) "33.3" else "45.0", 7f, Look.accent)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("33.3 RPM", st.rpm < 40f) { st.setSpeed(33.3f) }
                Pill("45 RPM", st.rpm >= 40f) { st.setSpeed(45f) }
            }
            Text("Changes speed and pitch, like a real turntable.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Section("Playback") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("SHUFFLE", st.shuffle) { st.updateShuffle(!st.shuffle) }
                Pill("REPEAT ONE", st.repeat) { st.updateRepeat(!st.repeat) }
                Pill("RESCAN") { scope.launch { st.scan() } }
            }
            Spacer(Modifier.height(14.dp))
            Text("Crossfade", color = Color.White, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("OFF", st.crossfade == 0) { st.updateCrossfade(0) }
                Pill("3 SEC", st.crossfade == 3) { st.updateCrossfade(3) }
                Pill("6 SEC", st.crossfade == 6) { st.updateCrossfade(6) }
                Pill("10 SEC", st.crossfade == 10) { st.updateCrossfade(10) }
            }
            Text("Blends the end of each song into the next, including when you skip.", color = Color.Gray, fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp))
        }
        Section("Sleep timer") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("OFF", st.sleepAt == 0L) { st.setSleep(0) }
                Pill("15 MIN") { st.setSleep(15) }
                Pill("30 MIN") { st.setSleep(30) }
                Pill("60 MIN") { st.setSleep(60) }
            }
            if (st.sleepAt > 0) Text("Stops in ${((st.sleepAt - st.tick) / 60000 + 1).coerceAtLeast(0)} min", color = Look.accent, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Section("Audio quality") {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val outRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE) ?: "?"
            val ll = st.quality.values.count { it.lossless }
            val hr = st.quality.values.count { it.hires }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Badge(Quality(true, true, "", 0, 0, 0)); Text("$hr files", color = Color.White, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Badge(Quality(true, false, "", 0, 0, 0)); Text("$ll files", color = Color.White, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            Pill("PURE MODE (NO EQ / SPEED FX)", st.pure) { st.updatePure(!st.pure) }
            val clean = st.pure || (st.rpm == 33.3f && st.bass == 0 && st.eqLevels.all { it == 0 })
            Text(if (clean) "Signal path: clean, no effects applied." else "Signal path: effects active (EQ, bass or speed).",
                color = if (clean) Look.accent else Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            Text("Phone output mixer: $outRate Hz. Hi-Res files are decoded at full quality, but Android may resample them to this rate unless you use a USB DAC or a device with Hi-Res output.",
                color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Section("Equalizer") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("FLAT") { st.setEqAll(listOf(0, 0, 0, 0, 0)) }
                Pill("BASS") { st.setEqAll(listOf(800, 500, 0, 0, 0)) }
                Pill("VOCAL") { st.setEqAll(listOf(-300, 0, 500, 500, -200)) }
                Pill("TREBLE") { st.setEqAll(listOf(0, 0, 0, 500, 800)) }
            }
            val names = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
            st.eqLevels.forEachIndexed { i, lv ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(names.getOrElse(i) { "" }, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Default, modifier = Modifier.width(64.dp))
                    Slider(lv.toFloat(), { st.setEq(i, it.roundToInt()) }, valueRange = st.eqMin.toFloat()..st.eqMax.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = Look.accent, activeTrackColor = Look.accent, inactiveTrackColor = Color(0xFF333336)))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BASS BOOST", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Default, modifier = Modifier.width(64.dp))
                Slider(st.bass.toFloat(), { st.updateBass(it.roundToInt()) }, valueRange = 0f..1000f,
                    colors = SliderDefaults.colors(thumbColor = Look.accent, activeTrackColor = Look.accent, inactiveTrackColor = Color(0xFF333336)))
            }
            Text("Applies from the next song if no sound change is heard.", color = Color.Gray, fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
    }
}


// ───────────── Online (Audius music + Radio Browser stations) ─────────────

@Composable
fun OnlineTab(st: PlayerState, openPlayer: () -> Unit) {
    val view = LocalView.current
    var mode by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var go by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf(listOf<Song>()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(mode, go) {
        busy = true; failed = false
        val q = query.trim()
        val r = withContext(Dispatchers.IO) { if (mode == 0) Net.music(q) else Net.radio(q) }
        results = r; failed = r.isEmpty(); busy = false
    }

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
            Text("Online", color = Color.White, fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 32.sp)
            Text(if (mode == 0) "Streaming from Audius" else "Live stations from Radio Browser", color = Color.Gray, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("MUSIC", mode == 0) { mode = 0 }
                Pill("RADIO", mode == 1) { mode = 1 }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicTextField(query, { query = it }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(Look.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { go++ }),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color(0x66000000)).padding(12.dp),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) Text(if (mode == 0) "Search tracks or artists…" else "Search stations…", color = Color.Gray, fontSize = 15.sp)
                            inner()
                        }
                    })
                Pill("GO", true) { go++ }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (busy) Text("Loading…", color = Look.accent, fontSize = 12.sp, fontFamily = FontFamily.Default, modifier = Modifier.padding(12.dp))
        else if (failed) Text("Nothing found, or no internet connection.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(results, key = { _, s -> s.id }) { i, s ->
                SongRow(s, st.current?.id == s.id, null, { view.tap(HapticFeedbackConstants.LONG_PRESS); st.playQueue(results, i); openPlayer() }, null)
            }
        }
    }
}


// ───────────── Lyrics ─────────────

@Composable
fun LyricsPanel(st: PlayerState, modifier: Modifier) {
    val view = LocalView.current
    val song = st.current ?: return
    val d = st.lyric?.takeIf { it.songId == song.id }
    val listState = rememberLazyListState()
    val idx = if (d != null && d.synced.isNotEmpty()) d.synced.indexOfLast { it.ms <= st.pos + 150 } else -1
    LaunchedEffect(idx) { if (idx >= 0) listState.animateScrollToItem(maxOf(idx - 2, 0)) }
    Box(modifier.frost(32.dp), contentAlignment = Alignment.Center) {
        when {
            d == null -> Text("Searching lyrics…", color = Look.accent, fontFamily = FontFamily.Default, fontSize = 13.sp)
            d.empty -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No lyrics found", color = Color.White, fontSize = 15.sp)
                Text("Needs internet. Matches by title, artist and length.", color = Color.Gray, fontSize = 11.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp))
                Pill("RETRY") { st.retryLyrics(song) }
            }
            d.synced.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize(), state = listState,
                contentPadding = PaddingValues(vertical = 60.dp, horizontal = 20.dp)) {
                itemsIndexed(d.synced) { i, line ->
                    val col by animateColorAsState(if (i == idx) Look.accent else Color(0x88FFFFFF))
                    Text(line.text.ifBlank { "♪" }, color = col, fontSize = if (i == idx) 24.sp else 19.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, lineHeight = 28.sp,
                        modifier = Modifier.fillMaxWidth().plain { view.tap(); st.seek(line.ms.toInt()) }.padding(vertical = 9.dp))
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
                item { Text(d.plain, color = Color.White, fontSize = 17.sp, lineHeight = 26.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}
