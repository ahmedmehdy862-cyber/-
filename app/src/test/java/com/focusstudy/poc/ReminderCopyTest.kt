package com.focusstudy.poc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderCopyTest {

    @Test fun nudgeReadsNaturallyForEveryCount() {
        assertEquals("عندك مهمة واحدة. واحدة فقط، والخطوة الأولى أقرب مما تظن.",
            ReminderCopy.taskNudge("", total = 1, overdue = 0, nextTitle = "Physics"))
        assertEquals("عندك مهمتان. أقرب خطوة: Physics",
            ReminderCopy.taskNudge("", total = 2, overdue = 0, nextTitle = "Physics"))
        assertEquals("عندك 5 مهام. أقرب خطوة: Physics",
            ReminderCopy.taskNudge("", total = 5, overdue = 0, nextTitle = "Physics"))
        assertEquals("عندك 12 مهمة. أقرب خطوة: Math",
            ReminderCopy.taskNudge("", total = 12, overdue = 0, nextTitle = "Math"))
    }

    @Test fun nudgeNamesTheStudentAndTheOverdueWork() {
        val one = ReminderCopy.taskNudge("أحمد", total = 4, overdue = 1, nextTitle = "Physics")
        assertEquals("يا أحمد، عندك 4 مهام. منها مهمة واحدة متأخرة — نبدأ بأصغر واحدة.", one)

        val two = ReminderCopy.taskNudge("أحمد", total = 4, overdue = 2, nextTitle = "Physics")
        assertTrue(two.contains("منها مهمتان متأخرتان"))

        val many = ReminderCopy.taskNudge("أحمد", total = 14, overdue = 12, nextTitle = "Physics")
        assertTrue(many.contains("منها 12 مهمة متأخرة"))
    }

    @Test fun morningEncouragementUsesRealCounts() {
        val text = ReminderCopy.encouragement("أحمد", morning = true, dueToday = 3, open = 3, dayOfYear = 1)
        assertTrue(text.startsWith("أحمد، "))
        assertTrue(text.contains("مهام اليوم جاهزة: 3 مهام"))
    }

    @Test fun emptyNameFallsBackToAWarmAddress() {
        val text = ReminderCopy.encouragement("", morning = false, dueToday = 0, open = 0, dayOfYear = 2)
        assertTrue(text.startsWith("يا بطل، "))
        assertTrue(text.contains("لا شيء ينتظرك الليلة"))
    }

    @Test fun eveningWithoutScheduleInvitesOneTask() {
        val text = ReminderCopy.encouragement("أحمد", morning = true, dueToday = 0, open = 7, dayOfYear = 4)
        assertTrue(text.contains("لا شيء مجدول اليوم، وعندك 7 مهام مفتوحة"))
    }

    @Test fun textNeverShowsADanglingPlaceholder() {
        val text = ReminderCopy.taskNudge("أحمد", total = 3, overdue = 0, nextTitle = "Chemistry")
        assertFalse(text.contains("null"))
        assertFalse(text.contains("${'$'}"))
    }
}
