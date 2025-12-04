package com.example.star.aiwork.domain.usecase.message

import com.example.star.aiwork.data.local.datasource.MessageLocalDataSource
import com.example.star.aiwork.domain.model.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * UseCase: 观察某个会话的消息列表
 */
class ObserveMessagesUseCase(
    private val messageDataSource: MessageLocalDataSource
) {
    operator fun invoke(sessionId: String): Flow<List<MessageEntity>> {
        return messageDataSource.observeMessages(sessionId) // 返回 Flow<List<MessageEntity>>
    }
}