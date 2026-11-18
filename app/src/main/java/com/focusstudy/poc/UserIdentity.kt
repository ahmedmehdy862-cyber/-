package com.focusstudy.poc

import android.content.Context
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
