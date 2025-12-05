package com.example.star.aiwork.ui.conversation.logic

import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.usecase.MessagePersistenceGateway
import com.example.star.aiwork.ui.conversation.ConversationUiState
import com.example.star.aiwork.ui.conversation.Message
import com.example.star.aiwork.ui.conversation.util.ConversationErrorHelper.formatErrorMessage
import com.example.star.aiwork.ui.conversation.util.ConversationLogHelper.logThrowableChain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StreamingResponseHandler(
    private val uiState: ConversationUiState,
    private val persistenceGateway: MessagePersistenceGateway?,
    private val sessionId: String,
    private val timeNow: String,
    private val onSessionUpdated: suspend (sessionId: String) -> Unit
) {

    /**
     * Handles the streaming response from the AI model.
     *
     * @param scope The coroutine scope to launch the collection in.
     * @param stream The flow of strings from the model.
     * @param isCancelledCheck A lambda to check if the process has been cancelled externally.
     * @param onJobCreated A callback to return the streaming job and hint job to the caller for management.
     * @return The full response string if successful, or empty string if failed/cancelled.
     */
    suspend fun handleStreaming(
        scope: CoroutineScope,
        stream: Flow<String>,
        isCancelledCheck: () -> Boolean,
        onJobCreated: (Job, Job?) -> Unit
    ): String {
        var fullResponse = ""
        var lastUpdateTime = 0L
        val UPDATE_INTERVAL_MS = 500L
        var hasShownSlowLoadingHint = false
        var hintTypingJob: Job? = null

        // Variable to capture exception to throw later
        var capturedException: Throwable? = null

        val streamingJob = scope.launch {
            try {
                stream.asCharTypingStream(charDelayMs = 30L).collect { delta ->
                    fullResponse += delta
                    withContext(Dispatchers.Main) {
                        if (uiState.streamResponse && delta.isNotEmpty()) {
                            uiState.updateLastMessageLoadingState(false)
                        }

                        if (!uiState.streamResponse && delta.isNotEmpty() && !hasShownSlowLoadingHint) {
                            hasShownSlowLoadingHint = true
                            val hintText = "加载较慢？试试流式输出~"
                            hintTypingJob = scope.launch {
                                try {
                                    for (char in hintText) {
                                        if (isCancelledCheck()) break
                                        withContext(Dispatchers.Main) {
                                            uiState.appendToLastMessage(char.toString())
                                            uiState.updateLastMessageLoadingState(true)
                                        }
                                        delay(30L)
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                }
                            }
                        }

                        if (uiState.streamResponse) {
                            uiState.appendToLastMessage(delta)
                        }

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL_MS) {
                            persistenceGateway?.replaceLastAssistantMessage(
                                sessionId,
                                ChatDataItem(
                                    role = MessageRole.ASSISTANT.name.lowercase(),
                                    content = fullResponse
                                )
                            )
                            lastUpdateTime = currentTime
                        }
                    }
                }
            } catch (streamError: CancellationException) {
                // 任何 CancellationException 都应该被正常处理，不应该被视为错误
                // 无论是用户主动取消还是切换对话后取消，都应该静默处理
                // 不记录为错误，也不向上抛出
                // 如果 isCancelledCheck() 返回 false，说明可能是切换对话后的取消，这是正常的
            } catch (streamError: Exception) {
                logThrowableChain("StreamingHandler", "streamError during collect", streamError)
                // Capture the exception and throw it later to let ConversationLogic handle fallback
                capturedException = streamError
            }
        }

        // Pass back jobs immediately
        onJobCreated(streamingJob, hintTypingJob)

        // Wait for completion
        try {
            streamingJob.join()
        } catch (e: CancellationException) {
            // Expected if cancelled
        }

        try {
            hintTypingJob?.join()
        } catch (e: CancellationException) {
            // Expected if cancelled
        }

        // If an exception occurred during streaming, throw it now so ConversationLogic can handle fallback
        if (capturedException != null) {
            throw capturedException!!
        }

        withContext(Dispatchers.Main) {
            if (!uiState.streamResponse && fullResponse.isNotBlank() && !isCancelledCheck()) {
                uiState.updateLastMessageLoadingState(false)
                uiState.replaceLastMessageContent(fullResponse)
            }
            uiState.isGenerating = false
        }

        if (fullResponse.isNotBlank() && !isCancelledCheck()) {
            persistenceGateway?.replaceLastAssistantMessage(
                sessionId,
                ChatDataItem(
                    role = MessageRole.ASSISTANT.name.lowercase(),
                    content = fullResponse
                )
            )
            onSessionUpdated(sessionId)
        }

        return if (isCancelledCheck()) "" else fullResponse
    }

    // Helper methods for char typing effect
    private fun Flow<String>.asCharTypingStream(charDelayMs: Long = 30L): Flow<String> = flow {
        collect { chunk ->
            if (chunk.isEmpty()) return@collect
            if (charDelayMs > 0) {
                for (ch in chunk) {
                    emit(ch.toString())
                    delay(charDelayMs)
                }
            } else {
                for (ch in chunk) {
                    emit(ch.toString())
                }
            }
        }
    }
}
