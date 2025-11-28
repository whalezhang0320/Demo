package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.domain.repository.SessionRepository
import com.example.star.aiwork.domain.repository.MessageRepository
import com.example.star.aiwork.domain.repository.DraftRepository

class DeleteSessionUseCase(
    private val sessionRepository: SessionRepository,
    private val messageRepository: MessageRepository,
    private val draftRepository: DraftRepository
) {
    suspend operator fun invoke(sessionId: String) {
        messageRepository.deleteMessagesBySession(sessionId)
        draftRepository.deleteDraft(sessionId)
        sessionRepository.deleteSession(sessionId)
    }
}
