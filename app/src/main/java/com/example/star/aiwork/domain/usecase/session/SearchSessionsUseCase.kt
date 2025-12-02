package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.domain.model.SessionEntity
import com.example.star.aiwork.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow

class SearchSessionsUseCase(private val sessionRepository: SessionRepository) {
    operator fun invoke(query: String): Flow<List<SessionEntity>> {
        return sessionRepository.searchSessions(query)
    }
}
