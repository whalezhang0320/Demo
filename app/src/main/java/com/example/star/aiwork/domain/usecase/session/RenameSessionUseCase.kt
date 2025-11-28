package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.domain.repository.SessionRepository

class RenameSessionUseCase(
    private val repository: SessionRepository
) {
    suspend operator fun invoke(id: String, newName: String) {
        val session = repository.getSession(id) ?: return
        repository.updateSession(
            session.copy(
                name = newName,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
