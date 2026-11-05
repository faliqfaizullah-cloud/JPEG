package com.jpeg.music

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SDark = Color(0xFF0B0B0C)
private val SPanel = Color(0xFF1A1A1C)
private val SBubble = Color(0xFF2A2A2D)

@Composable
fun Field(value: String, onChange: (String) -> Unit, hint: String, secret: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth()) {
    BasicTextField(value, onChange, singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(Look.accent),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(SDark).padding(13.dp),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, color = Color.Gray, fontSize = 15.sp); inner() } })
}

@Composable
fun FriendsTab(social: Social, st: PlayerState) {
    var chat by remember { mutableStateOf<Friend?>(null) }
    BackHandler(enabled = chat != null) { chat = null }
    val open = chat
    when {
        !social.configured -> SetupCard(social)
        !social.signedIn -> LoginCard(social)
        open != null -> ChatScreen(social, st, open) { chat = null }
        else -> FriendList(social) { chat = it }
    }
}

@Composable
fun SetupCard(social: Social) {
    val ctx = LocalContext.current
    val view = LocalView.current
    var key by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Floating(Modifier.fillMaxWidth(), 30.dp) {
            Text("CONNECT FRIENDS", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
            Text("Friends and chat run on your own free Firebase project:\n" +
                "1. console.firebase.google.com → create a project\n" +
                "2. Build → Authentication → enable Email/Password\n" +
                "3. Build → Realtime Database → create it, then open Rules, paste the rules below and Publish\n" +
                "4. Copy the Web API Key (Project settings) and the Database URL",
                color = Color(0xFFBBBBBB), fontSize = 12.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(10.dp))
            Pill("COPY DATABASE RULES") {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("rules", RULES))
                err = "Rules copied. Paste them in Firebase → Realtime Database → Rules."
            }
            Spacer(Modifier.height(12.dp))
            Field(key, { key = it }, "Web API Key")
            Spacer(Modifier.height(8.dp))
            Field(url, { url = it }, "Database URL (https://…firebasedatabase.app)")
            if (err.isNotEmpty()) Text(err, color = Look.accent, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(12.dp))
            Pill("SAVE & CONNECT", true) {
                if (key.isBlank() || !url.trim().startsWith("https://")) err = "Enter the API key and a database URL starting with https://"
                else social.saveConfig(key.trim(), url.trim())
            }
        }
    }
}

@Composable
fun LoginCard(social: Social) {
    val scope = rememberCoroutineScope()
    var u by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun go(create: Boolean) {
        if (busy) return
        busy = true; err = ""
        scope.launch { err = social.authenticate(u, p, create) ?: ""; busy = false }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Floating(Modifier.fillMaxWidth(), 30.dp) {
            Text("FRIENDS", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("Pick a username to add friends and chat.", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Field(u, { u = it }, "Username")
            Spacer(Modifier.height(8.dp))
            Field(p, { p = it }, "Password (6+ characters)", secret = true)
            if (err.isNotEmpty()) Text(err, color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (busy) "…" else "SIGN IN", true) { go(false) }
                Pill(if (busy) "…" else "CREATE ACCOUNT") { go(true) }
            }
            Spacer(Modifier.height(10.dp))
            Pill("CHANGE BACKEND") { social.resetConfig() }
        }
    }
}

@Composable
fun FriendList(social: Social, onOpen: (Friend) -> Unit) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var sub by remember { mutableIntStateOf(0) }
    var target by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf<Friend?>(null) }
    LaunchedEffect(Unit) { while (true) { social.refreshAll(); delay(8000) } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Floating(Modifier.fillMaxWidth(), 30.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("@${social.me}", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("${social.friends.size} friends", color = Color.Gray, fontSize = 11.sp)
                    }
                    Pill("SIGN OUT") { social.signOut() }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("FRIENDS", sub == 0) { sub = 0 }
                    Pill("REQUESTS ${social.requests.size}", sub == 1) { sub = 1 }
                    Pill("ADD", sub == 2) { sub = 2 }
                }
            }
            Spacer(Modifier.height(8.dp))
            when (sub) {
                0 -> {
                    if (social.friends.isEmpty()) Text("No friends yet. Tap ADD and send a request.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(social.friends, key = { it.uid }) { f ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).shadow(6.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))
                                .background(SPanel.copy(alpha = 0.9f)).clickable { view.tap(); onOpen(f) }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(46.dp).clip(CircleShape).background(Look.accent), contentAlignment = Alignment.Center) {
                                    Text(f.name.take(1).uppercase(), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("@${f.name}", color = Color.White, fontSize = 15.sp)
                                    Text(when {
                                        f.online -> "♪ ${f.nowTitle} — ${f.nowArtist}"
                                        f.lastText.isNotEmpty() -> (if (f.lastFromMe) "You: " else "") + f.lastText
                                        else -> "Tap to chat"
                                    }, color = if (f.online) Look.accent else Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (social.unread(f)) Box(Modifier.size(10.dp).clip(CircleShape).background(Look.accent))
                                Box(Modifier.size(40.dp).clip(CircleShape).clickable { menu = f }, contentAlignment = Alignment.Center) {
                                    Text("•••", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (social.requests.isEmpty()) Text("No pending requests.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(social.requests, key = { it.uid }) { r ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(24.dp)).background(SPanel.copy(alpha = 0.9f)).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("@${r.name}", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                Pill("ACCEPT", true) { scope.launch { social.accept(r); social.refreshAll() } }
                                Spacer(Modifier.width(6.dp))
                                Pill("✕") { scope.launch { social.decline(r); social.refreshAll() } }
                            }
                        }
                    }
                }
                else -> Floating(Modifier.fillMaxWidth()) {
                    Text("Add a friend by username", color = Color.White, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Field(target, { target = it }, "@username")
                    Spacer(Modifier.height(10.dp))
                    Pill("SEND REQUEST", true) { scope.launch { info = social.sendRequest(target); if (info.startsWith("Request sent")) target = "" } }
                    if (info.isNotEmpty()) Text(info, color = Look.accent, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        menu?.let { f ->
            FloatingMenu("@${f.name}", listOf("Remove friend" to { scope.launch { social.removeFriend(f); social.refreshAll() }; Unit })) { menu = null }
        }
    }
}

@Composable
fun ChatScreen(social: Social, st: PlayerState, f: Friend, onBack: () -> Unit) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var msgs by remember { mutableStateOf(listOf<Msg>()) }
    var text by remember { mutableStateOf("") }
    var lastCount by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(f.uid) {
        while (true) {
            val m = social.messages(f.uid)
            if (m != null) {
                if (lastCount != 0 && m.size > lastCount && m.last().mine.not()) view.tap(HapticFeedbackConstants.LONG_PRESS)
                if (m.size != msgs.size || m.lastOrNull()?.id != msgs.lastOrNull()?.id) msgs = m
                lastCount = m.size
                m.lastOrNull()?.let { social.markSeen(f.uid, it.ts) }
            }
            delay(2000)
        }
    }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Floating(Modifier.fillMaxWidth(), 30.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("◀ BACK") { onBack() }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("@${f.name}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (f.online) Text("♪ ${f.nowTitle}", color = Look.accent, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), state = listState) {
            items(msgs, key = { it.id }) { m ->
                Box(Modifier.fillMaxWidth().padding(vertical = 3.dp), contentAlignment = if (m.mine) Alignment.CenterEnd else Alignment.CenterStart) {
                    Text(m.text, color = if (m.mine) Color.Black else Color.White, fontSize = 14.sp,
                        modifier = Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(20.dp))
                            .background(if (m.mine) Look.accent else SBubble).padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
        }
        Floating(Modifier.fillMaxWidth(), 30.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(text, { text = it }, "Message…", modifier = Modifier.weight(1f))
                Pill("♪") { st.current?.let { text = "♪ ${it.title} — ${it.artist}" } }
                Pill("SEND", true) {
                    val t = text
                    if (t.isNotBlank()) {
                        text = ""
                        scope.launch { social.send(f.uid, t); social.messages(f.uid)?.let { msgs = it } }
                    }
                }
            }
        }
    }
}
