package com.focusstudy.poc

/**
 * Pure helpers for the notebook. Keeping them free of Android and Compose types lets the
 * formatting and search rules be unit-tested without a device or an emulator.
 */
internal object NotesText {

    /** Common buckets offered in the composer; the person can still type any label. */
    val SUGGESTED_CATEGORIES = listOf("عام", "فكرة", "مراجعة", "اجتماع", "مهمة")

    fun title(raw: String, max: Int = 80): String = clean(raw, max)

    fun body(raw: String, max: Int = 8000): String = raw.trim().take(max)

    fun category(raw: String, max: Int = 32): String = clean(raw, max)

    /** A single-line card preview: newlines become spaces so cards keep a predictable height. */
    fun preview(note: Note, max: Int = 120): String {
        if (note.body.isBlank()) return ""
        val flat = note.body.replace(Regex("\\s+"), " ").trim()
        return if (flat.length <= max) flat else flat.take(max - 1).trimEnd() + "…"
    }

    /** Search across every visible field, case-insensitively; an empty query matches everything. */
    fun matches(note: Note, query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return listOf(note.title, note.body, note.category)
            .any { it.contains(needle, ignoreCase = true) }
    }

    /** Deterministic ordering: pinned notes first, then the most recently edited. */
    fun sort(notes: List<Note>): List<Note> =
        notes.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })

    /** Rough reading length, shown so long notes are easy to spot before opening them. */
    fun readingMinutes(text: String): Int {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        return when {
            words < 120 -> 1
            else -> (words + 199) / 200
        }
    }

    /** Short Arabic "last edited" label. [now] is injected so the test never depends on the clock. */
    fun relativeTime(updatedAt: Long, now: Long): String {
        if (updatedAt <= 0L) return ""
        val minutes = ((now - updatedAt).coerceAtLeast(0L) / 60_000L)
        return when {
            minutes < 1 -> "الآن"
            minutes < 60 -> "منذ $minutes د"
            minutes < 1440 -> "منذ ${minutes / 60} س"
            minutes < 10_080 -> "منذ ${minutes / 1440} يوم"
            else -> "منذ ${minutes / 10_080} أسبوع"
        }
    }

    private fun clean(raw: String, max: Int): String = raw.trim().replace(Regex("\\s+"), " ").take(max)
}
