package com.example.star.aiwork.data.remote

import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.ProviderSetting
import kotlinx.coroutines.flow.Flow

/**
 * 专门负责 Google Gemini 流式聊天的远程数据源。
 *
 * 注意：真正的网络与 SSE 细节在 infra 层的 HttpClient / SseClient 中实现，
 * 这里仅负责构造请求与解析返回。
 */
interface GeminiRemoteDataSource {

    fun streamChat(
        history: List<ChatDataItem>,
        providerSetting: ProviderSetting.Google,
        params: TextGenerationParams,
        taskId: String
    ): Flow<String>
}


