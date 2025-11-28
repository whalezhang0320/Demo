package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.domain.repository.SessionRepository

class GetSessionListUseCase(
    private val repository: SessionRepository
) {
    operator fun invoke() = repository.observeSessions()
}