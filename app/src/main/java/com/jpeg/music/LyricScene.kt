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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.graphics.FilterQuality
import kotlin.math.abs
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



// ───────────── Lyric scene: white screen, word-by-word text, animated cartoon stickers ─────────────

private val ArialLike = FontFamily(Font(R.font.lib_sans))
private val ScriptSerif = FontFamily(Font(R.font.lib_serif_italic, style = FontStyle.Italic))
private val LyricRed = Color(0xFFD31E21)
private val LyricBlue = Color(0xFF3189D3)

private val HEART_DUR = intArrayOf(67, 133, 167, 133, 167, 133, 167, 133, 167, 133, 167, 133, 100)
private val EMOJI_DUR = intArrayOf(67, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 200, 100, 100, 100, 100, 33)

class LWord(val text: String, val at: Long, val red: Boolean, val gapEm: Float)
class LPhrase(val start: Long, val end: Long, val adlib: Boolean, val rows: List<List<LWord>>, val x: Float, val y: Float)

private val adlibRe = Regex("^(?:o{2,}h*|o+h+|a{2,}h*|a+h+|u{2,}h*|u+h+|e+h+|i+h+|m{2,}|h+m+|ye+a+h*|woa+h*|whoa+h*|hey+|(?:la)+|(?:na)+|(?:da)+)$")
private val stripRe = Regex("[\\[\\](),.;:!?\"“”…]")

private fun cleanLine(s: String) = s.lowercase().replace(stripRe, " ").replace(Regex("\\s+"), " ").trim()

/** Stack words into short rows (at most 4 rows, 2 words per row) like the reference. */
private fun chunkRows(words: List<String>): List<List<Int>> {
    var limit = 10
    repeat(4) {
        val rows = ArrayList<MutableList<Int>>()
        var len = 0
        for (i in words.indices) {
            val w = words[i].length
            val cur = rows.lastOrNull()
            if (cur != null && cur.size < 2 && len + 1 + w <= limit) { cur.add(i); len += 1 + w }
            else { rows.add(mutableListOf(i)); len = w }
        }
        if (rows.size <= 4) return rows
        limit += 3
    }
    return words.indices.toList().chunked(3)
}

fun buildPhrases(d: LyricData, durMs: Int): List<LPhrase> {
    val raw: List<Pair<Long, String>> =
        if (d.synced.isNotEmpty()) d.synced.map { it.ms to it.text }
        else {
            val ls = d.plain.lines().filter { it.isNotBlank() }
            val step = durMs.toLong() / maxOf(ls.size, 1)
            ls.mapIndexed { i, t -> (i * step) to t }
        }
    val lines = raw.map { it.first to cleanLine(it.second) }.filter { it.second.isNotEmpty() }
    return lines.mapIndexed { i, pair ->
        val start = pair.first
        val text = pair.second
        val next = lines.getOrNull(i + 1)?.first ?: (start + 6000L)
        val span = (next - start).coerceIn(900L, 6500L)
        val end = minOf(next, start + span + 400L)
        val h = abs(text.hashCode())
        val adlib = adlibRe.matches(text.replace(" ", ""))
        val ws = text.split(" ")
        val rowsIdx: List<List<Int>> = if (adlib) listOf(ws.indices.toList()) else chunkRows(ws)
        val total = ws.map { it.length + 1 }.sum()
        var cum = 0
        val times = ws.map { w ->
            val t = start + (span * 0.8 * cum / total).toLong()
            cum += w.length + 1
            t
        }
        val cands = ws.indices.filter { ws[it].length >= 4 }
        val redIdx = if (!adlib && cands.isNotEmpty() && h % 5 < 3) cands[(h / 7) % cands.size] else -1
        val rows = rowsIdx.map { r ->
            r.mapIndexed { k, wi ->
                LWord(ws[wi], times[wi], wi == redIdx, if (k == 0) 0f else if (wi == ws.lastIndex && r.size >= 2) 2.2f else 0.56f)
            }
        }
        val x = if (adlib) 0.504f else 0.285f + ((h shr 3) % 25) / 1000f
        val y = if (adlib) 0.521f else minOf(0.404f + ((h shr 9) % 40) / 1000f, 0.62f - rows.size * 0.0694f).coerceAtLeast(0.2f)
        LPhrase(start, end, adlib, rows, x, y)
    }
}

private suspend fun loadSprite(ctx: Context, id: Int): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        val o = BitmapFactory.Options().apply { inScaled = false }
        BitmapFactory.decodeResource(ctx.resources, id, o)?.asImageBitmap()
    } catch (e: Exception) { null }
}

@Composable
private fun Sticker(sheet: ImageBitmap?, fw: Int, fh: Int, dur: IntArray, alpha: Float, modifier: Modifier) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(sheet) {
        if (sheet != null) {
            var i = 0
            while (true) { frame = i; delay(dur[i].toLong()); i = (i + 1) % dur.size }
        }
    }
    Canvas(modifier) {
        if (sheet != null) drawImage(sheet, srcOffset = IntOffset(frame * fw, 0), srcSize = IntSize(fw, fh),
            dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()), alpha = alpha, filterQuality = FilterQuality.Medium)
    }
}

@Composable
private fun PhraseView(p: LPhrase, pos: Long, wPx: Float, top: Float) {
    val d = LocalDensity.current
    val fs = with(d) { (wPx * 0.059f).toSp() }
    val ls = with(d) { (wPx * 0.0694f).toSp() }
    val rowH = with(d) { (wPx * 0.0694f).toDp() }
    val boxTop = top + p.y * wPx - 0.0554f * wPx
    if (p.adlib) {
        val w = p.rows[0].first()
        Text(p.rows[0].joinToString(" ") { it.text }, color = LyricBlue, fontFamily = ScriptSerif, fontStyle = FontStyle.Italic, fontSize = fs, lineHeight = ls,
            textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
            modifier = Modifier.fillMaxWidth().offset { IntOffset(((p.x - 0.5f) * wPx).roundToInt(), boxTop.roundToInt()) }.alpha(if (pos >= w.at) 1f else 0f))
        return
    }
    Column(Modifier.offset { IntOffset((p.x * wPx).roundToInt(), boxTop.roundToInt()) }) {
        p.rows.forEach { row ->
            Row(Modifier.height(rowH), verticalAlignment = Alignment.CenterVertically) {
                row.forEachIndexed { k, w ->
                    if (k > 0) Spacer(Modifier.width(with(d) { (w.gapEm * wPx * 0.059f).toDp() }))
                    Text(w.text, color = if (w.red) LyricRed else Color.Black, fontFamily = ArialLike, fontSize = fs, lineHeight = ls,
                        maxLines = 1, softWrap = false, modifier = Modifier.alpha(if (pos >= w.at) 1f else 0f))
                }
            }
        }
    }
}

/** Full-screen lyric view for every song: pure white, square stage, word-by-word text and the two animated stickers. */
@Composable
fun LyricScene(st: PlayerState, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val song = st.current
    BackHandler { onClose() }
    val d = st.lyric?.takeIf { it.songId == song?.id }
    val phrases = remember(d, song?.id) { if (d != null && !d.empty) buildPhrases(d, song?.duration ?: 0) else emptyList() }
    val heart by produceState<ImageBitmap?>(null) { value = loadSprite(ctx, R.drawable.sk_heart) }
    val emoji by produceState<ImageBitmap?>(null) { value = loadSprite(ctx, R.drawable.sk_emoji) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val fade by animateFloatAsState(if (shown) 1f else 0f, tween(500), label = "stickers")
    val pos = st.pos.toLong()

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.White).plain { view.tap(HapticFeedbackConstants.LONG_PRESS); st.toggle() }) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val top = (hPx - wPx) / 2f

        Sticker(heart, 130, 165, HEART_DUR, fade,
            Modifier.offset { IntOffset((wPx * 0.1215f).roundToInt(), (top + wPx * 0.6337f).roundToInt()) }
                .size(with(density) { (wPx * 0.2257f).toDp() }, with(density) { (wPx * 0.2865f).toDp() }))
        Sticker(emoji, 188, 179, EMOJI_DUR, fade,
            Modifier.offset { IntOffset((wPx * 0.6736f).roundToInt(), (top + wPx * 0.599f).roundToInt()) }
                .size(with(density) { (wPx * 0.3264f).toDp() }, with(density) { (wPx * 0.3108f).toDp() }))

        val cur = phrases.indexOfLast { it.start <= pos }.let { if (it >= 0 && pos < phrases[it].end) phrases[it] else null }
        when {
            song == null -> {}
            song.live -> PhraseView(LPhrase(0, Long.MAX_VALUE, true, listOf(listOf(LWord("live radio", 0, false, 0f))), 0.504f, 0.521f), pos, wPx, top)
            d == null -> PhraseView(LPhrase(0, Long.MAX_VALUE, true, listOf(listOf(LWord("searching…", 0, false, 0f))), 0.504f, 0.521f), pos, wPx, top)
            d.empty -> {
                PhraseView(LPhrase(0, Long.MAX_VALUE, false, listOf(listOf(LWord("no", 0, false, 0f), LWord("lyrics", 0, false, 0.56f)), listOf(LWord("found", 0, false, 0f))), 0.285f, 0.404f), pos, wPx, top)
                Text("retry", color = LyricBlue, fontFamily = ScriptSerif, fontStyle = FontStyle.Italic, fontSize = with(density) { (wPx * 0.059f).toSp() },
                    modifier = Modifier.align(Alignment.TopCenter).offset { IntOffset(0, (top + wPx * 0.5f).roundToInt()) }.plain { st.retryLyrics(song) }.padding(12.dp))
            }
            else -> cur?.let { PhraseView(it, pos, wPx, top) }
        }

        Box(Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 30.dp).size(44.dp).bounce { view.tap(); onClose() }, contentAlignment = Alignment.Center) {
            Ic(IC_CLOSE, 22.dp, Color(0x55000000))
        }
    }
}
