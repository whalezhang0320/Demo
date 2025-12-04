package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.data.local.datasource.SessionLocalDataSource

class PinSessionUseCase(
    private val dataSource: SessionLocalDataSource
) {
    suspend operator fun invoke(id: String, pinned: Boolean) {
        val session = dataSource.getSession(id) ?: return
        dataSource.upsertSession(
            session.copy(
                pinned = pinned,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
