package com.focusstudy.poc

/**
 * Arabic counts the way people say them, because "5 مهمة" reads wrong in a reminder.
 * Singular 1, dual 2, plural 3–10, and the tamyiz singular again from 11 up.
 */
internal fun taskCount(count: Int): String = when {
    count <= 0 -> "لا مهام"
    count == 1 -> "مهمة واحدة"
    count == 2 -> "مهمتان"
    count in 3..10 -> "$count مهام"
    else -> "$count مهمة"
}

/** "عندك ٣ مهام" / "متبقية مهمة واحدة" with the verb kept outside the count. */
internal fun taskCountWith(count: Int, prefix: String): String = when {
    count <= 0 -> prefix
    count == 1 -> "$prefix مهمة واحدة"
    count == 2 -> "$prefix مهمتان"
    count in 3..10 -> "$prefix $count مهام"
    else -> "$prefix $count مهمة"
}

internal fun minuteCount(count: Int): String = when {
    count <= 0 -> "لا دقائق"
    count == 1 -> "دقيقة واحدة"
    count == 2 -> "دقيقتان"
    count in 3..10 -> "$count دقائق"
    else -> "$count دقيقة"
}
