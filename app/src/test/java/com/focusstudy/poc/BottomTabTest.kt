package com.focusstudy.poc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomTabTest {

    @Test fun notesIsARoutableDestination() {
        assertNotNull(BottomTab.from("notes"))
        assertEquals("نوتة", BottomTab.from("notes")?.label)
    }

    @Test fun notesCanBePinnedToTheDockWithoutChangingTheDefault() {
        assertEquals(listOf("today", "tasks", "calendar", "subjects", "more"), BottomTab.DEFAULT)
        val pinned = BottomTab.normalize(listOf("notes", "today", "notes"))
        assertEquals(listOf("notes", "today"), pinned)
    }

    @Test fun normalizeDropsUnknownIdsAndCapsTheRow() {
        val ids = listOf("notes", "unknown", "notes", "today", "tasks", "calendar", "subjects")
        assertEquals(BottomTab.MAX, BottomTab.normalize(ids).size)
        assertEquals("notes", BottomTab.normalize(ids).first())
        assertEquals(BottomTab.DEFAULT, BottomTab.normalize(listOf("unknown")))
    }
}
