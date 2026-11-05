package com.jpeg.music

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class LyricLine(val ms: Long, val text: String)
data class LyricData(val songId: Long, val synced: List<LyricLine>, val plain: String) {
    val empty: Boolean get() = synced.isEmpty() && plain.isBlank()
}

object Lyrics {
    private val cache = HashMap<Long, LyricData>()
    private val ts = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

    fun parse(raw: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        for (line in raw.lines()) {
            val stamps = ts.findAll(line).toList()
            if (stamps.isEmpty()) continue
            val text = line.substring(stamps.last().range.last + 1).trim()
            for (m in stamps) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
                out.add(LyricLine(min * 60000 + sec * 1000 + ms, text))
            }
        }
        return out.sortedBy { it.ms }
    }

    private fun file(ctx: Context, id: Long) = File(ctx.filesDir, "lyr_$id.json")

    fun forget(ctx: Context, id: Long) { cache.remove(id); try { file(ctx, id).delete() } catch (e: Exception) { } }

    suspend fun load(ctx: Context, s: Song): LyricData = withContext(Dispatchers.IO) {
        cache[s.id]?.let { return@withContext it }
        val f = file(ctx, s.id)
        if (f.exists()) {
            try {
                val j = JSONObject(f.readText())
                val d = LyricData(s.id, parse(j.optString("s")), j.optString("p"))
                cache[s.id] = d
                return@withContext d
            } catch (e: Exception) { }
        }
        val (synced, plain, reached) = Net.lyricsFor(s)
        val d = LyricData(s.id, parse(synced), plain)
        if (reached) {
            cache[s.id] = d
            try { f.writeText(JSONObject().put("s", synced).put("p", plain).toString()) } catch (e: Exception) { }
        }
        d
    }
}
