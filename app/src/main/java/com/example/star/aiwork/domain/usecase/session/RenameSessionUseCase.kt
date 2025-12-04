package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.data.local.datasource.SessionLocalDataSource

class RenameSessionUseCase(
    private val dataSource: SessionLocalDataSource
) {
    suspend operator fun invoke(id: String, newName: String) {
        val session = dataSource.getSession(id) ?: return
        dataSource.upsertSession(
            session.copy(
                name = newName,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
