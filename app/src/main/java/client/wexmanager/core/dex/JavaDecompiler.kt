package client.wexmanager.core.dex

import jadx.api.CommentsLevel
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.plugins.JadxPlugin
import jadx.api.plugins.loader.JadxPluginLoader
import jadx.plugins.input.dex.DexInputPlugin
import java.io.Closeable
import java.io.IOException

/**
 * Java-код класса из DEX через jadx (как «Перейти к Java» в MT Manager). Входом служат байты DEX
 * с несохранёнными правками. Загруженный DEX держится до смены [key]: следующие классы того же DEX
 * открываются без повторного разбора всего файла.
 */
class JavaDecompiler : Closeable {
    private var key: Any? = null
    private var loaded: JadxDecompiler? = null

    /** [descriptor] — `Lcom/a/B;` или `Lcom/a/B$C;` (вложенный показывается вместе с внешним). */
    @Synchronized
    fun decompile(key: Any, dex: () -> ByteArray, descriptor: String): String {
        val d = loaded?.takeIf { this.key == key } ?: run {
            close()
            load(dex()).also { loaded = it; this.key = key }
        }
        // Точное сырое имя («a.b$c»): по имени через точки в обфусцированном DEX нашёлся бы чужой класс a.b.c.
        val raw = descriptor.removePrefix("L").removeSuffix(";").replace('/', '.')
        val node = d.root.resolveRawClass(raw) ?: throw IOException("jadx не нашёл класс $raw")
        val top = node.topParentClass
        val cls = d.classes.firstOrNull { it.classNode === top }
            ?: d.searchJavaClassByOrigFullName(top.classInfo.fullName)
            ?: throw IOException("jadx не нашёл класс $raw")
        return cls.code
    }

    @Synchronized
    override fun close() {
        runCatching { loaded?.close() }
        loaded = null
        key = null
    }

    companion object {
        /** Разовая декомпиляция без кеша. */
        fun decompile(dex: ByteArray, descriptor: String): String = JavaDecompiler().use { it.decompile(Unit, { dex }, descriptor) }

        private fun load(dex: ByteArray): JadxDecompiler {
            val args = JadxArgs().apply {
                isSkipResources = true
                threadsCount = 1
                isShowInconsistentCode = true
                commentsLevel = CommentsLevel.INFO
                // Плагины не ищем через ServiceLoader: вход — свой загрузчик DEX ниже.
                pluginLoader = object : JadxPluginLoader {
                    override fun load(): List<JadxPlugin> = emptyList()
                    override fun close() = Unit
                }
            }
            val d = JadxDecompiler(args)
            try {
                d.addCustomCodeLoader(DexInputPlugin().loadDex(dex, "classes.dex"))
                d.load()
            } catch (e: Throwable) {
                runCatching { d.close() }
                throw e
            }
            return d
        }
    }
}
