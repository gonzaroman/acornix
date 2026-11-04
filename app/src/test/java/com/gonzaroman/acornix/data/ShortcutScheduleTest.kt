package com.gonzaroman.acornix.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ShortcutScheduleTest {
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, min) }
    private fun fmt(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }.let {
        "%04d-%02d-%02d %02d:%02d".format(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH), it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE))
    }

    @Test
    fun everyDayAtEight() {
        val s = ShortcutSchedule("cnbc", 8, 0)
        assertEquals("2026-11-04 08:00", fmt(s.next(at(2026, 11, 4, 7, 30))))   // later today
        assertEquals("2026-11-05 08:00", fmt(s.next(at(2026, 11, 4, 8, 0))))    // just ran: tomorrow
        assertEquals("2026-11-05 08:00", fmt(s.next(at(2026, 11, 4, 12, 0))))
    }

    @Test
    fun onlyWorkdays() {
        val s = ShortcutSchedule("cnbc", 8, 0, days = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY))
        // Friday 6 Nov 2026 after 8:00 → Monday 9 Nov.
        assertEquals("2026-11-09 08:00", fmt(s.next(at(2026, 11, 6, 9, 0))))
    }
}
