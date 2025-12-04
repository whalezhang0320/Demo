package com.example.star.aiwork.domain.usecase.draft

import com.example.star.aiwork.data.local.datasource.DraftLocalDataSource
import com.example.star.aiwork.domain.model.DraftEntity

class UpdateDraftUseCase(
    private val draftDataSource: DraftLocalDataSource
) {
    suspend operator fun invoke(sessionId: String, content: String) {
        if (content.isEmpty()) {
            draftDataSource.deleteDraft(sessionId)
        } else {
            val draft = DraftEntity(
                sessionId = sessionId,
                content = content,
                updatedAt = System.currentTimeMillis()
            )
            draftDataSource.upsertDraft(draft)
        }
    }
}
