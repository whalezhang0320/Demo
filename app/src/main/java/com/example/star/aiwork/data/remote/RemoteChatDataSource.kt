package com.example.star.aiwork.data.remote

import com.example.star.aiwork.data.model.AiMessage
import com.example.star.aiwork.data.model.ModelConfig
import kotlinx.coroutines.flow.Flow

/**
 * 面向 data/remote 层的统一协议接口。
 * 仅负责与第三方 API 直接通信。
 */
interface RemoteChatDataSource {
    fun streamChat(
        history: List<AiMessage>,
        config: ModelConfig
    ): Flow<String>

    suspend fun cancelStreaming(taskId: String)
}

