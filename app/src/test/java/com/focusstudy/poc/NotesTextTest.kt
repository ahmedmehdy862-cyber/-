package com.focusstudy.poc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesTextTest {

    private fun note(
        title: String = "",
        body: String = "",
        category: String = "",
        pinned: Boolean = false,
        updatedAt: Long = 0L
    ) = Note(id = 1L, title = title, body = body, category = category, pinned = pinned, updatedAt = updatedAt)

    @Test fun composerFieldsAreTrimmedAndCapped() {
        assertEquals("خطة المراجعة", NotesText.title("  خطة   المراجعة  "))
        assertEquals("أ", NotesText.title("أ", max = 1))
        assertEquals("مراجعة", NotesText.category("  مراجعة  "))
        assertEquals("سطر", NotesText.body(" سطر "))
    }

    @Test fun previewFlattensNewlinesAndTruncates() {
        val flat = NotesText.preview(note(body = "أولاً\nثانياً   ثالثاً"))
        assertEquals("أولاً ثانياً ثالثاً", flat)
        val long = NotesText.preview(note(body = "x".repeat(300)), max = 10)
        assertEquals(10, long.length)
        assertTrue(long.endsWith("…"))
    }

    @Test fun searchCoversTitleBodyAndCategoryIgnoringCase() {
        val note = note(title = "مقابلة", body = "راجع الملاحظات", category = "مراجعة")
        assertTrue(NotesText.matches(note, ""))
        assertTrue(NotesText.matches(note, "مقابلة"))
        assertTrue(NotesText.matches(note, "الملاحظات"))
        assertTrue(NotesText.matches(note, "مراجعة"))
        assertFalse(NotesText.matches(note, "غير موجود"))
    }

    @Test fun pinnedNotesComeFirstThenRecentlyEdited() {
        val oldPinned = note(title = "مثبّتة", pinned = true, updatedAt = 100L)
        val fresh = note(title = "جديدة", updatedAt = 900L)
        val old = note(title = "قديمة", updatedAt = 200L)
        val sorted = NotesText.sort(listOf(old, fresh, oldPinned))
        assertEquals(listOf("مثبّتة", "جديدة", "قديمة"), sorted.map { it.title })
    }

    @Test fun readingMinutesHasUsefulLowerBound() {
        assertEquals(1, NotesText.readingMinutes(""))
        assertEquals(1, NotesText.readingMinutes("كلمة كلمة"))
        assertEquals(2, NotesText.readingMinutes((1..250).joinToString(" ") { "كلمة" }))
    }

    @Test fun relativeTimeIsArabicAndClockIndependent() {
        val now = 1_700_000_000_000L
        assertEquals("الآن", NotesText.relativeTime(now, now))
        assertEquals("منذ 5 د", NotesText.relativeTime(now - 5 * 60_000L, now))
        assertEquals("منذ 2 س", NotesText.relativeTime(now - 2 * 3_600_000L, now))
        assertEquals("منذ 3 يوم", NotesText.relativeTime(now - 3 * 86_400_000L, now))
        assertEquals("", NotesText.relativeTime(0L, now))
    }
}
