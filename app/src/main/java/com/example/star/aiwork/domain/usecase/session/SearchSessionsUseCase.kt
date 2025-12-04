package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.data.local.datasource.SessionLocalDataSource
import com.example.star.aiwork.domain.model.SessionEntity
import kotlinx.coroutines.flow.Flow

class SearchSessionsUseCase(private val dataSource: SessionLocalDataSource) {
    operator fun invoke(query: String): Flow<List<SessionEntity>> {
        return dataSource.searchSessions(query)
    }
}
