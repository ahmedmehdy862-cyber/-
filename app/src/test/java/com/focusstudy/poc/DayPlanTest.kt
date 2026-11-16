package com.focusstudy.poc

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The today screen must answer “what now?” and stay short. */
class DayPlanTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 16)

    private fun ms(day: Int, hour: Int = 0) =
        LocalDateTime.of(2026, 11, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun task(
        id: Long,
        dueAt: Long? = null,
        done: Boolean = false,
        status: TaskStatus = TaskStatus.TODO,
        priority: TaskPriority = TaskPriority.MEDIUM,
        recurrence: Recurrence = Recurrence.NONE
    ) = StudyTask(
        id = id,
        title = "t$id",
        plannedMinutes = 30,
        doneMinutes = 0,
        isDone = done,
        blockedPackages = emptySet(),
        createdAt = 0,
        dueAt = dueAt,
        status = status,
        priority = priority,
        recurrence = recurrence
    )

    @Test fun lateWorkComesFirst() {
        val groups = DayPlan.build(
            listOf(task(1, ms(16, 9)), task(2, ms(14, 9)), task(3, null)),
            today, zone
        )
        assertEquals(listOf(DayBucket.OVERDUE, DayBucket.TODAY, DayBucket.UNDATED), groups.map { it.bucket })
        assertEquals(2L, groups[0].tasks.single().id)
        assertEquals(1L, groups[1].tasks.single().id)
        assertEquals(3L, groups[2].tasks.single().id)
    }

    @Test fun futureWorkStaysOutOfToday() {
        val groups = DayPlan.build(listOf(task(1, ms(20, 9)), task(2, ms(16, 12))), today, zone)
        assertEquals(listOf(DayBucket.TODAY), groups.map { it.bucket })
    }

    @Test fun doneAndCancelledAreHidden() {
        val groups = DayPlan.build(
            listOf(
                task(1, ms(16), done = true),
                task(2, ms(16), status = TaskStatus.CANCELLED),
                task(3, ms(16))
            ),
            today, zone
        )
        assertEquals(3L, groups.single().tasks.single().id)
    }

    @Test fun todayIsOrderedByClockTime() {
        val groups = DayPlan.build(listOf(task(1, ms(16, 18)), task(2, ms(16, 9)), task(3, ms(16, 13))), today, zone)
        assertEquals(listOf(2L, 3L, 1L), groups.single().tasks.map { it.id })
    }

    @Test fun sameTimeFallsBackToPriority() {
        val groups = DayPlan.build(
            listOf(task(1, ms(16, 9), priority = TaskPriority.LOW), task(2, ms(16, 9), priority = TaskPriority.URGENT)),
            today, zone
        )
        assertEquals(listOf(2L, 1L), groups.single().tasks.map { it.id })
    }

    @Test fun emptyDayHasNoGroups() {
        assertTrue(DayPlan.build(emptyList(), today, zone).isEmpty())
    }

    @Test fun midnightMeansAllDay() {
        assertEquals("طوال اليوم", DayPlan.timeLabel(task(1, ms(16)), zone))
    }

    @Test fun timedTaskShowsItsClock() {
        assertEquals("18:00", DayPlan.timeLabel(task(1, ms(16, 18)), zone))
    }

    @Test fun undatedTaskSaysNoTime() {
        assertEquals("بلا وقت", DayPlan.timeLabel(task(1), zone))
    }
}
