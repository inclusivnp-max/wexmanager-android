package client.wexmanager.feature.compare

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive

enum class RowKind { Equal, Added, Removed, Changed }

/**
 * Строка выравнивания «рядом»: номера строк с 1, `0` — строки на этой стороне нет.
 * [leftMarks]/[rightMarks] — изменённые символы внутри строки парами [начало, конец).
 */
@Immutable
class DiffRow(
    val kind: RowKind,
    val leftNo: Int,
    val rightNo: Int,
    val left: String?,
    val right: String?,
    val leftMarks: IntArray? = null,
    val rightMarks: IntArray? = null,
)

@Immutable
data class DiffStats(val added: Int, val removed: Int, val changed: Int) {
    val total: Int get() = added + removed + changed
}

/**
 * Построчный diff: Майерс O(ND) в линейной памяти (поиск «средней змеи», как в GNU diff)
 * с эвристикой для слишком дорогих участков; небольшие участки — классическим LCS.
 */
object DiffEngine {
    /** После стольких шагов поиск средней змеи берёт приближённую точку разбиения. */
    private const val TOO_EXPENSIVE = 1024
    /** Участки до стольких ячеек (n·m) решаются точным LCS. */
    private const val LCS_CELLS = 1_000_000L
    /** Для подсветки символов — порог n·m посимвольного LCS. */
    private const val CHAR_CELLS = 40_000
    /** Длина строки для отображения (очень длинные строки обрезаются). */
    const val MAX_LINE_CHARS = 4000

    /**
     * Возвращает пары флагов: строка a удалена / строка b добавлена.
     * [job] — для отмены долгих вычислений.
     */
    fun diff(a: List<String>, b: List<String>, job: Job?): Pair<BooleanArray, BooleanArray> {
        // Интернирование строк в числа: сравнения в основном цикле — по Int.
        val ids = HashMap<String, Int>(((a.size + b.size) * 4 / 3).coerceAtLeast(16))
        val xa = IntArray(a.size) { ids.getOrPut(a[it]) { ids.size } }
        val yb = IntArray(b.size) { ids.getOrPut(b[it]) { ids.size } }
        val delA = BooleanArray(a.size)
        val insB = BooleanArray(b.size)
        Myers(xa, yb, delA, insB, job).run()
        return delA to insB
    }

    private class Myers(
        private val xv: IntArray,
        private val yv: IntArray,
        private val delA: BooleanArray,
        private val insB: BooleanArray,
        private val job: Job?,
    ) {
        private val offset = yv.size + 1
        private val fd = IntArray(xv.size + yv.size + 3)
        private val bd = IntArray(xv.size + yv.size + 3)
        private var steps = 0

        fun run() {
            // Явный стек вместо рекурсии: глубина не зависит от стека потока.
            val stack = ArrayDeque<IntArray>()
            stack.addLast(intArrayOf(0, xv.size, 0, yv.size))
            while (stack.isNotEmpty()) {
                val r = stack.removeLast()
                var xoff = r[0]
                var xlim = r[1]
                var yoff = r[2]
                var ylim = r[3]
                while (xoff < xlim && yoff < ylim && xv[xoff] == yv[yoff]) { xoff++; yoff++ }
                while (xlim > xoff && ylim > yoff && xv[xlim - 1] == yv[ylim - 1]) { xlim--; ylim-- }
                when {
                    xoff == xlim -> for (y in yoff until ylim) insB[y] = true
                    yoff == ylim -> for (x in xoff until xlim) delA[x] = true
                    (xlim - xoff).toLong() * (ylim - yoff) <= LCS_CELLS -> lcs(xoff, xlim, yoff, ylim)
                    else -> {
                        val mid = diag(xoff, xlim, yoff, ylim)
                        val xmid = mid ushr 32
                        val ymid = (mid and 0xFFFFFFFFL).toInt()
                        stack.addLast(intArrayOf(xmid.toInt(), xlim, ymid, ylim))
                        stack.addLast(intArrayOf(xoff, xmid.toInt(), yoff, ymid))
                    }
                }
            }
        }

        private fun tick() {
            if (++steps and 63 == 0) job?.ensureActive()
        }

        /** Точный LCS для небольшого участка (таблица суффиксов). */
        private fun lcs(xoff: Int, xlim: Int, yoff: Int, ylim: Int) {
            val n = xlim - xoff
            val m = ylim - yoff
            val w = m + 1
            val dp = IntArray((n + 1) * w)
            for (i in n - 1 downTo 0) {
                if (i and 255 == 0) job?.ensureActive()
                val xi = xv[xoff + i]
                val row = i * w
                val next = (i + 1) * w
                for (j in m - 1 downTo 0) {
                    dp[row + j] = if (xi == yv[yoff + j]) dp[next + j + 1] + 1
                    else maxOf(dp[next + j], dp[row + j + 1])
                }
            }
            var i = 0
            var j = 0
            while (i < n && j < m) {
                when {
                    xv[xoff + i] == yv[yoff + j] -> { i++; j++ }
                    dp[(i + 1) * w + j] >= dp[i * w + j + 1] -> { delA[xoff + i] = true; i++ }
                    else -> { insB[yoff + j] = true; j++ }
                }
            }
            while (i < n) { delA[xoff + i] = true; i++ }
            while (j < m) { insB[yoff + j] = true; j++ }
        }

        /** Средняя точка кратчайшего редакционного пути; результат упакован как (x shl 32) or y. */
        private fun diag(xoff: Int, xlim: Int, yoff: Int, ylim: Int): Long {
            val dmin = xoff - ylim
            val dmax = xlim - yoff
            val fmid = xoff - yoff
            val bmid = xlim - ylim
            var fmin = fmid
            var fmax = fmid
            var bmin = bmid
            var bmax = bmid
            val odd = ((fmid - bmid) and 1) != 0
            val o = offset
            fd[o + fmid] = xoff
            bd[o + bmid] = xlim
            var c = 1
            while (true) {
                tick()
                // Шаг прямого поиска.
                if (fmin > dmin) fd[o + --fmin - 1] = -1 else ++fmin
                if (fmax < dmax) fd[o + ++fmax + 1] = -1 else --fmax
                var d = fmax
                while (d >= fmin) {
                    val tlo = fd[o + d - 1]
                    val thi = fd[o + d + 1]
                    var x = if (tlo >= thi) tlo + 1 else thi
                    var y = x - d
                    while (x < xlim && y < ylim && xv[x] == yv[y]) { x++; y++ }
                    fd[o + d] = x
                    if (odd && d in bmin..bmax && bd[o + d] <= x) return pack(x, y)
                    d -= 2
                }
                // Шаг обратного поиска.
                if (bmin > dmin) bd[o + --bmin - 1] = Int.MAX_VALUE else ++bmin
                if (bmax < dmax) bd[o + ++bmax + 1] = Int.MAX_VALUE else --bmax
                d = bmax
                while (d >= bmin) {
                    val tlo = bd[o + d - 1]
                    val thi = bd[o + d + 1]
                    var x = if (tlo < thi) tlo else thi - 1
                    var y = x - d
                    while (x > xoff && y > yoff && xv[x - 1] == yv[y - 1]) { x--; y-- }
                    bd[o + d] = x
                    if (!odd && d in fmin..fmax && x <= fd[o + d]) return pack(x, y)
                    d -= 2
                }
                if (c >= TOO_EXPENSIVE) {
                    // Слишком дорого: берём лучшую из достигнутых диагоналей (как GNU diff).
                    var fxybest = -1
                    var fxbest = 0
                    d = fmax
                    while (d >= fmin) {
                        var x = minOf(fd[o + d], xlim)
                        var y = x - d
                        if (ylim < y) { x = ylim + d; y = ylim }
                        if (fxybest < x + y) { fxybest = x + y; fxbest = x }
                        d -= 2
                    }
                    var bxybest = Int.MAX_VALUE
                    var bxbest = 0
                    d = bmax
                    while (d >= bmin) {
                        var x = maxOf(xoff, bd[o + d])
                        var y = x - d
                        if (y < yoff) { x = yoff + d; y = yoff }
                        if (x + y < bxybest) { bxybest = x + y; bxbest = x }
                        d -= 2
                    }
                    return if ((xlim + ylim) - bxybest < fxybest - (xoff + yoff)) pack(fxbest, fxybest - fxbest)
                    else pack(bxbest, bxybest - bxbest)
                }
                c++
            }
        }

        private fun pack(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)
    }

    /**
     * Строки выравнивания: подряд идущие удаления и добавления в одном блоке
     * попарно становятся «изменёнными», остаток — удалёнными или добавленными.
     */
    fun align(a: List<String>, b: List<String>, delA: BooleanArray, insB: BooleanArray, job: Job?): List<DiffRow> {
        val rows = ArrayList<DiffRow>(maxOf(a.size, b.size) + 16)
        var i = 0
        var j = 0
        var tick = 0
        while (i < a.size || j < b.size) {
            if (++tick and 1023 == 0) job?.ensureActive()
            if (i < a.size && j < b.size && !delA[i] && !insB[j]) {
                val t = display(a[i])
                rows += DiffRow(RowKind.Equal, i + 1, j + 1, t, display(b[j]))
                i++; j++
                continue
            }
            val ds = i
            while (i < a.size && delA[i]) i++
            val ins = j
            while (j < b.size && insB[j]) j++
            if (i == ds && j == ins) {
                // Несогласованные флаги (не должно случаться) — не зацикливаемся.
                if (i < a.size) { rows += DiffRow(RowKind.Removed, i + 1, 0, display(a[i]), null); i++ }
                if (j < b.size) { rows += DiffRow(RowKind.Added, 0, j + 1, null, display(b[j])); j++ }
                continue
            }
            val dels = i - ds
            val adds = j - ins
            val paired = minOf(dels, adds)
            for (k in 0 until paired) {
                val l = display(a[ds + k])
                val r = display(b[ins + k])
                val (lm, rm) = charDiff(l, r)
                rows += DiffRow(RowKind.Changed, ds + k + 1, ins + k + 1, l, r, lm, rm)
            }
            for (k in paired until dels) rows += DiffRow(RowKind.Removed, ds + k + 1, 0, display(a[ds + k]), null)
            for (k in paired until adds) rows += DiffRow(RowKind.Added, 0, ins + k + 1, null, display(b[ins + k]))
        }
        return rows
    }

    fun stats(rows: List<DiffRow>): DiffStats {
        var add = 0
        var del = 0
        var ch = 0
        for (r in rows) when (r.kind) {
            RowKind.Added -> add++
            RowKind.Removed -> del++
            RowKind.Changed -> ch++
            RowKind.Equal -> Unit
        }
        return DiffStats(add, del, ch)
    }

    /** Табуляции → 4 пробела, очень длинные строки обрезаются. */
    fun display(line: String): String {
        val expanded = if (line.indexOf('\t') >= 0) line.replace("\t", "    ") else line
        return if (expanded.length > MAX_LINE_CHARS) expanded.substring(0, MAX_LINE_CHARS) + " …" else expanded
    }

    /**
     * Простой посимвольный diff: общий префикс/суффикс, середина — LCS по символам,
     * если она небольшая, иначе подсвечивается целиком.
     */
    fun charDiff(a: String, b: String): Pair<IntArray?, IntArray?> {
        var p = 0
        val minLen = minOf(a.length, b.length)
        while (p < minLen && a[p] == b[p]) p++
        var s = 0
        while (s < minLen - p && a[a.length - 1 - s] == b[b.length - 1 - s]) s++
        val aMid = a.length - p - s
        val bMid = b.length - p - s
        if (aMid == 0 && bMid == 0) return null to null
        if (aMid == 0) return null to intArrayOf(p, p + bMid)
        if (bMid == 0) return intArrayOf(p, p + aMid) to null
        if (aMid.toLong() * bMid > CHAR_CELLS) return intArrayOf(p, p + aMid) to intArrayOf(p, p + bMid)
        val w = bMid + 1
        val dp = IntArray((aMid + 1) * w)
        for (i in aMid - 1 downTo 0) {
            val ci = a[p + i]
            for (j in bMid - 1 downTo 0) {
                dp[i * w + j] = if (ci == b[p + j]) dp[(i + 1) * w + j + 1] + 1 else maxOf(dp[(i + 1) * w + j], dp[i * w + j + 1])
            }
        }
        // Если общего почти нет — подсвечиваем середину целиком (иначе получается «шум»).
        val common = dp[0]
        if (common * 3 < minOf(aMid, bMid)) return intArrayOf(p, p + aMid) to intArrayOf(p, p + bMid)
        val markA = BooleanArray(aMid)
        val markB = BooleanArray(bMid)
        var i = 0
        var j = 0
        while (i < aMid && j < bMid) {
            when {
                a[p + i] == b[p + j] -> { i++; j++ }
                dp[(i + 1) * w + j] >= dp[i * w + j + 1] -> { markA[i] = true; i++ }
                else -> { markB[j] = true; j++ }
            }
        }
        while (i < aMid) { markA[i] = true; i++ }
        while (j < bMid) { markB[j] = true; j++ }
        return ranges(markA, p) to ranges(markB, p)
    }

    /** Флаги → пары [начало, конец); одиночные совпавшие символы между отметками склеиваются. */
    private fun ranges(marks: BooleanArray, base: Int): IntArray? {
        val out = ArrayList<Int>()
        var k = 0
        while (k < marks.size) {
            if (!marks[k]) { k++; continue }
            val start = k
            while (k < marks.size && (marks[k] || (k + 1 < marks.size && marks[k + 1] && k > start))) k++
            out += base + start
            out += base + k
        }
        return if (out.isEmpty()) null else out.toIntArray()
    }
}
