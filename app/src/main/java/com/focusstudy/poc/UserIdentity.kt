package com.focusstudy.poc

import android.content.Context
import kotlinx.coroutines.Dispatchers
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
    private const val PREFS = "focus_team_messages"
    private const val KEY = "user_id"

    fun id(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY, null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY, created).apply()
        return created
    }

    /** Short form for the support inbox, still unique enough to paste into the dashboard. */
    fun short(context: Context): String = id(context).take(8)

    /**
     * A code the person can read over the phone: eight characters of the install id, grouped.
     *
     * The dashboard recomputes the same value from the id each request sends, so a message can be
     * addressed with the code alone — no device id, no account, nothing to look up.
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
 */
internal object UserRegistry {
    suspend fun register(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = FocusPreferences.load(context)
            val payload = JSONObject()
                .put("action", "register")
                .put("uid", UserIdentity.id(context))
                .put("name", prefs.studentName.ifBlank { "مستخدم مُنظِّم" })
                .put("profile", prefs.profile.key)
                .put("versionName", TeamMessages.versionName(context))
                .put("versionCode", TeamMessages.versionCode(context))
            TeamMessages.request("${TeamMessages.BASE}/api/messages", "POST", payload.toString())
        }
    }
}
