package com.focusstudy.poc

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Entry point for the private notebook; screens never touch [StudyDb] directly.
 *
 * There is deliberately no network code here: note text stays in the app's local database.
 */
class NoteRepository private constructor(private val db: StudyDb) {

    suspend fun notes(): List<Note> = io { db.notes() }

    suspend fun add(title: String, body: String, category: String): Long =
        io { db.addNote(title, body, category) }

    suspend fun update(id: Long, title: String, body: String, category: String) =
        io { db.updateNote(id, title, body, category) }

    suspend fun setPinned(id: Long, pinned: Boolean) = io { db.setNotePinned(id, pinned) }

    suspend fun delete(id: Long) = io { db.deleteNote(id) }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        @Volatile
        private var instance: NoteRepository? = null

        fun get(context: Context): NoteRepository =
            instance ?: synchronized(this) {
                instance ?: NoteRepository(StudyDb.get(context)).also { instance = it }
            }
    }
}
