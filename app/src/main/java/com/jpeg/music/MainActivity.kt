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
    var accent by mutableStateOf(Color(0xFF111113))
    var style by mutableIntStateOf(0)
    var backdrop by mutableStateOf(true)
    var gyro by mutableStateOf(true)
    var gyroInvert by mutableStateOf(false)
    var glassStyle by mutableIntStateOf(0)       // 0 default, 1 frosted, 2 liquid
    var glassBlur by mutableFloatStateOf(24f)
    var glassTint by mutableFloatStateOf(0.55f)
    var glassBorder by mutableFloatStateOf(0.7f)
    var glassShadow by mutableFloatStateOf(0.18f)
}

val Bodies = listOf(Color(0xFF0E0E0E), Color(0xFF8E1B16), Color(0xFF12233F), Color(0xFF3A3A3A))
val Accents = listOf(Color(0xFF111113), Color(0xFF34C759), Color(0xFFFF9F0A), Color(0xFFFF375F), Color(0xFF0A84FF), Color(0xFF8E5CF7))
const val FAV = "Favorites"

// ───────────── Data ─────────────

data class Song(val id: Long, val title: String, val artist: String, val album: String, val duration: Int, val uri: android.net.Uri,
                val name: String = "", val mime: String = "",
                val artUrl: String = "", val live: Boolean = false, val online: Boolean = false, val info: String = "",
                val track: Int = 0, val year: Int = 0)

data class Album(val key: String, val title: String, val artist: String, val tracks: List<Song>)

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

object ArtCache {
    private val lru = object : android.util.LruCache<Long, ImageBitmap>((Runtime.getRuntime().maxMemory() / 6).toInt().coerceAtLeast(8 * 1024 * 1024)) {
        override fun sizeOf(key: Long, value: ImageBitmap): Int = value.width * value.height * 4
    }
    private val none = HashSet<Long>()
    fun has(id: Long): Boolean = none.contains(id) || lru.get(id) != null
    fun get(id: Long): ImageBitmap? = lru.get(id)
    fun put(id: Long, b: ImageBitmap?) { if (b == null) none.add(id) else lru.put(id, b) }
}

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
                while (o.outWidth / sample > 640) sample *= 2
                val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o2)
                if (bmp != null) return@withContext bmp.asImageBitmap()
            }
        } finally { r.release() }
        if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.loadThumbnail(s.uri, android.util.Size(600, 600), null).asImageBitmap() else null
    } catch (e: Exception) { null }
}

@Composable
fun rememberArt(s: Song?): ImageBitmap? {
    val ctx = LocalContext.current
    val st = produceState<ImageBitmap?>(if (s != null) ArtCache.get(s.id) else null, s?.id) {
        if (s == null) { value = null } else {
            if (!ArtCache.has(s.id)) ArtCache.put(s.id, loadArt(ctx, s))
            value = ArtCache.get(s.id)
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
    var albums by mutableStateOf(listOf<Album>())
    private var albumById = HashMap<Long, Album>()
    fun albumOf(s: Song): Album? = albumById[s.id]
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
        Look.accent = Color(prefs.getInt("accent2", Look.accent.toArgb()))
        Look.style = prefs.getInt("style", 0)
        Look.backdrop = prefs.getBoolean("backdrop", true)
        Look.gyro = prefs.getBoolean("gyro", true)
        Look.gyroInvert = prefs.getBoolean("gyroInv", false)
        Look.glassStyle = prefs.getInt("gs", 0)
        Look.glassBlur = prefs.getFloat("gb", 24f)
        Look.glassTint = prefs.getFloat("gt", 0.55f)
        Look.glassBorder = prefs.getFloat("gbd", 0.7f)
        Look.glassShadow = prefs.getFloat("gsh", 0.18f)
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
                MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.YEAR)
            try {
                ctx.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 30000", null,
                    "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        val artist = c.getString(2) ?: "Unknown artist"
                        out.add(Song(id, c.getString(1) ?: "Unknown", if (artist == "<unknown>") "Unknown artist" else artist,
                            c.getString(3) ?: "", c.getInt(4),
                            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id), c.getString(5) ?: "", c.getString(6) ?: "", track = c.getInt(7), year = c.getInt(8)))
                    }
                }
            } catch (e: Exception) { }
            out
        }
        songs = list
        val grouped = list.groupBy { it.album.ifBlank { "Unknown album" }.lowercase() }
        val built = grouped.map { (k, ts) ->
            val sorted = ts.sortedWith(compareBy<Song>({ if (it.track > 0) it.track else Int.MAX_VALUE }, { it.title.lowercase() }))
            val artists = sorted.map { it.artist }.distinct()
            Album(k, sorted.first().album.ifBlank { "Unknown album" }, if (artists.size == 1) artists[0] else "Various artists", sorted)
        }.sortedBy { it.title.lowercase() }
        val idx = HashMap<Long, Album>()
        built.forEach { al -> al.tracks.forEach { t -> idx[t.id] = al } }
        albumById = idx
        albums = built
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
    fun setAccent(c: Color) { Look.accent = c; prefs.edit().putInt("accent2", c.toArgb()).apply() }
    fun setStyle(i: Int) { Look.style = i; prefs.edit().putInt("style", i).apply() }
    private fun saveGlass() {
        prefs.edit().putFloat("gb", Look.glassBlur).putFloat("gt", Look.glassTint)
            .putFloat("gbd", Look.glassBorder).putFloat("gsh", Look.glassShadow).apply()
    }
    fun updateGlassStyle(i: Int) {
        Look.glassStyle = i
        when (i) {
            1 -> { Look.glassBlur = 24f; Look.glassTint = 0.55f; Look.glassBorder = 0.7f; Look.glassShadow = 0.18f }
            2 -> { Look.glassBlur = 18f; Look.glassTint = 0.32f; Look.glassBorder = 0.85f; Look.glassShadow = 0.22f }
        }
        prefs.edit().putInt("gs", i).apply()
        saveGlass()
    }
    fun updateGlass(which: Int, v: Float) {
        when (which) { 0 -> Look.glassBlur = v; 1 -> Look.glassTint = v; 2 -> Look.glassBorder = v; else -> Look.glassShadow = v }
        saveGlass()
    }
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
