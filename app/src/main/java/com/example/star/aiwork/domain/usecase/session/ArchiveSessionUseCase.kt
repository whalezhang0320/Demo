package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.data.local.datasource.SessionLocalDataSource

class ArchiveSessionUseCase(
    private val dataSource: SessionLocalDataSource
) {
    suspend operator fun invoke(id: String, archived: Boolean) {
        val session = dataSource.getSession(id) ?: return
        dataSource.upsertSession(
            session.copy(
                archived = archived,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
