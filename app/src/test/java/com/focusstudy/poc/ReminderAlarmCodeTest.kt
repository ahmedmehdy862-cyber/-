package com.focusstudy.poc

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderAlarmCodeTest {
    @Test fun leadAndDueRemindersGetDifferentRequestCodes() {
        val lead = ReminderEngine.alarmRequestCode(101)
        val due = ReminderEngine.alarmRequestCode(102)
        assertNotEquals(lead, due)
        assertTrue(lead >= 0)
        assertTrue(due >= 0)
    }

    @Test fun deferredRemindersDoNotReuseStoredReminderCodes() {
        val stored = ReminderEngine.alarmRequestCode(1)
        val deferred = ReminderEngine.deferredAlarmRequestCode(1, 1_800_000L)
        assertNotEquals(stored, deferred)
    }
}
