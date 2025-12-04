package com.example.star.aiwork.data.repository

import com.example.star.aiwork.data.local.datasource.MessageLocalDataSource
import com.example.star.aiwork.data.local.datasource.SessionLocalDataSource
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.MessageEntity
import com.example.star.aiwork.domain.model.MessageMetadata
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.MessageStatus
import com.example.star.aiwork.domain.model.MessageType
import com.example.star.aiwork.domain.usecase.MessagePersistenceGateway
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * MessagePersistenceGateway 的真实实现。
 * 
 * 负责在流式对话过程中将消息持久化到数据库。
 * 
 * @param messageDataSource 消息数据源，用于数据库操作
 * @param sessionDataSource 会话数据源，用于更新会话的 updatedAt
 */
class MessagePersistenceGatewayImpl(
    private val messageDataSource: MessageLocalDataSource,
    private val sessionDataSource: SessionLocalDataSource? = null
) : MessagePersistenceGateway {

    /**
     * 追加一条新消息到会话。
     * 
     * @param sessionId 会话 ID
     * @param message 要追加的消息（ChatDataItem 格式）
     */
    override suspend fun appendMessage(sessionId: String, message: ChatDataItem) {
        val messageEntity = convertToMessageEntity(sessionId, message)
        messageDataSource.upsertMessage(messageEntity)
        updateSessionUpdatedAt(sessionId)
    }

    /**
     * 替换会话中最后一条助手消息。
     * 
     * 用于流式响应过程中更新 AI 回复内容。
     * 
     * @param sessionId 会话 ID
     * @param newMessage 新的消息内容
     */
    override suspend fun replaceLastAssistantMessage(
        sessionId: String,
        newMessage: ChatDataItem
    ) {
        val messages = messageDataSource.observeMessages(sessionId).first()
        val lastAssistantMessage = messages
            .asReversed()
            .firstOrNull { it.role == MessageRole.ASSISTANT }
        
        if (lastAssistantMessage != null) {
            val updatedMessage = lastAssistantMessage.copy(
                content = newMessage.content,
                status = when {
                    newMessage.content.isEmpty() -> MessageStatus.SENDING
                    newMessage.content.trim().let { content ->
                        content.endsWith(".") || content.endsWith("?") || content.endsWith("!") || 
                        content.endsWith("。") || content.endsWith("？") || content.endsWith("！") ||
                        content.endsWith("\n\n")
                    } -> MessageStatus.DONE
                    else -> MessageStatus.STREAMING
                }
            )
            messageDataSource.upsertMessage(updatedMessage)
            if (updatedMessage.status == com.example.star.aiwork.domain.model.MessageStatus.DONE) {
                updateSessionUpdatedAt(sessionId)
            }
        } else {
            appendMessage(sessionId, newMessage)
        }
    }

    /**
     * 删除会话中最后一条助手消息。
     * 
     * 用于回滚操作，当用户点击"重新生成"时使用。
     * 
     * @param sessionId 会话 ID
     */
    override suspend fun removeLastAssistantMessage(sessionId: String) {
        val messages = messageDataSource.observeMessages(sessionId).first()
        val lastAssistantMessage = messages
            .asReversed()
            .firstOrNull { it.role == MessageRole.ASSISTANT }
        
        if (lastAssistantMessage != null) {
            messageDataSource.deleteMessage(lastAssistantMessage.id)
        }
    }

    /**
     * 将 ChatDataItem 转换为 MessageEntity。
     * 
     * @param sessionId 会话 ID
     * @param chatDataItem 聊天数据项
     * @return 转换后的 MessageEntity
     */
    private fun convertToMessageEntity(
        sessionId: String,
        chatDataItem: ChatDataItem
    ): MessageEntity {
        val role = when (chatDataItem.role.lowercase()) {
            "user" -> MessageRole.USER
            "assistant" -> MessageRole.ASSISTANT
            "system" -> MessageRole.SYSTEM
            "tool" -> MessageRole.TOOL
            else -> MessageRole.USER
        }

        val type = when {
            chatDataItem.content.contains("[image:") -> MessageType.IMAGE
            chatDataItem.content.contains("[audio:") -> MessageType.AUDIO
            chatDataItem.role.lowercase() == "system" -> MessageType.SYSTEM
            else -> MessageType.TEXT
        }

        val status = when {
            chatDataItem.content.isEmpty() && role == MessageRole.ASSISTANT -> MessageStatus.SENDING
            chatDataItem.content.isNotEmpty() && role == MessageRole.ASSISTANT -> MessageStatus.STREAMING
            else -> MessageStatus.DONE
        }

        return MessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = role,
            type = type,
            content = chatDataItem.content,
            metadata = MessageMetadata(),
            parentMessageId = null,
            createdAt = System.currentTimeMillis(),
            status = status
        )
    }
    
    /**
     * 更新会话的 updatedAt 时间戳
     */
    private suspend fun updateSessionUpdatedAt(sessionId: String) {
        sessionDataSource?.let { ds ->
            val session = ds.getSession(sessionId)
            if (session != null) {
                ds.upsertSession(
                    session.copy(updatedAt = System.currentTimeMillis())
                )
            }
        }
    }
}
