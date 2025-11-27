package com.example.star.aiwork.domain.usecase

import com.example.star.aiwork.data.repository.AiRepository
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.ProviderSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * 负责发送消息并拉起流式生成。
 */
class SendMessageUseCase(
    private val aiRepository: AiRepository,
    private val persistenceGateway: MessagePersistenceGateway,
    private val scope: CoroutineScope
) {

    data class Output(
        val stream: Flow<String>,
        val taskId: String
    )

    operator fun invoke(
        sessionId: String,
        userMessage: ChatDataItem,
        history: List<ChatDataItem>,
        providerSetting: ProviderSetting,
        params: TextGenerationParams,
        taskId: String = java.util.UUID.randomUUID().toString()
    ): Output {
        scope.launch(Dispatchers.IO) {
            persistenceGateway.appendMessage(sessionId, userMessage)
            // 占位的 assistant message，等待流式结果补充
            persistenceGateway.appendMessage(
                sessionId,
                ChatDataItem(role = MessageRole.ASSISTANT.name.lowercase(), content = "")
            )
        }

        val stream = aiRepository.streamChat(history + userMessage, providerSetting, params, taskId)
            .onStart {
                // 可以在此通知 UI 启动中
            }

        return Output(stream = stream, taskId = taskId)
    }
}

