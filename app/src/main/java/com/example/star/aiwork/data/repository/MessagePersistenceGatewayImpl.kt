package com.example.star.aiwork.data.repository

import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.MessageEntity
import com.example.star.aiwork.domain.model.MessageMetadata
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.MessageStatus
import com.example.star.aiwork.domain.model.MessageType
import com.example.star.aiwork.domain.repository.MessageRepository
import com.example.star.aiwork.domain.repository.SessionRepository
import com.example.star.aiwork.domain.usecase.MessagePersistenceGateway
import java.util.UUID

/**
 * MessagePersistenceGateway 的真实实现。
 * 
 * 负责在流式对话过程中将消息持久化到数据库。
 * 
 * @param messageRepository 消息仓库，用于数据库操作
 * @param sessionRepository 会话仓库，用于更新会话的 updatedAt
 */
class MessagePersistenceGatewayImpl(
    private val messageRepository: MessageRepository,
    private val sessionRepository: SessionRepository? = null
) : MessagePersistenceGateway {

    /**
     * 追加一条新消息到会话。
     * 
     * @param sessionId 会话 ID
     * @param message 要追加的消息（ChatDataItem 格式）
     */
    override suspend fun appendMessage(sessionId: String, message: ChatDataItem) {
        val messageEntity = convertToMessageEntity(sessionId, message)
        messageRepository.insertMessage(messageEntity)
        // 更新会话的 updatedAt，让 drawer 中的会话按更新时间排序
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
        // 获取该会话的所有消息
        val messages = messageRepository.getMessages(sessionId)
        
        // 找到最后一条助手消息（从后往前找）
        val lastAssistantMessage = messages
            .asReversed()
            .firstOrNull { it.role == MessageRole.ASSISTANT }
        
        if (lastAssistantMessage != null) {
            // 更新消息内容，保持相同的 ID 和 createdAt
            // 判断消息状态：空内容为 SENDING，有内容为 STREAMING（流式过程中）或 DONE（流式结束）
            // 注意：这里我们默认设为 STREAMING，流式结束后会再次调用更新为 DONE
            val updatedMessage = lastAssistantMessage.copy(
                content = newMessage.content,
                status = when {
                    newMessage.content.isEmpty() -> MessageStatus.SENDING
                    // 如果消息已经完成（以句号等结尾），标记为 DONE
                    newMessage.content.trim().let { content ->
                        content.endsWith(".") || content.endsWith("?") || content.endsWith("!") || 
                        content.endsWith("。") || content.endsWith("？") || content.endsWith("！") ||
                        content.endsWith("\n\n") // 多行回复通常以双换行结尾
                    } -> MessageStatus.DONE
                    else -> MessageStatus.STREAMING
                }
            )
            
            // 使用 insertMessage，由于数据库使用 CONFLICT_REPLACE，相同 ID 会更新记录
            messageRepository.insertMessage(updatedMessage)
            // 如果消息状态为 DONE（流式结束），更新会话的 updatedAt
            if (updatedMessage.status == com.example.star.aiwork.domain.model.MessageStatus.DONE) {
                updateSessionUpdatedAt(sessionId)
            }
        } else {
            // 如果没有找到助手消息，则追加新消息
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
        // 获取该会话的所有消息
        val messages = messageRepository.getMessages(sessionId)
        
        // 找到最后一条助手消息
        val lastAssistantMessage = messages
            .asReversed()
            .firstOrNull { it.role == MessageRole.ASSISTANT }
        
        if (lastAssistantMessage != null) {
            messageRepository.deleteMessage(lastAssistantMessage.id)
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
        // 将 role 字符串转换为 MessageRole 枚举
        val role = when (chatDataItem.role.lowercase()) {
            "user" -> MessageRole.USER
            "assistant" -> MessageRole.ASSISTANT
            "system" -> MessageRole.SYSTEM
            "tool" -> MessageRole.TOOL
            else -> MessageRole.USER
        }

        // 根据内容判断消息类型
        val type = when {
            chatDataItem.content.contains("[image:") -> MessageType.IMAGE
            chatDataItem.content.contains("[audio:") -> MessageType.AUDIO
            chatDataItem.role.lowercase() == "system" -> MessageType.SYSTEM
            else -> MessageType.TEXT
        }

        // 判断消息状态
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
        sessionRepository?.let { repo ->
            val session = repo.getSession(sessionId)
            if (session != null) {
                repo.updateSession(
                    session.copy(updatedAt = System.currentTimeMillis())
                )
            }
        }
    }
}

