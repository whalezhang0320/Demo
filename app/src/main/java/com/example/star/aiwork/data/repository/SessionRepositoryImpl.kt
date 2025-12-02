package com.example.star.aiwork.data.repository

import com.example.star.aiwork.data.local.datasource.SessionLocalDataSource
import com.example.star.aiwork.data.local.mapper.SessionMapper
import com.example.star.aiwork.domain.model.SessionEntity
import com.example.star.aiwork.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SessionRepositoryImpl(
    private val local: SessionLocalDataSource
) : SessionRepository {

    override suspend fun insertSession(session: SessionEntity) {
        local.insertSession(SessionMapper.toRecord(session))
    }

    override suspend fun updateSession(session: SessionEntity) {
        local.updateSession(SessionMapper.toRecord(session))
    }

    override suspend fun getSession(id: String): SessionEntity? =
        local.getSession(id)?.let { SessionMapper.toEntity(it) }

    override fun observeSessions(): Flow<List<SessionEntity>> =
        local.observeSessions().map { list -> list.map { SessionMapper.toEntity(it) } }

    override suspend fun deleteSession(id: String) {
        local.deleteSession(id)
    }

    override fun searchSessions(query: String): Flow<List<SessionEntity>> {
        return local.searchSessions(query).map { list ->
            list.map { SessionMapper.toEntity(it) }
        }
    }
}
