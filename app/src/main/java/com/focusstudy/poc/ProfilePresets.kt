package com.focusstudy.poc

import android.content.Context

/**
 * Sections that make the app feel tailored on first launch: a student sees «موادي», an employee
 * sees «اجتماعات» and a retiree sees «صحة». They are ordinary task types, so they can be renamed,
 * reordered or archived like any other — the preset is a starting point, not a cage.
 */
object ProfilePresets {

    fun sectionsFor(profile: UserProfile): List<String> = when (profile) {
        UserProfile.STUDENT -> listOf("موادي", "امتحانات", "واجبات", "مراجعة")
        UserProfile.GRADUATE -> listOf("مشروع التخرج", "مهارات", "تدريب", "بحث عن عمل")
        UserProfile.PROFESSIONAL -> listOf("عمل", "اجتماعات", "مشاريع", "تعلّم")
        UserProfile.RETIRED -> listOf("صحة", "عائلة", "ورد يومي", "أنشطة")
        UserProfile.GENERAL -> listOf("مهامي", "شخصي", "بيت", "تعلّم")
    }

    /** Idempotent: adds only the sections that are missing, and never renames a user's own. */
    fun seed(context: Context, profile: UserProfile) {
        val db = StudyDb.get(context)
        val existing = db.taskTypes(includeArchived = true).map { it.name.trim() }
        sectionsFor(profile)
            .filterNot { it in existing }
            .forEach { runCatching { db.addTaskType(it) } }
    }
}
