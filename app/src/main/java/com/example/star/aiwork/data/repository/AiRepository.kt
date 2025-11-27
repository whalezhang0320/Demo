package com.example.star.aiwork.data.repository

import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.ProviderSetting
import kotlinx.coroutines.flow.Flow

/**
 * data/repository 层对外暴露的统一接口。
 * 负责承接 domain 层的实体，调用 remote 层并返回标准化结果。
 */
interface AiRepository {
    fun streamChat(
        history: List<ChatDataItem>,
        providerSetting: ProviderSetting,
        params: TextGenerationParams,
        taskId: String = java.util.UUID.randomUUID().toString()
    ): Flow<String>

    suspend fun cancelStreaming(taskId: String)
}

