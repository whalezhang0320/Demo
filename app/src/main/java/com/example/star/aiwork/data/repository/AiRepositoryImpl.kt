package com.example.star.aiwork.data.repository

import com.example.star.aiwork.data.remote.RemoteChatDataSource
import com.example.star.aiwork.data.repository.mapper.toAiMessages
import com.example.star.aiwork.data.repository.mapper.toModelConfig
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.ProviderSetting

class AiRepositoryImpl(
    private val remoteChatDataSource: RemoteChatDataSource
) : AiRepository {

    override fun streamChat(
        history: List<ChatDataItem>,
        providerSetting: ProviderSetting,
        params: TextGenerationParams,
        taskId: String
    ) = remoteChatDataSource.streamChat(
        history = history.toAiMessages(),
        config = providerSetting.toModelConfig(params, taskId)
    )

    override suspend fun cancelStreaming(taskId: String) {
        remoteChatDataSource.cancelStreaming(taskId)
    }
}

