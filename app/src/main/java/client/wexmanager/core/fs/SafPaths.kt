package client.wexmanager.core.fs

/**
 * Пути `Android/data` и `Android/obb` на внешнем томе. Чистые функции, без Android API,
 * чтобы их можно было проверить юнит-тестом.
 */
internal object SafPaths {
    const val DATA = "Android/data"
    const val OBB = "Android/obb"

    fun normalize(path: String): String =
        if (path.length > 1 && path.endsWith('/')) path.trimEnd('/') else path

    /** Путь относительно корня тома. `null` — путь вне тома или с `..`. */
    fun relative(volumeRoot: String, path: String): String? {
        val root = normalize(volumeRoot)
        val p = normalize(path)
        if (p == root) return ""
        val prefix = "$root/"
        if (!p.startsWith(prefix)) return null
        val rel = p.removePrefix(prefix)
        if (rel.split('/').any { it.isEmpty() || it == "." || it == ".." }) return null
        return rel
    }

    fun documentId(volumeId: String, volumeRoot: String, path: String): String? {
        val rel = relative(volumeRoot, path) ?: return null
        return if (rel.isEmpty()) "$volumeId:" else "$volumeId:$rel"
    }

    /** `Android/data` или `Android/obb`, если путь — эта папка или лежит внутри. */
    fun restrictedRelative(volumeRoot: String, path: String): String? {
        val rel = relative(volumeRoot, path) ?: return null
        return when {
            rel == DATA || rel.startsWith("$DATA/") -> DATA
            rel == OBB || rel.startsWith("$OBB/") -> OBB
            else -> null
        }
    }

    fun restrictedRoot(volumeRoot: String, path: String): String? {
        val kind = restrictedRelative(volumeRoot, path) ?: return null
        return "${normalize(volumeRoot)}/$kind"
    }

    /** [grantRelative] пустой — выдан весь том. */
    fun grantCovers(volumeRoot: String, grantRelative: String, path: String): Boolean {
        val rel = relative(volumeRoot, path) ?: return false
        if (grantRelative.isEmpty()) return true
        return rel == grantRelative || rel.startsWith("$grantRelative/")
    }
}
