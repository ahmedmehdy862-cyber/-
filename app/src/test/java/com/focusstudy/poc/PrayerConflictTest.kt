package com.focusstudy.poc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The planner must never drop a study block inside a prayer window. */
class PrayerConflictTest {

    private val minute = 60_000L
    private fun t(hour: Int, min: Int = 0) = (hour * 60L + min) * minute
    private val dayEnd = t(14)

    /** A busy calendar event between two clock times. */
    private fun event(fromHour: Int, fromMin: Int, toHour: Int, toMin: Int) = StudyEvent(
        id = fromHour * 100L + fromMin,
        title = "e",
        kind = "study",
        startsAt = t(fromHour, fromMin),
        endsAt = t(toHour, toMin)
    )

    /** 12:00 → 12:20, i.e. 10 minutes before … 20 after a 12:10 prayer. */
    private val window = t(12)..t(12, 20)

    @Test fun blockInsideWindowIsDetected() {
        assertTrue(ScheduleConflictDetector.overlapsPrayer(t(12, 5), t(12, 25), listOf(window)))
        assertTrue(ScheduleConflictDetector.overlapsPrayer(t(11, 55), t(12, 5), listOf(window)))
        assertFalse(ScheduleConflictDetector.overlapsPrayer(t(13), t(13, 30), listOf(window)))
    }

    @Test fun blockEndingExactlyAtWindowStartIsAllowed() {
        assertFalse(ScheduleConflictDetector.overlapsPrayer(t(11, 30), t(12), listOf(window)))
    }

    @Test fun nextAvailableSkipsWindow() {
        // 11:50 + 30 minutes would run into 12:00, so the block starts right after the window.
        val start = ScheduleConflictDetector.nextAvailableStart(
            t(11, 50), 30 * minute, dayEnd, emptyList(), listOf(window)
        )
        assertEquals(t(12, 20) + 1, start)
    }

    @Test fun nextAvailableRespectsEventsAndWindowTogether() {
        // Free from 11:00, but 11:30–11:45 is busy and 12:00–12:20 is prayer: 45 minutes only fit after that.
        val start = ScheduleConflictDetector.nextAvailableStart(
            t(11), 45 * minute, dayEnd, listOf(event(11, 30, 11, 45)), listOf(window)
        )
        assertEquals(t(12, 20) + 1, start)
    }

    @Test fun noRoomBeforeDayEndReturnsNull() {
        assertNull(
            ScheduleConflictDetector.nextAvailableStart(
                t(12), 60 * minute, t(12, 50), emptyList(), listOf(window)
            )
        )
    }

    @Test fun noWindowsKeepsOldBehaviour() {
        assertEquals(t(9), ScheduleConflictDetector.nextAvailableStart(t(9), 30 * minute, dayEnd, emptyList()))
        assertEquals(t(9, 45), ScheduleConflictDetector.nextAvailableStart(t(9), 60 * minute, dayEnd, listOf(event(9, 15, 9, 45))))
    }
}
