package client.wexmanager.core.util

import client.wexmanager.core.settings.DateFormatOption
import client.wexmanager.core.settings.SizeUnits
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Format {
    /** Интерфейс приложения русскоязычный — даты и числа форматируем по-русски независимо от системы. */
    val RU: Locale = Locale.forLanguageTag("ru-RU")
    private val symbols = java.text.DecimalFormatSymbols(RU)
    private val oneDecimal = ThreadLocal.withInitial { DecimalFormat("#,##0.#", symbols) }
    private val twoDecimals = ThreadLocal.withInitial { DecimalFormat("#,##0.##", symbols) }
    private val binaryUnits = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ", "ПБ")

    fun size(bytes: Long, units: SizeUnits = SizeUnits.Binary): String {
        if (bytes < 0) return "—"
        val base = if (units == SizeUnits.Binary) 1024.0 else 1000.0
        if (bytes < base) return "$bytes ${binaryUnits[0]}"
        var value = bytes.toDouble()
        var unit = 0
        while (value >= base && unit < binaryUnits.lastIndex) {
            value /= base
            unit++
        }
        val fmt = if (value < 10) twoDecimals.get()!! else oneDecimal.get()!!
        return "${fmt.format(value)} ${binaryUnits[unit]}"
    }

    fun bytesExact(bytes: Long): String = DecimalFormat("#,##0", symbols).format(bytes) + " байт"

    private val shortDate = ThreadLocal.withInitial { SimpleDateFormat("dd.MM.yy HH:mm", RU) }
    private val fullDate = ThreadLocal.withInitial { SimpleDateFormat("d MMMM yyyy, HH:mm:ss", RU) }
    private val timeOnly = ThreadLocal.withInitial { SimpleDateFormat("HH:mm", RU) }
    private val dayMonth = ThreadLocal.withInitial { SimpleDateFormat("d MMM", RU) }
    private val dayMonthYear = ThreadLocal.withInitial { SimpleDateFormat("d MMM yyyy", RU) }
    private val shortDay = ThreadLocal.withInitial { SimpleDateFormat("dd.MM.yy", RU) }

    fun date(millis: Long, option: DateFormatOption, now: Long = System.currentTimeMillis()): String {
        if (millis <= 0) return "—"
        return when (option) {
            DateFormatOption.Short -> shortDate.get()!!.format(Date(millis))
            DateFormatOption.Full -> fullDate.get()!!.format(Date(millis))
            DateFormatOption.Relative -> relative(millis, now)
        }
    }

    fun fullDate(millis: Long): String = if (millis <= 0) "—" else fullDate.get()!!.format(Date(millis))

    /** «25 сен 2056» — дата без времени. */
    fun day(millis: Long): String = if (millis <= 0) "—" else dayMonthYear.get()!!.format(Date(millis))

    fun relative(millis: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - millis
        if (diff in 0 until 60_000L) return "только что"
        if (diff in 0 until 3_600_000L) return "${diff / 60_000} мин назад"
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val then = Calendar.getInstance().apply { timeInMillis = millis }
        val sameYear = cal.get(Calendar.YEAR) == then.get(Calendar.YEAR)
        val dayDiff = cal.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
        return when {
            sameYear && dayDiff == 0 -> "сегодня, ${timeOnly.get()!!.format(Date(millis))}"
            sameYear && dayDiff == 1 -> "вчера, ${timeOnly.get()!!.format(Date(millis))}"
            sameYear -> dayMonth.get()!!.format(Date(millis))
            else -> dayMonthYear.get()!!.format(Date(millis))
        }
    }

    /** «только что» (< 45 с), «N мин назад», «N ч назад», «вчера» или «дд.мм.гг». */
    fun relativeTime(epochMs: Long, now: Long): String {
        if (epochMs <= 0L) return "—"
        val diff = now - epochMs
        if (diff < 0L) return shortDay.get()!!.format(Date(epochMs))
        if (diff < 45_000L) return "только что"
        val minutes = diff / 60_000L
        if (minutes < 60L) return "${minutes.coerceAtLeast(1L)} мин назад"
        val hours = diff / 3_600_000L
        if (hours < 24L) return "$hours ч назад"
        if (isYesterday(epochMs, now)) return "вчера"
        return shortDay.get()!!.format(Date(epochMs))
    }

    private fun isYesterday(epochMs: Long, now: Long): Boolean {
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val then = Calendar.getInstance().apply { timeInMillis = epochMs }
        today.add(Calendar.DATE, -1)
        return today.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            today.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    }

    fun items(count: Int): String = "$count ${plural(count, "элемент", "элемента", "элементов")}"
    fun files(count: Int): String = "$count ${plural(count, "файл", "файла", "файлов")}"
    fun folders(count: Int): String = "$count ${plural(count, "папка", "папки", "папок")}"

    fun plural(n: Int, one: String, few: String, many: String): String {
        val mod10 = n % 10
        val mod100 = n % 100
        return when {
            mod10 == 1 && mod100 != 11 -> one
            mod10 in 2..4 && mod100 !in 12..14 -> few
            else -> many
        }
    }

    fun duration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(RU, "%d:%02d:%02d", h, m, s) else String.format(RU, "%d:%02d", m, s)
    }

    fun speed(bytesPerSecond: Long): String = size(bytesPerSecond) + "/с"
}
