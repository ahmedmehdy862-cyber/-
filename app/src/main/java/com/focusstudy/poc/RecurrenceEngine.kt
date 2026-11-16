package com.focusstudy.poc

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Recurrence math for routines, kept pure so it can be tested without a database.
 *
 * Two behaviours matter for a real organizer:
 *  - the clock time of the original due date is kept (a 18:30 workout repeats at 18:30,
 *    not at midnight), and
 *  - finishing late never creates an occurrence that is already overdue: the next date is
 *    always strictly after the day the task was completed.
 */
object RecurrenceEngine {

    fun next(
        dueAt: Long?,
        recurrence: Recurrence,
        completedAt: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long? {
        if (recurrence == Recurrence.NONE) return null
        val due = dueAt
            ?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
            ?: LocalDateTime.ofInstant(Instant.ofEpochMilli(completedAt), zone).withSecond(0).withNano(0)
        val completedDate = Instant.ofEpochMilli(completedAt).atZone(zone).toLocalDate()
        var candidate = advance(due, recurrence)
        var guard = 0
        while (!candidate.toLocalDate().isAfter(completedDate) && guard++ < 400) {
            candidate = advance(candidate, recurrence)
        }
        return candidate.atZone(zone).toInstant().toEpochMilli()
    }

    private fun advance(from: LocalDateTime, recurrence: Recurrence): LocalDateTime = when (recurrence) {
        Recurrence.DAILY -> from.plusDays(1)
        Recurrence.WEEKLY -> from.plusWeeks(1)
        Recurrence.MONTHLY -> from.plusMonths(1)
        Recurrence.NONE -> from
    }

    fun label(recurrence: Recurrence): String = when (recurrence) {
        Recurrence.NONE -> ""
        Recurrence.DAILY -> "يتكرر يوميًا"
        Recurrence.WEEKLY -> "يتكرر أسبوعيًا"
        Recurrence.MONTHLY -> "يتكرر شهريًا"
    }

    /** The first date at or after [from] that falls on [day]; today counts for a routine. */
    fun firstWeekday(from: LocalDate, day: java.time.DayOfWeek): LocalDate {
        val diff = (day.value - from.dayOfWeek.value + 7) % 7
        return from.plusDays(diff.toLong())
    }
}
