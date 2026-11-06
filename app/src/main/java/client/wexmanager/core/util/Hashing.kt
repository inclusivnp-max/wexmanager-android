package client.wexmanager.core.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32

enum class HashAlgorithm(val label: String, val jca: String?) {
    MD5("MD5", "MD5"),
    SHA1("SHA-1", "SHA-1"),
    SHA256("SHA-256", "SHA-256"),
    SHA512("SHA-512", "SHA-512"),
    CRC32("CRC32", null),
}

object Hashing {
    /** Считает сразу несколько хешей за один проход по файлу. */
    suspend fun compute(file: File, algorithms: List<HashAlgorithm>, onProgress: (Float) -> Unit = {}): Map<HashAlgorithm, String> =
        withContext(Dispatchers.IO) {
            val digests = algorithms.filter { it.jca != null }.associateWith { MessageDigest.getInstance(it.jca!!) }
            val crc = if (HashAlgorithm.CRC32 in algorithms) CRC32() else null
            val total = file.length().coerceAtLeast(1)
            var read = 0L
            file.inputStream().buffered(1 shl 16).use { input ->
                val buf = ByteArray(1 shl 16)
                var tick = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    digests.values.forEach { it.update(buf, 0, n) }
                    crc?.update(buf, 0, n)
                    read += n
                    if (++tick and 31 == 0) {
                        currentCoroutineContext().ensureActive()
                        onProgress(read.toFloat() / total)
                    }
                }
            }
            onProgress(1f)
            buildMap {
                digests.forEach { (alg, md) -> put(alg, md.digest().toHex()) }
                crc?.let { put(HashAlgorithm.CRC32, it.value.toString(16).padStart(8, '0')) }
            }
        }

    /** CRC-32 файла одним проходом, lowercase hex из 8 символов. */
    fun crc32(file: File): String {
        val crc = CRC32()
        file.inputStream().buffered(1 shl 16).use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                crc.update(buf, 0, n)
            }
        }
        return crc.value.toString(16).padStart(8, '0')
    }

    /** CRC-32 массива байт, lowercase hex из 8 символов. */
    fun crc32(data: ByteArray): String {
        val crc = CRC32()
        crc.update(data)
        return crc.value.toString(16).padStart(8, '0')
    }

    fun ByteArray.toHex(): String {
        val chars = "0123456789abcdef"
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append(chars[v shr 4]).append(chars[v and 0xF])
        }
        return sb.toString()
    }
}
