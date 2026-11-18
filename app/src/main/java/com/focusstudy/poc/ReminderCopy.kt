package com.focusstudy.poc

/**
 * The exact words the notifications use, kept pure so they can be checked by a unit test
 * instead of a phone. Counts go through [taskCount] so Arabic reads correctly at 1, 2, 3 and 11.
 */
internal object ReminderCopy {

    private val mornings = listOf(
        "صباحك فرصة جديدة. ابدأ بأصغر خطوة وسترى اليوم يتحرك.",
        "الإنجاز الكبير يبدأ بتركيز بسيط. اختر أول مهمة الآن.",
        "اجعل هدفك واضحًا وخطوتك صغيرة، وسهّل على نفسك البداية."
    )

    private val evenings = listOf(
        "اقفل يومك بفخر على ما أنجزته، وجهّز خطوة لبكرة.",
        "حتى الإنجاز الصغير تقدّم يستحق التقدير. تصبح على خير.",
        "راجع تقدّمك بهدوء وخُذ راحتك. بكرة فرصة جديدة."
    )

    fun taskNudge(name: String, total: Int, overdue: Int, nextTitle: String): String {
        val waiting = taskCountWith(total, "عندك")
        val greeting = if (name.isBlank()) waiting else "يا $name، $waiting"
        val opener = when {
            overdue > 0 -> "منها ${overdueCount(overdue)} — نبدأ بأصغر واحدة."
            total == 1 -> "واحدة فقط، والخطوة الأولى أقرب مما تظن."
            else -> "أقرب خطوة: $nextTitle"
        }
        return "$greeting. $opener"
    }

    fun encouragement(name: String, morning: Boolean, dueToday: Int, open: Int, dayOfYear: Int): String {
        val who = name.ifBlank { "يا بطل" }
        val opening = when {
            dueToday > 0 && morning -> taskCountWith(dueToday, "مهام اليوم جاهزة:") + ". ابدأ بأخفّها لتبني الإيقاع."
            dueToday > 0 -> taskCountWith(dueToday, "بقيت لليوم") + ". عشر دقائق الآن تُغلق واحدة."
            open > 0 && morning -> taskCountWith(open, "لا شيء مجدول اليوم، وعندك") + " مفتوحة. اختر واحدة صغيرة."
            morning -> "لا مهام بعد. مهمة واحدة تكفي ليكون ليومك اتجاه."
            else -> "لا شيء ينتظرك الليلة. اكتب مهمة لبكرة وأنت مرتاح."
        }
        val pool = if (morning) mornings else evenings
        return "$who، ${pool[Math.floorMod(dayOfYear, pool.size)]} — $opening"
    }

    private fun overdueCount(count: Int): String = when {
        count == 1 -> "مهمة واحدة متأخرة"
        count == 2 -> "مهمتان متأخرتان"
        count in 3..10 -> "$count مهام متأخرة"
        else -> "$count مهمة متأخرة"
    }
}
