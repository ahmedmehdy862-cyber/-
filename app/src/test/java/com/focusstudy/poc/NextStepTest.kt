package com.focusstudy.poc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class NextStepTest {
    private val zone = ZoneId.systemDefault()
    private val now = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli() + 9 * 3_600_000L
    private val hour = 3_600_000L
    private val day = 24 * hour

    private fun task(
        id: Long,
        title: String,
        planned: Int = 30,
        priority: TaskPriority = TaskPriority.MEDIUM,
        dueAt: Long? = null,
        subjectId: Long? = null,
        originalDueAt: Long? = null,
        status: TaskStatus = TaskStatus.TODO
    ) = StudyTask(
        id = id,
        title = title,
        plannedMinutes = planned,
        doneMinutes = 0,
        isDone = status == TaskStatus.DONE,
        blockedPackages = emptySet(),
        createdAt = now - day,
        priority = priority,
        status = status,
        dueAt = dueAt,
        subjectId = subjectId,
        originalDueAt = originalDueAt
    )

    @Test
    fun `no open task means no next step`() {
        val done = task(1, "منتهية", status = TaskStatus.DONE)
        assertNull(NextStepPlanner.pick(listOf(done), now))
        assertNull(NextStepPlanner.pick(emptyList(), now))
    }

    @Test
    fun `an exam in two days outranks an older low priority task`() {
        val examSubject = 7L
        val examTask = task(1, "مراجعة الكيمياء", subjectId = examSubject, priority = TaskPriority.MEDIUM)
        val oldTask = task(2, "ترتيب المكتب", priority = TaskPriority.LOW, dueAt = now - 5 * day)
        val step = NextStepPlanner.pick(
            listOf(oldTask, examTask),
            now,
            subjectNames = mapOf(examSubject to "الكيمياء"),
            examBySubject = mapOf(examSubject to now + 2 * day)
        )
        assertEquals(examTask.id, step?.task?.id)
        assertTrue(step!!.reason.contains("الكيمياء"))
        assertTrue(step.reason.contains("بعد يومين"))
    }

    @Test
    fun `an overdue task explains how late it is`() {
        val late = task(3, "تقرير", dueAt = now - 3 * day)
        val step = NextStepPlanner.pick(listOf(late), now)
        assertEquals("تأخرت 3 أيام عن موعدها", step?.reason)
    }

    @Test
    fun `a task due today shows its time`() {
        val due = now + 2 * hour
        val step = NextStepPlanner.pick(listOf(task(4, "واجب", dueAt = due)), now)
        assertTrue(step!!.reason.startsWith("موعدها اليوم"))
    }

    @Test
    fun `a nearby prayer changes the advice`() {
        val step = NextStepPlanner.pick(
            listOf(task(5, "قراءة")),
            now,
            prayerAtMillis = now + 10 * 60_000L
        )
        assertTrue(step!!.reason.contains("الصلاة"))
    }

    @Test
    fun `suggested minutes respect the remaining work`() {
        val step = NextStepPlanner.pick(listOf(task(6, "قصيرة", planned = 10)), now)
        assertEquals(10, step!!.suggestedMinutes)
        val longStep = NextStepPlanner.pick(listOf(task(7, "طويلة", planned = 120)), now)
        assertEquals(25, longStep!!.suggestedMinutes)
    }

    @Test
    fun `smart sort puts the urgent exam task first`() {
        val examSubject = 9L
        val tasks = listOf(
            task(10, "مهمة قديمة", priority = TaskPriority.LOW, dueAt = now - day),
            task(11, "امتحان الغد", subjectId = examSubject),
            task(12, "مهمة عادية", priority = TaskPriority.MEDIUM)
        )
        val sorted = SmartOrder.sort(tasks, now, mapOf(examSubject to now + day))
        assertEquals(11L, sorted.first().id)
    }

    @Test
    fun `smart sort keeps high priority ahead of low when nothing is urgent`() {
        val sorted = SmartOrder.sort(
            listOf(task(20, "منخفضة", priority = TaskPriority.LOW), task(21, "عالية", priority = TaskPriority.HIGH)),
            now,
            emptyMap()
        )
        assertEquals(21L, sorted.first().id)
    }

    @Test
    fun `dismissed tasks never become the next step`() {
        val cancelled = task(30, "ملغاة", status = TaskStatus.CANCELLED)
        assertNull(NextStepPlanner.pick(listOf(cancelled), now))
    }

    @Test
    fun `a postponed task mentions that it slipped`() {
        val moved = task(40, "تحليل", dueAt = now + 5 * day, originalDueAt = now - day)
        val step = NextStepPlanner.pick(listOf(moved), now)
        assertEquals("أُجّلت مرة، والأفضل إنهاؤها الآن", step?.reason)
    }
}
