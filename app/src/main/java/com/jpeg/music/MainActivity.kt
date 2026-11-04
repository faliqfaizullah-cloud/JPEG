package com.jpeg.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContent { App() }
    }
}

private val Dark = Color(0xFF0B0B0C)
private val Panel = Color(0xFF1A1A1C)
private val Light = Color(0xFFE9ECEA)

// 3x5 dot-matrix glyphs
private val G = mapOf(
    '0' to "111101101101111", '1' to "010110010010111", '2' to "111001111100111",
    '3' to "111001111001111", '4' to "101101111001001", '5' to "111100111001111",
    '6' to "111100111101111", '7' to "111001001001001", '8' to "111101111101111",
    '9' to "111101111001111", ':' to "000010000010000", '.' to "000000000000010", ' ' to "000000000000000"
)

@Composable
fun DotText(text: String, dot: Float = 5f, color: Color = Color.White) {
    val w = text.sumOf { if (it == ':' || it == '.') 2 else 4 }
    Canvas(Modifier.size((w * dot * 2.2f).dp.coerceAtLeast(1.dp), (5 * dot * 2.2f).dp)) {
        val step = size.height / 5f
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
fun Vinyl(size: Int, label: Color, prism: Boolean = false, body: Color = Color(0xFF0E0E0E)) {
    Canvas(Modifier.size(size.dp)) {
        val c = center; val r = this.size.minDimension / 2
        drawCircle(body, r, c)
        for (i in 1..14) drawCircle(Color(0x22FFFFFF), r * (0.4f + i * 0.04f), c, style = Stroke(1f))
        drawCircle(label, r * 0.38f, c)
        drawCircle(Color(0xFFE9ECEA), r * 0.035f, c)
        if (prism) {
            val p = Path().apply { moveTo(c.x - r * .2f, c.y + r * .3f); lineTo(c.x, c.y - r * .1f); lineTo(c.x + r * .2f, c.y + r * .3f); close() }
            drawPath(p, Color.White, style = Stroke(3f))
            listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Magenta).forEachIndexed { i, col ->
                drawRect(col, Offset(c.x + r * .1f, c.y + r * .05f + i * 6f), Size(r * .8f, 6f))
            }
        }
    }
}

@Composable
fun App() {
    var screen by remember { mutableStateOf(0) }
    var secs by remember { mutableStateOf(288) }
    var playing by remember { mutableStateOf(true) }
    LaunchedEffect(playing) { while (playing) { delay(1000); secs++ } }
    Box(Modifier.fillMaxSize().background(Dark).statusBarsPadding()) {
        if (screen == 0) Player(secs, playing, { playing = !playing }) { screen = 1; playing = false }
        else Rpm { screen = 0; playing = true }
    }
}

@Composable
fun Player(secs: Int, playing: Boolean, toggle: () -> Unit, stop: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Pill(if (playing) "II" else "▶", toggle)
            DotText("%02d:%02d".format(secs / 60, secs % 60), 5f)
            Pill("Stop", stop)
        }
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)).background(Light)) {
            Text("SPIN\nNOTES", Modifier.align(Alignment.TopCenter).padding(top = 24.dp), color = Color(0xFF111111),
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 60.sp)
            Column(Modifier.align(Alignment.BottomCenter).offset(y = 90.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Vinyl(300, Color(0xFF7CC242))
                Box(Modifier.offset(y = (-60).dp)) { Vinyl(300, Color(0xFFE5E5E5), body = Color(0xFFD0382A)) }
                Box(Modifier.offset(y = (-120).dp)) { Vinyl(300, Color.Black, prism = true) }
            }
        }
    }
}

@Composable
fun Pill(t: String, onClick: () -> Unit) =
    Box(Modifier.clip(RoundedCornerShape(50)).background(Panel).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(t, color = Color.White, fontSize = 12.sp)
    }

@Composable
fun Rpm(next: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("RPM", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Panel).padding(vertical = 28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly) { DotText("33", 7f); DotText(".3", 7f) }
        Spacer(Modifier.height(20.dp))
        Text("Recently played", color = Color.White, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Vinyl(44, Color(0xFF7CC242)); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Blonde on Blonde", color = Color.White, fontSize = 14.sp)
                Text("19:42 · YESTERDAY · SIDE B", color = Color.Gray, fontSize = 10.sp)
            }
            Text("›", color = Color.Gray, fontSize = 20.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Tile("12  ····· LP", "Format"); Tile("NM", "Condition") }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Tile("1.8", "Tracking"); Tile("1.5", "Anti-skate", true) }
        Spacer(Modifier.weight(1f))
        Box(Modifier.clip(RoundedCornerShape(50)).background(Panel).clickable { next() }.padding(horizontal = 28.dp, vertical = 16.dp)) {
            Text("Next", color = Color.White, fontSize = 13.sp)
        }
    }
}

@Composable
fun RowScope.Tile(v: String, label: String, light: Boolean = false) =
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(50.dp)).background(if (light) Light else Panel), contentAlignment = Alignment.Center) {
            Text(v, color = if (light) Color.Black else Color.White, fontSize = 22.sp, fontFamily = FontFamily.Monospace)
        }
        Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }
