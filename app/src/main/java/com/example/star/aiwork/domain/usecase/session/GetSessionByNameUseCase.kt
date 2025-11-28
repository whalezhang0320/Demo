package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.domain.repository.SessionRepository
import kotlinx.coroutines.flow.map
class GetSessionByNameUseCase(
    private val repository: SessionRepository
) {
    operator fun invoke(name: String) =
        repository.observeSessions().map { list ->
            list.firstOrNull { it.name == name }
        }
}
