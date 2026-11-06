package client.wexmanager.core.db

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.IOException

/** Таблица или представление базы. */
data class DbTable(val name: String, val type: String, val rows: Long)

/** Ячейка: текст для показа, исходное значение (для правки) и признак BLOB/NULL. */
data class DbCell(val text: String, val isNull: Boolean = false, val isBlob: Boolean = false)

/** Результат запроса: колонки и строки; [rowIds] — rowid строк (если таблица их имеет). */
data class DbResult(
    val columns: List<String>,
    val rows: List<List<DbCell>>,
    val rowIds: List<Long?> = emptyList(),
    val changed: Int? = null,
)

/**
 * Просмотр и правка SQLite (как редактор баз в MT Manager). По умолчанию база открывается только для
 * чтения; правка — явным режимом, изменения пишутся прямо в файл.
 */
class SqliteBrowser private constructor(private val db: SQLiteDatabase, val writable: Boolean) : AutoCloseable {

    fun tables(): List<DbTable> {
        val out = ArrayList<DbTable>()
        db.rawQuery("SELECT name, type FROM sqlite_master WHERE type IN ('table','view') ORDER BY type DESC, name", null).use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0)
                val rows = runCatching { db.rawQuery("SELECT count(*) FROM ${quote(name)}", null).use { r -> if (r.moveToFirst()) r.getLong(0) else 0L } }.getOrDefault(-1L)
                out += DbTable(name, c.getString(1), rows)
            }
        }
        return out
    }

    /** Строки таблицы с [offset]; rowid берётся, если он у таблицы есть. */
    fun page(table: String, offset: Int, limit: Int): DbResult {
        val withRowId = hasRowId(table)
        val sql = if (withRowId) "SELECT rowid AS __wex_rowid, * FROM ${quote(table)} LIMIT $limit OFFSET $offset"
        else "SELECT * FROM ${quote(table)} LIMIT $limit OFFSET $offset"
        return db.rawQuery(sql, null).use { read(it, withRowId) }
    }

    /** Произвольный SQL: SELECT/PRAGMA/WITH — таблица, остальное — число изменённых строк. */
    fun execute(sql: String): DbResult {
        val trimmed = sql.trim().trimEnd(';')
        // Первое слово — до любого пробельного символа (после SELECT бывает перевод строки).
        val head = trimmed.split(Regex("\\s+"), limit = 2).first().uppercase()
        if (head in setOf("SELECT", "PRAGMA", "WITH", "EXPLAIN", "VALUES")) {
            return db.rawQuery(trimmed, null).use { read(it, false) }
        }
        if (!writable) throw IOException("База открыта только для чтения — включите режим правки")
        db.compileStatement(trimmed).use { st ->
            val changed = if (head in setOf("INSERT", "UPDATE", "DELETE", "REPLACE")) st.executeUpdateDelete() else { st.execute(); 0 }
            return DbResult(emptyList(), emptyList(), changed = changed)
        }
    }

    /** Новое значение ячейки по rowid; «NULL» в [text] при [setNull]. */
    fun update(table: String, column: String, rowId: Long, text: String?, setNull: Boolean) {
        if (!writable) throw IOException("Режим только для чтения")
        db.compileStatement("UPDATE ${quote(table)} SET ${quote(column)} = ? WHERE rowid = ?").use { st ->
            if (setNull || text == null) st.bindNull(1) else st.bindString(1, text)
            st.bindLong(2, rowId)
            st.executeUpdateDelete()
        }
    }

    fun deleteRow(table: String, rowId: Long) {
        if (!writable) throw IOException("Режим только для чтения")
        db.compileStatement("DELETE FROM ${quote(table)} WHERE rowid = ?").use { st -> st.bindLong(1, rowId); st.executeUpdateDelete() }
    }

    /** Схема таблицы (CREATE …). */
    fun schema(table: String): String =
        db.rawQuery("SELECT sql FROM sqlite_master WHERE name = ?", arrayOf(table)).use { c -> if (c.moveToFirst()) c.getString(0).orEmpty() else "" }

    private fun hasRowId(table: String): Boolean = runCatching {
        db.rawQuery("SELECT rowid FROM ${quote(table)} LIMIT 0", null).use { true }
    }.getOrDefault(false)

    private fun read(c: Cursor, withRowId: Boolean): DbResult {
        val names = c.columnNames.toList()
        val first = if (withRowId) 1 else 0
        val rows = ArrayList<List<DbCell>>()
        val ids = ArrayList<Long?>()
        while (c.moveToNext() && rows.size < MAX_ROWS) {
            ids += if (withRowId) c.getLong(0) else null
            rows += (first until names.size).map { i -> cell(c, i) }
        }
        return DbResult(names.drop(first), rows, ids)
    }

    private fun cell(c: Cursor, i: Int): DbCell = when (c.getType(i)) {
        Cursor.FIELD_TYPE_NULL -> DbCell("NULL", isNull = true)
        Cursor.FIELD_TYPE_BLOB -> {
            val b = runCatching { c.getBlob(i) }.getOrNull()
            DbCell(if (b == null) "BLOB" else "BLOB · ${b.size} Б · " + b.take(16).joinToString(" ") { "%02X".format(it) }, isBlob = true)
        }
        else -> DbCell(runCatching { c.getString(i) }.getOrDefault("") ?: "")
    }

    override fun close() = db.close()

    companion object {
        const val MAX_ROWS = 5000
        private val MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)

        fun quote(name: String) = "\"" + name.replace("\"", "\"\"") + "\""

        /**
         * Текущий результат в CSV (RFC 4180: кавычки, удвоенные кавычки, CRLF) в папке базы.
         * Имя — «<имя базы без расширения>-result.csv». NULL — пустое поле.
         */
        fun exportCsv(database: File, result: DbResult): File {
            if (result.columns.isEmpty()) throw IOException("Нет результата для экспорта")
            val dir = database.parentFile?.takeIf { it.isDirectory } ?: throw IOException("Нет папки базы")
            val stem = database.name.substringBeforeLast('.').ifEmpty { database.name }.ifBlank { "db" }
            val out = File(dir, "$stem-result.csv")
            val part = File(dir, "$stem-result.csv.part")
            try {
                part.bufferedWriter(Charsets.UTF_8).use { w ->
                    w.write(csvLine(result.columns))
                    for (row in result.rows) w.write(csvLine(row.map { cell -> if (cell.isNull) "" else cell.text }))
                }
                if (!part.renameTo(out)) {
                    part.copyTo(out, overwrite = true)
                    if (!part.delete()) part.deleteOnExit()
                }
            } catch (e: IOException) {
                part.delete()
                throw e
            } catch (e: Exception) {
                part.delete()
                throw IOException(e.message?.let { "Не удалось записать CSV: $it" } ?: "Не удалось записать CSV")
            }
            return out
        }

        /** Поле по RFC 4180: кавычки, если есть запятая, кавычка или перевод строки; кавычка удваивается. */
        private fun appendCsvField(out: StringBuilder, value: String) {
            val quote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
            if (!quote) {
                out.append(value)
                return
            }
            out.append('"')
            for (c in value) {
                if (c == '"') out.append('"')
                out.append(c)
            }
            out.append('"')
        }

        private fun csvLine(fields: List<String>): String {
            val sb = StringBuilder()
            fields.forEachIndexed { i, value ->
                if (i > 0) sb.append(',')
                appendCsvField(sb, value)
            }
            sb.append("\r\n")
            return sb.toString()
        }

        fun isSqlite(file: File): Boolean = runCatching {
            file.inputStream().use { s -> val h = ByteArray(16); s.read(h) == 16 && h.contentEquals(MAGIC) }
        }.getOrDefault(false)

        /** Открыть [file]; без прав на запись в папку (журнал WAL) — копия для чтения в [cacheDir]. */
        fun open(file: File, writable: Boolean, cacheDir: File): SqliteBrowser {
            if (!isSqlite(file)) throw IOException("Не база SQLite")
            val flags = (if (writable) SQLiteDatabase.OPEN_READWRITE else SQLiteDatabase.OPEN_READONLY) or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            return try {
                SqliteBrowser(SQLiteDatabase.openDatabase(file.path, null, flags), writable)
            } catch (e: Exception) {
                if (writable) throw IOException("Не удалось открыть для правки: ${e.message}")
                // Своя копия на каждый путь: две одноимённые базы не должны перезаписывать друг друга.
                val copy = File(cacheDir, "db/${file.absolutePath.hashCode().toUInt()}/${file.name}")
                copy.parentFile?.mkdirs()
                file.copyTo(copy, overwrite = true)
                listOf("-wal", "-shm").forEach { s -> File(file.path + s).takeIf { it.isFile }?.copyTo(File(copy.path + s), overwrite = true) }
                SqliteBrowser(SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS), false)
            }
        }
    }
}
