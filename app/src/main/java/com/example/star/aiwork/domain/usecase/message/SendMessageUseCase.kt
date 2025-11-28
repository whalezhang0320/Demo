package com.example.star.aiwork.domain.usecase.message

import com.example.star.aiwork.domain.model.MessageEntity
import com.example.star.aiwork.domain.repository.MessageRepository
import com.example.star.aiwork.domain.repository.SessionRepository

class SendMessageUseCase(
    private val messageRepository: MessageRepository,
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(message: MessageEntity) {
        messageRepository.insertMessage(message)

        // 更新会话更新时间
        val session = sessionRepository.getSession(message.sessionId)
        if (session != null) {
            sessionRepository.updateSession(
                session.copy(updatedAt = System.currentTimeMillis())
            )
        }
    }
}
