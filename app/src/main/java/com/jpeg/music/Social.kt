package com.jpeg.music

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Optional: fill these in before building if you want every copy of the app to connect automatically.
const val DEFAULT_API_KEY = ""
const val DEFAULT_DB_URL = ""

val RULES = """{"rules":{
"users":{".read":"auth != null","§uid":{".write":"auth != null && auth.uid == §uid"}},
"usernames":{".read":"auth != null","§n":{".write":"auth != null && !data.exists()"}},
"requests":{"§uid":{".read":"auth != null && auth.uid == §uid","§from":{".write":"auth != null && (auth.uid == §from || auth.uid == §uid)"}}},
"friends":{"§uid":{".read":"auth != null && auth.uid == §uid","§f":{".write":"auth != null && (auth.uid == §uid || auth.uid == §f)"}}},
"chats":{"§c":{".read":"auth != null && §c.contains(auth.uid)",".write":"auth != null && §c.contains(auth.uid)"}}
}}""".replace('§', '$')

data class Friend(val uid: String, val name: String, val nowTitle: String = "", val nowArtist: String = "",
                  val online: Boolean = false, val lastTs: Long = 0, val lastFromMe: Boolean = false, val lastText: String = "")
data class Req(val uid: String, val name: String)
data class Msg(val id: String, val mine: Boolean, val text: String, val ts: Long)

object SocialHub {
    private var s: Social? = null
    fun get(c: Context): Social = s ?: Social(c.applicationContext).also { s = it }
}

/** Accounts, friends and chat on top of a Firebase project (Auth + Realtime Database) via REST. */
class Social(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("jpeg_social", Context.MODE_PRIVATE)
    var apiKey by mutableStateOf(prefs.getString("api", null) ?: DEFAULT_API_KEY)
    var dbUrl by mutableStateOf(prefs.getString("db", null) ?: DEFAULT_DB_URL)
    var me by mutableStateOf(prefs.getString("name", "") ?: "")
    var friends by mutableStateOf(listOf<Friend>())
    var requests by mutableStateOf(listOf<Req>())
    var seenTick by mutableStateOf(0)

    private var uid = prefs.getString("uid", "") ?: ""
    private var refresh = prefs.getString("refresh", "") ?: ""
    private var idToken = ""
    private var tokenExp = 0L

    val configured: Boolean get() = apiKey.isNotBlank() && dbUrl.isNotBlank()
    val signedIn: Boolean get() = me.isNotBlank()

    fun saveConfig(api: String, db: String) {
        apiKey = api; dbUrl = db.trim().trimEnd('/')
        prefs.edit().putString("api", apiKey).putString("db", dbUrl).apply()
    }
    fun resetConfig() { saveConfig("", ""); signOut() }
    fun signOut() {
        uid = ""; refresh = ""; idToken = ""; tokenExp = 0L
        prefs.edit().remove("uid").remove("refresh").remove("name").apply()
        me = ""; friends = emptyList(); requests = emptyList()
    }

    // ── http
    private fun http(method: String, url: String, body: String? = null, form: Boolean = false): Pair<Int, String> {
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 15000
            c.requestMethod = method
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", if (form) "application/x-www-form-urlencoded" else "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val txt = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            code to txt
        } catch (e: Exception) { -1 to (e.message ?: "Network error") }
    }

    @Synchronized
    private fun token(): String? {
        if (idToken.isNotEmpty() && System.currentTimeMillis() < tokenExp - 60_000) return idToken
        if (refresh.isEmpty()) return null
        return try {
            val (code, txt) = http("POST", "https://securetoken.googleapis.com/v1/token?key=$apiKey",
                "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refresh, "UTF-8"), true)
            if (code != 200) return null
            val j = JSONObject(txt)
            idToken = j.optString("id_token")
            refresh = j.optString("refresh_token", refresh)
            tokenExp = System.currentTimeMillis() + j.optString("expires_in", "3600").toLong() * 1000
            prefs.edit().putString("refresh", refresh).apply()
            idToken
        } catch (e: Exception) { null }
    }

    private fun db(method: String, path: String, body: String? = null, q: String = ""): Pair<Int, String> {
        val t = token() ?: return 401 to "No session"
        return http(method, "${dbUrl.trim().trimEnd('/')}/$path.json?auth=$t$q", body)
    }

    private fun friendlyErr(txt: String): String {
        val m = try { JSONObject(txt).optJSONObject("error")?.optString("message") ?: txt } catch (e: Exception) { txt }
        return when {
            m.contains("EMAIL_EXISTS") -> "That username is taken"
            m.contains("INVALID_LOGIN") || m.contains("EMAIL_NOT_FOUND") || m.contains("INVALID_PASSWORD") -> "Wrong username or password"
            m.contains("OPERATION_NOT_ALLOWED") -> "Turn on Email/Password in Firebase Authentication"
            m.contains("API key") || m.contains("API_KEY") -> "Invalid API key"
            m.contains("TOO_MANY") -> "Too many attempts, try later"
            else -> m.take(90)
        }
    }

    // ── account
    suspend fun authenticate(name: String, pass: String, create: Boolean): String? = withContext(Dispatchers.IO) {
        val lower = name.trim().lowercase().removePrefix("@")
        if (!Regex("[a-z0-9_]{3,16}").matches(lower)) return@withContext "Username: 3-16 letters, numbers or _"
        if (pass.length < 6) return@withContext "Password needs at least 6 characters"
        val endpoint = if (create) "signUp" else "signInWithPassword"
        val body = JSONObject().put("email", "$lower@jpeg.music.app").put("password", pass).put("returnSecureToken", true).toString()
        val (code, txt) = http("POST", "https://identitytoolkit.googleapis.com/v1/accounts:$endpoint?key=${apiKey.trim()}", body)
        if (code != 200) return@withContext friendlyErr(txt)
        try {
            val j = JSONObject(txt)
            uid = j.getString("localId"); idToken = j.getString("idToken"); refresh = j.getString("refreshToken")
            tokenExp = System.currentTimeMillis() + j.optString("expiresIn", "3600").toLong() * 1000
        } catch (e: Exception) { return@withContext "Unexpected reply from server" }
        if (create) {
            db("PUT", "users/$uid", JSONObject().put("name", lower).toString())
            db("PUT", "usernames/$lower", JSONObject.quote(uid))
        }
        prefs.edit().putString("uid", uid).putString("refresh", refresh).putString("name", lower).apply()
        me = lower
        null
    }

    // ── friends
    private fun findUid(lower: String): String? {
        val (c, t) = db("GET", "usernames/$lower")
        return if (c == 200 && t != "null") t.trim().trim('"') else null
    }

    suspend fun sendRequest(name: String): String = withContext(Dispatchers.IO) {
        val lower = name.trim().lowercase().removePrefix("@")
        if (lower.isEmpty()) return@withContext "Type a username"
        if (lower == me) return@withContext "That's you!"
        val target = findUid(lower) ?: return@withContext "No user named @$lower (or database rules not set)"
        if (friends.any { it.uid == target }) return@withContext "Already friends"
        val (c, _) = db("PUT", "requests/$target/$uid", JSONObject().put("name", me).put("ts", System.currentTimeMillis()).toString())
        if (c == 200) "Request sent to @$lower" else "Couldn't send the request"
    }

    suspend fun accept(r: Req): Unit = withContext(Dispatchers.IO) {
        db("PUT", "friends/$uid/${r.uid}", JSONObject().put("name", r.name).toString())
        db("PUT", "friends/${r.uid}/$uid", JSONObject().put("name", me).toString())
        db("DELETE", "requests/$uid/${r.uid}")
        Unit
    }
    suspend fun decline(r: Req): Unit = withContext(Dispatchers.IO) { db("DELETE", "requests/$uid/${r.uid}"); Unit }
    suspend fun removeFriend(f: Friend): Unit = withContext(Dispatchers.IO) {
        db("DELETE", "friends/$uid/${f.uid}"); db("DELETE", "friends/${f.uid}/$uid"); Unit
    }

    private fun chatId(f: String) = listOf(uid, f).sorted().joinToString("_")
    fun seen(f: String): Long = prefs.getLong("seen_${chatId(f)}", 0L)
    fun markSeen(f: String, ts: Long) { if (ts > seen(f)) { prefs.edit().putLong("seen_${chatId(f)}", ts).apply(); seenTick++ } }
    fun unread(f: Friend): Boolean { seenTick; return f.lastTs > seen(f.uid) && !f.lastFromMe }

    private fun loadFriend(fuid: String, name: String): Friend {
        var t = ""; var a = ""; var on = false
        val (c, txt) = db("GET", "users/$fuid/now")
        if (c == 200 && txt != "null") {
            try {
                val j = JSONObject(txt)
                t = j.optString("t"); a = j.optString("a")
                on = j.optBoolean("p") && System.currentTimeMillis() - j.optLong("ts") < 15 * 60_000
            } catch (e: Exception) { }
        }
        var lts = 0L; var lmine = false; var ltxt = ""
        val (c2, t2) = db("GET", "chats/${chatId(fuid)}", null, "&orderBy=%22%24key%22&limitToLast=1")
        if (c2 == 200 && t2 != "null") {
            try {
                val j = JSONObject(t2)
                val m = j.getJSONObject(j.keys().next())
                lts = m.optLong("ts"); lmine = m.optString("from") == uid; ltxt = m.optString("text")
            } catch (e: Exception) { }
        }
        return Friend(fuid, name, t, a, on, lts, lmine, ltxt)
    }

    suspend fun refreshAll(): Unit = withContext(Dispatchers.IO) {
        if (!signedIn) return@withContext
        val (c1, t1) = db("GET", "requests/$uid")
        if (c1 == 200) {
            val rq = ArrayList<Req>()
            if (t1 != "null") { val j = JSONObject(t1); for (k in j.keys()) rq.add(Req(k, j.optJSONObject(k)?.optString("name") ?: k)) }
            requests = rq
        }
        val (c2, t2) = db("GET", "friends/$uid")
        if (c2 == 200) {
            val out = ArrayList<Friend>()
            if (t2 != "null") { val j = JSONObject(t2); for (k in j.keys()) out.add(loadFriend(k, j.optJSONObject(k)?.optString("name") ?: k)) }
            friends = out.sortedBy { it.name }
        }
        Unit
    }

    // ── chat
    suspend fun messages(fuid: String): List<Msg>? = withContext(Dispatchers.IO) {
        val (c, t) = db("GET", "chats/${chatId(fuid)}", null, "&orderBy=%22%24key%22&limitToLast=80")
        if (c != 200) return@withContext null
        if (t == "null") return@withContext emptyList<Msg>()
        try {
            val j = JSONObject(t)
            val out = ArrayList<Msg>()
            for (k in j.keys()) { val m = j.getJSONObject(k); out.add(Msg(k, m.optString("from") == uid, m.optString("text"), m.optLong("ts"))) }
            out.sortedBy { it.ts }
        } catch (e: Exception) { null }
    }

    suspend fun send(fuid: String, text: String): Boolean = withContext(Dispatchers.IO) {
        val t = text.trim().take(500)
        if (t.isEmpty()) return@withContext false
        db("POST", "chats/${chatId(fuid)}", JSONObject().put("from", uid).put("text", t).put("ts", System.currentTimeMillis()).toString()).first == 200
    }

    suspend fun setNow(title: String, artist: String, playing: Boolean): Unit = withContext(Dispatchers.IO) {
        if (!signedIn) return@withContext
        db("PUT", "users/$uid/now", JSONObject().put("t", title).put("a", artist).put("p", playing).put("ts", System.currentTimeMillis()).toString())
        Unit
    }
}
