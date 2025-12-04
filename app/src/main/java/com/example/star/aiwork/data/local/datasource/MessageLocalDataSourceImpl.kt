package com.example.star.aiwork.data.local.datasource

import android.content.Context
import com.example.star.aiwork.data.local.DatabaseProvider
import com.example.star.aiwork.domain.model.MessageEntity
import kotlinx.coroutines.flow.Flow

class MessageLocalDataSourceImpl(context: Context) : MessageLocalDataSource {

    private val messageDao = DatabaseProvider.getDatabase(context).messageDao()

    override suspend fun upsertMessage(message: MessageEntity) {
        messageDao.upsertMessage(message)
    }

    override suspend fun getMessage(id: String): MessageEntity? {
        return messageDao.getMessage(id)
    }

    override fun observeMessages(sessionId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessages(sessionId)
    }

    override suspend fun deleteMessagesBySession(sessionId: String) {
        messageDao.deleteMessages(sessionId)
    }

    override suspend fun deleteMessage(messageId: String) {
        messageDao.deleteMessage(messageId)
    }
}
