package com.example.star.aiwork.ui.conversation

import android.content.Context
import android.util.Log
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ModelType
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.domain.usecase.GenerateChatNameUseCase
import com.example.star.aiwork.domain.usecase.ImageGenerationUseCase
import com.example.star.aiwork.domain.usecase.MessagePersistenceGateway
import com.example.star.aiwork.domain.usecase.PauseStreamingUseCase
import com.example.star.aiwork.domain.usecase.RollbackMessageUseCase
import com.example.star.aiwork.domain.usecase.SendMessageUseCase
import com.example.star.aiwork.ui.conversation.util.ConversationErrorHelper.formatErrorMessage
import com.example.star.aiwork.ui.conversation.util.ConversationErrorHelper.isCancellationRelatedException
import com.example.star.aiwork.ui.conversation.util.ConversationLogHelper.logAllMessagesToSend
import com.example.star.aiwork.ui.conversation.logic.AutoLoopHandler
import com.example.star.aiwork.ui.conversation.logic.ImageGenerationHandler
import com.example.star.aiwork.ui.conversation.logic.MessageConstructionHelper
import com.example.star.aiwork.ui.conversation.logic.RollbackHandler
import com.example.star.aiwork.ui.conversation.logic.StreamingResponseHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Handles the business logic for processing messages in the conversation.
 * Includes sending messages to AI providers, handling fallbacks, and autolooping agents.
 * 
 * Refactored to delegate responsibilities to smaller handlers:
 * - ImageGenerationHandler
 * - StreamingResponseHandler
 * - RollbackHandler
 * - AutoLoopHandler
 * - MessageConstructionHelper
 */
class ConversationLogic(
    private val uiState: ConversationUiState,
    private val context: Context,
    private val authorMe: String,
    private val timeNow: String,
    private val sendMessageUseCase: SendMessageUseCase,
    private val pauseStreamingUseCase: PauseStreamingUseCase,
    private val rollbackMessageUseCase: RollbackMessageUseCase,
    private val imageGenerationUseCase: ImageGenerationUseCase,
    private val generateChatNameUseCase: GenerateChatNameUseCase? = null,
    private val sessionId: String,
    private val getProviderSettings: () -> List<ProviderSetting>,
    private val persistenceGateway: MessagePersistenceGateway? = null,
    private val onRenameSession: (sessionId: String, newName: String) -> Unit,
    private val onPersistNewChatSession: suspend (sessionId: String) -> Unit = { },
    private val isNewChat: (sessionId: String) -> Boolean = { false },
    private val onSessionUpdated: suspend (sessionId: String) -> Unit = { }
) {

    private var activeTaskId: String? = null
    // 用于保存流式收集协程的 Job，以便可以立即取消
    private var streamingJob: Job? = null
    // 用于保存提示消息流式显示的 Job，以便可以立即取消
    private var hintTypingJob: Job? = null
    // 使用 uiState 的协程作用域，这样每个会话可以管理自己的协程
    private val streamingScope: CoroutineScope = uiState.coroutineScope
    // 标记是否已被取消，用于非流式模式下避免显示已收集的内容
    @Volatile private var isCancelled = false

    // Handlers
    private val imageGenerationHandler = ImageGenerationHandler(
        uiState = uiState,
        imageGenerationUseCase = imageGenerationUseCase,
        persistenceGateway = persistenceGateway,
        sessionId = sessionId,
        timeNow = timeNow,
        onSessionUpdated = onSessionUpdated
    )

    private val streamingResponseHandler = StreamingResponseHandler(
        uiState = uiState,
        persistenceGateway = persistenceGateway,
        sessionId = sessionId,
        timeNow = timeNow,
        onSessionUpdated = onSessionUpdated
    )

    private val rollbackHandler = RollbackHandler(
        uiState = uiState,
        rollbackMessageUseCase = rollbackMessageUseCase,
        streamingResponseHandler = streamingResponseHandler,
        sessionId = sessionId,
        authorMe = authorMe,
        timeNow = timeNow
    )

    private val autoLoopHandler = AutoLoopHandler(
        uiState = uiState,
        sendMessageUseCase = sendMessageUseCase,
        getProviderSettings = getProviderSettings,
        timeNow = timeNow
    )

    /**
     * 取消当前的流式生成。
     */
    suspend fun cancelStreaming() {
        // 立即取消流式收集协程和提示消息的流式显示协程
        isCancelled = true
        streamingJob?.cancel()
        streamingJob = null
        hintTypingJob?.cancel() // 取消提示消息的流式显示
        hintTypingJob = null
        
        // 根据流式模式决定处理方式
        val currentContent: String
        withContext(Dispatchers.Main) {
            if (uiState.streamResponse) {
                // 流式模式：在消息末尾追加取消提示
                uiState.appendToLastMessage("\n（已取消生成）")
                uiState.updateLastMessageLoadingState(false)
                // 获取当前消息内容（包含取消提示）
                val lastMessage = uiState.messages.firstOrNull { it.author == "AI" }
                currentContent = lastMessage?.content ?: ""
            } else {
                // 非流式模式：清空已收集的内容，只显示取消提示
                uiState.replaceLastMessageContent("（已取消生成）")
                uiState.updateLastMessageLoadingState(false)
                currentContent = "（已取消生成）"
            }
        }
        
        // 保存当前内容到数据库（包含取消提示）
        if (currentContent.isNotEmpty()) {
            persistenceGateway?.replaceLastAssistantMessage(
                sessionId,
                ChatDataItem(
                    role = MessageRole.ASSISTANT.name.lowercase(),
                    content = currentContent
                )
            )
        }
        
        val taskId = activeTaskId
        if (taskId != null) {
            // 无论成功还是失败，都要清除状态
            pauseStreamingUseCase(taskId).fold(
                onSuccess = {
                    activeTaskId = null
                    withContext(Dispatchers.Main) {
                        uiState.isGenerating = false
                    }
                },
                onFailure = { error ->
                    // 取消失败时也清除状态，但不显示错误（取消操作本身不应该报错）
                    activeTaskId = null
                    withContext(Dispatchers.Main) {
                        uiState.isGenerating = false
                    }
                    // 记录日志但不显示给用户
                    android.util.Log.d("ConversationLogic", "Cancel streaming failed: ${error.message}")
                }
            )
        } else {
            // 如果没有活跃任务，直接清除状态
            withContext(Dispatchers.Main) {
                uiState.isGenerating = false
            }
        }
    }

    suspend fun processMessage(
        inputContent: String,
        providerSetting: ProviderSetting?,
        model: Model?,
        isAutoTriggered: Boolean = false,
        loopCount: Int = 0,
        retrieveKnowledge: suspend (String) -> String = { "" },
        isRetry: Boolean = false
    ) {
        // Session management (New Chat / Rename)
        if (isNewChat(sessionId)) {
            onPersistNewChatSession(sessionId)
            
            // ADDED: Auto-rename session logic using GenerateChatNameUseCase
            // 只有在新聊天且是第一条用户消息时才自动重命名
            if (!isAutoTriggered && (uiState.channelName == "New Chat" || uiState.channelName == "新聊天" || uiState.channelName == "新会话" || uiState.channelName == "new chat") && uiState.messages.none { it.author == authorMe }) {
                if (generateChatNameUseCase != null && providerSetting != null && model != null) {
                    // 使用GenerateChatNameUseCase生成标题
                    streamingScope.launch(Dispatchers.IO) {
                        try {
                        val titleFlow = generateChatNameUseCase(
                            userMessage = inputContent,
                            providerSetting = providerSetting,
                            model = model
                        )
                        
                            var generatedTitle = StringBuilder()
                            titleFlow
                                .onCompletion { 
                                    // 流完成后，持久化生成的标题
                                    val finalTitle = generatedTitle.toString().trim()
                                    if (finalTitle.isNotBlank()) {
                                        // 限制标题长度，避免过长
                                        val trimmedTitle = finalTitle.take(30).trim()
                                        withContext(Dispatchers.Main) {
                                            // 确保UI显示最终处理后的标题（可能和流过程中的显示略有不同）
                                            uiState.channelName = trimmedTitle
                                            // 持久化标题到数据库
                                            onRenameSession(sessionId, trimmedTitle)
                                            onSessionUpdated(sessionId)
                                            Log.d("ConversationLogic", "✅ [Auto-Rename] AI生成标题持久化完成: $trimmedTitle")
                                        }
                                    } else {
                                        // 如果AI生成失败，回退到简单截取
                                        val fallbackTitle = inputContent.take(20).trim()
                                        if (fallbackTitle.isNotBlank()) {
                                            withContext(Dispatchers.Main) {
                                                // 更新UI显示
                                                uiState.channelName = fallbackTitle
                                                // 持久化标题到数据库
                                                onRenameSession(sessionId, fallbackTitle)
                                                onSessionUpdated(sessionId)
                                                Log.d("ConversationLogic", "✅ [Auto-Rename] 回退标题完成: $fallbackTitle")
                                            }
                                        }
                                    }
                                }
                                .collect { chunk ->
                                    // 实时更新UI中的标题显示（不等待流结束）
                                    generatedTitle.append(chunk)
                                    val currentTitle = generatedTitle.toString().trim()
                                    if (currentTitle.isNotBlank()) {
                                        // 限制显示长度，避免过长
                                        val displayTitle = currentTitle.take(30).trim()
                                        withContext(Dispatchers.Main) {
                                            uiState.channelName = displayTitle
                                        }
                                    }
                                }
                        } catch (e: Exception) {
                            // 如果生成标题失败，回退到简单截取
                            Log.e("ConversationLogic", "❌ [Auto-Rename] AI生成标题失败: ${e.message}", e)
                            val fallbackTitle = inputContent.take(20).trim()
                            if (fallbackTitle.isNotBlank()) {
                                withContext(Dispatchers.Main) {
                                    // 更新UI显示
                                    uiState.channelName = fallbackTitle
                                    // 持久化标题到数据库
                                    onRenameSession(sessionId, fallbackTitle)
                                    onSessionUpdated(sessionId)
                                    Log.d("ConversationLogic", "✅ [Auto-Rename] 回退标题完成: $fallbackTitle")
                                }
                            }
                        }
                } else {
                    // 如果没有提供GenerateChatNameUseCase，使用简单的截取方式
                    val newTitle = inputContent.take(20).trim()
                    if (newTitle.isNotBlank()) {
                        onRenameSession(sessionId, newTitle)
                        onSessionUpdated(sessionId)
                        Log.d("ConversationLogic", "✅ [Auto-Rename] 简单标题完成，已调用 onSessionUpdated")
                    }
                }
            }
        }

        // UI Update: Display User Message
        if (!isRetry) {
            if (!isAutoTriggered) {
                val currentImageUri = uiState.selectedImageUri
                uiState.addMessage(
                    Message(
                        author = authorMe,
                        content = inputContent,
                        timestamp = timeNow,
                        imageUrl = currentImageUri?.toString()
                    )
                )
                uiState.selectedImageUri = null
            } else {
                uiState.addMessage(Message(authorMe, "[Auto-Loop ${loopCount}] $inputContent", timeNow))
            }
        }

        // 2. Call LLM or Image Generation
        if (providerSetting != null && model != null) {
            try {
                withContext(Dispatchers.Main) {
                    uiState.isGenerating = true
                }
                
                if (model.type == ModelType.IMAGE) {
                    imageGenerationHandler.generateImage(providerSetting, model, inputContent)
                    return
                }

                // Construct Messages
                val messagesToSend = MessageConstructionHelper.constructMessages(
                    uiState = uiState,
                    authorMe = authorMe,
                    inputContent = inputContent,
                    isAutoTriggered = isAutoTriggered,
                    activeAgent = uiState.activeAgent,
                    retrieveKnowledge = retrieveKnowledge,
                    context = context
                )

                val params = TextGenerationParams(
                    model = model,
                    temperature = uiState.temperature,
                    maxTokens = uiState.maxTokens
                )

                // Add empty AI message placeholder
                withContext(Dispatchers.Main) {
                    uiState.addMessage(Message("AI", "", timeNow, isLoading = true))
                }

                val historyChat: List<ChatDataItem> = messagesToSend.dropLast(1).map { message ->
                    MessageConstructionHelper.toChatDataItem(message)
                }
                val userMessage: ChatDataItem = MessageConstructionHelper.toChatDataItem(messagesToSend.last())

                logAllMessagesToSend(
                    sessionId = sessionId,
                    model = model,
                    params = params,
                    messagesToSend = messagesToSend,
                    historyChat = historyChat,
                    userMessage = userMessage,
                    isAutoTriggered = isAutoTriggered,
                    loopCount = loopCount
                )

                val sendResult = sendMessageUseCase(
                    sessionId = sessionId,
                    userMessage = userMessage,
                    history = historyChat,
                    providerSetting = providerSetting,
                    params = params
                )

                activeTaskId = sendResult.taskId
                isCancelled = false

                // Streaming Response Handling
                val fullResponse = streamingResponseHandler.handleStreaming(
                    scope = streamingScope,
                    stream = sendResult.stream,
                    isCancelledCheck = { isCancelled },
                    onJobCreated = { job, hintJob ->
                        streamingJob = job
                        hintTypingJob = hintJob
                    }
                )

                // Clear Jobs references after completion
                streamingJob = null
                hintTypingJob = null

                // --- Auto-Loop Logic with Planner ---
                if (uiState.isAutoLoopEnabled && loopCount < uiState.maxLoopCount && fullResponse.isNotBlank()) {
                    autoLoopHandler.handleAutoLoop(
                        fullResponse = fullResponse,
                        loopCount = loopCount,
                        currentProviderSetting = providerSetting,
                        currentModel = model,
                        retrieveKnowledge = retrieveKnowledge,
                        onProcessMessage = { content, pSetting, mod, auto, count, knowledge ->
                            processMessage(content, pSetting, mod, auto, count, knowledge)
                        }
                    )
                }

            } catch (e: Exception) {
                handleError(e, inputContent, providerSetting, model, isAutoTriggered, loopCount, retrieveKnowledge, isRetry)
            }
        } else {
             uiState.addMessage(
                Message("System", "No AI Provider configured.", timeNow)
            )
            uiState.isGenerating = false
        }
    }

    private suspend fun handleError(
        e: Exception,
        inputContent: String,
        providerSetting: ProviderSetting?,
        model: Model?,
        isAutoTriggered: Boolean,
        loopCount: Int,
        retrieveKnowledge: suspend (String) -> String,
        isRetry: Boolean
    ) {
        Log.e("ConversationLogic", "❌ handleError triggered: ${e.javaClass.simpleName} - ${e.message}", e)

        if (e is CancellationException || isCancellationRelatedException(e)) {
            Log.d("ConversationLogic", "⚠️ Error is cancellation related, ignoring.")
            withContext(Dispatchers.Main) {
                uiState.isGenerating = false
                uiState.updateLastMessageLoadingState(false)
            }
            return
        }

        Log.d("ConversationLogic", "🔍 Checking fallback eligibility: isRetry=$isRetry, enabled=${uiState.isFallbackEnabled}")

        // Fallback logic
        if (!isRetry && // 仅在尚未重试过的情况下尝试兜底
            uiState.isFallbackEnabled &&
            uiState.fallbackProviderId != null &&
            uiState.fallbackModelId != null
        ) {
            Log.d("ConversationLogic", "🔍 Fallback config found: providerId=${uiState.fallbackProviderId}, modelId=${uiState.fallbackModelId}")
            
            val providers = getProviderSettings()
            val fallbackProvider = providers.find { it.id == uiState.fallbackProviderId }
            val fallbackModel = fallbackProvider?.models?.find { it.id == uiState.fallbackModelId }
                ?: fallbackProvider?.models?.find { it.modelId == uiState.fallbackModelId }

            // 避免在当前已经是兜底配置的情况下陷入死循环（虽然!isRetry已经能大部分避免，但双重保险更好）
            val isSameAsCurrent = providerSetting?.id == uiState.fallbackProviderId && 
                (model?.id == fallbackModel?.id)

            Log.d("ConversationLogic", "🔍 Fallback candidates: provider=${fallbackProvider?.name}, model=${fallbackModel?.displayName}")
            Log.d("ConversationLogic", "🔍 isSameAsCurrent=$isSameAsCurrent (currentProvider=${providerSetting?.id}, currentModel=${model?.id})")

            if (fallbackProvider != null && fallbackModel != null && !isSameAsCurrent) {
                Log.i("ConversationLogic", "✅ Triggering configured fallback to ${fallbackProvider.name}...")
                withContext(Dispatchers.Main) {
                    uiState.updateLastMessageLoadingState(false)
                    uiState.addMessage(
                        Message("System", "Request failed (${e.message}). Fallback to ${fallbackProvider.name} (${fallbackModel.displayName})...", timeNow)
                    )
                }
                processMessage(
                    inputContent = inputContent,
                    providerSetting = fallbackProvider,
                    model = fallbackModel,
                    isAutoTriggered = isAutoTriggered,
                    loopCount = loopCount,
                    retrieveKnowledge = retrieveKnowledge,
                    isRetry = true
                )
                return
            } else {
                Log.w("ConversationLogic", "⚠️ Fallback skipped: Provider/Model not found or same as current.")
            }
        } else if (!isRetry) {
            Log.d("ConversationLogic", "🔍 Checking default Ollama fallback...")
            // 尝试默认的 Ollama 兜底，如果用户没有配置特定兜底模型，但有本地模型可用
            // 且当前不是 Ollama
            val isCurrentOllama = providerSetting is ProviderSetting.Ollama
            if (!isCurrentOllama) {
                val ollamaProvider = getProviderSettings().find { it is ProviderSetting.Ollama }
                if (ollamaProvider != null && ollamaProvider.models.isNotEmpty()) {
                    Log.i("ConversationLogic", "✅ Triggering default Ollama fallback...")
                    withContext(Dispatchers.Main) {
                        uiState.updateLastMessageLoadingState(false)
                        uiState.addMessage(
                            Message("System", "Request failed (${e.message}). Fallback to local Ollama...", timeNow)
                        )
                    }
                    processMessage(
                        inputContent = inputContent,
                        providerSetting = ollamaProvider,
                        model = ollamaProvider.models.first(),
                        isAutoTriggered = isAutoTriggered,
                        loopCount = loopCount,
                        retrieveKnowledge = retrieveKnowledge,
                        isRetry = true
                    )
                    return
                } else {
                     Log.d("ConversationLogic", "⚠️ No Ollama provider found or it has no models.")
                }
            } else {
                Log.d("ConversationLogic", "⚠️ Current provider is already Ollama.")
            }
        } else {
            Log.d("ConversationLogic", "Skipping configured fallback (retry or disabled or missing config).")
        }

        Log.e("ConversationLogic", "❌ No fallback triggered. Displaying error message.")
        withContext(Dispatchers.Main) {
            uiState.updateLastMessageLoadingState(false)
            uiState.isGenerating = false
            // 如果是重试产生的空消息（或第一次尝试），且内容为空，移除它
            if (uiState.messages.isNotEmpty() && 
                uiState.messages[0].author == "AI" && 
                uiState.messages[0].content.isBlank()) {
                uiState.removeFirstMessage()
            }
            
            val errorMessage = formatErrorMessage(e)
            uiState.addMessage(
                Message("System", errorMessage, timeNow)
            )
        }
        e.printStackTrace()
    }
    
    /**
     * 回滚最后一条助手消息并重新生成
     */
    suspend fun rollbackAndRegenerate(
        providerSetting: ProviderSetting?,
        model: Model?,
        retrieveKnowledge: suspend (String) -> String = { "" }
    ) {
        rollbackHandler.rollbackAndRegenerate(
            providerSetting = providerSetting,
            model = model,
            scope = streamingScope,
            isCancelledCheck = { isCancelled },
            onJobCreated = { job, hintJob ->
                streamingJob = job
                hintTypingJob = hintJob
            },
            onTaskIdUpdated = { taskId ->
                activeTaskId = taskId
            }
        )
    }
}
