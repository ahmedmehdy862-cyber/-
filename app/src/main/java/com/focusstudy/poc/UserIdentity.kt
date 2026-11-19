package com.focusstudy.poc

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/**
 * A random id created on first launch, used only to address a message to this one person.
 *
 * It is not an account and carries no personal data: the team can type it in the dashboard to
 * reach a single install, and it travels with support messages so they can copy it back.
 */
object UserIdentity {
    internal const val PREFS = "focus_team_messages"
    private const val KEY = "user_id"

    /**
     * Returns the one id for this install. Synchronised because the message check and the
     * registration effect can start together on a cold launch; a check-then-act with `apply()`
     * could otherwise let both see "no id" and save two different ids. `commit()` makes the id
     * durable before it is sent to the server or shown to the person.
     */
    @Synchronized
    fun id(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY, null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY, created).commit()
        return created
    }

    /**
     * A code the person can read over the phone: eight characters of the install id, grouped.
     *
     * The dashboard looks this code up in the user registry and resolves it to the full install id
     * before storing a message, so only this install can read it — the code alone is not a key.
     */
    fun code(context: Context): String = codeOf(id(context))

    internal fun codeOf(rawId: String): String {
        val clean = rawId.lowercase().filter { it.isLetterOrDigit() }.take(8).uppercase()
        return if (clean.length == 8) clean.chunked(4).joinToString("-") else clean
    }
}

/**
 * Quietly introduces this install to the team dashboard.
 *
 * There is no sign-up form and no extra tap: the app sends the anonymous install id, the name the
 * person already typed in «ملفك الشخصي», and the current version. Nothing else leaves the device.
 * The dashboard stores it under the short code shown in «رسائل الفريق», so the team can recognise
 * and answer a new user without ever asking for a phone number or email address.
 *
 * The request is fire-and-forget. A failed attempt is remembered as "not registered" and retried on
 * the next app open or profile change, with a short throttle so an offline device never loops.
 */
internal object UserRegistry {
    private const val KEY_SIGNATURE = "registered_signature"
    private const val KEY_LAST_ATTEMPT = "register_last_attempt_ms"
    private const val RETRY_DELAY_MS = 60_000L

    private val mutex = Mutex()

    /** [force] bypasses the retry throttle when the person's name or profile just changed. */
    suspend fun register(context: Context, force: Boolean = false) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefs = FocusPreferences.load(context)
            val uid = UserIdentity.id(context)
            val signature = listOf(
                uid,
                prefs.studentName,
                prefs.profile.key,
                TeamMessages.versionCode(context).toString()
            ).joinToString("\u001f")

            val store = context.getSharedPreferences(UserIdentity.PREFS, Context.MODE_PRIVATE)
            if (store.getString(KEY_SIGNATURE, null) == signature) return@withLock
            val now = System.currentTimeMillis()
            if (!force && now - store.getLong(KEY_LAST_ATTEMPT, 0L) < RETRY_DELAY_MS) return@withLock

            val payload = JSONObject()
                .put("action", "register")
                .put("uid", uid)
                .put("name", prefs.studentName.ifBlank { "مستخدم مُنظِّم" })
                .put("profile", prefs.profile.key)
                .put("versionName", TeamMessages.versionName(context))
                .put("versionCode", TeamMessages.versionCode(context))

            val ok = runCatching {
                TeamMessages.request("${TeamMessages.BASE}/api/messages", "POST", payload.toString())
            }.isSuccess

            store.edit()
                .putLong(KEY_LAST_ATTEMPT, System.currentTimeMillis())
                .apply()
            if (ok) store.edit().putString(KEY_SIGNATURE, signature).apply()
        }
    }
}
