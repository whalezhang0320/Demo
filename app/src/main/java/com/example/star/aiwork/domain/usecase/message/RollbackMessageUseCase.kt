package com.example.star.aiwork.domain.usecase.message

import com.example.star.aiwork.domain.repository.MessageRepository

class RollbackMessageUseCase(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(messageId: String) {
        messageRepository.deleteMessage(messageId)
    }
}
