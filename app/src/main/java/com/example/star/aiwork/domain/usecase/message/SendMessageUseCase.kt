package com.example.star.aiwork.domain.usecase.message

import com.example.star.aiwork.data.local.datasource.MessageLocalDataSource
import com.example.star.aiwork.data.local.datasource.SessionLocalDataSource
import com.example.star.aiwork.domain.model.MessageEntity

class SendMessageUseCase(
    private val messageDataSource: MessageLocalDataSource,
    private val sessionDataSource: SessionLocalDataSource
) {
    suspend operator fun invoke(message: MessageEntity) {
        messageDataSource.upsertMessage(message)

        // 更新会话更新时间
        val session = sessionDataSource.getSession(message.sessionId)
        if (session != null) {
            sessionDataSource.upsertSession(
                session.copy(updatedAt = System.currentTimeMillis())
            )
        }
    }
}
