package client.wexmanager.core.dex

import android.content.Context
import client.wexmanager.ui.navigation.Route
import com.tim.clazz.writer.ClassWriter
import com.tim.csmali.Smali
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** JVM `.class`: расшифровка в csmali (синтаксис smali с опкодами JVM) для правки и обратная сборка через clazzpatcher. */
object ClassFiles {
    /** CAFEBABE и версия класса (у универсальных Mach-O та же сигнатура, но там дальше — число архитектур). */
    fun isClass(file: File): Boolean = runCatching {
        file.inputStream().use { s ->
            val h = ByteArray(8)
            s.read(h) == 8 && h[0] == 0xCA.toByte() && h[1] == 0xFE.toByte() && h[2] == 0xBA.toByte() && h[3] == 0xBE.toByte() &&
                (((h[6].toInt() and 0xFF) shl 8) or (h[7].toInt() and 0xFF)) in 45..80
        }
    }.getOrDefault(false)

    /** Текст класса в кеше: `<Имя>.smali` — подсветка и подсказки smali. */
    suspend fun decodeToText(context: Context, binary: File): File = withContext(Dispatchers.IO) {
        val text = try {
            Smali.classToSmali(binary)
        } catch (e: Throwable) {
            if (e is VirtualMachineError) throw e
            throw IOException("Не удалось разобрать класс: ${e.message ?: e.javaClass.simpleName}")
        }
        val dir = File(context.cacheDir, "class-text/${(binary.absolutePath + binary.lastModified()).hashCode().toUInt()}").apply { mkdirs() }
        File(dir, binary.nameWithoutExtension + ".smali").apply { writeText(text) }
    }

    /** csmali → байты `.class`; ошибка синтаксиса или верификации модели — [IOException] с местом. */
    fun encode(text: String): ByteArray = try {
        ClassWriter(Smali.smaliToClass(text)).toByteArray()
    } catch (e: Throwable) {
        if (e is VirtualMachineError) throw e
        throw IOException("Ошибка сборки класса: ${e.message ?: e.javaClass.simpleName}")
    }

    /** Маршрут редактора для `.class` на диске; null — не класс или не разобрался. */
    suspend fun editorRoute(context: Context, path: String, readOnly: Boolean = false): Route.Editor? {
        val f = File(path)
        if (!withContext(Dispatchers.IO) { isClass(f) }) return null
        val text = runCatching { decodeToText(context, f) }.getOrNull() ?: return null
        val writable = !readOnly && withContext(Dispatchers.IO) { f.canWrite() }
        return Route.Editor(text.path, binarySource = f.path, readOnly = !writable)
    }
}
