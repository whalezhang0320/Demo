package com.example.star.aiwork.domain.usecase.draft

import com.example.star.aiwork.domain.model.DraftEntity
import com.example.star.aiwork.domain.repository.DraftRepository

class UpdateDraftUseCase(
    private val draftRepository: DraftRepository
) {
    suspend operator fun invoke(sessionId: String, content: String) {
        val draft = DraftEntity(
            sessionId = sessionId,
            content = content,
            updatedAt = System.currentTimeMillis()
        )
        draftRepository.upsertDraft(draft)
    }
}
