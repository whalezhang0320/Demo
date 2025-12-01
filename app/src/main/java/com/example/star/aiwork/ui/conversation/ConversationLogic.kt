/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.star.aiwork.ui.conversation

import android.content.Context
import android.net.Uri
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.data.model.LlmError
import com.example.star.aiwork.domain.usecase.MessagePersistenceGateway
import com.example.star.aiwork.domain.usecase.PauseStreamingUseCase
import com.example.star.aiwork.domain.usecase.RollbackMessageUseCase
import com.example.star.aiwork.domain.usecase.SendMessageUseCase
import com.example.star.aiwork.infra.util.toBase64
import com.example.star.aiwork.ui.ai.UIMessage
import com.example.star.aiwork.ui.ai.UIMessagePart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Handles the business logic for processing messages in the conversation.
 * Includes sending messages to AI providers, handling fallbacks, and auto-looping agents.
 */
class ConversationLogic(
    private val uiState: ConversationUiState,
    private val context: Context,
    private val authorMe: String,
    private val timeNow: String,
    private val sendMessageUseCase: SendMessageUseCase,
    private val pauseStreamingUseCase: PauseStreamingUseCase,
    private val rollbackMessageUseCase: RollbackMessageUseCase,
    private val sessionId: String,
    private val getProviderSettings: () -> List<ProviderSetting>,
    private val persistenceGateway: MessagePersistenceGateway? = null,
    private val onRenameSession: (sessionId: String, newName: String) -> Unit // ADDED
) {

    private var activeTaskId: String? = null

    /**
     * 取消当前的流式生成。
     */
    suspend fun cancelStreaming() {
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
        // ADDED: Auto-rename session logic
        if (!isAutoTriggered && uiState.channelName == "New Chat" && uiState.messages.none { it.author == authorMe }) {
            val newTitle = inputContent.take(20).trim()
            if (newTitle.isNotBlank()) {
                onRenameSession(sessionId, newTitle)
            }
        }

        // 1. 如果是用户手动发送，立即显示消息；自动追问也显示在 UI 上
        // 如果是重试 (isRetry=true)，则跳过 UI 消息添加
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
                // 清空已选择的图片
                uiState.selectedImageUri = null
            } else {
                // 自动追问消息，可以显示不同的样式或前缀，这里简单处理
                uiState.addMessage(Message(authorMe, "[Auto-Loop ${loopCount}] $inputContent", timeNow))
            }
        }

        // 2. 调用 LLM 获取响应
        if (providerSetting != null && model != null) {
            try {
                // ✅ 设置生成状态为 true
                withContext(Dispatchers.Main) {
                    uiState.isGenerating = true
                }
                
                // RAG Retrieval: 仅对非自动触发的消息尝试检索知识库
                val knowledgeContext = if (!isAutoTriggered) {
                    retrieveKnowledge(inputContent)
                } else {
                    ""
                }

                // 构建增强后的输入内容
                val augmentedInput = if (knowledgeContext.isNotBlank()) {
                    """
                    [Context from Knowledge Base]
                    $knowledgeContext
                    
                    [User Question]
                    $inputContent
                    """.trimIndent()
                } else {
                    inputContent
                }

                val activeAgent = uiState.activeAgent

                // 构造实际要发送的用户消息（考虑模板）
                // 仅对第一条用户原始输入应用模板，自动循环的消息通常是系统生成的指令，不应用模板
                // 注意：我们使用 augmentedInput 进行模板替换或直接发送
                val finalUserContent = if (activeAgent != null && !isAutoTriggered) {
                    activeAgent.messageTemplate.replace("{{ message }}", augmentedInput)
                } else {
                    augmentedInput
                }

                // 收集上下文消息：最近的聊天历史
                val contextMessages = uiState.messages.asReversed()
                    .filter { it.author != "System" } // 过滤掉 System (错误/提示) 消息，避免污染上下文
                    .map { msg ->
                        val role = if (msg.author == authorMe) MessageRole.USER else MessageRole.ASSISTANT
                        val parts = mutableListOf<UIMessagePart>()

                        // 文本部分
                        if (msg.content.isNotEmpty()) {
                            parts.add(UIMessagePart.Text(msg.content))
                        }

                        UIMessage(role = role, parts = parts)
                    }.takeLast(10).toMutableList()

                // **组装完整的消息列表 (Prompt Construction)**
                val messagesToSend = mutableListOf<UIMessage>()

                // 1. 系统提示词 (System Prompt)
                if (activeAgent != null && activeAgent.systemPrompt.isNotEmpty()) {
                    messagesToSend.add(UIMessage(
                        role = MessageRole.SYSTEM,
                        parts = listOf(UIMessagePart.Text(activeAgent.systemPrompt))
                    ))
                }

                // 2. 少样本示例 (Few-shot Examples)
                if (activeAgent != null) {
                    activeAgent.presetMessages.forEach { preset ->
                        messagesToSend.add(UIMessage(
                            role = preset.role,
                            parts = listOf(UIMessagePart.Text(preset.content))
                        ))
                    }
                }

                // 4. 历史对话 (Conversation History)
                messagesToSend.addAll(contextMessages)

                // 5. 当前用户输入 (Current Input)
                // 同样的逻辑：如果是新的一轮对话（非从历史中取出），我们需要确保它在列表中
                // 如果从历史中取出的最后一条和当前输入重复（或 UI 已经添加了），需要小心处理
                // 注意：我们在前面 UI 上添加的是 raw inputContent，但发送给 LLM 的是 finalUserContent (augmented)
                // 历史记录里存的是 raw content。所以 contextMessages 里的最后一条也是 raw content。
                // 我们现在要添加当前这一轮的"真实"请求（包含 context）。

                // 如果 contextMessages 中已经包含了用户刚刚发的 raw message (因为我们先 addMessage 到 uiState)，
                // 我们可能不想重复发一遍 raw message，而是发 augmented version。
                // uiState.messages 包含所有显示的消息。
                // 我们刚才 `uiState.addMessage` 添加了 inputContent。
                // `uiState.messages` 最前面是刚刚添加的消息。
                // `contextMessages` 是 takeLast(10) 并且 asReversed()，所以它包含了刚刚添加的消息作为最后一条。

                // 我们需要把最后一条（即当前的 raw input）替换为 augmented input，或者干脆移除它，单独添加 finalUserContent。
                if (messagesToSend.isNotEmpty() && messagesToSend.last().role == MessageRole.USER) {
                    // 简单起见，我们移除它，并用我们构造的 finalUserContent 代替
                    messagesToSend.removeAt(messagesToSend.lastIndex)
                }

                // 构建当前消息 parts
                val currentMessageParts = mutableListOf<UIMessagePart>()
                currentMessageParts.add(UIMessagePart.Text(finalUserContent))

                // 如果用户选择了图片，也加入到当前输入中
                if (!isAutoTriggered) {
                    val lastUserMsg = uiState.messages.firstOrNull { it.author == authorMe && it.author != "System" }
                    if (lastUserMsg?.imageUrl != null) {
                        try {
                            val imageUri = Uri.parse(lastUserMsg.imageUrl)
                            val base64Image = context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                                inputStream.readBytes().toBase64()
                            }
                            if (base64Image != null) {
                                val mimeType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
                                currentMessageParts.add(UIMessagePart.Image(url = "data:$mimeType;base64,$base64Image"))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                messagesToSend.add(UIMessage(
                    role = MessageRole.USER,
                    parts = currentMessageParts
                ))

                val params = TextGenerationParams(
                    model = model,
                    temperature = uiState.temperature,
                    maxTokens = uiState.maxTokens
                )

                // ✅ 添加一个带加载状态的空 AI 消息作为容器
                withContext(Dispatchers.Main) {
                    uiState.addMessage(Message("AI", "", timeNow, isLoading = true))
                }

                // 转换为 ChatDataItem
                val historyChat: List<ChatDataItem> = messagesToSend.dropLast(1).map { message ->
                    toChatDataItem(message)
                }
                val userMessage: ChatDataItem = toChatDataItem(messagesToSend.last())

                val sendResult = sendMessageUseCase(
                    sessionId = sessionId,
                    userMessage = userMessage,
                    history = historyChat,
                    providerSetting = providerSetting,
                    params = TextGenerationParams(
                        model = model,
                        temperature = uiState.temperature,
                        maxTokens = uiState.maxTokens
                    )
                )

                activeTaskId = sendResult.taskId

                var fullResponse = ""
                var lastUpdateTime = 0L
                val UPDATE_INTERVAL_MS = 500L

                // ✅ 无论流式还是非流式，都从 stream 收集响应
                sendResult.stream.collect { delta ->
                    fullResponse += delta
                    withContext(Dispatchers.Main) {
                        // ✅ 第一次收到内容时，移除加载状态
                        if (delta.isNotEmpty()) {
                            uiState.updateLastMessageLoadingState(false)
                        }
                        // ✅ 流式响应时逐字显示，非流式响应时一次性显示
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
                
                // ✅ 流式响应结束后，如果是非流式模式，一次性显示完整内容
                withContext(Dispatchers.Main) {
                    if (!uiState.streamResponse && fullResponse.isNotBlank()) {
                        uiState.updateLastMessageLoadingState(false)
                        uiState.appendToLastMessage(fullResponse)
                    }
                    // ✅ 响应结束后，设置生成状态为 false
                    uiState.isGenerating = false
                }

                // 流式响应结束后，更新最终内容到数据库（标记为完成状态）
                if (fullResponse.isNotBlank()) {
                    persistenceGateway?.replaceLastAssistantMessage(
                        sessionId,
                        ChatDataItem(
                            role = MessageRole.ASSISTANT.name.lowercase(),
                            content = fullResponse
                        )
                    )
                }

                // --- Auto-Loop Logic with Planner ---
                if (uiState.isAutoLoopEnabled && loopCount < uiState.maxLoopCount && fullResponse.isNotBlank()) {

                    // Step 2: 调用 Planner 模型生成下一步追问
                    val plannerSystemPrompt = """
                                        You are a task planner agent.
                                        Analyze the previous AI response and generate a short, specific instruction for the next step to deepen the task or solve remaining issues.
                                        If the task appears complete or no further meaningful steps are needed, reply with exactly "STOP".
                                        Output ONLY the instruction or "STOP".
                                    """.trimIndent()

                    val plannerUserMessage = ChatDataItem(
                        role = "user",
                        content = "Previous Response:\n$fullResponse"
                    )

                    val plannerHistory = listOf<ChatDataItem>(
                        ChatDataItem(role="system", content=plannerSystemPrompt)
                    )

                    val plannerResult = sendMessageUseCase(
                        sessionId = UUID.randomUUID().toString(), // Use a temporary session for planning
                        userMessage = plannerUserMessage,
                        history = plannerHistory,
                        providerSetting = providerSetting,
                        params = TextGenerationParams(
                            model = model,
                            temperature = 0.3f, // Lower temperature for more deterministic instructions
                            maxTokens = 100
                        )
                    )

                    var nextInstruction = ""
                    plannerResult.stream.collect { delta ->
                        nextInstruction += delta
                    }
                    nextInstruction = nextInstruction.trim()

                    if (nextInstruction != "STOP" && nextInstruction.isNotEmpty()) {
                        // 递归调用，使用 Planner 生成的指令
                        processMessage(
                            inputContent = nextInstruction,
                            providerSetting = providerSetting,
                            model = model,
                            isAutoTriggered = true,
                            loopCount = loopCount + 1,
                            retrieveKnowledge = retrieveKnowledge
                        )
                    }
                }

            } catch (e: Exception) {
                if (e is CancellationException) {
                    // 流被取消是正常操作，不需要显示错误
                    withContext(Dispatchers.Main) {
                        uiState.isGenerating = false
                        // 确保 AI 消息容器不是加载状态
                        uiState.updateLastMessageLoadingState(false)
                        // 可以选择添加一条“已取消”的提示，或者直接保持原样
                        if (uiState.messages.last().content.isBlank()) {
                            uiState.appendToLastMessage("[Cancelled]")
                        }
                    }
                    return@processMessage
                }
                withContext(Dispatchers.Main) {
                    uiState.addMessage(
                        Message("System", "Error: ${e.message}", timeNow)
                    )
                    uiState.isGenerating = false
                }
                e.printStackTrace()
            }
        } else {
             uiState.addMessage(
                Message("System", "No AI Provider configured.", timeNow)
            )
            uiState.isGenerating = false
        }
    }
    
    /**
     * 将 UIMessage 转换为 ChatDataItem
     */
    private fun toChatDataItem(message: UIMessage): ChatDataItem {
        val builder = StringBuilder()
        message.parts.forEach { part ->
            when (part) {
                is UIMessagePart.Text -> builder.append(part.text)
                is UIMessagePart.Image -> builder.append("\n[image:${part.url}]")
                else -> {}
            }
        }
        return ChatDataItem(
            role = message.role.name.lowercase(),
            content = builder.toString()
        )
    }
}
