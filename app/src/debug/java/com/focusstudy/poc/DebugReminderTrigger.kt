package com.focusstudy.poc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Debug builds only: `adb shell am broadcast -a com.focusstudy.poc.DEBUG_REMINDER --el task_id 1`. */
class DebugReminderTrigger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val taskId = intent?.getLongExtra("task_id", -1L) ?: -1L
        if (taskId < 0) return
        ReminderEngine.channels(context)
        ReminderEngine.dispatch(context, reminderId = -1, taskId = taskId, kind = ReminderKind.TASK_DUE.key)
    }
}
