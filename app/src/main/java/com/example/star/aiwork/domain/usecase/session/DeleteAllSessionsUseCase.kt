package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.data.local.datasource.SessionLocalDataSource

class DeleteAllSessionsUseCase(private val sessionLocalDataSource: SessionLocalDataSource) {
    suspend operator fun invoke() {
        sessionLocalDataSource.deleteAllSessions()
    }
}
