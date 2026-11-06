package client.wexmanager.feature.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import client.wexmanager.core.util.Format
import client.wexmanager.feature.browser.copyToSystemClipboard
import client.wexmanager.ui.components.CheckRow
import client.wexmanager.ui.components.ConnectedChoice
import client.wexmanager.ui.components.SectionLabel
import client.wexmanager.ui.theme.LocalAppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.TreeMap
import kotlin.math.max
import kotlin.math.min

private const val BLOCK_SHIFT = 12
private const val BLOCK_SIZE = 1 shl BLOCK_SHIFT
private const val BLOCK_MASK = (BLOCK_SIZE - 1).toLong()

/** Операции крупнее этого размера выполняются вне главного потока. */
private const val SMALL_OP = 64 * 1024

/** Максимальная длина одной массовой операции (заполнение/вставка). */
internal const val MAX_BULK = 16 shl 20

/** Ограничения истории правок. */
private const val MAX_UNDO = 2000
private const val MAX_UNDO_BYTES = 64L shl 20

/** Правка доступна только для файлов не больше 2 ГБ. */
internal const val MAX_EDITABLE_SIZE = 2L shl 30

/** Состояние патчей диапазона: значения и битовая маска «байт изменён». */
internal class PatchSnapshot(val values: ByteArray, val mask: LongArray) {
    fun has(i: Int) = mask[i ushr 6] and (1L shl (i and 63)) != 0L
    val weight: Long get() = values.size + mask.size * 8L
    fun sameAs(o: PatchSnapshot) = values.contentEquals(o.values) && mask.contentEquals(o.mask)
}

/** Непрерывный отрезок изменённых байтов (для записи в файл). */
internal class PatchRun(val start: Long, val data: ByteArray)

/**
 * Разреженная карта патчей «смещение → новый байт». Хранится блоками по 4 КБ с битовой маской,
 * поэтому даже плотные правки (заполнение диапазона) занимают ~1.1 байта на изменённый байт.
 * Потокобезопасна: читается при отрисовке, поиске и сохранении.
 */
internal class PatchMap {
    private class Block {
        val data = ByteArray(BLOCK_SIZE)
        val bits = LongArray(BLOCK_SIZE / 64)
        var count = 0
        fun has(i: Int) = bits[i ushr 6] and (1L shl (i and 63)) != 0L
    }

    private val lock = Any()
    private val blocks = TreeMap<Long, Block>()
    private var total = 0L

    val size: Long get() = synchronized(lock) { total }

    /** Новое значение байта (0..255) или -1, если байт не изменён. */
    fun get(offset: Long): Int = synchronized(lock) {
        val b = blocks[offset ushr BLOCK_SHIFT] ?: return -1
        val i = (offset and BLOCK_MASK).toInt()
        if (b.has(i)) b.data[i].toInt() and 0xFF else -1
    }

    private fun putLocked(b: Block, i: Int, value: Byte) {
        val w = i ushr 6
        val m = 1L shl (i and 63)
        if (b.bits[w] and m == 0L) {
            b.bits[w] = b.bits[w] or m
            b.count++
            total++
        }
        b.data[i] = value
    }

    private fun removeLocked(b: Block, i: Int) {
        val w = i ushr 6
        val m = 1L shl (i and 63)
        if (b.bits[w] and m != 0L) {
            b.bits[w] = b.bits[w] and m.inv()
            b.data[i] = 0
            b.count--
            total--
        }
    }

    /** Проход по диапазону кусками в пределах одного блока; блокировка берётся на каждый кусок. */
    private inline fun forChunks(start: Long, len: Int, create: Boolean, action: (block: Block?, inBlock: Int, pos: Int, n: Int) -> Unit) {
        var pos = 0
        while (pos < len) {
            val off = start + pos
            val key = off ushr BLOCK_SHIFT
            val inBlock = (off and BLOCK_MASK).toInt()
            val n = min(BLOCK_SIZE - inBlock, len - pos)
            synchronized(lock) {
                val b = if (create) blocks.getOrPut(key) { Block() } else blocks[key]
                action(b, inBlock, pos, n)
                if (b != null && b.count == 0) blocks.remove(key)
            }
            pos += n
        }
    }

    fun capture(start: Long, len: Int): PatchSnapshot {
        val values = ByteArray(len)
        val mask = LongArray((len + 63) ushr 6)
        forChunks(start, len, create = false) { b, inBlock, pos, n ->
            if (b != null) for (k in 0 until n) {
                if (b.has(inBlock + k)) {
                    val j = pos + k
                    values[j] = b.data[inBlock + k]
                    mask[j ushr 6] = mask[j ushr 6] or (1L shl (j and 63))
                }
            }
        }
        return PatchSnapshot(values, mask)
    }

    fun restore(start: Long, snap: PatchSnapshot) {
        forChunks(start, snap.values.size, create = true) { b, inBlock, pos, n ->
            for (k in 0 until n) {
                if (snap.has(pos + k)) putLocked(b!!, inBlock + k, snap.values[pos + k]) else removeLocked(b!!, inBlock + k)
            }
        }
    }

    /**
     * Запись диапазона: value(i) в 0..255 — новый байт, < 0 — вернуть исходный.
     * Если новый байт совпадает с исходным [orig] — патч снимается.
     */
    fun write(start: Long, len: Int, orig: ByteArray?, value: (Int) -> Int) {
        forChunks(start, len, create = true) { b, inBlock, pos, n ->
            for (k in 0 until n) {
                val j = pos + k
                val v = value(j)
                if (v < 0 || (orig != null && j < orig.size && (orig[j].toInt() and 0xFF) == v)) {
                    removeLocked(b!!, inBlock + k)
                } else {
                    putLocked(b!!, inBlock + k, v.toByte())
                }
            }
        }
    }

    /** Наложить патчи на буфер [buf] с данными файла, начиная с позиции [pos]. */
    fun overlay(pos: Long, buf: ByteArray, off: Int, len: Int) {
        if (len <= 0) return
        synchronized(lock) {
            if (total == 0L) return
            val first = pos ushr BLOCK_SHIFT
            val last = (pos + len - 1) ushr BLOCK_SHIFT
            for ((key, b) in blocks.subMap(first, true, last, true)) {
                val base = key shl BLOCK_SHIFT
                val from = max(pos, base)
                val to = min(pos + len, base + BLOCK_SIZE)
                var o = from
                while (o < to) {
                    val i = (o - base).toInt()
                    if (b.has(i)) buf[off + (o - pos).toInt()] = b.data[i]
                    o++
                }
            }
        }
    }

    /** Первое изменённое смещение строго после [after], либо -1. */
    fun next(after: Long): Long = synchronized(lock) {
        val start = after + 1
        val key = start ushr BLOCK_SHIFT
        var e = blocks.ceilingEntry(key)
        while (e != null) {
            val base = e.key shl BLOCK_SHIFT
            val from = if (e.key == key) (start - base).toInt() else 0
            for (i in from until BLOCK_SIZE) if (e.value.has(i)) return base + i
            e = blocks.higherEntry(e.key)
        }
        -1L
    }

    /** Последнее изменённое смещение строго до [before], либо -1. */
    fun prev(before: Long): Long = synchronized(lock) {
        if (before <= 0) return -1L
        val end = before - 1
        val key = end ushr BLOCK_SHIFT
        var e = blocks.floorEntry(key)
        while (e != null) {
            val base = e.key shl BLOCK_SHIFT
            val from = if (e.key == key) (end - base).toInt() else BLOCK_SIZE - 1
            for (i in from downTo 0) if (e.value.has(i)) return base + i
            e = blocks.lowerEntry(e.key)
        }
        -1L
    }

    /** Первое НЕизменённое смещение ≥ [from]. */
    fun nextUnpatched(from: Long): Long = synchronized(lock) {
        var o = from
        var result = -1L
        while (result < 0) {
            val key = o ushr BLOCK_SHIFT
            val b = blocks[key]
            if (b == null) {
                result = o
            } else {
                val base = key shl BLOCK_SHIFT
                var i = (o - base).toInt()
                while (i < BLOCK_SIZE && b.has(i)) i++
                if (i < BLOCK_SIZE) result = base + i else o = base + BLOCK_SIZE
            }
        }
        result
    }

    /** Последнее НЕизменённое смещение ≤ [from], либо -1. */
    fun prevUnpatched(from: Long): Long = synchronized(lock) {
        var o = from
        var result = -2L
        while (result == -2L) {
            if (o < 0) {
                result = -1L
                break
            }
            val key = o ushr BLOCK_SHIFT
            val b = blocks[key]
            if (b == null) {
                result = o
            } else {
                val base = key shl BLOCK_SHIFT
                var i = (o - base).toInt()
                while (i >= 0 && b.has(i)) i--
                if (i >= 0) result = base + i else o = base - 1
            }
        }
        result
    }

    /** Изменённые байты, сгруппированные в непрерывные отрезки по возрастанию смещения. */
    fun runs(maxRun: Int = 1 shl 20): List<PatchRun> {
        val keys = synchronized(lock) { blocks.keys.toLongArray() }
        val out = ArrayList<PatchRun>()
        var runStart = -1L
        var buf = ByteArray(256)
        var len = 0
        fun flush() {
            if (len > 0) out += PatchRun(runStart, buf.copyOf(len))
            len = 0
        }
        for (key in keys) {
            synchronized(lock) {
                val b = blocks[key] ?: return@synchronized
                val base = key shl BLOCK_SHIFT
                for (i in 0 until BLOCK_SIZE) {
                    if (!b.has(i)) continue
                    val o = base + i
                    if (len > 0 && (o != runStart + len || len >= maxRun)) flush()
                    if (len == 0) runStart = o
                    if (len == buf.size) buf = buf.copyOf(buf.size * 2)
                    buf[len++] = b.data[i]
                }
            }
        }
        flush()
        return out
    }

    fun clear() = synchronized(lock) {
        blocks.clear()
        total = 0
    }
}

/** Запись истории: состояние патчей диапазона до и после операции. */
internal class EditOp(
    val start: Long,
    val length: Int,
    val label: String,
    val before: PatchSnapshot,
    val after: PatchSnapshot,
) {
    val weight: Long get() = before.weight + after.weight + 64
}

/**
 * Правки HEX-редактора: карта патчей поверх файла, история Undo/Redo и набираемая тетрада.
 * Все методы вызываются из главного потока; тяжёлые операции уходят на [Dispatchers.Default].
 */
internal class HexEdits {
    val patches = PatchMap()

    /** Растёт при любом изменении патчей — подписка для перерисовки. */
    var version by mutableIntStateOf(0)
        private set
    var modified by mutableLongStateOf(0L)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /** Идёт массовая операция или сохранение — новые правки не принимаются. */
    var busy by mutableStateOf(false)

    /** Набранная старшая тетрада (0..15) или -1. */
    var nibble by mutableIntStateOf(-1)

    private val undoStack = ArrayDeque<EditOp>()
    private val redoStack = ArrayDeque<EditOp>()
    private var undoWeight = 0L

    fun get(offset: Long): Int = patches.get(offset)

    /** Однобайтовая правка; [original] — исходный байт или -1, если он сейчас неизвестен. */
    fun setByte(offset: Long, value: Int, original: Int): Boolean {
        if (busy) return false
        val orig = if (original >= 0) byteArrayOf(original.toByte()) else null
        return commitNow(offset, 1, "Правка байта", orig) { value }
    }

    fun revertByte(offset: Long): Boolean {
        if (busy || patches.get(offset) < 0) return false
        return commitNow(offset, 1, "Возврат байта", null) { -1 }
    }

    private fun commitNow(start: Long, len: Int, label: String, orig: ByteArray?, value: (Int) -> Int): Boolean {
        val before = patches.capture(start, len)
        patches.write(start, len, orig, value)
        return push(EditOp(start, len, label, before, patches.capture(start, len)))
    }

    /** Пакетная запись диапазона одной записью истории. [orig] — исходные байты диапазона. */
    suspend fun commit(start: Long, len: Int, label: String, orig: ByteArray?, value: (Int) -> Int): Boolean {
        if (busy || len <= 0) return false
        if (len <= SMALL_OP) return commitNow(start, len, label, orig, value)
        busy = true
        try {
            val op = withContext(Dispatchers.Default) {
                val before = patches.capture(start, len)
                patches.write(start, len, orig, value)
                EditOp(start, len, label, before, patches.capture(start, len))
            }
            return push(op)
        } finally {
            busy = false
            changed()
        }
    }

    private fun push(op: EditOp): Boolean {
        if (op.before.sameAs(op.after)) return false
        undoStack.addLast(op)
        undoWeight += op.weight
        redoStack.clear()
        while (undoStack.size > MAX_UNDO || (undoWeight > MAX_UNDO_BYTES && undoStack.size > 1)) {
            undoWeight -= undoStack.removeFirst().weight
        }
        changed()
        return true
    }

    private fun changed() {
        version++
        modified = patches.size
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    private suspend fun restore(op: EditOp, snap: PatchSnapshot) {
        if (op.length <= SMALL_OP) {
            patches.restore(op.start, snap)
            return
        }
        busy = true
        try {
            withContext(Dispatchers.Default) { patches.restore(op.start, snap) }
        } finally {
            busy = false
        }
    }

    suspend fun undo(): EditOp? {
        if (busy) return null
        val op = undoStack.removeLastOrNull() ?: return null
        undoWeight -= op.weight
        nibble = -1
        restore(op, op.before)
        redoStack.addLast(op)
        changed()
        return op
    }

    suspend fun redo(): EditOp? {
        if (busy) return null
        val op = redoStack.removeLastOrNull() ?: return null
        nibble = -1
        restore(op, op.after)
        undoStack.addLast(op)
        undoWeight += op.weight
        changed()
        return op
    }

    /** Сбросить все правки и историю (после сохранения или по запросу). */
    fun reset() {
        patches.clear()
        undoStack.clear()
        redoStack.clear()
        undoWeight = 0
        nibble = -1
        changed()
    }
}

// ------------------------------------------------------------------ Разбор чисел и строк

internal enum class IntKind(val label: String, val bytes: Int, val min: Long, val max: Long, val hint: String) {
    U8("uint8", 1, -128, 255, "0…255 (или −128…−1)"),
    I16("int16", 2, Short.MIN_VALUE.toLong(), 0xFFFF, "−32 768…65 535"),
    I32("int32", 4, Int.MIN_VALUE.toLong(), 0xFFFFFFFFL, "−2 147 483 648…4 294 967 295"),
}

internal enum class TextMode(val label: String) { Utf8("UTF-8"), Utf16("UTF-16 LE"), Hex("HEX") }

/** Число в десятичном виде или hex с префиксом 0x / суффиксом h, допускается знак. */
internal fun parseNumber(text: String): Long? {
    val t = text.trim().replace("_", "").replace(" ", "").replace(" ", "")
    if (t.isEmpty()) return null
    val neg = t.startsWith("-")
    val body = t.removePrefix("-").removePrefix("+")
    val v = when {
        body.startsWith("0x", ignoreCase = true) -> body.drop(2).toLongOrNull(16)
        body.length > 1 && body.endsWith("h", ignoreCase = true) -> body.dropLast(1).toLongOrNull(16)
        else -> body.toLongOrNull()
    } ?: return null
    return if (neg) -v else v
}

internal fun encodeInt(value: Long, bytes: Int, littleEndian: Boolean): ByteArray =
    ByteArray(bytes) { i -> (value ushr ((if (littleEndian) i else bytes - 1 - i) * 8)).toByte() }

internal fun encodeText(mode: TextMode, text: String): ByteArray? = when (mode) {
    TextMode.Utf8 -> text.toByteArray(Charsets.UTF_8)
    TextMode.Utf16 -> text.toByteArray(Charsets.UTF_16LE)
    TextMode.Hex -> if (text.isBlank()) ByteArray(0) else parseHex(text)
}

internal fun hexPreview(bytes: ByteArray, limit: Int = 12): String {
    val head = bytes.take(limit).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
    return if (bytes.size > limit) "$head …" else head
}

internal fun bytesLabel(n: Long): String = "%,d %s".format(Format.RU, n, Format.plural((n % 100).toInt(), "байт", "байта", "байт"))

// ------------------------------------------------------------------ HEX-клавиатура

@Composable
internal fun HexKeypad(
    offset: Long?,
    current: Int,
    original: Int,
    patched: Boolean,
    nibble: Int,
    modified: Long,
    enabled: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onDigit: (Int) -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onRevert: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onInspector: () -> Unit,
    onSave: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val hapticsOn = LocalAppSettings.current.haptics
    val tap: (() -> Unit) -> () -> Unit = { action ->
        {
            if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
            action()
        }
    }
    val keysEnabled = enabled && offset != null
    Surface(
        color = scheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    val mono = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
                    if (offset == null) {
                        Text("Выберите байт", style = MaterialTheme.typography.titleMedium)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("0x%X".format(offset), style = mono, color = scheme.primary, maxLines = 1)
                            Spacer(Modifier.width(8.dp))
                            val shown = when {
                                nibble >= 0 -> "%X_".format(nibble)
                                current >= 0 -> "%02X".format(current)
                                else -> "··"
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (nibble >= 0 || patched) scheme.tertiaryContainer else scheme.surfaceContainerHighest,
                                contentColor = if (nibble >= 0 || patched) scheme.onTertiaryContainer else scheme.onSurface,
                            ) {
                                Text(shown, style = mono.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 6.dp))
                            }
                        }
                    }
                    val status = when {
                        offset != null && patched && original >= 0 -> "Исходное: %02X • изменено: %s".format(original, bytesLabel(modified))
                        modified > 0 -> "Изменено: ${bytesLabel(modified)}"
                        else -> "Нет изменений"
                    }
                    Text(status, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onUndo, enabled = enabled && canUndo, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, "Отменить")
                }
                IconButton(onClick = onRedo, enabled = enabled && canRedo, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.AutoMirrored.Rounded.Redo, "Повторить")
                }
                IconButton(onClick = onInspector, enabled = keysEnabled, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Tune, "Инспектор и запись значений")
                }
                FilledTonalIconButton(onClick = onSave, enabled = enabled && modified > 0, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Save, "Сохранить")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (d in 0..7) DigitKey(d, keysEnabled, tap { onDigit(d) })
                ControlKey(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Предыдущий байт", keysEnabled, tap(onLeft))
                ControlKey(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Следующий байт", keysEnabled, tap(onRight))
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (d in 8..15) DigitKey(d, keysEnabled, tap { onDigit(d) })
                ControlKey(
                    Icons.AutoMirrored.Rounded.Backspace,
                    "Вернуть исходное значение",
                    keysEnabled,
                    tap(onRevert),
                    weight = 2f,
                    tertiary = true,
                )
            }
        }
    }
}

@Composable
private fun RowScope.DigitKey(d: Int, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.weight(1f).height(44.dp),
    ) {
        Text(
            "%X".format(d),
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun RowScope.ControlKey(
    icon: ImageVector,
    desc: String,
    enabled: Boolean,
    onClick: () -> Unit,
    weight: Float = 1f,
    tertiary: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        colors = if (tertiary) {
            ButtonDefaults.filledTonalButtonColors(containerColor = scheme.tertiaryContainer, contentColor = scheme.onTertiaryContainer)
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.weight(weight).height(44.dp),
    ) {
        Icon(icon, desc, Modifier.size(20.dp))
    }
}

// ------------------------------------------------------------------ Инспектор в режиме правки

@Composable
internal fun HexEditSheet(
    vm: HexViewModel,
    offset: Long,
    onDismiss: () -> Unit,
    onWrite: (start: Long, data: ByteArray, label: String) -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Сначала анимированно скрываем лист, затем выполняем запись.
    val finish: (() -> Unit) -> Unit = { action ->
        scope.launch { state.hide() }.invokeOnCompletion { action() }
    }
    val size = vm.fileSize
    val available = (size - offset).coerceAtLeast(0)
    val version = vm.edits.version
    val original by produceState(-1, offset, vm.pageVersion) { value = vm.readOriginalByte(offset) }
    val current = remember(offset, version, original) { vm.edits.get(offset).takeIf { it >= 0 } ?: original }

    var kind by remember { mutableStateOf(IntKind.U8) }
    var little by remember { mutableStateOf(true) }
    var number by remember { mutableStateOf("") }
    var numberError by remember { mutableStateOf<String?>(null) }

    var textMode by remember { mutableStateOf(TextMode.Utf8) }
    var text by remember { mutableStateOf("") }
    var textError by remember { mutableStateOf<String?>(null) }

    val parsed = parseNumber(number)
    val encodedNumber = parsed?.takeIf { it in kind.min..kind.max }?.let { encodeInt(it, kind.bytes, little) }
    val encodedText = encodeText(textMode, text)
    val busy = vm.edits.busy || vm.saving

    fun writeNumber() {
        val v = parseNumber(number)
        numberError = when {
            v == null -> "Некорректное число"
            v !in kind.min..kind.max -> "Вне диапазона ${kind.label}: ${kind.hint}"
            kind.bytes > available -> "Не помещается: до конца файла ${bytesLabel(available)}"
            else -> null
        }
        if (numberError == null && v != null) {
            val data = encodeInt(v, kind.bytes, little)
            val label = "Запись ${kind.label}${if (kind.bytes > 1) if (little) " LE" else " BE" else ""}"
            finish { onWrite(offset, data, label) }
        }
    }

    fun writeText() {
        val data = encodeText(textMode, text)
        textError = when {
            data == null -> "Нужно чётное число HEX-цифр"
            data.isEmpty() -> "Пустая строка"
            data.size > available -> "Не помещается: ${bytesLabel(data.size.toLong())}, до конца файла ${bytesLabel(available)}"
            data.size > MAX_BULK -> "Слишком длинная строка"
            else -> null
        }
        if (textError == null && data != null) finish { onWrite(offset, data, "Вставка строки") }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Смещение", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    Text(
                        "0x%X  •  %,d".format(Format.RU, offset, offset),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        color = scheme.primary,
                    )
                    if (original >= 0 && current >= 0 && current != original) {
                        Text(
                            "Изменён: %02X → %02X".format(original, current),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.tertiary,
                        )
                    }
                }
                IconButton(onClick = { copyToSystemClipboard(context, "0x%X".format(offset)) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.ContentCopy, "Копировать смещение")
                }
            }
            Spacer(Modifier.height(8.dp))
            InspectorGrid(vm, offset)

            SectionLabel("Записать число", Modifier.padding(start = 4.dp))
            EditCard {
                ConnectedChoice(
                    options = IntKind.entries,
                    selected = kind,
                    onSelect = {
                        kind = it
                        numberError = null
                    },
                    label = { it.label },
                )
                if (kind.bytes > 1) {
                    Spacer(Modifier.height(6.dp))
                    ConnectedChoice(
                        options = listOf(true, false),
                        selected = little,
                        onSelect = { little = it },
                        label = { if (it) "Little-endian" else "Big-endian" },
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = number,
                    onValueChange = {
                        number = it
                        numberError = null
                    },
                    singleLine = true,
                    label = { Text("Значение (десятичное или 0x…)") },
                    isError = numberError != null,
                    supportingText = {
                        Text(
                            numberError ?: encodedNumber?.let { "Байты: ${hexPreview(it)}" } ?: "Диапазон: ${kind.hint}",
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onDone = { writeNumber() }),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { writeNumber() },
                    enabled = number.isNotBlank() && !busy,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Записать ${bytesLabel(kind.bytes.toLong())}")
                }
            }

            SectionLabel("Записать поверх", Modifier.padding(start = 4.dp))
            EditCard {
                ConnectedChoice(
                    options = TextMode.entries,
                    selected = textMode,
                    onSelect = {
                        textMode = it
                        textError = null
                    },
                    label = { it.label },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = if (textMode == TextMode.Hex) it.filter { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' || c == ' ' } else it
                        textError = null
                    },
                    maxLines = 4,
                    label = { Text(if (textMode == TextMode.Hex) "HEX-байты" else "Текст") },
                    placeholder = { Text(if (textMode == TextMode.Hex) "DE AD BE EF" else "Строка для записи") },
                    isError = textError != null,
                    supportingText = {
                        Text(
                            textError ?: when {
                                encodedText == null -> "Нужно чётное число HEX-цифр"
                                encodedText.isEmpty() -> "Перезапись без сдвига • до конца файла ${bytesLabel(available)}"
                                else -> "${bytesLabel(encodedText.size.toLong())}: ${hexPreview(encodedText, 8)}"
                            },
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (textMode == TextMode.Hex) KeyboardType.Ascii else KeyboardType.Text,
                        capitalization = if (textMode == TextMode.Hex) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { writeText() },
                    enabled = text.isNotEmpty() && !busy,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Записать поверх")
                }
            }
        }
    }
}

@Composable
private fun EditCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), content = content)
    }
}

// ------------------------------------------------------------------ Заполнение диапазона

@Composable
internal fun FillRangeDialog(
    size: Long,
    initialStart: Long,
    onDismiss: () -> Unit,
    onFill: (start: Long, length: Int, value: Int) -> Unit,
) {
    var hex by remember { mutableStateOf(true) }
    var start by remember { mutableStateOf("%X".format(initialStart)) }
    var length by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("00") }
    var error by remember { mutableStateOf<String?>(null) }

    fun num(t: String): Long? {
        val s = t.trim()
        return when {
            s.startsWith("0x", ignoreCase = true) -> s.drop(2).toLongOrNull(16)
            hex -> s.toLongOrNull(16)
            else -> s.toLongOrNull()
        }
    }

    fun submit() {
        val st = num(start)
        val len = num(length)
        val v = value.trim().removePrefix("0x").removePrefix("0X").toIntOrNull(16)
        error = when {
            st == null || st !in 0 until size -> "Начало вне файла: допустимо 0…0x%X".format(size - 1)
            len == null || len <= 0 -> "Некорректная длина"
            len > size - st -> "Диапазон выходит за конец файла (доступно ${bytesLabel(size - st)})"
            len > MAX_BULK -> "Не больше ${Format.size(MAX_BULK.toLong())} за раз"
            v == null || v !in 0..255 -> "Значение байта — 00…FF"
            else -> null
        }
        if (error == null && st != null && len != null && v != null) onFill(st, len.toInt(), v)
    }

    val field: @Composable (String, String, (String) -> Unit, Boolean) -> Unit = { text, label, onChange, numeric ->
        OutlinedTextField(
            value = text,
            onValueChange = {
                onChange(it)
                error = null
            },
            singleLine = true,
            label = { Text(label) },
            prefix = if (hex || !numeric) {
                { Text("0x") }
            } else null,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric && !hex) KeyboardType.Number else KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.FormatColorFill, null) },
        title = { Text("Заполнить диапазон") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ConnectedChoice(
                    options = listOf(true, false),
                    selected = hex,
                    onSelect = {
                        if (it != hex) {
                            // Пересчитываем введённые числа в новую систему счисления.
                            val s = num(start)
                            val l = num(length)
                            hex = it
                            s?.let { v -> start = if (it) "%X".format(v) else v.toString() }
                            l?.let { v -> length = if (it) "%X".format(v) else v.toString() }
                        }
                        error = null
                    },
                    label = { if (it) "HEX" else "DEC" },
                )
                field(start, "Начальное смещение", { start = it }, true)
                field(length, "Длина, байт", { length = it }, true)
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it.filter { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' }.take(2)
                        error = null
                    },
                    singleLine = true,
                    label = { Text("Значение байта") },
                    prefix = { Text("0x") },
                    isError = error != null,
                    supportingText = {
                        Text(error ?: "Размер файла: 0x%X (%,d байт)".format(Format.RU, size, size))
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { Button(onClick = { submit() }, shapes = ButtonDefaults.shapes()) { Text("Заполнить") } },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Отмена") } },
    )
}

// ------------------------------------------------------------------ Сохранение

/**
 * Диалог сохранения. [exiting] — вызван при выходе с экрана: добавляется кнопка «Не сохранять».
 */
@Composable
internal fun SaveChangesDialog(
    path: String,
    modified: Long,
    exiting: Boolean,
    backup: Boolean,
    onBackupChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    val name = remember(path) { File(path).name }
    val bakExists by produceState(false, path) {
        value = withContext(Dispatchers.IO) { runCatching { File("$path.bak").exists() }.getOrDefault(false) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Save, null) },
        title = { Text(if (exiting) "Сохранить изменения?" else "Сохранить файл") },
        text = {
            Column {
                Text(
                    if (exiting) "В файле «$name» есть несохранённые правки: ${bytesLabel(modified)}."
                    else "Будет перезаписано ${bytesLabel(modified)} в файле «$name». Размер файла не изменится.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                CheckRow("Создать резервную копию «$name.bak»", backup, onBackupChange)
                if (backup && bakExists) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Существующая копия будет перезаписана",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onSave, shapes = ButtonDefaults.shapes()) { Text("Сохранить") } },
        dismissButton = {
            Row {
                if (exiting) TextButton(onClick = onDiscard, shapes = ButtonDefaults.shapes()) { Text("Не сохранять") }
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Отмена") }
            }
        },
    )
}
