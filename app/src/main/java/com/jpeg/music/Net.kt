package com.jpeg.music

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

object Net {
    private const val APP = "JPEG"

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 10000
        c.setRequestProperty("User-Agent", "JPEG-Music/1.0")
        c.instanceFollowRedirects = true
        return c
    }

    private fun get(url: String): String? = try {
        val c = open(url)
        if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null
    } catch (e: Exception) { null }

    // online ids are negative so they never clash with MediaStore ids
    private fun sid(prefix: String, raw: String): Long = -1L - abs((prefix + raw).hashCode().toLong())

    fun fetchImage(url: String): ImageBitmap? {
        if (url.isBlank()) return null
        return try {
            val c = open(url)
            val bytes = c.inputStream.use { it.readBytes() }
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            var sample = 1
            while (o.outWidth / sample > 800) sample *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o2)?.asImageBitmap()
        } catch (e: Exception) { null }
    }

    /** Audius: free, open music network. Empty query = trending. */
    fun music(q: String): List<Song> {
        for (host in listOf("https://api.audius.co", "https://discoveryprovider.audius.co")) {
            try {
                val path = if (q.isBlank()) "/v1/tracks/trending?limit=40&"
                else "/v1/tracks/search?query=${URLEncoder.encode(q, "UTF-8")}&limit=40&"
                val body = get("$host${path}app_name=$APP") ?: continue
                val arr = JSONObject(body).optJSONArray("data") ?: continue
                val out = ArrayList<Song>()
                for (i in 0 until arr.length()) {
                    val t = arr.getJSONObject(i)
                    val id = t.optString("id")
                    if (id.isBlank() || !t.optBoolean("is_streamable", true)) continue
                    val art = t.optJSONObject("artwork")?.optString("480x480") ?: ""
                    out.add(Song(sid("a:", id), t.optString("title", "Untitled"),
                        t.optJSONObject("user")?.optString("name") ?: "Audius", "Audius",
                        t.optInt("duration", 0) * 1000, Uri.parse("$host/v1/tracks/$id/stream?app_name=$APP"),
                        "", "", art, false, true, "STREAM · Audius"))
                }
                if (out.isNotEmpty()) return out
            } catch (e: Exception) { }
        }
        return emptyList()
    }

    /** Radio Browser: community directory of live radio stations. Empty query = most popular. */
    fun radio(q: String): List<Song> {
        for (host in listOf("https://de1.api.radio-browser.info", "https://nl1.api.radio-browser.info", "https://at1.api.radio-browser.info")) {
            try {
                val url = if (q.isBlank()) "$host/json/stations/topclick/40"
                else "$host/json/stations/search?name=${URLEncoder.encode(q, "UTF-8")}&limit=40&hidebroken=true&order=clickcount&reverse=true"
                val arr = JSONArray(get(url) ?: continue)
                val out = ArrayList<Song>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val u = o.optString("url_resolved")
                    if (u.isBlank() || o.optInt("lastcheckok", 1) != 1) continue
                    val name = o.optString("name").trim().ifBlank { "Radio" }
                    val sub = listOf(o.optString("country"), o.optString("tags").split(",").firstOrNull() ?: "")
                        .filter { it.isNotBlank() }.joinToString(" · ")
                    val br = o.optInt("bitrate", 0)
                    out.add(Song(sid("r:", o.optString("stationuuid", u)), name, if (sub.isBlank()) "Live radio" else sub, "", 0,
                        Uri.parse(u), "", "", o.optString("favicon"), true, true,
                        o.optString("codec").uppercase() + if (br > 0) " · $br kbps" else ""))
                }
                if (out.isNotEmpty()) return out
            } catch (e: Exception) { }
        }
        return emptyList()
    }

    private fun jstr(j: JSONObject, k: String): String = if (j.isNull(k)) "" else j.optString(k)

    /** LRCLIB community lyrics database. Returns (synced, plain, serverReached). */
    fun lyricsFor(s: Song): Triple<String, String, Boolean> {
        if (s.live) return Triple("", "", false)
        val dur = s.duration / 1000
        val artist = if (s.artist.startsWith("Unknown")) "" else s.artist
        fun enc(x: String) = URLEncoder.encode(x, "UTF-8")
        var reached = false
        if (artist.isNotBlank() && s.album.isNotBlank() && dur > 0) {
            try {
                val c = open("https://lrclib.net/api/get?track_name=${enc(s.title)}&artist_name=${enc(artist)}&album_name=${enc(s.album)}&duration=$dur")
                val code = c.responseCode
                if (code == 200) {
                    val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                    val sy = jstr(j, "syncedLyrics"); val pl = jstr(j, "plainLyrics")
                    if (sy.isNotBlank() || pl.isNotBlank()) return Triple(sy, pl, true)
                    reached = true
                } else if (code == 404) reached = true
            } catch (e: Exception) { }
        }
        try {
            val q = "https://lrclib.net/api/search?track_name=${enc(s.title)}" + (if (artist.isNotBlank()) "&artist_name=${enc(artist)}" else "")
            val c = open(q)
            if (c.responseCode == 200) {
                reached = true
                val arr = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
                var best: JSONObject? = null
                var bestScore = Int.MAX_VALUE
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val sy = jstr(o, "syncedLyrics"); val pl = jstr(o, "plainLyrics")
                    if (sy.isBlank() && pl.isBlank()) continue
                    val diff = if (dur > 0) abs(o.optInt("duration", 0) - dur) else 0
                    if (dur > 0 && diff > 8) continue
                    val score = diff + (if (sy.isBlank()) 20 else 0)
                    if (score < bestScore) { bestScore = score; best = o }
                }
                best?.let { return Triple(jstr(it, "syncedLyrics"), jstr(it, "plainLyrics"), true) }
            }
        } catch (e: Exception) { }
        return Triple("", "", reached)
    }
}
