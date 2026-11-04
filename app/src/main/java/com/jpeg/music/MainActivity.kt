package com.jpeg.music

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import kotlin.math.min
import kotlin.math.roundToInt

// ───────────────────────── Data ─────────────────────────

data class Song(val id: Long, val title: String, val artist: String, val album: String, val duration: Int, val uri: android.net.Uri)

object ArtCache { val map = HashMap<Long, ImageBitmap?>() }

suspend fun loadArt(ctx: Context, s: Song): ImageBitmap? = withContext(Dispatchers.IO) {
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

// ───────────────────────── Player ─────────────────────────

class PlayerState(private val ctx: Context) {
    var songs by mutableStateOf(listOf<Song>())
    var index by mutableIntStateOf(0)
    var playing by mutableStateOf(false)
    var pos by mutableIntStateOf(0)
    var shuffle by mutableStateOf(false)
    var repeat by mutableStateOf(false)
    var rpm by mutableFloatStateOf(33.3f)
    var scanned by mutableStateOf(false)
    private var mp: MediaPlayer? = null

    val current: Song? get() = songs.getOrNull(index)

    suspend fun scan() {
        val list = withContext(Dispatchers.IO) {
            val out = ArrayList<Song>()
            val proj = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION)
            try {
                ctx.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 30000", null,
                    "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        val artist = c.getString(2) ?: "Unknown artist"
                        out.add(Song(id, c.getString(1) ?: "Unknown", if (artist == "<unknown>") "Unknown artist" else artist,
                            c.getString(3) ?: "", c.getInt(4),
                            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)))
                    }
                }
            } catch (e: Exception) { }
            out
        }
        songs = list
        scanned = true
        if (index >= list.size) index = 0
    }

    private fun applySpeed() {
        val m = mp ?: return
        try {
            val r = rpm / 33.3f
            m.playbackParams = PlaybackParams().setSpeed(r).setPitch(r)
        } catch (e: Exception) { }
    }

    fun setSpeed(v: Float) { rpm = v; if (playing) applySpeed() }

    fun play(i: Int) {
        if (songs.isEmpty()) return
        release()
        index = i
        try {
            mp = MediaPlayer().apply {
                setDataSource(ctx, songs[i].uri)
                setOnCompletionListener { if (repeat) play(index) else next() }
                prepare()
                start()
            }
            playing = true
            pos = 0
            if (rpm != 33.3f) applySpeed()
        } catch (e: Exception) { playing = false }
    }

    fun toggle() {
        val m = mp
        if (m == null) { play(index); return }
        if (playing) { m.pause(); playing = false }
        else { m.start(); playing = true; if (rpm != 33.3f) applySpeed() }
    }

    fun stop() { mp?.let { try { it.pause(); it.seekTo(0) } catch (e: Exception) { } }; playing = false; pos = 0 }

    fun next() {
        if (songs.isEmpty()) return
        val n = if (shuffle && songs.size > 1) { var r: Int; do { r = songs.indices.random() } while (r == index); r } else (index + 1) % songs.size
        play(n)
    }

    fun prev() {
        if (songs.isEmpty()) return
        if (pos > 3000) { seek(0); return }
        play((index - 1 + songs.size) % songs.size)
    }

    fun seek(ms: Int) { try { mp?.seekTo(ms); pos = ms } catch (e: Exception) { } }

    fun refresh() { val m = mp ?: return; if (playing) try { pos = m.currentPosition } catch (e: Exception) { } }

    fun release() { try { mp?.release() } catch (e: Exception) { }; mp = null }
}

// ───────────────────────── Activity ─────────────────────────

class MainActivity : ComponentActivity() {
    private val st by lazy { PlayerState(applicationContext) }

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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onDestroy() { st.release(); super.onDestroy() }
}

// ───────────────────────── UI ─────────────────────────

private val Dark = Color(0xFF0B0B0C)
private val Panel = Color(0xFF1A1A1C)
private val Light = Color(0xFFE9ECEA)

fun View.tap(kind: Int = HapticFeedbackConstants.KEYBOARD_TAP) { performHapticFeedback(kind) }

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
fun Vinyl(dia: Dp, art: ImageBitmap?, angle: Float = 0f, tint: Color = Color(0xFF7CC242)) {
    Canvas(Modifier.size(dia).graphicsLayer { rotationZ = angle }) {
        val c = center
        val r = this.size.minDimension / 2
        drawCircle(Color(0xFF0E0E0E), r, c)
        for (i in 1..16) drawCircle(Color(0x1FFFFFFF), r * (0.58f + i * 0.025f), c, style = Stroke(1f))
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
        } else drawCircle(tint, lr, c)
        drawCircle(Color(0x66000000), lr, c, style = Stroke(3f))
        drawCircle(Color(0xFF0E0E0E), r * 0.04f, c)
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

@Composable
fun Pill(t: String, selected: Boolean = false, onClick: () -> Unit) {
    val v = LocalView.current
    Box(Modifier.clip(RoundedCornerShape(50)).background(if (selected) Light else Panel)
        .clickable { v.tap(); onClick() }.padding(horizontal = 16.dp, vertical = 11.dp)) {
        Text(t, color = if (selected) Color.Black else Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

fun fmt(ms: Int) = "%02d:%02d".format(ms / 60000, (ms / 1000) % 60)

@Composable
fun App(st: PlayerState) {
    val ctx = LocalContext.current
    val view = LocalView.current
    var tab by remember { mutableIntStateOf(0) }
    var angle by remember { mutableFloatStateOf(0f) }
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LaunchedEffect(Unit) { if (!granted) launcher.launch(perm) }
    LaunchedEffect(granted) { if (granted) st.scan() }
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
    Box(Modifier.fillMaxSize().background(Dark)) {
        if (art != null) Image(art, null, Modifier.fillMaxSize().blur(50.dp).alpha(0.35f), contentScale = ContentScale.Crop)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).padding(top = 28.dp, bottom = 20.dp)) {
            Box(Modifier.weight(1f)) {
                if (!granted) Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                    Text("Allow access to your music", color = Color.White, fontSize = 16.sp)
                    Spacer(Modifier.height(14.dp))
                    Pill("GRANT ACCESS") { launcher.launch(perm) }
                } else when (tab) {
                    0 -> PlayerTab(st, art, angle)
                    1 -> LibraryTab(st) { tab = 0 }
                    else -> RpmTab(st)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), Arrangement.Center) {
                Pill("PLAYER", tab == 0) { tab = 0 }; Spacer(Modifier.width(8.dp))
                Pill("LIBRARY", tab == 1) { tab = 1 }; Spacer(Modifier.width(8.dp))
                Pill("RPM", tab == 2) { tab = 2 }
            }
        }
    }
}

@Composable
fun PlayerTab(st: PlayerState, art: ImageBitmap?, angle: Float) {
    val view = LocalView.current
    val song = st.current
    val n = st.songs.size
    val nextSong = if (n > 1) st.songs[(st.index + 1) % n] else null
    val next2 = if (n > 2) st.songs[(st.index + 2) % n] else null
    val artN = rememberArt(nextSong)
    val artN2 = rememberArt(next2)
    val enter = remember { Animatable(0f) }
    LaunchedEffect(st.index) { enter.snapTo(1f); enter.animateTo(0f, spring(0.62f, Spring.StiffnessLow)) }
    var lastSec by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Pill(if (st.playing) "PAUSE" else "PLAY") { st.toggle() }
            DotText(fmt(st.pos), 5f)
            Pill("STOP") { st.stop() }
        }
        Spacer(Modifier.height(14.dp))
        if (song == null) {
            Spacer(Modifier.weight(1f))
            Text(if (st.scanned) "NO SONGS FOUND" else "SCANNING…", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Spacer(Modifier.weight(1f))
            return@Column
        }
        Text(song.title.uppercase(), color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            fontSize = 26.sp, lineHeight = 30.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
        Text(song.artist, color = Color(0xFFAAAAAA), fontSize = 13.sp, maxLines = 1, modifier = Modifier.fillMaxWidth())

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()
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
            val dia = minOf(maxWidth - 10.dp, maxHeight - 40.dp)
            Box(Modifier.align(Alignment.TopCenter).padding(top = 16.dp)) {
                if (next2 != null) Box(Modifier.offset(y = 120.dp)) { Vinyl(dia, artN2, 40f) }
                if (nextSong != null) Box(Modifier.offset(y = 60.dp)) { Vinyl(dia, artN, 110f) }
                Box(Modifier.graphicsLayer {
                    translationY = enter.value * 260f
                    alpha = 1f - enter.value * 0.7f
                    val s = 1f - enter.value * 0.12f; scaleX = s; scaleY = s
                }.clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                    view.tap(); st.toggle()
                }) { Vinyl(dia, art, angle) }
            }
        }

        val dur = maxOf(song.duration, 1)
        Slider(value = st.pos.coerceIn(0, dur).toFloat(), onValueChange = {
            val sec = (it / 1000).toInt()
            if (sec != lastSec) { lastSec = sec; view.tap(HapticFeedbackConstants.CLOCK_TICK) }
            st.seek(it.toInt())
        }, valueRange = 0f..dur.toFloat(),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Panel))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(fmt(st.pos), color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text(fmt(song.duration), color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            RoundBtn(52.dp, Panel, { view.tap(HapticFeedbackConstants.LONG_PRESS); st.prev() }) { Icon(3) }
            RoundBtn(72.dp, Light, { view.tap(HapticFeedbackConstants.LONG_PRESS); st.toggle() }) { Icon(if (st.playing) 1 else 0, Color.Black, 26.dp) }
            RoundBtn(52.dp, Panel, { view.tap(HapticFeedbackConstants.LONG_PRESS); st.next() }) { Icon(2) }
        }
    }
}

@Composable
fun RoundBtn(size: Dp, bg: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(bg).clickable { onClick() }, contentAlignment = Alignment.Center) { content() }
}

@Composable
fun LibraryTab(st: PlayerState, openPlayer: () -> Unit) {
    val view = LocalView.current
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("LIBRARY", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        Text("${st.songs.size} songs", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
        if (st.songs.isEmpty()) {
            Text(if (st.scanned) "No music found on this device." else "Scanning…", color = Color.Gray, modifier = Modifier.padding(top = 20.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(st.songs, key = { _, s -> s.id }) { i, s ->
                val a = rememberArt(s)
                val cur = i == st.index
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (cur) Panel else Color.Transparent)
                    .clickable { view.tap(HapticFeedbackConstants.LONG_PRESS); st.play(i); openPlayer() }
                    .padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Vinyl(52.dp, a)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.artist, color = Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(fmt(s.duration), color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun RpmTab(st: PlayerState) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("RPM", color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(8.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Panel).padding(vertical = 30.dp), horizontalArrangement = Arrangement.Center) {
            DotText(if (st.rpm < 40f) "33.3" else "45.0", 7f)
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("33.3 RPM", st.rpm < 40f) { st.setSpeed(33.3f) }
            Pill("45 RPM", st.rpm >= 40f) { st.setSpeed(45f) }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("SHUFFLE", if (st.shuffle) "ON" else "OFF", st.shuffle, Modifier.weight(1f)) { view.tap(); st.shuffle = !st.shuffle }
            Tile("REPEAT", if (st.repeat) "ON" else "OFF", st.repeat, Modifier.weight(1f)) { view.tap(); st.repeat = !st.repeat }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("SONGS", "${st.songs.size}", false, Modifier.weight(1f)) { }
            Tile("RESCAN", "↻", false, Modifier.weight(1f)) { view.tap(); scope.launch { st.scan() } }
        }
        Text("Speed changes the playback rate and pitch, like a real turntable.", color = Color.Gray, fontSize = 11.sp,
            modifier = Modifier.padding(top = 18.dp))
    }
}

@Composable
fun Tile(label: String, value: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(45.dp)).background(if (on) Light else Panel)
            .clickable { onClick() }, contentAlignment = Alignment.Center) {
            Text(value, color = if (on) Color.Black else Color.White, fontSize = 22.sp, fontFamily = FontFamily.Monospace)
        }
        Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }
}
