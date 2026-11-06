package client.wexmanager.core.fs

import client.wexmanager.core.settings.SortBy

/** Естественная сортировка: «file2» < «file10», без учёта регистра. */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val si = i
                val sj = j
                while (i < a.length && a[i] == '0') i++
                while (j < b.length && b[j] == '0') j++
                val ni = i
                val nj = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val lenA = i - ni
                val lenB = j - nj
                if (lenA != lenB) return lenA - lenB
                for (k in 0 until lenA) {
                    val d = a[ni + k] - b[nj + k]
                    if (d != 0) return d
                }
                val zeros = (ni - si) - (nj - sj)
                if (zeros != 0) return zeros
            } else {
                val la = ca.lowercaseChar()
                val lb = cb.lowercaseChar()
                if (la != lb) return la - lb
                i++
                j++
            }
        }
        val rest = (a.length - i) - (b.length - j)
        return if (rest != 0) rest else a.compareTo(b)
    }
}

fun fileComparator(sortBy: SortBy, descending: Boolean, foldersFirst: Boolean): Comparator<FileItem> {
    val byName = Comparator<FileItem> { a, b -> NaturalOrder.compare(a.name, b.name) }
    val byPath = compareBy<FileItem> { it.path }
    val primary: Comparator<FileItem> = when (sortBy) {
        SortBy.Name -> byName
        SortBy.Date -> compareBy<FileItem> { it.lastModified }.thenComparing(byName)
        SortBy.Size -> compareBy<FileItem> { if (it.isDirectory && it.size <= 0) it.childCount.toLong() else it.size }.thenComparing(byName)
        SortBy.Type -> compareBy<FileItem> { it.extension }.thenComparing(byName)
    }.thenComparing(byPath)
    val ordered = if (descending) primary.reversed() else primary
    return if (foldersFirst) compareBy<FileItem> { !it.isDirectory }.thenComparing(ordered) else ordered
}

/**
 * Фильтр в стиле MT Manager:
 * - `текст` — имя содержит текст (без учёта регистра);
 * - `*.apk`, `img_??.png` — шаблон с подстановочными символами;
 * - `/regex` — регулярное выражение;
 * - `!текст` или `!/regex` — отрицание.
 */
class NameFilter private constructor(private val predicate: (String) -> Boolean, val isValid: Boolean) {
    fun matches(name: String): Boolean = predicate(name)

    companion object {
        val All = NameFilter({ true }, true)

        fun parse(query: String): NameFilter {
            if (query.isEmpty()) return All
            var q = query
            var negate = false
            if (q.startsWith('!')) {
                negate = true
                q = q.substring(1)
            }
            if (q.isEmpty()) return All
            val base: (String) -> Boolean
            var valid = true
            when {
                q.startsWith('/') && q.length > 1 -> {
                    val regex = runCatching { Regex(q.substring(1), RegexOption.IGNORE_CASE) }.getOrNull()
                    if (regex == null) {
                        valid = false
                        base = { true }
                    } else {
                        base = { regex.containsMatchIn(it) }
                    }
                }
                q.contains('*') || q.contains('?') -> {
                    val regex = globToRegex(q)
                    base = { regex.matches(it) }
                }
                else -> base = { it.contains(q, ignoreCase = true) }
            }
            return NameFilter(if (negate) { n -> !base(n) } else base, valid)
        }

        fun globToRegex(glob: String): Regex {
            val sb = StringBuilder()
            for (c in glob) {
                when (c) {
                    '*' -> sb.append(".*")
                    '?' -> sb.append('.')
                    '.', '(', ')', '[', ']', '{', '}', '+', '^', '$', '|', '\\' -> sb.append('\\').append(c)
                    else -> sb.append(c)
                }
            }
            return Regex(sb.toString(), RegexOption.IGNORE_CASE)
        }
    }
}
