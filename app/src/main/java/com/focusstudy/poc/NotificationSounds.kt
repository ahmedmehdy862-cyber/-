package com.focusstudy.poc

import android.content.Context
import org.json.JSONObject

/**
 * Per-notification sound choices published by the admin dashboard.
 *
 * The public message feed also carries this small config, so the app needs no extra endpoint and
 * stays inside the Vercel Hobby function limit. The last good copy is cached on the device: a
 * receiver that fires while offline still uses the last sound the team chose.
 */
internal object NotificationSounds {

    const val PRAYER = "prayer"
    const val TASK = "task"
    const val EXAM = "exam"
    const val TEAM = "team"
    const val UPDATE = "update"
    const val DAILY = "daily"
    const val DHIKR = "dhikr"

    const val DEFAULT = "default"
    const val CHIME = "chime"
    const val ADHAN = "adhan"
    const val SYSTEM = "system"
    const val SILENT = "silent"

    val keys = listOf(PRAYER, TASK, EXAM, TEAM, UPDATE, DAILY, DHIKR)
    private val allowed = setOf(DEFAULT, CHIME, ADHAN, SYSTEM, SILENT)
    private const val PREFS = "focus_notification_sounds"
    private const val CACHE = "cache"

    /** Accepts the whole `/api/messages` payload or a bare `{sounds:{…}}` object. */
    fun parse(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            val source = root.optJSONObject("sounds") ?: root
            buildMap {
                keys.forEach { key ->
                    val value = source.optString(key, DEFAULT).trim().lowercase()
                    if (value in allowed) put(key, value)
                }
            }
        }.getOrDefault(emptyMap())
    }

    /** Stores a valid remote change and immediately rewrites channel sounds on this device. */
    fun cache(context: Context, raw: String?) {
        val values = parse(raw)
        if (values.isEmpty()) return
        val json = JSONObject().apply { values.forEach { put(it.key, it.value) } }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(CACHE, json.toString()).apply()
        AppNotifications.refreshChannels(context)
    }

    fun cached(context: Context): Map<String, String> =
        parse(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CACHE, null))

    fun choice(context: Context, key: String): String =
        cached(context)[key] ?: DEFAULT
}
