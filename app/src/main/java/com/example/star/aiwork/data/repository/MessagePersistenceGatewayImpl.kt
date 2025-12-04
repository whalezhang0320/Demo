package com.example.star.aiwork.data.repository

import android.util.Log
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
        try {
            // 添加重试机制，处理可能的竞态条件（消息可能正在被保存）
            var retryCount = 0
            val maxRetries = 3
            val retryDelayMs = 100L
            
            while (retryCount < maxRetries) {
                val messages = messageDataSource.observeMessages(sessionId).first()
                
                // 记录调试信息（只在第一次尝试时记录）
                if (retryCount == 0) {
                    Log.d("MessagePersistenceGateway", "🔄 [removeLastAssistantMessage] 开始删除最后一条助手消息")
                    Log.d("MessagePersistenceGateway", "会话ID: $sessionId, 消息总数: ${messages.size}")
                }
                
                if (messages.isEmpty()) {
                    if (retryCount == 0) {
                        Log.w("MessagePersistenceGateway", "⚠️ 消息列表为空，无法删除助手消息")
                    }
                    // 如果消息列表为空，可能是消息还没保存，重试一次
                    if (retryCount < maxRetries - 1) {
                        retryCount++
                        kotlinx.coroutines.delay(retryDelayMs)
                        continue
                    }
                    return
                }
                
                // 从后往前查找最后一条助手消息
                // 注意：messages 是按 createdAt ASC 排序的，所以最新的消息在列表末尾
                // 需要从末尾往前找，找到第一个（时间上最新的）助手消息
                val lastAssistantMessage = messages
                    .lastOrNull { it.role == MessageRole.ASSISTANT }
                
                if (lastAssistantMessage != null) {
                    Log.d("MessagePersistenceGateway", "✅ 找到要删除的助手消息: id=${lastAssistantMessage.id}, " +
                        "content=${lastAssistantMessage.content.take(50)}..., " +
                        "status=${lastAssistantMessage.status}, " +
                        "createdAt=${lastAssistantMessage.createdAt}, " +
                        "重试次数: $retryCount")
                    
                    messageDataSource.deleteMessage(lastAssistantMessage.id)
                    Log.d("MessagePersistenceGateway", "✅ 成功删除助手消息: ${lastAssistantMessage.id}")
                    return // 成功删除，退出重试循环
                } else {
                    // 如果没找到助手消息，可能是消息还没保存，重试一次
                    if (retryCount < maxRetries - 1) {
                        retryCount++
                        kotlinx.coroutines.delay(retryDelayMs)
                        continue
                    }
                    
                    // 记录详细信息以便调试（只在最后一次尝试时记录）
                    val messageRoles = messages.map { "${it.role.name}(${it.id.take(8)})" }.joinToString(", ")
                    Log.w("MessagePersistenceGateway", "⚠️ 未找到助手消息（已重试 $retryCount 次）。消息列表角色: [$messageRoles]")
                    Log.w("MessagePersistenceGateway", "⚠️ 最后5条消息详情:")
                    messages.takeLast(5).forEachIndexed { index, msg ->
                        Log.w("MessagePersistenceGateway", "  [${messages.size - 5 + index}] ${msg.role.name} - " +
                            "id=${msg.id.take(8)}..., status=${msg.status}, " +
                            "content=${msg.content.take(30)}...")
                    }
                    return // 重试失败，退出
                }
            }
        } catch (e: Exception) {
            Log.e("MessagePersistenceGateway", "❌ 删除助手消息时发生错误", e)
            throw e
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
