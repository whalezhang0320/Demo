package com.example.star.aiwork.data.local.datasource

import android.content.ContentValues
import android.content.Context
import com.example.star.aiwork.data.local.db.ChatDatabase
import com.example.star.aiwork.data.local.record.SessionRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SessionLocalDataSourceImpl(context: Context) : SessionLocalDataSource {

    private val dbHelper = ChatDatabase(context)

    override suspend fun insertSession(session: SessionRecord) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("id", session.id)
            put("name", session.name)
            put("createdAt", session.createdAt)
            put("updatedAt", session.updatedAt)
            put("pinned", if (session.pinned) 1 else 0)
            put("archived", if (session.archived) 1 else 0)
        }
        db.insertWithOnConflict("sessions", null, values, 5 /* CONFLICT_REPLACE */)
    }

    override suspend fun updateSession(session: SessionRecord) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("name", session.name)
            put("createdAt", session.createdAt)
            put("updatedAt", session.updatedAt)
            put("pinned", if (session.pinned) 1 else 0)
            put("archived", if (session.archived) 1 else 0)
        }
        db.update("sessions", values, "id = ?", arrayOf(session.id))
    }

    override suspend fun getSession(id: String): SessionRecord? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "sessions",
            null,
            "id = ?",
            arrayOf(id),
            null,
            null,
            null
        )

        val record = if (cursor.moveToFirst()) {
            SessionRecord(
                id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")),
                updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt")),
                pinned = cursor.getInt(cursor.getColumnIndexOrThrow("pinned")) == 1,
                archived = cursor.getInt(cursor.getColumnIndexOrThrow("archived")) == 1
            )
        } else null

        cursor.close()
        return record
    }

    override fun observeSessions(): Flow<List<SessionRecord>> = flow {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "sessions",
            null,
            null,
            null,
            null,
            null,
            "updatedAt DESC"
        )

        val list = mutableListOf<SessionRecord>()
        while (cursor.moveToNext()) {
            list.add(
                SessionRecord(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")),
                    updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt")),
                    pinned = cursor.getInt(cursor.getColumnIndexOrThrow("pinned")) == 1,
                    archived = cursor.getInt(cursor.getColumnIndexOrThrow("archived")) == 1
                )
            )
        }

        cursor.close()
        emit(list)
    }

    override suspend fun deleteSession(id: String) {
        val db = dbHelper.writableDatabase
        db.delete("sessions", "id = ?", arrayOf(id))
    }

    override fun searchSessions(query: String): Flow<List<SessionRecord>> = flow {
        val db = dbHelper.readableDatabase
        val searchQuery = "%$query%"
        val cursor = db.rawQuery(
            """
            SELECT * FROM sessions WHERE id IN (
                SELECT id FROM sessions WHERE name LIKE ?
                UNION
                SELECT sessionId FROM messages WHERE content LIKE ?
            ) ORDER BY updatedAt DESC
            """.trimIndent(),
            arrayOf(searchQuery, searchQuery)
        )

        val list = mutableListOf<SessionRecord>()
        while (cursor.moveToNext()) {
            list.add(
                SessionRecord(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow(
                        "name"
                    )),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")),
                    updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt")),
                    pinned = cursor.getInt(cursor.getColumnIndexOrThrow("pinned")) == 1,
                    archived = cursor.getInt(cursor.getColumnIndexOrThrow("archived")) == 1
                )
            )
        }

        cursor.close()
        emit(list)
    }
}
