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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.foundation.layout.aspectRatio


// ───────────── Track helpers ─────────────

fun sideIndex(tracks: List<Song>): List<Int> {
    val total = tracks.map { it.duration }.sum().coerceAtLeast(1)
    val sides = if (tracks.size < 3) 1 else maxOf(2, Math.ceil(total / (22.0 * 60000.0)).toInt())
    val target = total.toDouble() / sides
    var cum = 0
    return tracks.map { t ->
        val mid = cum + t.duration / 2.0
        cum += t.duration
        minOf(sides - 1, (mid / target).toInt())
    }
}

fun trackCodes(tracks: List<Song>): List<String> {
    val si = sideIndex(tracks)
    val counts = IntArray(26)
    return tracks.mapIndexed { i, _ ->
        val s = minOf(si[i], 25)
        counts[s]++
        "${'A' + s}${counts[s]}"
    }
}

fun waveform(seed: Long, n: Int): FloatArray {
    val r = kotlin.random.Random(seed)
    val out = FloatArray(n)
    var v = 0.5f
    for (i in 0 until n) {
        v += (r.nextFloat() - 0.5f) * 0.55f
        v = v.coerceIn(0.2f, 1f)
        out[i] = v
    }
    return out
}

// ───────────── App shell ─────────────

@Composable
fun App(st: PlayerState) {
    val ctx = LocalContext.current
    val social = remember { SocialHub.get(ctx) }
    val imeOn = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var tab by remember { mutableIntStateOf(0) }
    var angle by remember { mutableFloatStateOf(0f) }
    var sheetAlbum by remember { mutableStateOf<Album?>(null) }
    var sheetVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }

    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) { if (!granted) launcher.launch(perm) }
    LaunchedEffect(granted) { if (granted) st.scan() }
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

    BackHandler(enabled = tab != 0) { tab = 0 }
    BackHandler(enabled = settingsVisible) { settingsVisible = false }
    BackHandler(enabled = sheetVisible) { sheetVisible = false }

    fun openAlbum(a: Album, play: Boolean) {
        if (play && a.tracks.none { it.id == st.current?.id }) st.playQueue(a.tracks, 0)
        sheetAlbum = a
        sheetVisible = true
    }
    fun openQueue() {
        val c = st.current ?: return
        openAlbum(st.albumOf(c) ?: Album("queue", c.album.ifBlank { "Now playing" }, c.artist, st.queue), false)
    }

    val art = rememberArt(st.current)
    val backdrop = rememberBackdrop()
    Box(Modifier.fillMaxSize()) {
      Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
        AppBackground()
        if (!granted) {
            Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                Text("Allow access to your music", color = FG, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(14.dp))
                Pill("GRANT ACCESS", true) { launcher.launch(perm) }
            }
        } else if (tab == 0) {
            LibraryTab(st) { openAlbum(it, true) }
        } else {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)
                .padding(top = 90.dp, bottom = if (imeOn) 0.dp else 150.dp)) {
                when (tab) {
                    1 -> OnlineTab(st) { openQueue() }
                    2 -> FriendsTab(social, st)
                    else -> SearchTab(st) { a, p -> openAlbum(a, p) }
                }
            }
        }

        if (tab == 0 && !imeOn) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(118.dp)
                .background(Brush.verticalGradient(listOf(Bg, Bg.copy(alpha = 0f)))))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(210.dp)
                .background(Brush.verticalGradient(listOf(Bg.copy(alpha = 0f), Bg))))
        }
      }
      CompositionLocalProvider(LocalBackdrop provides backdrop) {
        if (!imeOn) {
            TopBar(onStack = { settingsVisible = true }, onCrate = { tab = 2 })
            Column(Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 18.dp)) {
                if (st.current != null) { MiniPlayer(st, art, angle) { openQueue() }; Spacer(Modifier.height(10.dp)) }
                TabBar(tab) { tab = it }
            }
        }
      }

        AnimatedVisibility(settingsVisible, enter = fadeIn(tween(200)) + slideInVertically(tween(260)) { it / 8 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 8 }) {
            SettingsSheet(st) { settingsVisible = false }
        }
        AnimatedVisibility(sheetVisible,
            enter = fadeIn(tween(220)) + scaleIn(spring(0.85f, 300f), initialScale = 0.9f) + slideInVertically(spring(0.85f, 300f)) { it / 6 },
            exit = fadeOut(tween(180)) + scaleOut(tween(200), targetScale = 0.95f) + slideOutVertically(tween(220)) { it / 8 }) {
            sheetAlbum?.let { ReleaseSheet(st, it, angle) { sheetVisible = false } }
        }
    }
}

@Composable
fun TopBar(onStack: () -> Unit, onCrate: () -> Unit) {
    val v = LocalView.current
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 34.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.size(46.dp).bounce { v.tap(); onStack() }.glass(23.dp, 0.7f), contentAlignment = Alignment.Center) { Ic(IC_STACK, 24.dp, FG) }
        Box(Modifier.size(46.dp).bounce { v.tap(); onCrate() }, contentAlignment = Alignment.Center) { Crate(38.dp) }
    }
}

@Composable
fun TabBar(tab: Int, onTab: (Int) -> Unit) {
    val v = LocalView.current
    Row(Modifier.fillMaxWidth().glass(32.dp, 0.8f).padding(5.dp)) {
        listOf(IC_GRID, IC_RADIO, IC_PROFILE, IC_SEARCH).forEachIndexed { i, k ->
            val bgc by animateColorAsState(if (tab == i) Color(0x17000000) else Color.Transparent, label = "tab")
            Box(Modifier.weight(1f).height(48.dp).bounce { v.tap(); onTab(i) }.clip(RoundedCornerShape(26.dp)).background(bgc),
                contentAlignment = Alignment.Center) { Ic(k, 26.dp, FG) }
        }
    }
}

@Composable
fun MiniPlayer(st: PlayerState, art: ImageBitmap?, angle: Float, onOpen: () -> Unit) {
    val s = st.current ?: return
    val v = LocalView.current
    val al = st.albumOf(s)
    val code = al?.let { a -> val i = a.tracks.indexOfFirst { it.id == s.id }; if (i >= 0) trackCodes(a.tracks)[i] else null }
    Row(Modifier.fillMaxWidth().height(58.dp).bounce { onOpen() }.glass(29.dp, 0.8f).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Vinyl(40.dp, art, angle, labelRatio = 0.42f)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (code != null) "$code · ${s.title}" else s.title, color = FG, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.artist, color = SUB, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(44.dp).bounce { v.tap(); st.toggle() }, contentAlignment = Alignment.Center) {
            Ic(if (st.playing) IC_PAUSE else IC_PLAY, 20.dp, FG)
        }
    }
}

// ───────────── Library grid ─────────────

@Composable
fun AlbumCell(a: Album, onClick: () -> Unit) {
    val art = rememberArt(a.tracks.firstOrNull())
    val shape = RoundedCornerShape(3.dp)
    Cover(art, Modifier.fillMaxWidth().aspectRatio(1f).bounce(onClick)
        .shadow(6.dp, shape, false, Color(0x22000000), Color(0x33000000)).clip(shape))
}

@Composable
fun LibraryTab(st: PlayerState, onOpen: (Album) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 100.dp, bottom = 210.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(st.albums, key = { it.key }) { a -> AlbumCell(a) { onOpen(a) } }
        }
        if (st.albums.isEmpty()) Text(if (st.scanned) "No music found on this device" else "Scanning your music…", color = SUB, fontSize = 15.sp,
            modifier = Modifier.align(Alignment.Center))
    }
}

// ───────────── Search / songs / playlists ─────────────

@Composable
fun SearchTab(st: PlayerState, onOpenAlbum: (Album, Boolean) -> Unit) {
    val view = LocalView.current
    var q by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(0) }
    var openPl by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<Song?>(null) }
    var newDialog by remember { mutableStateOf(false) }
    val query = q.trim().lowercase()
    val songs = st.songs.filter { s ->
        (query.isEmpty() || s.title.lowercase().contains(query) || s.artist.lowercase().contains(query) || s.album.lowercase().contains(query)) &&
            when (mode) { 3 -> st.quality[s.id]?.lossless ?: s.isLossless(); 4 -> st.quality[s.id]?.hires == true; else -> true }
    }
    val albums = st.albums.filter { a -> query.isEmpty() || a.title.lowercase().contains(query) || a.artist.lowercase().contains(query) }
    fun playSong(s: Song) {
        val a = st.albumOf(s)
        if (a != null) { st.playQueue(a.tracks, a.tracks.indexOfFirst { it.id == s.id }.coerceAtLeast(0)); onOpenAlbum(a, false) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text("Search", color = FG, fontWeight = FontWeight.Bold, fontSize = 32.sp, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
            Field(q, { q = it }, "Songs, albums, artists")
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("SONGS", mode == 0) { mode = 0 }
                Pill("ALBUMS", mode == 1) { mode = 1 }
                Pill("PLAYLISTS", mode == 2) { mode = 2; openPl = null }
                Pill("LOSSLESS", mode == 3) { mode = 3 }
                Pill("HI-RES", mode == 4) { mode = 4 }
            }
            Spacer(Modifier.height(8.dp))
            when (mode) {
                1 -> LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(albums, key = { it.key }) { a -> AlbumCell(a) { onOpenAlbum(a, true) } }
                }
                2 -> {
                    val name = openPl
                    if (name == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${st.playlists.size} playlists", color = SUB, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Pill("+ NEW") { newDialog = true }
                        }
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(st.playlists.keys.toList(), key = { it }) { n ->
                                val list = st.songsOf(n)
                                val a = rememberArt(list.firstOrNull())
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { view.tap(); openPl = n }.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Cover(a, Modifier.size(52.dp).clip(RoundedCornerShape(4.dp)))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(if (n == FAV) "♥ Favorites" else n, color = FG, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${list.size} songs", color = SUB, fontSize = 12.sp)
                                    }
                                    Box(Modifier.size(40.dp).bounce {
                                        if (list.isNotEmpty()) { view.tap(HapticFeedbackConstants.LONG_PRESS); st.playQueue(list, 0); onOpenAlbum(Album("pl:$n", n, "Playlist", list), false) }
                                    }.clip(CircleShape).background(Look.accent), contentAlignment = Alignment.Center) { Ic(IC_PLAY, 16.dp, onAcc(Look.accent)) }
                                    if (n != FAV) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(Modifier.size(40.dp).bounce { view.tap(HapticFeedbackConstants.LONG_PRESS); st.deletePl(n) }.clip(CircleShape).background(Color(0x12000000)),
                                            contentAlignment = Alignment.Center) { Ic(IC_CLOSE, 14.dp, FG) }
                                    }
                                }
                            }
                        }
                    } else {
                        val list = st.songsOf(name)
                        val al = Album("pl:$name", name, "Playlist", list)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).bounce { openPl = null }, contentAlignment = Alignment.Center) { Ic(IC_BACK, 24.dp, FG) }
                            Text(if (name == FAV) "♥ Favorites" else name, color = FG, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            Pill("PLAY") { if (list.isNotEmpty()) { st.playQueue(list, 0); onOpenAlbum(al, false) } }
                        }
                        if (list.isEmpty()) Text("Empty. Tap ••• on a song and add it here.", color = SUB, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                        LazyColumn(Modifier.fillMaxSize()) {
                            itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                                SongRow(s, st.current?.id == s.id, st.quality[s.id], { st.playQueue(list, i); onOpenAlbum(al, false) }, { menu = s })
                            }
                        }
                    }
                }
                else -> {
                    if (songs.isEmpty()) Text(if (st.scanned) "Nothing found" else "Scanning…", color = SUB, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(songs, key = { _, s -> s.id }) { _, s ->
                            SongRow(s, st.current?.id == s.id, st.quality[s.id], { playSong(s) }, { menu = s })
                        }
                    }
                }
            }
        }
        menu?.let { s ->
            val items = ArrayList<Pair<String, () -> Unit>>()
            items.add("Play next" to { st.playNext(s) })
            items.add((if (st.isFav(s.id)) "Remove from Favorites" else "Add to Favorites") to { st.toggleFav(s.id) })
            st.playlists.keys.filter { it != FAV }.forEach { p -> items.add("Add to $p" to { st.addTo(p, s.id) }) }
            val op = openPl
            if (mode == 2 && op != null && op != FAV) items.add("Remove from $op" to { st.removeFrom(op, s.id) })
            FloatingMenu(s.title, items) { menu = null }
        }
        if (newDialog) NameDialog({ st.createPl(it) }) { newDialog = false }
    }
}

// ───────────── Release sheet (sleeve, record, tracklist) ─────────────

@Composable
fun RecordStage(art: ImageBitmap?, p: State<Float>, angle: Float, w: Dp, sIn: Dp, h: Dp, onToggle: () -> Unit, onSwipe: (Boolean) -> Unit) {
    val d = LocalDensity.current
    val wPx = with(d) { w.toPx() }
    val sPx = with(d) { sIn.toPx() }
    val hPx = with(d) { h.toPx() }
    val recDp = sIn * 0.97f
    val recPx = sPx * 0.97f
    val x0 = ((wPx - sPx) / 2f).roundToInt()
    val y0 = ((hPx - sPx) / 2f).roundToInt()
    val rx = ((wPx - recPx) / 2f).roundToInt()
    val ry = ((hPx - recPx) / 2f).roundToInt()
    val margin = 0.09f * wPx

    Box(Modifier.fillMaxWidth().height(h)
        .graphicsLayer { rotationY = Tilt.x * 6f; rotationX = -Tilt.y * 6f; cameraDistance = 16f * density }
        .pointerInput(Unit) {
            var dx = 0f
            detectHorizontalDragGestures(onDragStart = { dx = 0f },
                onDragEnd = { if (dx > 40f) onSwipe(true) else if (dx < -40f) onSwipe(false) },
                onHorizontalDrag = { _, a -> dx += a })
        }
        .pointerInput(Unit) { detectTapGestures(onTap = { onToggle() }) }) {

        fun sleeveShift(pv: Float): Float {
            val sc = 1f - 0.23f * pv
            val cx = wPx / 2f + ((margin + sPx * sc / 2f) - wPx / 2f) * pv
            return cx - wPx / 2f
        }

        // paper visible through the sleeve hole once the record is pulled out
        Box(Modifier.offset { IntOffset(x0, y0) }.size(sIn).graphicsLayer {
            val pv = p.value; val sc = 1f - 0.23f * pv
            translationX = sleeveShift(pv); scaleX = sc; scaleY = sc
        }) {
            Canvas(Modifier.fillMaxSize()) {
                val s = this.size.minDimension
                drawCircle(Color(0xFFF6F6F7), s * 0.15f, center)
                drawArc(Color(0x33000000), 200f, 150f, false, Offset(center.x - s * 0.15f, center.y - s * 0.15f), Size(s * 0.3f, s * 0.3f), style = Stroke(s * 0.012f))
            }
        }
        // the record
        Box(Modifier.offset { IntOffset(rx, ry) }.size(recDp).graphicsLayer {
            val pv = p.value; val sc = 1f - 0.23f * pv
            translationX = sleeveShift(pv) + pv * 0.55f * sPx * sc; scaleX = sc; scaleY = sc
        }) { Vinyl(recDp, art, angle, sheen = true, labelRatio = 0.36f) }
        // the sleeve with a die-cut hole
        Box(Modifier.offset { IntOffset(x0, y0) }.size(sIn).graphicsLayer {
            val pv = p.value; val sc = 1f - 0.23f * pv
            translationX = sleeveShift(pv); scaleX = sc; scaleY = sc
        }) {
            Canvas(Modifier.fillMaxSize()) {
                val s = this.size.minDimension
                for (i in 1..5) drawRoundRect(Color(0x0C000000), Offset(s * 0.004f * i, s * 0.012f * i), Size(s, s), CornerRadius(s * 0.006f))
                val path = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, s, s))
                    addOval(Rect(center, s * 0.15f))
                }
                clipPath(path) {
                    if (art != null) {
                        val side = min(art.width, art.height)
                        drawImage(art, srcOffset = IntOffset((art.width - side) / 2, (art.height - side) / 2), srcSize = IntSize(side, side),
                            dstSize = IntSize(s.roundToInt(), s.roundToInt()))
                    } else {
                        drawRect(Color(0xFF151517))
                        drawCircle(Color(0xFF2A2A2D), s * 0.34f, center, style = Stroke(s * 0.01f))
                    }
                }
                drawRect(Color(0x22000000), style = Stroke(s * 0.003f))
                drawCircle(Color(0x44000000), s * 0.15f, center, style = Stroke(s * 0.006f))
            }
        }
    }
}

@Composable
fun TrackTable(st: PlayerState, album: Album) {
    val view = LocalView.current
    val si = remember(album.key, album.tracks.size) { sideIndex(album.tracks) }
    Column(Modifier.fillMaxWidth().padding(top = 26.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Side", color = FG, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(46.dp))
            Text("Time", color = FAINT, fontSize = 12.sp, modifier = Modifier.width(62.dp))
            Spacer(Modifier.width(32.dp))
            Text("Tracklist", color = Color(0xFF6B6B70), fontSize = 12.sp)
        }
        album.tracks.forEachIndexed { i, t ->
            val cur = st.current?.id == t.id
            Row(Modifier.fillMaxWidth().height(44.dp).clickable {
                view.tap(HapticFeedbackConstants.LONG_PRESS)
                if (cur) st.toggle() else st.playQueue(album.tracks, i)
            }.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (i == 0 || si[i] != si[i - 1]) "${'A' + minOf(si[i], 25)}" else "", color = FG, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(46.dp))
                Text(if (t.live) "LIVE" else fmt(t.duration), color = FAINT, fontSize = 15.sp, modifier = Modifier.width(62.dp))
                Box(Modifier.width(32.dp)) { Ic(if (cur && st.playing) IC_PAUSE else IC_PLAY, 14.dp, FG) }
                Text("${t.artist} - ${t.title}", color = if (cur) FG else SUB, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun WavePill(st: PlayerState, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val song = st.current
    val dur = maxOf(song?.duration ?: 0, 1)
    val wave = remember(song?.id) { waveform(song?.id ?: 0L, 150) }
    Row(modifier.fillMaxWidth().height(54.dp).glass(27.dp, 0.8f).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).bounce { view.tap(HapticFeedbackConstants.LONG_PRESS); st.toggle() }, contentAlignment = Alignment.Center) {
            Ic(if (st.playing) IC_PAUSE else IC_PLAY, 18.dp, FG)
        }
        Spacer(Modifier.width(6.dp))
        Canvas(Modifier.weight(1f).height(36.dp)
            .pointerInput(song?.id) {
                detectTapGestures(onTap = { off ->
                    if (song != null && !song.live) { st.seek(((off.x / size.width).coerceIn(0f, 1f) * dur).toInt()); view.tap(HapticFeedbackConstants.CLOCK_TICK) }
                })
            }
            .pointerInput(song?.id) {
                var last = -1
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    if (song != null && !song.live) {
                        val f = (change.position.x / size.width).coerceIn(0f, 1f)
                        st.seek((f * dur).toInt())
                        val sec = (f * dur / 1000).toInt()
                        if (sec != last) { last = sec; view.tap(HapticFeedbackConstants.CLOCK_TICK) }
                    }
                }
            }) {
            val n = wave.size
            val step = this.size.width / n
            val prog = if (song == null || song.live) 0f else st.pos.toFloat() / dur
            for (i in 0 until n) {
                val hh = wave[i] * this.size.height
                val x = i * step + step / 2f
                val played = i / n.toFloat() < prog
                drawLine(if (played) Color(0xFF6A6A70) else Color(0xFFC9C9CE), Offset(x, (this.size.height - hh) / 2f),
                    Offset(x, (this.size.height + hh) / 2f), strokeWidth = step * 0.55f, cap = StrokeCap.Round)
            }
            val px = prog.coerceIn(0f, 1f) * this.size.width
            drawLine(FG, Offset(px, this.size.height * 0.08f), Offset(px, this.size.height * 0.92f), strokeWidth = 3.5f, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun ReleaseSheet(st: PlayerState, album: Album, angle: Float, onClose: () -> Unit) {
    val view = LocalView.current
    val cur = st.current
    val inAlbum = cur != null && album.tracks.any { it.id == cur.id }
    val head = if (inAlbum) cur else album.tracks.firstOrNull()
    var out by remember { mutableStateOf(false) }
    val p = animateFloatAsState(if (out) 1f else 0f, spring(0.6f, 110f), label = "pull")
    val art = rememberArt(album.tracks.firstOrNull())
    val backdrop = rememberBackdrop()
    LaunchedEffect(cur?.id, st.showLyrics, st.lyric == null) { if (st.showLyrics) cur?.let { st.loadLyrics(it) } }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
            AppBackground()
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.displayCutout).padding(top = 34.dp)) {
                Spacer(Modifier.height(46.dp))
                BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                    val sIn = maxWidth * 0.78f
                    val stageH = sIn + 20.dp
                    RecordStage(art, p, angle, maxWidth, sIn, stageH, onToggle = { out = !out }, onSwipe = { right -> out = right })
                }
                TrackTable(st, album)
                val total = album.tracks.map { it.duration }.sum()
                val year = album.tracks.firstOrNull()?.year ?: 0
                Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(art, Modifier.size(66.dp).clip(RoundedCornerShape(4.dp)))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(album.artist, color = FG, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(album.title, color = SUB, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text((if (year > 0) "$year · " else "") + "${album.tracks.size} tracks · ${total / 60000} min", color = FAINT, fontSize = 12.sp)
                    }
                    if (cur != null && !cur.online) {
                        val fav = st.isFav(cur.id)
                        Box(Modifier.size(44.dp).bounce { view.tap(HapticFeedbackConstants.LONG_PRESS); st.toggleFav(cur.id) }, contentAlignment = Alignment.Center) {
                            Ic(if (fav) IC_HEART else IC_HEARTO, 26.dp, FG)
                        }
                    }
                }
                Spacer(Modifier.height(150.dp))
            }
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(110.dp).background(Brush.verticalGradient(listOf(Bg, Bg.copy(alpha = 0f)))))
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.displayCutout).padding(start = 16.dp, end = 16.dp, top = 34.dp).align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).bounce { view.tap(); onClose() }.glass(23.dp, 0.7f), contentAlignment = Alignment.Center) { Ic(IC_CLOSE, 22.dp, FG) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(head?.title ?: album.title, color = FG, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(head?.artist ?: album.artist, color = SUB, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(46.dp).bounce { view.tap(); st.showLyrics = !st.showLyrics }, contentAlignment = Alignment.Center) { Crate(38.dp) }
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(130.dp).background(Brush.verticalGradient(listOf(Bg.copy(alpha = 0f), Bg))))
            WavePill(st, Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 18.dp))
        }
        if (st.showLyrics && cur != null) LyricScene(st) { st.showLyrics = false }
    }
}

// ───────────── Settings sheet ─────────────

@Composable
fun SettingsSheet(st: PlayerState, onClose: () -> Unit) {
    val view = LocalView.current
    Box(Modifier.fillMaxSize()) {
        AppBackground()
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 34.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).bounce { view.tap(); onClose() }.glass(23.dp, 0.7f), contentAlignment = Alignment.Center) { Ic(IC_CLOSE, 22.dp, FG) }
            }
            Box(Modifier.weight(1f)) { StudioTab(st) }
        }
    }
}
