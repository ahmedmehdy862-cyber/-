package com.focusstudy.poc

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * State of every permission the app asks for, kept only on this phone.
 * Android keeps its own grants across updates; this ledger exists so the app can tell
 * "لم يُمنح قط" from "كان ممنوحًا وأوقفه النظام بعد التحديث" instead of asking blind.
 */
enum class ConsentKey(val storage: String, val label: String) {
    NOTIFICATIONS("notifications", "الإشعارات"),
    ACCESSIBILITY("accessibility", "الوصول"),
    VPN("vpn", "VPN"),
    BATTERY("battery", "البطارية"),
    DND("dnd", "لا تزعج"),
    EXACT_ALARMS("exact_alarms", "التنبيهات الدقيقة");

    companion object {
        fun fromStorage(value: String): ConsentKey? = entries.firstOrNull { it.storage == value }
    }
}

/** What is true on the device right now, regardless of what we remembered. */
data class PermissionSnapshot(
    val notifications: Boolean,
    val accessibility: Boolean,
    val vpn: Boolean,
    val battery: Boolean,
    val dnd: Boolean,
    val exactAlarms: Boolean
) {
    fun asMap(): Map<ConsentKey, Boolean> = mapOf(
        ConsentKey.NOTIFICATIONS to notifications,
        ConsentKey.ACCESSIBILITY to accessibility,
        ConsentKey.VPN to vpn,
        ConsentKey.BATTERY to battery,
        ConsentKey.DND to dnd,
        ConsentKey.EXACT_ALARMS to exactAlarms
    )

    val missing: Set<ConsentKey> get() = asMap().filterValues { granted -> !granted }.keys
}

/** Pure comparison of the stored consent and today's reality. */
object ConsentPolicy {
    /** Stored with every row; raise it when a stored row needs reinterpreting. */
    const val VERSION = 1

    enum class Change { KEPT, REVOKED, NEVER_GRANTED, NEWLY_GRANTED }

    /**
     * @param everGranted true when this permission was granted at any point in the past,
     * even if a later update turned it off.
     */
    fun classify(everGranted: Boolean, actual: Boolean): Change = when {
        actual && !everGranted -> Change.NEWLY_GRANTED
        actual -> Change.KEPT
        everGranted -> Change.REVOKED
        else -> Change.NEVER_GRANTED
    }

    /** True when the app should explain instead of asking as if it were the first time. */
    fun wasRevokedBySystem(change: Change): Boolean = change == Change.REVOKED

    fun hint(change: Change): String? = when (change) {
        Change.REVOKED -> "كان مفعّلًا وأوقفه نظام الهاتف بعد التحديث — إعادة تفعيل مرة واحدة تكفي."
        else -> null
    }
}

/** Reads the real state and mirrors it into the local ledger. Nothing leaves the phone. */
object ConsentStore {

    fun snapshot(context: Context): PermissionSnapshot = PermissionSnapshot(
        notifications = notificationPermission(context),
        accessibility = runCatching { AppBlockAccessibilityService.isEnabled(context) }.getOrDefault(false),
        vpn = runCatching { VpnService.prepare(context) == null }.getOrDefault(false),
        battery = runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true
        }.getOrDefault(false),
        dnd = runCatching {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.isNotificationPolicyAccessGranted == true
        }.getOrDefault(false),
        exactAlarms = runCatching {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)
                    ?.canScheduleExactAlarms() == true
        }.getOrDefault(false)
    )

    /** Writes today's state to the ledger and reports how each permission changed. */
    fun record(
        context: Context,
        snapshot: PermissionSnapshot = snapshot(context)
    ): Map<ConsentKey, ConsentPolicy.Change> {
        val db = StudyDb.get(context)
        val saved = db.consents().associateBy { it.key }
        val now = System.currentTimeMillis()
        val changes = linkedMapOf<ConsentKey, ConsentPolicy.Change>()
        snapshot.asMap().forEach { (key, actual) ->
            val stored = saved[key.storage]
            val everGranted = actual || (stored?.lastGrantedAt ?: 0L) > 0L
            val lastGrantedAt = if (actual) now else stored?.lastGrantedAt ?: 0L
            val change = ConsentPolicy.classify(everGranted = everGranted, actual = actual)
            changes[key] = change
            // Only rewrite when something changed, so an ordinary open costs no writes.
            val sameState = stored != null && stored.granted == actual &&
                stored.version == ConsentPolicy.VERSION && stored.lastGrantedAt == lastGrantedAt
            if (!sameState) {
                db.saveConsent(key.storage, actual, ConsentPolicy.VERSION, now, lastGrantedAt)
            }
        }
        return changes
    }

    fun changes(context: Context): Map<ConsentKey, ConsentPolicy.Change> {
        val saved = StudyDb.get(context).consents().associateBy { it.key }
        return snapshot(context).asMap().mapValues { (key, actual) ->
            val everGranted = actual || (saved[key.storage]?.lastGrantedAt ?: 0L) > 0L
            ConsentPolicy.classify(everGranted = everGranted, actual = actual)
        }
    }

    /** True when this permission was granted before and the system has since turned it off. */
    fun revokedBySystem(context: Context, key: ConsentKey): Boolean =
        ConsentPolicy.wasRevokedBySystem(changes(context)[key] ?: ConsentPolicy.Change.NEVER_GRANTED)

    private fun notificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
}

/** UI wording shared by every place that lists permissions. */
fun consentDescription(base: String, change: ConsentPolicy.Change?): String =
    ConsentPolicy.hint(change ?: ConsentPolicy.Change.NEVER_GRANTED)?.let { "$base\n$it" } ?: base

fun consentActionLabel(change: ConsentPolicy.Change?): String =
    if (change == ConsentPolicy.Change.REVOKED) "إعادة التفعيل" else "منح"
