package com.example.star.aiwork.data.local.datasource

import android.content.Context
import com.example.star.aiwork.data.local.DatabaseProvider
import com.example.star.aiwork.domain.model.SessionEntity
import kotlinx.coroutines.flow.Flow

class SessionLocalDataSourceImpl(context: Context) : SessionLocalDataSource {

    private val sessionDao = DatabaseProvider.getDatabase(context).sessionDao()

    override suspend fun upsertSession(session: SessionEntity) {
        sessionDao.upsertSession(session)
    }

    override suspend fun getSession(id: String): SessionEntity? {
        return sessionDao.getSession(id)
    }

    override fun observeSessions(): Flow<List<SessionEntity>> {
        return sessionDao.getSessions()
    }

    override suspend fun deleteSession(id: String) {
        sessionDao.deleteSession(id)
    }

    override fun searchSessions(query: String): Flow<List<SessionEntity>> {
        val searchQuery = "%$query%"
        return sessionDao.searchSessions(searchQuery)
    }
}
