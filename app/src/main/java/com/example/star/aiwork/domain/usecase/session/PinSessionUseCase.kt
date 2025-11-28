package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.domain.repository.SessionRepository

class PinSessionUseCase(
    private val repository: SessionRepository
) {
    suspend operator fun invoke(id: String, pinned: Boolean) {
        val session = repository.getSession(id) ?: return
        repository.updateSession(
            session.copy(
                pinned = pinned,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
