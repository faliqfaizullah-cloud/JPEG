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

private val G = mapOf(
    '0' to "111101101101111", '1' to "010110010010111", '2' to "111001111100111",
    '3' to "111001111001111", '4' to "101101111001001", '5' to "111100111001111",
    '6' to "111100111101111", '7' to "111001001001001", '8' to "111101111101111",
    '9' to "111101111001111", ':' to "000010000010000", '.' to "000000000000010", ' ' to "000000000000000"
)

@Composable
fun DotText(text: String, dot: Float = 5f, color: Color = FG) {
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
fun Vinyl(dia: Dp, art: ImageBitmap?, angle: Float = 0f, sheen: Boolean = false, labelRatio: Float = 0.56f) {
    Canvas(Modifier.size(dia).graphicsLayer { rotationZ = angle }) {
        val c = center
        val r = this.size.minDimension / 2
        drawCircle(Bodies[Look.style], r, c)
        for (i in 1..16) drawCircle(Color(0x1FFFFFFF), r * (labelRatio + 0.03f + i * ((0.98f - labelRatio - 0.03f) / 16f)), c, style = Stroke(1f))
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
        val lr = r * labelRatio
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


// ───────────── Studio (speed, EQ, looks, sleep) ─────────────

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Floating(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, color = FG, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
fun Swatch(c: Color, selected: Boolean, onClick: () -> Unit) {
    val v = LocalView.current
    Box(Modifier.size(42.dp).clip(CircleShape).background(c).border(if (selected) 3.dp else 1.dp, if (selected) Color(0xFF8E8E93) else Color(0x22000000), CircleShape)
        .clickable { v.tap(); onClick() })
}

@Composable
fun StudioTab(st: PlayerState) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("Studio", color = FG, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Section("Glass style") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("DEFAULT", Look.glassStyle == 0) { st.updateGlassStyle(0) }
                Pill("FROSTED", Look.glassStyle == 1) { st.updateGlassStyle(1) }
                Pill("LIQUID", Look.glassStyle == 2) { st.updateGlassStyle(2) }
            }
            if (Look.glassStyle != 0) {
                Spacer(Modifier.height(8.dp))
                GlassSlider("Blur", Look.glassBlur, 0f..60f, "dp") { st.updateGlass(0, it) }
                GlassSlider("Tint", Look.glassTint * 100f, 0f..90f, "%") { st.updateGlass(1, it / 100f) }
                GlassSlider("Border", Look.glassBorder * 100f, 0f..100f, "%") { st.updateGlass(2, it / 100f) }
                GlassSlider("Shadow", Look.glassShadow * 100f, 0f..50f, "%") { st.updateGlass(3, it / 100f) }
                Text("Blur behind the bars needs Android 12 or newer. Older phones keep the tint, rim light and shadow.", color = SUB, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Pill("RESET THIS STYLE") { st.updateGlassStyle(Look.glassStyle) }
            }
        }
        Section("Appearance") {
            Text("Accent colour", color = FG, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Accents.forEach { c -> Swatch(c, Look.accent == c) { st.setAccent(c) } } }
            Spacer(Modifier.height(14.dp))
            Text("Vinyl colour", color = FG, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Bodies.forEachIndexed { i, c -> Swatch(c, Look.style == i) { st.setStyle(i) } } }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("GYRO TILT", Look.gyro) { st.updateGyro(!Look.gyro) }
                Pill("INVERT TILT", Look.gyroInvert) { st.updateGyroInvert(!Look.gyroInvert) }
            }

        }
        Section("Turntable speed") {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0x0F000000)).padding(vertical = 22.dp), horizontalArrangement = Arrangement.Center) {
                DotText(if (st.rpm < 40f) "33.3" else "45.0", 7f, Look.accent)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("33.3 RPM", st.rpm < 40f) { st.setSpeed(33.3f) }
                Pill("45 RPM", st.rpm >= 40f) { st.setSpeed(45f) }
            }
            Text("Changes speed and pitch, like a real turntable.", color = SUB, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Section("Playback") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("SHUFFLE", st.shuffle) { st.updateShuffle(!st.shuffle) }
                Pill("REPEAT ONE", st.repeat) { st.updateRepeat(!st.repeat) }
                Pill("RESCAN") { scope.launch { st.scan() } }
            }
            Spacer(Modifier.height(14.dp))
            Text("Crossfade", color = FG, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("OFF", st.crossfade == 0) { st.updateCrossfade(0) }
                Pill("3 SEC", st.crossfade == 3) { st.updateCrossfade(3) }
                Pill("6 SEC", st.crossfade == 6) { st.updateCrossfade(6) }
                Pill("10 SEC", st.crossfade == 10) { st.updateCrossfade(10) }
            }
            Text("Blends the end of each song into the next, including when you skip.", color = SUB, fontSize = 11.sp,
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
                Badge(Quality(true, true, "", 0, 0, 0)); Text("$hr files", color = FG, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Badge(Quality(true, false, "", 0, 0, 0)); Text("$ll files", color = FG, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            Pill("PURE MODE (NO EQ / SPEED FX)", st.pure) { st.updatePure(!st.pure) }
            val clean = st.pure || (st.rpm == 33.3f && st.bass == 0 && st.eqLevels.all { it == 0 })
            Text(if (clean) "Signal path: clean, no effects applied." else "Signal path: effects active (EQ, bass or speed).",
                color = if (clean) Look.accent else SUB, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            Text("Phone output mixer: $outRate Hz. Hi-Res files are decoded at full quality, but Android may resample them to this rate unless you use a USB DAC or a device with Hi-Res output.",
                color = SUB, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
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
                    Text(names.getOrElse(i) { "" }, color = FG, fontSize = 11.sp, fontFamily = FontFamily.Default, modifier = Modifier.width(64.dp))
                    Slider(lv.toFloat(), { st.setEq(i, it.roundToInt()) }, valueRange = st.eqMin.toFloat()..st.eqMax.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = Look.accent, activeTrackColor = Look.accent, inactiveTrackColor = Color(0x1F000000)))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BASS BOOST", color = FG, fontSize = 11.sp, fontFamily = FontFamily.Default, modifier = Modifier.width(64.dp))
                Slider(st.bass.toFloat(), { st.updateBass(it.roundToInt()) }, valueRange = 0f..1000f,
                    colors = SliderDefaults.colors(thumbColor = Look.accent, activeTrackColor = Look.accent, inactiveTrackColor = Color(0x1F000000)))
            }
            Text("Applies from the next song if no sound change is heard.", color = SUB, fontSize = 11.sp)
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
            Text("Discover", color = FG, fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 32.sp)
            Text(if (mode == 0) "Streaming from Audius" else "Live stations from Radio Browser", color = SUB, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("MUSIC", mode == 0) { mode = 0 }
                Pill("RADIO", mode == 1) { mode = 1 }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicTextField(query, { query = it }, singleLine = true,
                    textStyle = TextStyle(color = FG, fontSize = 15.sp), cursorBrush = SolidColor(Look.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { go++ }),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color(0x0F000000)).padding(12.dp),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) Text(if (mode == 0) "Search tracks or artists…" else "Search stations…", color = SUB, fontSize = 15.sp)
                            inner()
                        }
                    })
                Pill("GO", true) { go++ }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (busy) Text("Loading…", color = Look.accent, fontSize = 12.sp, fontFamily = FontFamily.Default, modifier = Modifier.padding(12.dp))
        else if (failed) Text("Nothing found, or no internet connection.", color = SUB, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(results, key = { _, s -> s.id }) { i, s ->
                SongRow(s, st.current?.id == s.id, null, { view.tap(HapticFeedbackConstants.LONG_PRESS); st.playQueue(results, i); openPlayer() }, null)
            }
        }
    }
}


@Composable
fun GlassSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = FG, fontSize = 12.sp, modifier = Modifier.width(56.dp))
        Slider(value, onChange, modifier = Modifier.weight(1f), valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Look.accent, activeTrackColor = Look.accent, inactiveTrackColor = Color(0x1F000000)))
        Text("${value.roundToInt()}$unit", color = SUB, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
    }
}
