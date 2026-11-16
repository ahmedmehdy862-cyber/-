package com.focusstudy.poc

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Routines must keep their clock time and never come back already overdue. */
class RecurrenceEngineTest {

    private val zone = ZoneId.of("UTC")
    private fun at(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun date(ms: Long): LocalDateTime =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), zone)

    @Test fun dailyKeepsTheClockTime() {
        val next = RecurrenceEngine.next(at(2026, 11, 16, 18, 30), Recurrence.DAILY, at(2026, 11, 16, 19, 5), zone)
        assertEquals(LocalDateTime.of(2026, 11, 17, 18, 30), date(next!!))
    }

    @Test fun weeklyAddsSevenDays() {
        val next = RecurrenceEngine.next(at(2026, 11, 16, 7, 0), Recurrence.WEEKLY, at(2026, 11, 16, 9, 0), zone)
        assertEquals(LocalDateTime.of(2026, 11, 23, 7, 0), date(next!!))
    }

    @Test fun monthlyClampsShortMonths() {
        val next = RecurrenceEngine.next(at(2027, 1, 31, 9, 0), Recurrence.MONTHLY, at(2027, 2, 1, 9, 0), zone)
        assertEquals(LocalDateTime.of(2027, 2, 28, 9, 0), date(next!!))
    }

    @Test fun catchingUpSkipsMissedOccurrences() {
        // A daily routine last done two weeks ago continues after today, not in the past.
        val next = RecurrenceEngine.next(at(2026, 11, 1, 6, 0), Recurrence.DAILY, at(2026, 11, 16, 22, 0), zone)
        assertEquals(LocalDateTime.of(2026, 11, 17, 6, 0), date(next!!))
    }

    @Test fun completingEarlyKeepsTheNextCycle() {
        val next = RecurrenceEngine.next(at(2026, 11, 20, 8, 0), Recurrence.DAILY, at(2026, 11, 16, 12, 0), zone)
        assertEquals(LocalDateTime.of(2026, 11, 21, 8, 0), date(next!!))
    }

    @Test fun noRecurrenceHasNoNextDate() {
        assertNull(RecurrenceEngine.next(at(2026, 11, 16), Recurrence.NONE, at(2026, 11, 16), zone))
    }

    @Test fun missingDueDateCountsFromCompletion() {
        val next = RecurrenceEngine.next(null, Recurrence.WEEKLY, at(2026, 11, 16, 15, 20), zone)
        assertEquals(LocalDateTime.of(2026, 11, 23, 15, 20), date(next!!))
    }

    @Test fun labelsDescribeTheRoutine() {
        assertEquals("يتكرر يوميًا", RecurrenceEngine.label(Recurrence.DAILY))
        assertEquals("يتكرر أسبوعيًا", RecurrenceEngine.label(Recurrence.WEEKLY))
        assertEquals("يتكرر شهريًا", RecurrenceEngine.label(Recurrence.MONTHLY))
        assertEquals("", RecurrenceEngine.label(Recurrence.NONE))
    }

    @Test fun firstWeekdayIncludesToday() {
        val sunday = LocalDate.of(2026, 11, 15)
        assertEquals(sunday, RecurrenceEngine.firstWeekday(sunday, DayOfWeek.SUNDAY))
        assertEquals(LocalDate.of(2026, 11, 21), RecurrenceEngine.firstWeekday(sunday, DayOfWeek.SATURDAY))
    }
}
