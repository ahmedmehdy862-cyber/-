package com.focusstudy.poc

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class SessionRow(
    val id: Long,
    val taskId: Long,
    val startedAt: Long,
    val endsAt: Long,
    val blockedPackages: Set<String>,
    val taskTitle: String
)

/**
 * Local persistence for tasks, subtasks and focus sessions.
 *
 * Implemented on SQLiteOpenHelper so the project builds without annotation processors.
 * Schema history:
 *   v1 — tasks(id, title, planned_minutes, done_minutes, is_done, blocked_packages, created_at)
 *        sessions(id, task_id, started_at, ends_at, blocked_packages, state)
 *   v2 — rich task fields (description, category, priority, status, dates, notes, recurrence,
 *        subject, sort_order, completed_at) and the subtasks table.
 *
 * Every upgrade is additive: no column or table is dropped, so existing data survives.
 */
class StudyDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE tasks(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL,
              planned_minutes INTEGER NOT NULL,
              done_minutes INTEGER NOT NULL DEFAULT 0,
              is_done INTEGER NOT NULL DEFAULT 0,
              blocked_packages TEXT NOT NULL DEFAULT '',
              created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE sessions(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              task_id INTEGER NOT NULL,
              started_at INTEGER NOT NULL,
              ends_at INTEGER NOT NULL,
              blocked_packages TEXT NOT NULL DEFAULT '',
              state TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_sessions_state ON sessions(state)")
        db.execSQL("CREATE INDEX idx_tasks_created ON tasks(created_at)")
        migrateToV2(db)
        migrateToV3(db)
        migrateToV4(db)
        migrateToV5(db)
        migrateToV6(db)
        migrateToV7(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) migrateToV2(db)
        if (oldVersion < 3) migrateToV3(db)
        if (oldVersion < 4) migrateToV4(db)
        if (oldVersion < 5) migrateToV5(db)
        if (oldVersion < 6) migrateToV6(db)
        if (oldVersion < 7) migrateToV7(db)
    }

    private fun migrateToV4(db: SQLiteDatabase) {
        addColumnIfMissing(db, "tasks", "original_due_at", "INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_due ON tasks(due_at, status)")
    }

    private fun migrateToV5(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS events(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL,
              kind TEXT NOT NULL DEFAULT 'study',
              subject_id INTEGER,
              task_id INTEGER,
              starts_at INTEGER NOT NULL,
              ends_at INTEGER NOT NULL,
              notes TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_range ON events(starts_at, ends_at)")
    }

    private fun migrateToV6(db: SQLiteDatabase) {
        db.execSQL(
            """
            UPDATE tasks
            SET status = CASE WHEN is_done = 1 OR status = 'done' THEN 'done' ELSE status END,
                is_done = CASE WHEN is_done = 1 OR status = 'done' THEN 1 ELSE 0 END
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_subject ON tasks(subject_id)")
    }

    /** v7 - user-editable task types, additive: existing tasks keep working unchanged. */
    private fun migrateToV7(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS task_types(
              id TEXT PRIMARY KEY,
              name TEXT NOT NULL,
              sort_order INTEGER NOT NULL DEFAULT 0,
              archived INTEGER NOT NULL DEFAULT 0,
              built_in INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        addColumnIfMissing(db, "tasks", "type_id", "TEXT")
        seedTaskTypes(db)
        db.execSQL(
            """
            UPDATE tasks SET type_id = CASE category
                WHEN 'study' THEN 'study'
                WHEN 'university' THEN 'study'
                ELSE 'life' END
            WHERE type_id IS NULL
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_type ON tasks(type_id)")
    }

    private fun seedTaskTypes(db: SQLiteDatabase) {
        val builtIns = listOf(
            "religious" to "ديني",
            "study" to "دراسة",
            "life" to "حياة",
            "volunteering" to "تطوّع"
        )
        builtIns.forEachIndexed { index, (id, name) ->
            db.execSQL(
                "INSERT OR IGNORE INTO task_types(id, name, sort_order, archived, built_in) VALUES(?, ?, ?, 0, 1)",
                arrayOf<Any?>(id, name, index)
            )
        }
    }

    // -------------------------------------------------------------- task types

    fun taskTypes(includeArchived: Boolean = false): List<TaskType> {
        val rows = ArrayList<TaskType>()
        val where = if (includeArchived) "" else "WHERE archived = 0"
        readableDatabase.rawQuery(
            "SELECT id, name, sort_order, archived, built_in FROM task_types $where " +
                "ORDER BY sort_order ASC, name COLLATE NOCASE",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(
                    TaskType(
                        id = cursor.getString(0),
                        name = cursor.getString(1),
                        sortOrder = cursor.getInt(2),
                        archived = cursor.getInt(3) == 1,
                        builtIn = cursor.getInt(4) == 1
                    )
                )
            }
        }
        return rows
    }

    fun addTaskType(name: String): String {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "اسم النوع مطلوب" }
        val exists = readableDatabase.rawQuery(
            "SELECT 1 FROM task_types WHERE name = ? COLLATE NOCASE LIMIT 1",
            arrayOf(clean)
        ).use { it.moveToFirst() }
        require(!exists) { "هذا النوع موجود بالفعل" }
        val id = "c_" + System.currentTimeMillis().toString(36)
        val order = readableDatabase
            .rawQuery("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM task_types", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 1 }
        writableDatabase.insertOrThrow("task_types", null, ContentValues().apply {
            put("id", id)
            put("name", clean)
            put("sort_order", order)
            put("archived", 0)
            put("built_in", 0)
        })
        return id
    }

    fun renameTaskType(id: String, name: String) {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "اسم النوع مطلوب" }
        writableDatabase.update(
            "task_types",
            ContentValues().apply { put("name", clean) },
            "id = ?",
            arrayOf(id)
        )
    }

    fun setTaskTypeArchived(id: String, archived: Boolean) {
        writableDatabase.update(
            "task_types",
            ContentValues().apply { put("archived", if (archived) 1 else 0) },
            "id = ?",
            arrayOf(id)
        )
    }

    /** Swaps the type with its neighbour and renumbers the list so the order stays dense. */
    fun moveTaskType(id: String, up: Boolean) {
        val ordered = taskTypes(includeArchived = true)
        val index = ordered.indexOfFirst { it.id == id }
        if (index < 0) return
        val swapWith = if (up) index - 1 else index + 1
        if (swapWith !in ordered.indices) return
        val reordered = ordered.toMutableList().apply {
            val moved = removeAt(index)
            add(swapWith, moved)
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            reordered.forEachIndexed { position, type ->
                db.execSQL(
                    "UPDATE task_types SET sort_order = ? WHERE id = ?",
                    arrayOf<Any?>(position, type.id)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Deletes a custom type; its tasks stay, they simply lose the label. */
    fun deleteTaskType(id: String) {
        val builtIn = readableDatabase.rawQuery(
            "SELECT built_in FROM task_types WHERE id = ? LIMIT 1",
            arrayOf(id)
        ).use { if (it.moveToFirst()) it.getInt(0) == 1 else false }
        require(!builtIn) { "لا يمكن حذف نوع أساسي" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE tasks SET type_id = NULL WHERE type_id = ?", arrayOf<Any?>(id))
            db.delete("task_types", "id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun migrateToV3(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS semesters(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              university TEXT NOT NULL DEFAULT '',
              year_label TEXT NOT NULL DEFAULT '',
              starts_on INTEGER,
              ends_on INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS subjects(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              semester_id INTEGER,
              name TEXT NOT NULL,
              color INTEGER NOT NULL DEFAULT 4281558682,
              icon TEXT NOT NULL DEFAULT 'book',
              instructor TEXT NOT NULL DEFAULT '',
              goal_minutes INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS exams(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              subject_id INTEGER NOT NULL,
              title TEXT NOT NULL,
              at INTEGER NOT NULL,
              kind TEXT NOT NULL DEFAULT 'exam'
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_subjects_semester ON subjects(semester_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_exams_subject_at ON exams(subject_id, at)")
    }

    private fun migrateToV2(db: SQLiteDatabase) {
        addColumnIfMissing(db, "tasks", "description", "TEXT NOT NULL DEFAULT ''")
        addColumnIfMissing(db, "tasks", "category", "TEXT NOT NULL DEFAULT 'study'")
        addColumnIfMissing(db, "tasks", "priority", "INTEGER NOT NULL DEFAULT 1")
        addColumnIfMissing(db, "tasks", "status", "TEXT NOT NULL DEFAULT 'todo'")
        addColumnIfMissing(db, "tasks", "start_at", "INTEGER")
        addColumnIfMissing(db, "tasks", "due_at", "INTEGER")
        addColumnIfMissing(db, "tasks", "subject_id", "INTEGER")
        addColumnIfMissing(db, "tasks", "notes", "TEXT NOT NULL DEFAULT ''")
        addColumnIfMissing(db, "tasks", "recurrence", "TEXT NOT NULL DEFAULT 'none'")
        addColumnIfMissing(db, "tasks", "completed_at", "INTEGER")
        addColumnIfMissing(db, "tasks", "sort_order", "INTEGER NOT NULL DEFAULT 0")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS subtasks(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              task_id INTEGER NOT NULL,
              title TEXT NOT NULL,
              is_done INTEGER NOT NULL DEFAULT 0,
              sort_order INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_subtasks_task ON subtasks(task_id)")
    }

    private fun addColumnIfMissing(
        db: SQLiteDatabase,
        table: String,
        column: String,
        definition: String
    ) {
        var exists = false
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == column) {
                    exists = true
                    break
                }
            }
        }
        if (!exists) db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
    }

    // ---------------------------------------------------------------- tasks

    fun insertTask(
        title: String,
        plannedMinutes: Int,
        blockedPackages: Set<String>,
        description: String = "",
        category: TaskCategory = TaskCategory.STUDY,
        priority: TaskPriority = TaskPriority.MEDIUM,
        status: TaskStatus = TaskStatus.TODO,
        startAt: Long? = null,
        dueAt: Long? = null,
        subjectId: Long? = null,
        notes: String = "",
        recurrence: Recurrence = Recurrence.NONE,
        typeId: String? = null
    ): Long {
        require(title.isNotBlank()) { "Task title cannot be blank" }
        require(plannedMinutes in 1..720) { "Task duration must be between 1 and 720 minutes" }
        require(subjectId == null || subjectExists(subjectId)) { "Subject does not exist" }
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("title", title)
            put("planned_minutes", plannedMinutes)
            put("done_minutes", 0)
            put("is_done", 0)
            put("blocked_packages", encodePackages(blockedPackages))
            put("created_at", now)
            put("description", description)
            put("category", category.key)
            put("priority", priority.level)
            put("status", status.key)
            if (startAt != null) put("start_at", startAt) else putNull("start_at")
            if (dueAt != null) put("due_at", dueAt) else putNull("due_at")
            if (subjectId != null) put("subject_id", subjectId) else putNull("subject_id")
            put("notes", notes)
            put("recurrence", recurrence.key)
            if (typeId != null) put("type_id", typeId) else putNull("type_id")
            put("completed_at", if (status == TaskStatus.DONE) now else null)
            put("sort_order", 0)
        }
        return writableDatabase.insert("tasks", null, values)
    }

    fun updateTask(task: StudyTask) {
        require(task.title.isNotBlank()) { "Task title cannot be blank" }
        require(task.plannedMinutes in 1..720) { "Task duration must be between 1 and 720 minutes" }
        require(task.subjectId == null || subjectExists(task.subjectId)) { "Subject does not exist" }
        val completed = task.status == TaskStatus.DONE || task.isDone
        val values = ContentValues().apply {
            put("title", task.title)
            put("planned_minutes", task.plannedMinutes)
            put("description", task.description)
            put("category", task.category.key)
            put("priority", task.priority.level)
            put("status", if (completed) TaskStatus.DONE.key else task.status.key)
            put("is_done", if (completed) 1 else 0)
            put("blocked_packages", encodePackages(task.blockedPackages))
            if (task.startAt != null) put("start_at", task.startAt) else putNull("start_at")
            if (task.dueAt != null) put("due_at", task.dueAt) else putNull("due_at")
            if (task.subjectId != null) put("subject_id", task.subjectId) else putNull("subject_id")
            put("notes", task.notes)
            put("recurrence", task.recurrence.key)
            if (task.typeId != null) put("type_id", task.typeId) else putNull("type_id")
            if (completed) put("completed_at", task.completedAt ?: System.currentTimeMillis()) else putNull("completed_at")
            put("sort_order", task.sortOrder)
            if (task.originalDueAt != null) put("original_due_at", task.originalDueAt) else putNull("original_due_at")
        }
        writableDatabase.update("tasks", values, "id = ?", arrayOf(task.id.toString()))
    }

    fun setTaskStatus(taskId: Long, status: TaskStatus) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val previous = task(taskId) ?: return
            val completedAt = if (status == TaskStatus.DONE) System.currentTimeMillis() else null
            db.execSQL(
                "UPDATE tasks SET status = ?, is_done = ?, completed_at = ? WHERE id = ?",
                arrayOf<Any?>(status.key, if (status == TaskStatus.DONE) 1 else 0, completedAt, taskId)
            )
            if (status == TaskStatus.DONE && previous.status != TaskStatus.DONE) {
                createNextRecurringTask(db, previous)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun rescheduleTask(taskId: Long, dueAt: Long?) {
        writableDatabase.execSQL(
            "UPDATE tasks SET original_due_at = COALESCE(original_due_at, due_at), due_at = ? WHERE id = ?",
            arrayOf<Any?>(dueAt, taskId)
        )
    }

    fun updateTaskBlocked(taskId: Long, blockedPackages: Set<String>) {
        val values = ContentValues().apply {
            put("blocked_packages", encodePackages(blockedPackages))
        }
        writableDatabase.update("tasks", values, "id = ?", arrayOf(taskId.toString()))
    }

    /** Deletes the task together with its subtasks; focus sessions stay for history. */
    fun deleteTask(taskId: Long) {
        writableDatabase.delete("subtasks", "task_id = ?", arrayOf(taskId.toString()))
        writableDatabase.delete("tasks", "id = ?", arrayOf(taskId.toString()))
    }

    /** Copies a task (and its subtasks) with a clean progress state. */
    fun duplicateTask(taskId: Long): Long {
        val original = task(taskId) ?: return -1L
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("title", "${original.title} (نسخة)")
            put("planned_minutes", original.plannedMinutes)
            put("done_minutes", 0)
            put("is_done", 0)
            put("blocked_packages", encodePackages(original.blockedPackages))
            put("created_at", now)
            put("description", original.description)
            put("category", original.category.key)
            if (original.typeId != null) put("type_id", original.typeId) else putNull("type_id")
            put("priority", original.priority.level)
            put("status", TaskStatus.TODO.key)
            put("start_at", original.startAt)
            put("due_at", original.dueAt)
            put("subject_id", original.subjectId)
            put("notes", original.notes)
            put("recurrence", original.recurrence.key)
            putNull("completed_at")
            put("sort_order", original.sortOrder)
            put("original_due_at", original.originalDueAt)
        }
        val newId = writableDatabase.insert("tasks", null, values)
        subtasks(taskId).forEach { subtask ->
            val copy = ContentValues().apply {
                put("task_id", newId)
                put("title", subtask.title)
                put("is_done", 0)
                put("sort_order", subtask.sortOrder)
            }
            writableDatabase.insert("subtasks", null, copy)
        }
        return newId
    }

    fun task(taskId: Long): StudyTask? =
        queryTasks("WHERE id = ?", arrayOf(taskId.toString())).firstOrNull()

    fun tasks(): List<StudyTask> = queryTasks(null, null)

    // ----------------------------------------------------------- academics

    fun subjects(): List<StudySubject> {
        val result = ArrayList<StudySubject>()
        readableDatabase.rawQuery(
            """
            SELECT s.id, s.semester_id, s.name, s.color, s.icon, s.instructor,
                   s.goal_minutes, s.created_at, MIN(e.at)
            FROM subjects s
            LEFT JOIN exams e ON e.subject_id = s.id AND e.at >= ?
            GROUP BY s.id
            ORDER BY COALESCE(MIN(e.at), 9223372036854775807), s.name COLLATE NOCASE
            """.trimIndent(),
            arrayOf(System.currentTimeMillis().toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    StudySubject(
                        id = cursor.getLong(0),
                        semesterId = cursor.nullableLong(1),
                        name = cursor.getString(2),
                        color = cursor.getLong(3).toInt(),
                        icon = cursor.getString(4),
                        instructor = cursor.getString(5),
                        goalMinutes = cursor.getInt(6),
                        createdAt = cursor.getLong(7),
                        nextExamAt = cursor.nullableLong(8)
                    )
                )
            }
        }
        return result
    }

    fun eventsBetween(startAt: Long, endAt: Long): List<StudyEvent> {
        val result = ArrayList<StudyEvent>()
        readableDatabase.rawQuery(
            "SELECT id, title, kind, starts_at, ends_at, notes FROM events WHERE starts_at < ? AND ends_at > ? ORDER BY starts_at",
            arrayOf(endAt.toString(), startAt.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) result.add(
                StudyEvent(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3), cursor.getLong(4), cursor.getString(5))
            )
        }
        return result
    }

    fun insertEvent(title: String, kind: String, startsAt: Long, endsAt: Long, notes: String = ""): Long {
        require(title.isNotBlank()) { "Event title cannot be blank" }
        require(endsAt > startsAt)
        val values = ContentValues().apply {
            put("title", title.trim())
            put("kind", kind)
            putNull("subject_id")
            putNull("task_id")
            put("starts_at", startsAt)
            put("ends_at", endsAt)
            put("notes", notes)
        }
        return writableDatabase.insertOrThrow("events", null, values)
    }

    fun deleteEvent(eventId: Long) { writableDatabase.delete("events", "id = ?", arrayOf(eventId.toString())) }

    fun hasEventConflict(startsAt: Long, endsAt: Long): Boolean =
        ScheduleConflictDetector.overlaps(startsAt, endsAt, eventsBetween(startsAt, endsAt))

    fun nextAvailableEventStart(startAt: Long, durationMillis: Long, dayEnd: Long): Long? {
        return ScheduleConflictDetector.nextAvailableStart(
            startAt, durationMillis, dayEnd, eventsBetween(startAt, dayEnd)
        )
    }

    fun insertSubject(
        name: String,
        instructor: String,
        goalMinutes: Int,
        examAt: Long? = null,
        color: Int = 0xFF3B82F6.toInt()
    ): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val subjectValues = ContentValues().apply {
                put("name", name.trim())
                put("instructor", instructor.trim())
                put("goal_minutes", goalMinutes.coerceAtLeast(0))
                put("color", color)
                put("created_at", System.currentTimeMillis())
            }
            val subjectId = db.insertOrThrow("subjects", null, subjectValues)
            if (examAt != null) {
                val examValues = ContentValues().apply {
                    put("subject_id", subjectId)
                    put("title", "امتحان ${name.trim()}")
                    put("at", examAt)
                    put("kind", "exam")
                }
                db.insertOrThrow("exams", null, examValues)
            }
            db.setTransactionSuccessful()
            return subjectId
        } finally {
            db.endTransaction()
        }
    }

    private fun queryTasks(where: String?, args: Array<String>?): List<StudyTask> {
        val sql = """
            SELECT id, title, planned_minutes, done_minutes, is_done, blocked_packages, created_at,
                   description, category, priority, status, start_at, due_at, subject_id, notes,
                   recurrence, completed_at, sort_order, original_due_at, type_id
            FROM tasks
            ${where.orEmpty()}
            ORDER BY is_done ASC, COALESCE(due_at, 9223372036854775807) ASC, created_at DESC
        """.trimIndent()
        val result = ArrayList<StudyTask>()
        readableDatabase.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    StudyTask(
                        id = cursor.getLong(0),
                        title = cursor.getString(1),
                        plannedMinutes = cursor.getInt(2),
                        doneMinutes = cursor.getInt(3),
                        isDone = cursor.getInt(4) == 1,
                        blockedPackages = decodePackages(cursor.getString(5)),
                        createdAt = cursor.getLong(6),
                        description = cursor.getString(7).orEmpty(),
                        category = TaskCategory.from(cursor.getString(8)),
                        priority = TaskPriority.from(cursor.getInt(9)),
                        status = TaskStatus.from(cursor.getString(10)),
                        startAt = cursor.nullableLong(11),
                        dueAt = cursor.nullableLong(12),
                        subjectId = cursor.nullableLong(13),
                        notes = cursor.getString(14).orEmpty(),
                        recurrence = Recurrence.from(cursor.getString(15)),
                        completedAt = cursor.nullableLong(16),
                        sortOrder = cursor.getInt(17),
                        originalDueAt = cursor.nullableLong(18),
                        typeId = cursor.getString(19)
                    )
                )
            }
        }
        return result
    }

    private fun subjectExists(subjectId: Long): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM subjects WHERE id = ? LIMIT 1",
        arrayOf(subjectId.toString())
    ).use { it.moveToFirst() }

    fun addFocusMinutes(taskId: Long, minutes: Int) {
        if (minutes <= 0) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val previous = task(taskId)
            db.execSQL(
                "UPDATE tasks SET done_minutes = done_minutes + ? WHERE id = ?",
                arrayOf<Any>(minutes, taskId)
            )
            db.execSQL(
                """
                UPDATE tasks
                SET status = CASE WHEN status = 'done' OR done_minutes >= planned_minutes THEN 'done' ELSE status END,
                    is_done = CASE WHEN status = 'done' OR done_minutes >= planned_minutes THEN 1 ELSE 0 END,
                    completed_at = CASE WHEN done_minutes >= planned_minutes THEN COALESCE(completed_at, ?) ELSE completed_at END
                WHERE id = ?
                """.trimIndent(),
                arrayOf<Any?>(System.currentTimeMillis(), taskId)
            )
            val updated = task(taskId)
            if (previous != null && updated?.status == TaskStatus.DONE && previous.status != TaskStatus.DONE) {
                createNextRecurringTask(db, previous)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun createNextRecurringTask(db: SQLiteDatabase, original: StudyTask) {
        if (original.recurrence == Recurrence.NONE) return
        val zone = java.time.ZoneId.systemDefault()
        val completedDate = java.time.Instant.ofEpochMilli(
            original.completedAt ?: System.currentTimeMillis()
        ).atZone(zone).toLocalDate()
        val dueDate = original.dueAt?.let {
            java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
        }
        // A late completion must not create another occurrence that's already overdue.
        val baseDate = dueDate?.let { maxOf(it, completedDate) } ?: completedDate
        val nextDueDate = when (original.recurrence) {
            Recurrence.DAILY -> baseDate.plusDays(1)
            Recurrence.WEEKLY -> baseDate.plusWeeks(1)
            Recurrence.MONTHLY -> baseDate.plusMonths(1)
            Recurrence.NONE -> return
        }
        val nextDueAt = nextDueDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val nextStartAt = original.startAt?.let { start ->
            val startDate = java.time.Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
            val daysBeforeDue = dueDate?.let { java.time.temporal.ChronoUnit.DAYS.between(startDate, it) } ?: 0L
            nextDueDate.minusDays(daysBeforeDue).atStartOfDay(zone).toInstant().toEpochMilli()
        }
        val values = ContentValues().apply {
            put("title", original.title)
            put("planned_minutes", original.plannedMinutes)
            put("done_minutes", 0)
            put("is_done", 0)
            put("blocked_packages", encodePackages(original.blockedPackages))
            put("created_at", System.currentTimeMillis())
            put("description", original.description)
            put("category", original.category.key)
            if (original.typeId != null) put("type_id", original.typeId) else putNull("type_id")
            put("priority", original.priority.level)
            put("status", TaskStatus.TODO.key)
            if (nextStartAt == null) putNull("start_at") else put("start_at", nextStartAt)
            put("due_at", nextDueAt)
            if (original.subjectId == null) putNull("subject_id") else put("subject_id", original.subjectId)
            put("notes", original.notes)
            put("recurrence", original.recurrence.key)
            putNull("completed_at")
            put("sort_order", original.sortOrder)
            putNull("original_due_at")
        }
        val nextId = db.insertOrThrow("tasks", null, values)
        subtasks(original.id).forEach { subtask ->
            db.insertOrThrow("subtasks", null, ContentValues().apply {
                put("task_id", nextId)
                put("title", subtask.title)
                put("is_done", 0)
                put("sort_order", subtask.sortOrder)
            })
        }
    }

    // ------------------------------------------------------------- subtasks

    fun subtasks(taskId: Long): List<Subtask> {
        val result = ArrayList<Subtask>()
        readableDatabase.rawQuery(
            "SELECT id, task_id, title, is_done, sort_order FROM subtasks WHERE task_id = ? ORDER BY sort_order ASC, id ASC",
            arrayOf(taskId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    Subtask(
                        id = cursor.getLong(0),
                        taskId = cursor.getLong(1),
                        title = cursor.getString(2),
                        isDone = cursor.getInt(3) == 1,
                        sortOrder = cursor.getInt(4)
                    )
                )
            }
        }
        return result
    }

    fun addSubtask(taskId: Long, title: String): Long {
        val nextOrder = readableDatabase.rawQuery(
            "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM subtasks WHERE task_id = ?",
            arrayOf(taskId.toString())
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
        val values = ContentValues().apply {
            put("task_id", taskId)
            put("title", title)
            put("is_done", 0)
            put("sort_order", nextOrder)
        }
        return writableDatabase.insert("subtasks", null, values)
    }

    fun setSubtaskDone(subtaskId: Long, done: Boolean) {
        writableDatabase.execSQL(
            "UPDATE subtasks SET is_done = ? WHERE id = ?",
            arrayOf<Any>(if (done) 1 else 0, subtaskId)
        )
    }

    fun deleteSubtask(subtaskId: Long) {
        writableDatabase.delete("subtasks", "id = ?", arrayOf(subtaskId.toString()))
    }

    // ------------------------------------------------------------- sessions

    fun startSession(
        taskId: Long,
        startedAt: Long,
        endsAt: Long,
        blockedPackages: Set<String>
    ): Long {
        val values = ContentValues().apply {
            put("task_id", taskId)
            put("started_at", startedAt)
            put("ends_at", endsAt)
            put("blocked_packages", encodePackages(blockedPackages))
            put("state", STATE_ACTIVE)
        }
        return writableDatabase.insert("sessions", null, values)
    }

    fun finishSession(sessionId: Long) {
        writableDatabase.execSQL(
            "UPDATE sessions SET state = 'finished' WHERE id = ?",
            arrayOf<Any>(sessionId)
        )
    }

    fun activeSession(): SessionRow? {
        readableDatabase.rawQuery(
            """
            SELECT s.id, s.task_id, s.started_at, s.ends_at, s.blocked_packages, t.title
            FROM sessions s JOIN tasks t ON t.id = s.task_id
            WHERE s.state = '$STATE_ACTIVE'
            ORDER BY s.id DESC LIMIT 1
            """.trimIndent(),
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return SessionRow(
                id = cursor.getLong(0),
                taskId = cursor.getLong(1),
                startedAt = cursor.getLong(2),
                endsAt = cursor.getLong(3),
                blockedPackages = decodePackages(cursor.getString(4)),
                taskTitle = cursor.getString(5)
            )
        }
    }

    fun totalFocusedMinutes(): Int {
        readableDatabase.rawQuery("SELECT COALESCE(SUM(done_minutes), 0) FROM tasks", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun completedTasksBetween(startAt: Long, endAt: Long): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM tasks WHERE status = 'done' AND completed_at >= ? AND completed_at < ?",
            arrayOf(startAt.toString(), endAt.toString())
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun overdueTaskCount(now: Long): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM tasks WHERE status NOT IN ('done','cancelled') AND due_at IS NOT NULL AND due_at < ?",
            arrayOf(now.toString())
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun finishedSessionsBetween(startAt: Long, endAt: Long): List<FocusSessionMetric> {
        val result = ArrayList<FocusSessionMetric>()
        readableDatabase.rawQuery(
            "SELECT started_at, ends_at, task_id FROM sessions WHERE state = 'finished' AND started_at >= ? AND started_at < ? ORDER BY started_at",
            arrayOf(startAt.toString(), endAt.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) result += FocusSessionMetric(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2))
        }
        return result
    }

    private fun android.database.Cursor.nullableLong(index: Int): Long? =
        if (isNull(index)) null else getLong(index)

    private fun encodePackages(packages: Set<String>): String = packages.joinToString(SEPARATOR)

    private fun decodePackages(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split(SEPARATOR).filter { it.isNotBlank() }.toSet()
    }

    companion object {
        private const val DB_NAME = "focus_study.db"
        private const val DB_VERSION = 7
        private const val STATE_ACTIVE = "active"
        private const val SEPARATOR = "\n"

        @Volatile
        private var instance: StudyDb? = null

        fun get(context: Context): StudyDb =
            instance ?: synchronized(this) {
                instance ?: StudyDb(context).also { instance = it }
            }
    }
}
