package com.example.star.aiwork.domain.usecase.draft

import com.example.star.aiwork.data.local.datasource.DraftLocalDataSource
import com.example.star.aiwork.domain.model.DraftEntity

class GetDraftUseCase(
    private val dataSource: DraftLocalDataSource
) {
    suspend operator fun invoke(sessionId: String): DraftEntity? =
        dataSource.getDraft(sessionId)
}
