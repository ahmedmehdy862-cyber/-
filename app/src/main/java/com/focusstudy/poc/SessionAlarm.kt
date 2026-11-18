package com.focusstudy.poc

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Wakes the app at the session end time so a locked session still releases its apps even if the
 * process was killed in the meantime. Exact alarms are used when the platform allows them, with an
 * inexact fallback otherwise.
 */
object SessionAlarm {

    private const val REQUEST_CODE = 73

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, SessionEndReceiver::class.java).setAction(SessionEndReceiver.ACTION_END),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun schedule(context: Context, atMillis: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = pending(context)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            runCatching { manager.canScheduleExactAlarms() }.getOrDefault(false)
        val scheduled = if (exact) {
            runCatching {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
            }
        } else {
            runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent) }
        }
        if (exact && scheduled.isFailure) {
            // Some OEMs refuse exact alarms even when canScheduleExactAlarms() returns true.
            // An inexact alarm is better than a session that never releases its apps.
            runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent) }
        }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(pending(context))
    }
}

class SessionEndReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_END) return
        SessionEngine.refresh(context)
    }

    companion object {
        const val ACTION_END = "com.focusstudy.poc.action.SESSION_END"
    }
}
