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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.bundleOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.TextFieldValue
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.findNavController
import com.example.star.aiwork.ui.MainViewModel
import com.example.star.aiwork.R
import com.example.star.aiwork.domain.model.MessageEntity
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.ui.theme.JetchatTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.star.aiwork.data.remote.StreamingChatRemoteDataSource
import com.example.star.aiwork.data.repository.AiRepositoryImpl
import com.example.star.aiwork.data.repository.MessagePersistenceGatewayImpl
import com.example.star.aiwork.data.local.datasource.MessageLocalDataSourceImpl
import com.example.star.aiwork.data.local.datasource.SessionLocalDataSourceImpl
import com.example.star.aiwork.domain.usecase.GenerateChatNameUseCase
import com.example.star.aiwork.domain.usecase.ImageGenerationUseCase
import com.example.star.aiwork.domain.usecase.PauseStreamingUseCase
import com.example.star.aiwork.domain.usecase.RollbackMessageUseCase
import com.example.star.aiwork.domain.usecase.SendMessageUseCase
import com.example.star.aiwork.infra.network.SseClient
import com.example.star.aiwork.infra.network.defaultOkHttpClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import java.util.UUID

class ConversationFragment : Fragment() {

    private val activityViewModel: MainViewModel by activityViewModels()
    private val chatViewModel: ChatViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)

            setContent {
                val providerSettings by activityViewModel.providerSettings.collectAsStateWithLifecycle()
                val temperature by activityViewModel.temperature.collectAsStateWithLifecycle()
                val maxTokens by activityViewModel.maxTokens.collectAsStateWithLifecycle()
                val streamResponse by activityViewModel.streamResponse.collectAsStateWithLifecycle()
                val activeProviderId by activityViewModel.activeProviderId.collectAsStateWithLifecycle()
                val activeModelId by activityViewModel.activeModelId.collectAsStateWithLifecycle()
                
                // 收集兜底设置
                val isFallbackEnabled by activityViewModel.isFallbackEnabled.collectAsStateWithLifecycle()
                val fallbackProviderId by activityViewModel.fallbackProviderId.collectAsStateWithLifecycle()
                val fallbackModelId by activityViewModel.fallbackModelId.collectAsStateWithLifecycle()

                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                val currentSession by chatViewModel.currentSession.collectAsStateWithLifecycle()
                val sessions by chatViewModel.sessions.collectAsStateWithLifecycle()
                val messagesFromDb by chatViewModel.messages.collectAsStateWithLifecycle()
                val searchQuery by chatViewModel.searchQuery.collectAsStateWithLifecycle()
                val searchResults by chatViewModel.searchResults.collectAsStateWithLifecycle()

                // 获取或创建当前会话的 UI 状态
                val uiState = remember(currentSession?.id) {
                    currentSession?.let { session ->
                        chatViewModel.getOrCreateSessionUiState(session.id, session.name)
                    } ?: ConversationUiState(
                        channelName = "新对话",
                        channelMembers = 1,
                        initialMessages = emptyList()
                    )
                }

                val okHttpClient = remember { defaultOkHttpClient() }
                val sseClient = remember { SseClient(okHttpClient) }
                val remoteChatDataSource = remember { StreamingChatRemoteDataSource(sseClient) }
                val aiRepository = remember { AiRepositoryImpl(remoteChatDataSource, okHttpClient) }

                val messageLocalDataSource = remember(context) { MessageLocalDataSourceImpl(context) }
                val sessionLocalDataSource = remember(context) { SessionLocalDataSourceImpl(context) }
                val messagePersistenceGateway = remember(messageLocalDataSource, sessionLocalDataSource) {
                    MessagePersistenceGatewayImpl(messageLocalDataSource, sessionLocalDataSource)
                }
                val sendMessageUseCase = remember(aiRepository, messagePersistenceGateway, scope) {
                    SendMessageUseCase(aiRepository, messagePersistenceGateway, scope)
                }
                val pauseStreamingUseCase = remember(aiRepository) {
                    PauseStreamingUseCase(aiRepository)
                }
                val rollbackMessageUseCase = remember(aiRepository) {
                    RollbackMessageUseCase(aiRepository, messagePersistenceGateway)
                }
                val imageGenerationUseCase = remember(aiRepository) {
                    ImageGenerationUseCase(aiRepository)
                }
                val generateChatNameUseCase = remember(aiRepository) {
                    GenerateChatNameUseCase(aiRepository)
                }

                val conversationLogic = remember(
                    currentSession?.id,
                    uiState,
                    chatViewModel,
                    providerSettings // Add providerSettings as a dependency
                ) {
                    ConversationLogic(
                        uiState = uiState,
                        context = context,
                        authorMe = "me",
                        timeNow = "Now",
                        sendMessageUseCase = sendMessageUseCase,
                        pauseStreamingUseCase = pauseStreamingUseCase,
                        rollbackMessageUseCase = rollbackMessageUseCase,
                        imageGenerationUseCase = imageGenerationUseCase,
                        generateChatNameUseCase = generateChatNameUseCase,
                        sessionId = currentSession?.id ?: UUID.randomUUID().toString(),
                        getProviderSettings = { providerSettings },
                        persistenceGateway = messagePersistenceGateway,
                        onRenameSession = { sessionId, newName ->
                            chatViewModel.renameSession(sessionId, newName)
                        },
                        onPersistNewChatSession = { sessionId ->
                            // 如果当前没有会话，先创建临时会话
                            if (currentSession == null) {
                                // 创建临时会话，使用传入的 sessionId
                                // 注意：这里我们需要创建一个临时会话并标记为新会话
                                val sessionName = "新聊天"
                                chatViewModel.createTemporarySession(sessionName)
                            }
                            chatViewModel.persistNewChatSession(sessionId)
                        },
                        isNewChat = { sessionId ->
                            chatViewModel.isNewChat(sessionId)
                        },
                        onSessionUpdated = { sessionId ->
                            // 刷新会话列表，让 drawer 中的会话按 updatedAt 排序
                            chatViewModel.refreshSessions()
                        },
                        taskManager = chatViewModel.streamingTaskManager
                    )
                }

                val convertedMessages = remember(messagesFromDb) {
                    messagesFromDb.map { entity ->
                        convertMessageEntityToMessage(entity)
                    }
                }

                // 只在会话切换时，从数据库同步消息到 UI 状态（仅在 uiState 中没有消息时）
                // uiState 是从缓存中获取的，如果它已经包含消息（之前加载过），则直接使用
                // 只有当 uiState.messages 为空时（新会话或首次加载），才从数据库加载
                // 这样可以保留正在流式生成的消息（临时状态），避免不必要的清空和重新加载
                LaunchedEffect(currentSession?.id) {
                    currentSession?.let { session ->
                        // 使用同一个 uiState 实例，确保一致性
                        // uiState 是从缓存中获取的，所以 isGenerating、isRecording、textFieldValue 等
                        // 会话级别的状态字段会自动从缓存中恢复，不需要重置
                        
                        // 只有当 uiState 中没有消息时，才从数据库加载
                        // 如果 uiState 中已有消息，说明这个会话之前已经被加载过，直接使用即可
                        val isTemporarySession = chatViewModel.isNewChat(session.id)
                        val needsLoad = if (uiState.messages.isEmpty()) {
                            !isTemporarySession
                        } else {
                            // 如果 uiState 不为空，检查消息数量是否与数据库一致
                            // 对于临时会话，不需要检查数据库（因为临时会话的消息不在数据库中）
                            if (isTemporarySession) {
                                false // 临时会话的消息只在内存中，不需要从数据库加载
                            } else {
                                // 检查数据库中的消息数量是否与 uiState 中的消息数量一致
                                // 如果不一致，说明需要重新加载（可能是从临时会话切换过来的）
                                val messagesFromDb = chatViewModel.messages.value
                                val currentSessionMessages = messagesFromDb.filter { it.sessionId == session.id }
                                uiState.messages.size != currentSessionMessages.size
                            }
                        }
                        if (needsLoad) {
                            // 如果 uiState 中有消息但不属于当前会话，先清空
                            if (uiState.messages.isNotEmpty()) {
                                uiState.clearMessages()
                            }
                            // 等待 messagesFromDb 异步更新到当前会话
                            // 通过 Flow 等待消息更新，确保获取的是当前会话的消息，而不是旧会话的消息
                            if (!isTemporarySession) {
                                val latestMessagesFromFlow = chatViewModel.messages
                                    .filter { messages ->
                                        // 等待消息更新：要么所有消息都属于当前会话，要么列表为空（新会话）
                                        messages.all { it.sessionId == session.id } || messages.isEmpty()
                                    }
                                    .first() // 等待第一次符合条件的更新
                                
                                // 转换消息并添加到 UI 状态
                                latestMessagesFromFlow
                                    .filter { it.sessionId == session.id } // 双重验证，确保消息属于当前会话
                                    .map { entity ->
                                        convertMessageEntityToMessage(entity)
                                    }
                                    .forEach { msg ->
                                        uiState.addMessage(msg)
                                    }
                            }
                        }
                        // 如果 uiState.messages 不为空，说明消息已经在 uiState 中（从缓存恢复），
                        // processMessage 和 Regenerate 也会更新 uiState 中的消息，所以不需要重新加载
                        
                        // 更新 channelName
                        uiState.channelName = session.name.ifBlank { "新对话" }
                        // 注意：isGenerating、isRecording、isTranscribing、pendingTranscription、
                        // textFieldValue、selectedImageUri 等字段会从缓存的 ConversationUiState 中
                        // 自动恢复，保持每个会话的独立状态
                    }
                }

                // 当会话名称变化时，更新 UI 状态的 channelName
                LaunchedEffect(currentSession?.name, currentSession?.id) {
                    currentSession?.let { session ->
                        uiState.channelName = session.name.ifBlank { "新对话" }
                    }
                }

                JetchatTheme {
                    ConversationContent(
                        uiState = uiState,
                        logic = conversationLogic,
                        navigateToProfile = { user ->
                            val bundle = bundleOf("userId" to user)
                            findNavController().navigate(
                                R.id.nav_profile,
                                bundle,
                            )
                        },
                        onNavIconPressed = {
                            activityViewModel.openDrawer()
                        },
                        providerSettings = providerSettings,
                        activeProviderId = activeProviderId,
                        activeModelId = activeModelId,
                        temperature = temperature,
                        maxTokens = maxTokens,
                        streamResponse = streamResponse,
                        isFallbackEnabled = isFallbackEnabled,
                        fallbackProviderId = fallbackProviderId,
                        fallbackModelId = fallbackModelId,
                        onUpdateSettings = { temp, tokens, stream ->
                            activityViewModel.updateTemperature(temp)
                            activityViewModel.updateMaxTokens(tokens)
                            activityViewModel.updateStreamResponse(stream)
                        },
                        onUpdateFallbackSettings = { enabled, pId, mId ->
                            activityViewModel.updateFallbackEnabled(enabled)
                            activityViewModel.updateFallbackModel(pId, mId)
                        },
                        retrieveKnowledge = { query ->
                            activityViewModel.retrieveKnowledge(query)
                        },
                        currentSessionId = currentSession?.id,
                        searchQuery = searchQuery,
                        onSearchQueryChanged = { query -> chatViewModel.searchSessions(query) },
                        searchResults = searchResults,
                        onSessionSelected = { session -> chatViewModel.selectSession(session) }
                    )
                }
            }
        }


    private fun convertMessageEntityToMessage(entity: MessageEntity): Message {
        val author = when (entity.role) {
            MessageRole.USER -> "me"
            MessageRole.ASSISTANT -> "assistant"
            MessageRole.SYSTEM -> "system"
            MessageRole.TOOL -> "tool"
        }

        val timestamp = if (entity.createdAt > 0) {
            val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            dateFormat.format(Date(entity.createdAt))
        } else {
            "Now"
        }

        return Message(
            author = author,
            content = entity.content,
            timestamp = timestamp,
            imageUrl = entity.metadata.localFilePath
        )
    }
}
