package client.wexmanager.feature.search

import org.json.JSONArray
import java.io.File

/**
 * Последние запросы сессии: `filesDir/search-queries.json`.
 * Массив строк, не больше [CAP], новые впереди. Ошибка чтения не роняет поиск.
 */
internal object SessionQueries {
    const val FILE = "search-queries.json"
    const val CAP = 8

    fun load(filesDir: File): List<String> = runCatching {
        val file = File(filesDir, FILE)
        if (!file.isFile) return emptyList()
        decode(file.readText())
    }.getOrDefault(emptyList())

    fun save(filesDir: File, queries: List<String>) {
        val text = encode(queries)
        val file = File(filesDir, FILE)
        val tmp = File(filesDir, "$FILE.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    fun remember(current: List<String>, query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return current
        return (listOf(q) + current.filterNot { it == q }).take(CAP)
    }

    /** Уже набранное в этой сессии остаётся впереди, затем то, что было в файле. */
    fun mergeLoaded(current: List<String>, loaded: List<String>): List<String> =
        (current + loaded.filter { it !in current }).take(CAP)

    private fun decode(text: String): List<String> {
        val arr = JSONArray(text)
        val out = ArrayList<String>(minOf(arr.length(), CAP))
        for (i in 0 until arr.length()) {
            val s = arr.optString(i).trim()
            if (s.isNotEmpty() && s !in out) out += s
            if (out.size == CAP) break
        }
        return out
    }

    private fun encode(queries: List<String>): String {
        val arr = JSONArray()
        queries.asSequence().map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(CAP).forEach { arr.put(it) }
        return arr.toString()
    }
}
