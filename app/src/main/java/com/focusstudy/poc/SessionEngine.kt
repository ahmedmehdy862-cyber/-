package com.focusstudy.poc

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Single source of truth for the running focus session.
 *
 * A session is always attached to a task and to an explicit list of apps. Once started it is
 * locked: there is no user-facing cancel path, and it only ends when the chosen duration is over
 * (or when the process is restored after that time). While it runs:
 *  - the selected apps cannot be opened (AppBlockAccessibilityService hides them and returns home);
 *  - their network traffic is dropped by BlockVpnService;
 *  - in the optional Device Owner mode they are also suspended by the system, which disables their
 *    icons and removes them from Recents.
 *
 * Every transition (restore, start, finish) is serialized by [restoreLock] and the database write
 * is conditional, so a ticker and an end-of-session alarm can never credit the same session twice.
 */
object SessionEngine {

    data class Active(
        val sessionId: Long,
        val taskId: Long,
        val taskTitle: String,
        val startsAt: Long,
        val endsAt: Long,
        val blockedPackages: Set<String>
    ) {
        val totalSeconds: Long get() = ((endsAt - startsAt) / 1000L).coerceAtLeast(1L)
    }

    private const val TAG = "FocusSession"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val restoreLock = Mutex()

    private val _active = MutableStateFlow<Active?>(null)
    val active: StateFlow<Active?> = _active.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(0L)
    val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()

    /** True when the session is running but the VPN tunnel was revoked, so network is not filtered. */
    private val _vpnLost = MutableStateFlow(false)
    val vpnLost: StateFlow<Boolean> = _vpnLost.asStateFlow()

    /** Bumped whenever tasks change, so screens know to reload them. */
    private val _tasksVersion = MutableStateFlow(0)
    val tasksVersion: StateFlow<Int> = _tasksVersion.asStateFlow()

    private var ticker: Job? = null
    private var appContext: Context? = null
    private var database: StudyDb? = null
    private var initialized = false

    fun init(context: Context) {
        val app = context.applicationContext
        if (initialized) return
        initialized = true
        appContext = app
        database = StudyDb.get(app)
        scope.launch { restore() }
    }

    /** Re-reads persisted state; used by the end-of-session alarm and after a reboot. */
    fun refresh(context: Context) {
        init(context)
        scope.launch { restore() }
    }

    private suspend fun restore() = restoreLock.withLock {
        val db = database ?: return
        val context = appContext ?: return
        val row = withContext(Dispatchers.IO) { db.activeSession() }

        if (row == null) {
            // Self-heal: apps suspended by a session that already ended must be released. If the
            // process is still alive, clear any runtime state left by a concurrent finalization.
            if (_active.value != null) clearRuntime(context)
            else withContext(Dispatchers.IO) { DeviceOwnerController.unsuspendAll(context) }
            return
        }

        if (row.endsAt <= System.currentTimeMillis()) {
            finalizeExpired(context, row)
            return
        }

        publishActive(context, row)
    }

    /**
     * Starts a locked session. [minutes] and [packages] come from the setup screen, so the learner
     * always decides both before the clock starts.
     */
    fun start(context: Context, taskId: Long, taskTitle: String, minutes: Int, packages: Set<String>) {
        val app = context.applicationContext
        init(app)
        scope.launch {
            restoreLock.withLock {
                val db = database ?: return@withLock
                if (_active.value != null) return@withLock
                val existing = withContext(Dispatchers.IO) { db.activeSession() }
                if (existing != null) {
                    if (existing.endsAt <= System.currentTimeMillis()) finalizeExpired(app, existing)
                    else publishActive(app, existing)
                    return@withLock
                }

                val now = System.currentTimeMillis()
                val endsAt = now + minutes * 60_000L
                val sessionId = withContext(Dispatchers.IO) {
                    db.startSession(taskId, now, endsAt, packages)
                }
                if (sessionId <= 0L) return@withLock
                publishActive(
                    app,
                    SessionRow(sessionId, taskId, now, endsAt, packages, taskTitle)
                )
            }
        }
    }

    /** Re-creates the tunnel after the user re-grants VPN consent; never unlocks the session. */
    fun restartVpn(context: Context) {
        val session = _active.value ?: return
        if (session.blockedPackages.isEmpty()) return
        startVpn(context.applicationContext, session.blockedPackages)
    }

    fun onVpnLost() {
        if (_active.value != null) _vpnLost.value = true
    }

    private fun startVpn(context: Context, packages: Set<String>): Boolean {
        val started = runCatching {
            ContextCompat.startForegroundService(
                context,
                BlockVpnService.startIntent(context, packages.toList())
            )
        }.onFailure { Log.w(TAG, "could not start VPN filtering", it) }.isSuccess
        if (!started) onVpnLost()
        return started
    }

    private suspend fun publishActive(context: Context, row: SessionRow) {
        val now = System.currentTimeMillis()
        val session = Active(
            sessionId = row.id,
            taskId = row.taskId,
            taskTitle = row.taskTitle,
            startsAt = row.startedAt,
            endsAt = row.endsAt,
            blockedPackages = row.blockedPackages
        )
        _active.value = session
        _remainingSeconds.value = ((row.endsAt - now) / 1000L).coerceAtLeast(0L)
        _vpnLost.value = false
        BlockGate.setBlocked(row.blockedPackages)
        if (row.blockedPackages.isNotEmpty()) {
            startVpn(context, row.blockedPackages)
            withContext(Dispatchers.IO) { DeviceOwnerController.suspend(context, row.blockedPackages) }
        }
        SessionAlarm.schedule(context, row.endsAt)
        startTicker()
    }

    /** Stops runtime blocking, releases suspensions and cancels the alarm. Safe to call twice. */
    private suspend fun clearRuntime(context: Context) {
        ticker?.cancel()
        ticker = null
        _active.value = null
        _remainingSeconds.value = 0L
        _vpnLost.value = false
        BlockGate.clear()
        AppBlockAccessibilityService.hideOverlayIfShown()
        SessionAlarm.cancel(context)
        runCatching { context.startService(BlockVpnService.stopIntent(context)) }
        withContext(Dispatchers.IO) { DeviceOwnerController.unsuspendAll(context) }
    }

    /** Credits minutes only if this call is the one that closes the active row. */
    private suspend fun finalizeExpired(context: Context, row: SessionRow) {
        clearRuntime(context)
        val minutes = ((row.endsAt - row.startedAt) / 60_000L).toInt().coerceAtLeast(0)
        val db = database
        if (db != null) {
            withContext(Dispatchers.IO) {
                db.finishSessionOnce(row.id, row.taskId, minutes)
            }
        }
        _tasksVersion.value++
    }

    private suspend fun expireCurrent(context: Context, session: Active) = restoreLock.withLock {
        val current = _active.value
        if (current == null || current.sessionId != session.sessionId) return@withLock
        if (System.currentTimeMillis() < current.endsAt) return@withLock
        finalizeExpired(
            context,
            SessionRow(
                id = current.sessionId,
                taskId = current.taskId,
                startedAt = current.startsAt,
                endsAt = current.endsAt,
                blockedPackages = current.blockedPackages,
                taskTitle = current.taskTitle
            )
        )
    }

    /** Lets other writers (view models, repositories) tell the UI that tasks changed. */
    fun notifyTasksChanged() {
        _tasksVersion.value++
    }

    fun updateTaskBlocked(taskId: Long, packages: Set<String>) {
        scope.launch {
            withContext(Dispatchers.IO) { database?.updateTaskBlocked(taskId, packages) }
            _tasksVersion.value++
        }
    }

    fun addTask(title: String, plannedMinutes: Int, blockedPackages: Set<String>) {
        scope.launch {
            withContext(Dispatchers.IO) {
                database?.insertTask(title, plannedMinutes, blockedPackages)
            }
            _tasksVersion.value++
        }
    }

    fun deleteTask(taskId: Long) {
        scope.launch {
            withContext(Dispatchers.IO) { database?.deleteTask(taskId) }
            _tasksVersion.value++
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                val session = _active.value ?: break
                val left = (session.endsAt - System.currentTimeMillis()) / 1000L
                if (left <= 0L) {
                    _remainingSeconds.value = 0L
                    appContext?.let { expireCurrent(it, session) }
                    break
                }
                _remainingSeconds.value = left
                delay(500)
            }
        }
    }
}
