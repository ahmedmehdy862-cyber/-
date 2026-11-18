package com.focusstudy.poc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Carries a "show my tasks" wish from a notification tap into the live composition.
 *
 * A notification tapped while the app is already alive arrives through onNewIntent, and Compose
 * never re-reads the activity intent on its own, so the tab would stay where it was. AppRoot watches
 * this counter and opens the tasks tab whenever it moves.
 */
internal object NotificationNavigation {
    var openTasksRequest by mutableStateOf(0)
        private set

    fun requestTasks() {
        openTasksRequest += 1
    }
}
