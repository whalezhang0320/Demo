package com.example.star.aiwork.domain.usecase

import com.example.star.aiwork.data.repository.AiRepository
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.ProviderSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 丢弃最近一次助手回答并重新生成。
 */
class RollbackMessageUseCase(
    private val aiRepository: AiRepository,
    private val persistenceGateway: MessagePersistenceGateway
) {

    suspend operator fun invoke(
        sessionId: String,
        history: List<ChatDataItem>,
        providerSetting: ProviderSetting,
        params: TextGenerationParams
    ): Result<FlowResult> = withContext(Dispatchers.IO) {
        runCatching {
            // 先创建流对象（此时还未真正发送请求，只是准备）
            val taskId = java.util.UUID.randomUUID().toString()
            val flow = aiRepository.streamChat(history, providerSetting, params, taskId)
            // 流创建成功后再删除数据库中的消息
            // 注意：这里只是创建流对象，真正的网络请求会在收集流时才开始
            persistenceGateway.removeLastAssistantMessage(sessionId)
            FlowResult(flow, taskId)
        }
    }

    data class FlowResult(
        val stream: Flow<String>,
        val taskId: String
    )
}

