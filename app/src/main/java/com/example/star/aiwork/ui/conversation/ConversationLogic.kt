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
import com.example.star.aiwork.domain.usecase.PauseStreamingUseCase
import com.example.star.aiwork.domain.usecase.RollbackMessageUseCase
import com.example.star.aiwork.domain.usecase.SendMessageUseCase
import com.example.star.aiwork.infra.util.toBase64
import com.example.star.aiwork.ui.ai.UIMessage
import com.example.star.aiwork.ui.ai.UIMessagePart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

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
    private val sessionId: String
) {

    private var activeTaskId: String? = null

    suspend fun processMessage(
        inputContent: String,
        providerSetting: ProviderSetting?,
        model: Model?,
        isAutoTriggered: Boolean = false,
        loopCount: Int = 0,
        retrieveKnowledge: suspend (String) -> String = { "" }
    ) {
        // 1. 如果是用户手动发送，立即显示消息；自动追问也显示在 UI 上
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

        // 2. 调用 LLM 获取响应
        if (providerSetting != null && model != null) {
            try {
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
                val contextMessages = uiState.messages.asReversed().map { msg ->
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
                // 我们现在要添加当前这一轮的“真实”请求（包含 context）。
                
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
                val currentParts = mutableListOf<UIMessagePart>()
                if (finalUserContent.isNotEmpty()) {
                    currentParts.add(UIMessagePart.Text(finalUserContent))
                }

                // 如果有图片（且不是自动循环），读取并转换为 Base64 添加到 parts
                if (!isAutoTriggered) {
                    // 查找最新一条用户消息（刚刚添加的）
                    val lastUserMsg = uiState.messages.firstOrNull { it.author == authorMe }
                    if (lastUserMsg?.imageUrl != null) {
                        try {
                            val imageUri = Uri.parse(lastUserMsg.imageUrl)
                            // 读取图片并转 Base64
                            val base64Image = context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                                inputStream.readBytes().toBase64()
                            }
                            if (base64Image != null) {
                                val mimeType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
                                currentParts.add(UIMessagePart.Image(url = "data:$mimeType;base64,$base64Image"))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                messagesToSend.add(UIMessage(
                    role = MessageRole.USER,
                    parts = currentParts
                ))

                // 添加初始空 AI 消息占位符
                uiState.addMessage(
                    Message("AI", "", timeNow)
                )

                val historyChat = messagesToSend.dropLast(1).map { it.toChatDataItem() }
                val userMessage = messagesToSend.last().toChatDataItem()

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
                sendResult.stream.collect { delta ->
                    if (delta.isNotBlank()) {
                        fullResponse += delta
                        if (uiState.streamResponse) {
                            withContext(Dispatchers.Main) {
                                uiState.appendToLastMessage(delta)
                            }
                        }
                    }
                }

                if (!uiState.streamResponse && fullResponse.isNotBlank()) {
                    withContext(Dispatchers.Main) {
                        uiState.appendToLastMessage(fullResponse)
                    }
                }

                // --- Auto-Loop Logic with Planner ---
                // TODO: Planner 自动循环需基于新的 domain API 重新实现

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    uiState.addMessage(
                        Message("System", "Error: ${e.message}", timeNow)
                    )
                }
                e.printStackTrace()
            }
        } else {
            uiState.addMessage(
                Message("System", "No AI Provider configured.", timeNow)
            )
        }
    }

    private fun UIMessage.toChatDataItem(): ChatDataItem {
        val builder = StringBuilder()
        parts.forEach { part ->
            when (part) {
                is UIMessagePart.Text -> builder.append(part.text)
                is UIMessagePart.Image -> builder.append("\n[image:${part.url}]")
                else -> {}
            }
        }
        return ChatDataItem(
            role = this.role.name.lowercase(),
            content = builder.toString()
        )
    }
}
