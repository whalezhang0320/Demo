package com.example.star.aiwork.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.star.aiwork.domain.model.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Upsert
    suspend fun upsertSession(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun getSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("""
        SELECT * FROM sessions WHERE id IN (
            SELECT id FROM sessions WHERE rowid IN (
                SELECT rowid FROM sessions_fts WHERE sessions_fts MATCH :query || '*'
            )
            UNION
            SELECT sessionId FROM messages WHERE rowid IN (
                SELECT rowid FROM messages_fts WHERE messages_fts MATCH :query || '*'
            )
        ) ORDER BY updatedAt DESC
        """)
    fun searchSessions(query: String): Flow<List<SessionEntity>>
}
