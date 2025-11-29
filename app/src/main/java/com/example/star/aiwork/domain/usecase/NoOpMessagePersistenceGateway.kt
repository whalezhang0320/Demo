package com.example.star.aiwork.domain.usecase

import com.example.star.aiwork.domain.model.ChatDataItem

/**
 * 临时的消息持久化空实现，等待数据层接入。
 */
object NoOpMessagePersistenceGateway : MessagePersistenceGateway {
    override suspend fun appendMessage(sessionId: String, message: ChatDataItem) = Unit

    override suspend fun replaceLastAssistantMessage(sessionId: String, newMessage: ChatDataItem) = Unit

    override suspend fun removeLastAssistantMessage(sessionId: String) = Unit
}


