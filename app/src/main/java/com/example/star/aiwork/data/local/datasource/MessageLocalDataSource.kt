package com.example.star.aiwork.data.local.datasource

import com.example.star.aiwork.domain.model.MessageEntity
import kotlinx.coroutines.flow.Flow

interface MessageLocalDataSource {

    suspend fun upsertMessage(message: MessageEntity)

    suspend fun getMessage(id: String): MessageEntity?

    fun observeMessages(sessionId: String): Flow<List<MessageEntity>>

    suspend fun deleteMessagesBySession(sessionId: String)

    suspend fun deleteMessage(messageId: String)
}
