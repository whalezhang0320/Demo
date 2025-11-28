package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.domain.model.SessionEntity
import com.example.star.aiwork.domain.repository.SessionRepository

class CreateSessionUseCase(
    private val repository: SessionRepository
) {
    suspend operator fun invoke(session: SessionEntity) {
        repository.insertSession(session)
    }
}
