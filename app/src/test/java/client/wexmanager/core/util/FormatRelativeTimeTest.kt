package client.wexmanager.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class FormatRelativeTimeTest {
    private val now = at(2026, Calendar.OCTOBER, 4, 15, 0)

    @Test
    fun justNowUnder45Seconds() {
        assertEquals("только что", Format.relativeTime(now, now))
        assertEquals("только что", Format.relativeTime(now - 44_999L, now))
    }

    @Test
    fun minutesFrom45SecondsUntilOneHour() {
        assertEquals("1 мин назад", Format.relativeTime(now - 45_000L, now))
        assertEquals("1 мин назад", Format.relativeTime(now - 60_000L, now))
        assertEquals("2 мин назад", Format.relativeTime(now - 2 * 60_000L, now))
        assertEquals("59 мин назад", Format.relativeTime(now - 59 * 60_000L, now))
    }

    @Test
    fun hoursUntilOneDayEvenIfCalendarYesterday() {
        assertEquals("1 ч назад", Format.relativeTime(now - 3_600_000L, now))
        assertEquals("5 ч назад", Format.relativeTime(now - 5 * 3_600_000L, now))
        assertEquals("23 ч назад", Format.relativeTime(now - 23 * 3_600_000L, now))
    }

    @Test
    fun yesterdayAfter24Hours() {
        assertEquals("вчера", Format.relativeTime(now - 25 * 3_600_000L, now))
        val newYear = at(2026, Calendar.JANUARY, 1, 12, 0)
        assertEquals("вчера", Format.relativeTime(at(2025, Calendar.DECEMBER, 31, 12, 0), newYear))
    }

    @Test
    fun olderAndFutureAreShortDates() {
        assertEquals("02.10.26", Format.relativeTime(at(2026, Calendar.OCTOBER, 2, 15, 0), now))
        assertEquals("30.12.25", Format.relativeTime(at(2025, Calendar.DECEMBER, 30, 12, 0), at(2026, Calendar.JANUARY, 1, 12, 0)))
        assertEquals("05.10.26", Format.relativeTime(at(2026, Calendar.OCTOBER, 5, 15, 0), now))
        assertEquals("—", Format.relativeTime(0L, now))
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
