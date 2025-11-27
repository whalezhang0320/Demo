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
            persistenceGateway.removeLastAssistantMessage(sessionId)
            val taskId = java.util.UUID.randomUUID().toString()
            val flow = aiRepository.streamChat(history, providerSetting, params, taskId)
            FlowResult(flow, taskId)
        }
    }

    data class FlowResult(
        val stream: Flow<String>,
        val taskId: String
    )
}

