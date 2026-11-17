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
