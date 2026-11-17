package com.focusstudy.poc

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Entry point for long-term goals; screens never touch [StudyDb] directly. */
class GoalRepository private constructor(private val db: StudyDb) {

    suspend fun goals(): List<Goal> = io { db.goals() }

    suspend fun add(title: String, target: Int, unit: String, dueAt: Long?, category: String): Long =
        io { db.addGoal(title, target, unit, dueAt, category) }

    suspend fun setProgress(goalId: Long, current: Int) = io { db.setGoalProgress(goalId, current) }

    suspend fun addProgress(goalId: Long, delta: Int) = io { db.addGoalProgress(goalId, delta) }

    suspend fun setStatus(goalId: Long, status: String) = io { db.setGoalStatus(goalId, status) }

    suspend fun delete(goalId: Long) = io { db.deleteGoal(goalId) }

    suspend fun edit(goalId: Long, title: String, target: Int, unit: String, category: String, dueAt: Long?) =
        io { db.updateGoal(goalId, title, target, unit, category, dueAt) }

    suspend fun links(goalId: Long): List<GoalLink> = io { db.goalLinks(goalId) }

    suspend fun link(goalId: Long, kind: String, refId: Long) = io { db.linkGoal(goalId, kind, refId) }

    suspend fun unlink(goalId: Long, kind: String, refId: Long) = io { db.unlinkGoal(goalId, kind, refId) }

    suspend fun tasks(): List<StudyTask> = io { db.tasks() }

    /** Completing a linked task is what moves its goal forward. */
    suspend fun setTaskStatus(taskId: Long, status: TaskStatus) = io { db.setTaskStatus(taskId, status) }

    suspend fun skills(): List<Skill> = io { db.skills() }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        @Volatile
        private var instance: GoalRepository? = null

        fun get(context: Context): GoalRepository =
            instance ?: synchronized(this) {
                instance ?: GoalRepository(StudyDb.get(context)).also { instance = it }
            }
    }
}
