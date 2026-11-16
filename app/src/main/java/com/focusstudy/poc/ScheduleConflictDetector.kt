package com.focusstudy.poc

/** Uses half-open ranges [start, end), so back-to-back events are allowed. */
object ScheduleConflictDetector {
    fun overlaps(startAt: Long, endAt: Long, events: List<StudyEvent>, ignoreId: Long? = null): Boolean {
        if (endAt <= startAt) return true
        return events.any { it.id != ignoreId && startAt < it.endsAt && endAt > it.startsAt }
    }

    fun nextAvailableStart(
        startAt: Long,
        duration: Long,
        dayEnd: Long,
        events: List<StudyEvent>,
        prayerWindows: List<LongRange> = emptyList()
    ): Long? {
        if (duration <= 0) return null
        var candidate = startAt
        val busyWindows = prayerWindows
            .filter { it.last >= startAt && it.first < dayEnd }
            .map { it.first to it.last + 1 }
            .sortedBy { it.first }
        var guard = 0
        while (guard++ < 200) {
            val windowHit = busyWindows.firstOrNull { (from, to) ->
                candidate < to && candidate + duration > from
            }
            val eventHit = events.asSequence()
                .filter { it.endsAt > candidate && it.startsAt < dayEnd }
                .sortedBy { it.startsAt }
                .firstOrNull { candidate < it.endsAt && candidate + duration > it.startsAt }
                ?.let { it.startsAt to it.endsAt }
            val hit = windowHit ?: eventHit ?: return candidate.takeIf { dayEnd - it >= duration }
            candidate = hit.second
            if (candidate + duration > dayEnd) return null
        }
        return null
    }

    /** True when a block would sit inside a prayer window (10 minutes before … 20 after). */
    fun overlapsPrayer(startAt: Long, endAt: Long, prayerWindows: List<LongRange>): Boolean =
        endAt > startAt && prayerWindows.any { startAt < it.last + 1 && endAt > it.first }
}
