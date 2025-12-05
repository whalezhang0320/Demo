package com.example.star.aiwork.ui.conversation.logic

import android.util.Log
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.domain.usecase.RollbackMessageUseCase
import com.example.star.aiwork.ui.conversation.ConversationUiState
import com.example.star.aiwork.ui.conversation.Message
import com.example.star.aiwork.ui.conversation.util.ConversationErrorHelper.formatErrorMessage
import com.example.star.aiwork.ui.conversation.util.ConversationErrorHelper.isCancellationRelatedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

class RollbackHandler(
    private val uiState: ConversationUiState,
    private val rollbackMessageUseCase: RollbackMessageUseCase,
    private val streamingResponseHandler: StreamingResponseHandler,
    private val sessionId: String,
    private val authorMe: String,
    private val timeNow: String
) {

    suspend fun rollbackAndRegenerate(
        providerSetting: ProviderSetting?,
        model: Model?,
        scope: CoroutineScope,
        isCancelledCheck: () -> Boolean,
        onJobCreated: (Job, Job?) -> Unit,
        onTaskIdUpdated: suspend (String?) -> Unit
    ) {
        if (providerSetting == null || model == null) {
            withContext(Dispatchers.Main) {
                uiState.addMessage(
                    Message("System", "No AI Provider configured.", timeNow)
                )
            }
            return
        }

        val lastUserMessage = uiState.messages.findLast { it.author == authorMe }
        if (lastUserMessage == null) return

        try {
            withContext(Dispatchers.Main) {
                uiState.isGenerating = true
            }

            // Prepare history for rollback
            val allMessages = uiState.messages.asReversed().filter { it.author != "System" }
            val lastAssistantIndex = allMessages.indexOfLast { it.author != authorMe }
            val historyMessages = if (lastAssistantIndex >= 0) {
                allMessages.take(lastAssistantIndex) + allMessages.drop(lastAssistantIndex + 1)
            } else {
                allMessages
            }
            
            val contextMessages = historyMessages.map { msg ->
                val role = if (msg.author == authorMe) MessageRole.USER else MessageRole.ASSISTANT
                ChatDataItem(role = role.name.lowercase(), content = msg.content)
            }.toMutableList()

            if (historyMessages.none { it.author == authorMe }) {
                withContext(Dispatchers.Main) { uiState.isGenerating = false }
                return
            }

            val params = TextGenerationParams(
                model = model,
                temperature = uiState.temperature,
                maxTokens = uiState.maxTokens
            )

            Log.d("RollbackHandler", "🔄 [rollbackAndRegenerate] Rolling back...")
            
            val rollbackResult = rollbackMessageUseCase(
                sessionId = sessionId,
                history = contextMessages,
                providerSetting = providerSetting,
                params = params
            )

            rollbackResult.fold(
                onSuccess = { flowResult ->
                    withContext(Dispatchers.Main) {
                        uiState.removeLastAssistantMessage(authorMe)
                        uiState.addMessage(Message("AI", "", timeNow, isLoading = true))
                    }

                    onTaskIdUpdated(flowResult.taskId)
                    // isCancelled = false (handled by caller or implicit)

                    streamingResponseHandler.handleStreaming(
                        scope = scope,
                        stream = flowResult.stream,
                        isCancelledCheck = isCancelledCheck,
                        onJobCreated = onJobCreated
                    )
                },
                onFailure = { error ->
                    withContext(Dispatchers.Main) {
                        uiState.isGenerating = false
                        uiState.updateLastMessageLoadingState(false)
                        val errorMessage = formatErrorMessage(error as? Exception ?: Exception(error.message, error))
                        uiState.addMessage(Message("System", errorMessage, timeNow))
                    }
                    error.printStackTrace()
                }
            )
        } catch (e: Exception) {
            if (e is CancellationException || isCancellationRelatedException(e)) {
                withContext(Dispatchers.Main) {
                    uiState.isGenerating = false
                    uiState.updateLastMessageLoadingState(false)
                }
                return
            }
            
            withContext(Dispatchers.Main) {
                uiState.isGenerating = false
                uiState.updateLastMessageLoadingState(false)
                val errorMessage = formatErrorMessage(e)
                uiState.addMessage(Message("System", errorMessage, timeNow))
            }
            e.printStackTrace()
        }
    }
}
