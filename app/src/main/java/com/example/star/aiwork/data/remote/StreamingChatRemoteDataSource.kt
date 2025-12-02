package com.example.star.aiwork.data.remote

import android.util.Log
import com.example.star.aiwork.data.model.ModelConfig
import com.example.star.aiwork.data.repository.mapper.toAiMessages
import com.example.star.aiwork.data.repository.mapper.toModelConfig
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.infra.network.SseClient
import com.example.star.aiwork.infra.util.json
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/**
 * 统一的流式聊天远程数据源，负责在 OpenAI 兼容实现与 Gemini 实现之间做路由。
 */
class StreamingChatRemoteDataSource(
    private val sseClient: SseClient,
    private val jsonParser: Json = json
) : RemoteChatDataSource {

    // OpenAI 兼容协议的具体实现
    private val openAIStreamingRemoteDataSource =
        OpenAIStreamingRemoteDataSource(sseClient, jsonParser)

    // Google Gemini 的具体实现
    private val geminiRemoteDataSource: GeminiRemoteDataSource =
        GeminiStreamingRemoteDataSource(sseClient, jsonParser)

    override fun streamChat(
        history: List<ChatDataItem>,
        providerSetting: ProviderSetting,
        params: TextGenerationParams,
        taskId: String
    ): Flow<String> {
        return when (providerSetting) {
            is ProviderSetting.Google -> {
                geminiRemoteDataSource.streamChat(
                    history = history,
                    providerSetting = providerSetting,
                    params = params,
                    taskId = taskId
                )
            }

            is ProviderSetting.OpenAICompatible -> {
                val aiMessages = history.toAiMessages()
                val config = providerSetting
                    .toModelConfig(params, taskId) as? ModelConfig.OpenAICompatible
                    ?: error("Expected OpenAICompatible ModelConfig for provider: ${providerSetting::class.simpleName}")

                openAIStreamingRemoteDataSource.streamChat(
                    history = aiMessages,
                    config = config
                )
            }

            else -> error("Unsupported provider setting: ${providerSetting::class.simpleName}")
        }
    }

    override suspend fun cancelStreaming(taskId: String) {
        // 取消操作应该静默处理，即使失败也不应该抛出异常
        // 底层使用同一个 SseClient，直接通过 taskId 取消即可
        try {
            sseClient.cancel(taskId)
        } catch (e: Exception) {
            Log.d("StreamingChatRemoteDataSource", "Cancel streaming failed for taskId: $taskId", e)
        }
    }
}
