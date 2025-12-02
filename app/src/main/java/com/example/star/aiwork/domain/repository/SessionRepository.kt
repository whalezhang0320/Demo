package com.example.star.aiwork.domain.repository

import com.example.star.aiwork.domain.model.SessionEntity
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    suspend fun insertSession(session: SessionEntity)
    suspend fun updateSession(session: SessionEntity)
    suspend fun getSession(id: String): SessionEntity?
    fun observeSessions(): Flow<List<SessionEntity>>
    suspend fun deleteSession(id: String)
    fun searchSessions(query: String): Flow<List<SessionEntity>>
}