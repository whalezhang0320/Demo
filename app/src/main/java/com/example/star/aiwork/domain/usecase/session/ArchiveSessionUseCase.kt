package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.domain.repository.SessionRepository

class ArchiveSessionUseCase(
    private val repository: SessionRepository
) {
    suspend operator fun invoke(id: String, archived: Boolean) {
        val session = repository.getSession(id) ?: return
        repository.updateSession(
            session.copy(
                archived = archived,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}

