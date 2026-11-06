package client.wexmanager.debug

import client.wexmanager.feature.search.QueryMatcher
import client.wexmanager.feature.search.Ripgrep
import client.wexmanager.feature.search.SearchEngine
import client.wexmanager.feature.search.SearchParams
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Замер поиска по содержимому: ripgrep против встроенного обхода, одним и тем же SearchEngine.
 * `app_process / client.wexmanager.debug.SearchBench <папка> <запрос> <путь к librg.so>`
 */
object SearchBench {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val (root, query, rg) = args
        val params = SearchParams(root = root, query = query, inContent = true)
        val matcher = QueryMatcher.create(query, regex = false, caseSensitive = false)
        for (engine in listOf("ripgrep", "встроенный", "ripgrep", "встроенный")) {
            Ripgrep.useBinary(if (engine == "ripgrep") File(rg) else null)
            var hits = 0
            var lines = 0
            val t0 = System.nanoTime()
            val (scanned, limit) = SearchEngine.run(params, matcher) { batch, _ ->
                hits += batch.size
                lines += batch.sumOf { it.content?.count ?: 0 }
            }
            val ms = (System.nanoTime() - t0) / 1_000_000
            println("$engine: $ms мс, файлов с совпадениями $hits, строк $lines, просмотрено $scanned${if (limit) " (лимит)" else ""}")
        }
    }
}
