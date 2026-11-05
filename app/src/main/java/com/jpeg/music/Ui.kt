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


// ───────────── Palette & helpers ─────────────

val Bg = Color(0xFFF2F2F4)
val FG = Color(0xFF111113)
val SUB = Color(0xFF8E8E93)
val FAINT = Color(0xFFC4C4C9)
val Ink = FG

fun onAcc(c: Color): Color = if (c.luminance() > 0.45f) FG else Color.White

fun View.tap(kind: Int = HapticFeedbackConstants.KEYBOARD_TAP) { performHapticFeedback(kind) }
fun fmt(ms: Int) = "%02d:%02d".format(ms / 60000, (ms / 1000) % 60)

@Composable
fun Modifier.plain(onClick: () -> Unit): Modifier =
    this.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)

/** Springy press: shrinks while pressed and bounces back. Put it BEFORE background/clip so the whole control scales. */
@Composable
fun Modifier.bounce(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.93f else 1f, spring(0.42f, 650f), label = "press")
    return this.graphicsLayer { scaleX = sc; scaleY = sc }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

/** Frosted white glass: translucent white, bright rim and a soft shadow. */
fun Modifier.glass(radius: Dp = 28.dp, alpha: Float = 0.74f): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.shadow(12.dp, shape, false, Color(0x14000000), Color(0x26000000))
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = minOf(1f, alpha + 0.14f)), Color.White.copy(alpha = alpha - 0.06f))))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White, Color(0x66FFFFFF))), shape)
}
fun Modifier.frost(radius: Dp = 28.dp): Modifier = glass(radius)

@Composable
fun Floating(modifier: Modifier = Modifier, radius: Dp = 28.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.glass(radius, 0.8f).padding(16.dp), content = content)
}

@Composable
fun AppBackground() {
    Box(Modifier.fillMaxSize().background(Bg)) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x38FF8FB1), Color.Transparent), center = Offset(1000f, 1500f), radius = 1000f)))
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x2E7EE08F), Color.Transparent), center = Offset(60f, 2300f), radius = 900f)))
    }
}

// ───────────── Line icons ─────────────

const val IC_GRID = 0
const val IC_RADIO = 1
const val IC_PROFILE = 2
const val IC_SEARCH = 3
const val IC_PLAY = 4
const val IC_PAUSE = 5
const val IC_CLOSE = 6
const val IC_STACK = 7
const val IC_HEART = 8
const val IC_HEARTO = 9
const val IC_DOTS = 10
const val IC_BACK = 11

fun DrawScope.ico(kind: Int, c: Color, u: Float) {
    val sw = u * 0.085f
    val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (kind) {
        IC_GRID -> {
            for (i in 0..1) for (j in 0..1)
                drawRoundRect(c, Offset((0.2f + i * 0.31f) * u, (0.2f + j * 0.31f) * u), Size(0.29f * u, 0.29f * u), CornerRadius(0.07f * u))
        }
        IC_RADIO -> {
            drawCircle(c, 0.07f * u, Offset(0.5f * u, 0.5f * u))
            for (r in listOf(0.2f, 0.34f)) {
                drawArc(c, -40f, 80f, false, Offset((0.5f - r) * u, (0.5f - r) * u), Size(2 * r * u, 2 * r * u), style = st)
                drawArc(c, 140f, 80f, false, Offset((0.5f - r) * u, (0.5f - r) * u), Size(2 * r * u, 2 * r * u), style = st)
            }
        }
        IC_PROFILE -> {
            drawCircle(c, 0.38f * u, Offset(0.5f * u, 0.5f * u), style = st)
            drawCircle(c, 0.1f * u, Offset(0.5f * u, 0.42f * u))
            drawArc(c, 180f, 180f, true, Offset(0.3f * u, 0.54f * u), Size(0.4f * u, 0.36f * u))
        }
        IC_SEARCH -> {
            drawCircle(c, 0.26f * u, Offset(0.44f * u, 0.44f * u), style = st)
            drawLine(c, Offset(0.64f * u, 0.64f * u), Offset(0.84f * u, 0.84f * u), strokeWidth = sw * 1.2f, cap = StrokeCap.Round)
        }
        IC_PLAY -> {
            val p = Path(); p.moveTo(0.3f * u, 0.2f * u); p.lineTo(0.8f * u, 0.5f * u); p.lineTo(0.3f * u, 0.8f * u); p.close()
            drawPath(p, c)
        }
        IC_PAUSE -> {
            drawRoundRect(c, Offset(0.26f * u, 0.2f * u), Size(0.16f * u, 0.6f * u), CornerRadius(0.03f * u))
            drawRoundRect(c, Offset(0.58f * u, 0.2f * u), Size(0.16f * u, 0.6f * u), CornerRadius(0.03f * u))
        }
        IC_CLOSE -> {
            drawLine(c, Offset(0.28f * u, 0.28f * u), Offset(0.72f * u, 0.72f * u), strokeWidth = sw * 1.15f, cap = StrokeCap.Round)
            drawLine(c, Offset(0.72f * u, 0.28f * u), Offset(0.28f * u, 0.72f * u), strokeWidth = sw * 1.15f, cap = StrokeCap.Round)
        }
        IC_STACK -> {
            val p = Path()
            p.moveTo(0.5f * u, 0.1f * u); p.lineTo(0.86f * u, 0.3f * u); p.lineTo(0.86f * u, 0.7f * u)
            p.lineTo(0.5f * u, 0.9f * u); p.lineTo(0.14f * u, 0.7f * u); p.lineTo(0.14f * u, 0.3f * u); p.close()
            drawPath(p, c, style = st)
            drawLine(c, Offset(0.5f * u, 0.5f * u), Offset(0.14f * u, 0.3f * u), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, Offset(0.5f * u, 0.5f * u), Offset(0.86f * u, 0.3f * u), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, Offset(0.5f * u, 0.5f * u), Offset(0.5f * u, 0.9f * u), strokeWidth = sw, cap = StrokeCap.Round)
        }
        IC_HEART, IC_HEARTO -> {
            val p = Path()
            p.moveTo(0.5f * u, 0.8f * u)
            p.cubicTo(0.1f * u, 0.55f * u, 0.22f * u, 0.2f * u, 0.5f * u, 0.36f * u)
            p.cubicTo(0.78f * u, 0.2f * u, 0.9f * u, 0.55f * u, 0.5f * u, 0.8f * u)
            p.close()
            if (kind == IC_HEART) drawPath(p, c) else drawPath(p, c, style = st)
        }
        IC_DOTS -> {
            for (x in listOf(0.25f, 0.5f, 0.75f)) drawCircle(c, 0.06f * u, Offset(x * u, 0.5f * u))
        }
        else -> {
            val p = Path(); p.moveTo(0.62f * u, 0.22f * u); p.lineTo(0.36f * u, 0.5f * u); p.lineTo(0.62f * u, 0.78f * u)
            drawPath(p, c, style = Stroke(width = sw * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun Ic(kind: Int, sz: Dp = 24.dp, c: Color = FG) {
    Canvas(Modifier.size(sz)) { ico(kind, c, this.size.minDimension) }
}

/** The little green record crate. */
@Composable
fun Crate(sz: Dp = 36.dp) {
    Canvas(Modifier.size(sz)) {
        val u = this.size.minDimension
        drawRoundRect(Color(0xFF1B1B1D), Offset(0.12f * u, 0.08f * u), Size(0.76f * u, 0.36f * u), CornerRadius(0.05f * u))
        val cols = listOf(Color(0xFFEDEDED), Color(0xFF9A9A9A), Color(0xFFEDEDED), Color(0xFF6B6B6B), Color(0xFFEDEDED), Color(0xFF9A9A9A))
        cols.forEachIndexed { i, cl -> drawRect(cl, Offset((0.17f + i * 0.115f) * u, 0.12f * u), Size(0.07f * u, 0.26f * u)) }
        drawRoundRect(Color(0xFF86E048), Offset(0.06f * u, 0.34f * u), Size(0.88f * u, 0.58f * u), CornerRadius(0.08f * u))
        for (r in 0..1) for (k in 0..2)
            drawRoundRect(Color(0xFF2E7D1E), Offset((0.14f + k * 0.27f) * u, (0.44f + r * 0.22f) * u), Size(0.2f * u, 0.14f * u), CornerRadius(0.03f * u))
    }
}

/** Album cover, or a black sleeve with a grey label when there is no artwork. */
@Composable
fun Cover(art: ImageBitmap?, modifier: Modifier = Modifier) {
    if (art != null) Image(art, null, modifier, contentScale = ContentScale.Crop)
    else Canvas(modifier) {
        drawRect(Color(0xFF151517))
        drawCircle(Color(0xFFD9D9DC), this.size.minDimension * 0.2f, center)
        drawCircle(Color(0xFF151517), this.size.minDimension * 0.025f, center)
    }
}

@Composable
fun Pill(t: String, selected: Boolean = false, onClick: () -> Unit) {
    val v = LocalView.current
    val r by animateDpAsState(if (selected) 22.dp else 13.dp, spring(0.5f, 500f), label = "shape")
    Box(Modifier.bounce { v.tap(); onClick() }.clip(RoundedCornerShape(r))
        .background(if (selected) Look.accent else Color(0x12000000)).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(t, color = if (selected) onAcc(Look.accent) else FG, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
    }
}

@Composable
fun Badge(q: Quality) {
    val t = q.tag()
    if (t.isEmpty()) return
    val bg = if (q.hires) Color(0xFFFFC857) else Look.accent
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(t, color = if (q.hires) FG else onAcc(Look.accent), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SongRow(s: Song, active: Boolean, q: Quality?, onPlay: () -> Unit, onMore: (() -> Unit)?) {
    val a = rememberArt(s)
    val sh = RoundedCornerShape(4.dp)
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(16.dp))
        .background(if (active) Color(0x0D000000) else Color.Transparent).clickable { onPlay() }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Cover(a, Modifier.size(52.dp).shadow(5.dp, sh, false, Color(0x22000000), Color(0x33000000)).clip(sh))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, color = FG, fontSize = 15.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (q != null && q.tag().isNotEmpty()) { Badge(q); Spacer(Modifier.width(6.dp)) }
                Text(s.artist + " · " + (if (s.live) "LIVE" else fmt(s.duration)), color = SUB, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onMore != null) Box(Modifier.size(40.dp).bounce { onMore() }.clip(CircleShape), contentAlignment = Alignment.Center) { Ic(IC_DOTS, 20.dp, SUB) }
    }
}

@Composable
fun FloatingMenu(title: String, items: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x66000000)).plain { onDismiss() }, contentAlignment = Alignment.BottomCenter) {
        Floating(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 110.dp).fillMaxWidth().plain { }) {
            Text(title, color = SUB, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val v = LocalView.current
            items.forEach { (label, act) ->
                Text(label, color = FG, fontSize = 16.sp, modifier = Modifier.fillMaxWidth()
                    .clickable { v.tap(HapticFeedbackConstants.LONG_PRESS); act(); onDismiss() }.padding(vertical = 13.dp))
            }
        }
    }
}

@Composable
fun NameDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Color(0x66000000)).imePadding().plain { onDismiss() }, contentAlignment = Alignment.Center) {
        Floating(Modifier.padding(24.dp).fillMaxWidth().plain { }) {
            Text("New playlist", color = FG, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            BasicTextField(text, { text = it.take(24).filter { c -> c != '\t' && c != '\n' } },
                textStyle = TextStyle(color = FG, fontSize = 18.sp), cursorBrush = SolidColor(FG), singleLine = true,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x0F000000)).padding(14.dp))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Pill("CANCEL") { onDismiss() }; Pill("CREATE", true) { onCreate(text); onDismiss() } }
        }
    }
}
