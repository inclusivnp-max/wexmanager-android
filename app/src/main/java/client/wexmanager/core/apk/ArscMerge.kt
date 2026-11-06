package client.wexmanager.core.apk

import java.io.IOException

/**
 * Слияние таблицы ресурсов config-split (язык, плотность, ABI) в таблицу базового APK — основа
 * «объединения split-APK в один APK», как в MT Manager. Id ресурсов у частей общие (их выдаёт один
 * bundletool), поэтому блоки type из части дописываются к пакету базы; меняются только индексы
 * ключей и строк: они переводятся в пулы базы. Стили строк переносятся вместе со строками.
 */
object ArscMerge {
    fun merge(base: ArscTable, split: ArscTable) {
        val strings = PoolMapper(split.strings) { s, spans -> base.strings.addStyled(s, spans) }
        for (sp in split.packages) {
            val bp = base.packages.firstOrNull { it.id == sp.id }
                ?: throw IOException("Пакет ресурсов 0x${sp.id.toString(16)} (${sp.name}) не найден в основном APK — модули функций не объединяются")
            val keys = KeyMapper(sp, bp)
            for (tc in sp.allTypes()) {
                val name = sp.typeName(tc.typeId)
                val typeId = bp.typeIdOf(name) ?: throw IOException("В основном APK нет типа ресурсов «$name»")
                // Такая конфигурация уже есть в базе (повторное слияние) — не дублируем.
                if (bp.types(typeId).any { it.config.bytes.contentEquals(tc.config.bytes) }) continue
                val raw = tc.raw.copyOf()
                raw[8] = typeId.toByte()
                val b = ArscTable.le(raw)
                for (e in tc.entries) {
                    if (e == null || e.start < 0) continue
                    val key = keys.map(e.key)
                    if (e.compact) {
                        if (key > 0xFFFF) throw IOException("Слишком много ключей для компактной записи ресурса")
                        b.putShort(e.start, key.toShort())
                    } else {
                        b.putInt(e.start + 4, key)
                    }
                    if (!e.complex) {
                        if (e.value.type == ResValue.TYPE_STRING) b.putInt(e.dataOffset, strings.map(e.value.data))
                    } else {
                        // Bag: за шапкой сущности — пары (имя атрибута, Res_value по 8 байт).
                        var q = e.start + (b.getShort(e.start).toInt() and 0xFFFF)
                        repeat(e.bag.size) {
                            if (raw[q + 7].toInt() and 0xFF == ResValue.TYPE_STRING) b.putInt(q + 8, strings.map(b.getInt(q + 8)))
                            q += 12
                        }
                    }
                }
                bp.replaceOrAppend(null, TypeChunk(raw))
                bp.mergeSpecFlags(typeId, sp.specFlags(tc.typeId))
            }
            if (keys.added) bp.keysChanged = true
        }
    }

    /** Индекс строки части → индекс в пуле базы (одинаковые строки части переносятся один раз). */
    private class PoolMapper(private val from: StringPool, private val add: (String, List<Triple<String, Int, Int>>) -> Int) {
        private val done = HashMap<Int, Int>()
        fun map(i: Int): Int = done.getOrPut(i) {
            val s = from[i] ?: throw IOException("Ссылка на строку $i вне пула части")
            add(s, from.spans(i))
        }
    }

    /** Индекс ключа части → индекс ключа с тем же именем в базе (новые имена дописываются). */
    private class KeyMapper(private val from: ArscPackage, private val to: ArscPackage) {
        var added = false
            private set
        private val index: HashMap<String, Int> by lazy {
            HashMap<String, Int>().also { m -> for (i in 0 until to.keyStrings.size) to.keyStrings[i]?.let { m.putIfAbsent(it, i) } }
        }
        private val done = HashMap<Int, Int>()
        fun map(i: Int): Int = done.getOrPut(i) {
            val name = from.keyStrings[i] ?: throw IOException("Ссылка на ключ $i вне пула части")
            index[name] ?: to.keyStrings.add(name).also { index[name] = it; added = true }
        }
    }
}
