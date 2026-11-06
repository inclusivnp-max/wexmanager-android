package client.wexmanager.core.apk

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Бинарный XML Android (AndroidManifest.xml и XML-ресурсы внутри APK): расшифровка для правки и обратная сборка. */
object AxmlFiles {
    /** Начинается ли файл с заголовка RES_XML_TYPE (03 00 08 00). */
    fun isBinaryXml(file: File): Boolean = runCatching {
        file.inputStream().use { s ->
            val h = ByteArray(4)
            s.read(h) == 4 && h[0].toInt() == 0x03 && h[1].toInt() == 0x00 && h[2].toInt() == 0x08 && h[3].toInt() == 0x00
        }
    }.getOrDefault(false)

    /** Расшифровывает [binary] в текстовый файл рядом в кеше и возвращает его. */
    suspend fun decodeToText(context: Context, binary: File): File = withContext(Dispatchers.IO) {
        val doc = try {
            AxmlDecoder.decode(binary.readBytes())
        } catch (e: Exception) {
            throw IOException("Не удалось расшифровать бинарный XML: ${e.message}")
        }
        val dir = File(context.cacheDir, "axml/${(binary.absolutePath + binary.lastModified()).hashCode().toUInt()}").apply { mkdirs() }
        File(dir, binary.name).apply { writeText(doc.toXml()) }.also { dir.setLastModified(System.currentTimeMillis()) }
    }

    /** id атрибута android:[name] из фреймворка (на случай новых атрибутов, которых не было в исходнике). */
    fun androidAttrId(name: String): Int =
        runCatching { android.R.attr::class.java.getField(name).getInt(null) }.getOrDefault(0)

    /** Кодирует текст обратно, беря типы значений из исходного бинарника [original]. */
    fun encode(text: String, original: File): ByteArray {
        // Без исходника типы значений не восстановить — лучше честная ошибка, чем испорченный манифест.
        if (!original.isFile) throw AxmlEncodeException("Исходный бинарный файл пропал из кеша — откройте его заново")
        val hints = AxmlEncoder.Hints.from(AxmlDecoder.decode(original.readBytes()))
        return AxmlEncoder.encode(text, android.util.Xml.newPullParser(), hints, ::androidAttrId)
    }

    /**
     * Маршрут редактора для файла: бинарный XML открывается расшифровкой (с обратной сборкой при сохранении),
     * остальное — как обычный текст.
     */
    suspend fun editorRoute(context: Context, path: String, charset: String? = null, readOnly: Boolean = false): client.wexmanager.ui.navigation.Route.Editor {
        val f = File(path)
        if (path.endsWith(".xml", ignoreCase = true) && withContext(Dispatchers.IO) { isBinaryXml(f) }) {
            runCatching { decodeToText(context, f) }.getOrNull()?.let { text ->
                val writable = !readOnly && withContext(Dispatchers.IO) { f.canWrite() }
                return client.wexmanager.ui.navigation.Route.Editor(text.path, binarySource = f.path, readOnly = !writable)
            }
        }
        return client.wexmanager.ui.navigation.Route.Editor(path, charset = charset, readOnly = readOnly)
    }
}
