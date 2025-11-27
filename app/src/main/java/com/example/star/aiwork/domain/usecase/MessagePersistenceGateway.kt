package com.example.star.aiwork.domain.usecase

import com.example.star.aiwork.domain.model.ChatDataItem

/**
 * 消息持久化抽象，由数据层实现。
 * TODO: 由负责持久化的同学提供实际实现。
 */
interface MessagePersistenceGateway {
    suspend fun appendMessage(sessionId: String, message: ChatDataItem)
    suspend fun replaceLastAssistantMessage(sessionId: String, newMessage: ChatDataItem)
    suspend fun removeLastAssistantMessage(sessionId: String)
}

