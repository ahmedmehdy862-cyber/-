package com.focusstudy.poc

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Entry point for the skills tracker; screens never touch [StudyDb] directly. */
class SkillRepository private constructor(private val db: StudyDb) {

    suspend fun skills(): List<Skill> = io { db.skills() }

    suspend fun add(name: String, category: String, targetLevel: Int): Long =
        io { db.addSkill(name, category, targetLevel) }

    suspend fun logPractice(skillId: Long, minutes: Int) = io { db.logSkillPractice(skillId, minutes) }

    suspend fun setLevel(skillId: Long, level: Int) = io { db.setSkillLevel(skillId, level) }

    suspend fun delete(skillId: Long) = io { db.deleteSkill(skillId) }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        @Volatile
        private var instance: SkillRepository? = null

        fun get(context: Context): SkillRepository =
            instance ?: synchronized(this) {
                instance ?: SkillRepository(StudyDb.get(context)).also { instance = it }
            }
    }
}
