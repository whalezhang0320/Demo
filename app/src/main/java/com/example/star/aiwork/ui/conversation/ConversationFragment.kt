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
import com.example.star.aiwork.data.repository.MessageRepositoryImpl
import com.example.star.aiwork.data.repository.SessionRepositoryImpl
import com.example.star.aiwork.data.local.datasource.MessageLocalDataSourceImpl
import com.example.star.aiwork.data.local.datasource.SessionLocalDataSourceImpl
import com.example.star.aiwork.domain.usecase.PauseStreamingUseCase
import com.example.star.aiwork.domain.usecase.RollbackMessageUseCase
import com.example.star.aiwork.domain.usecase.SendMessageUseCase
import com.example.star.aiwork.infra.network.SseClient
import kotlinx.coroutines.launch
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

                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                val currentSession by chatViewModel.currentSession.collectAsStateWithLifecycle()
                val sessions by chatViewModel.sessions.collectAsStateWithLifecycle()
                val messagesFromDb by chatViewModel.messages.collectAsStateWithLifecycle()
                val searchQuery by chatViewModel.searchQuery.collectAsStateWithLifecycle()

                // 获取或创建当前会话的 UI 状态
                val uiState = remember(currentSession?.id) {
                    currentSession?.let { session ->
                        chatViewModel.getOrCreateSessionUiState(session.id, session.name)
                    } ?: ConversationUiState(
                        channelName = "#composers",
                        channelMembers = 1,
                        initialMessages = emptyList()
                    )
                }

                val sseClient = remember { SseClient() }
                val remoteChatDataSource = remember { StreamingChatRemoteDataSource(sseClient) }
                val aiRepository = remember { AiRepositoryImpl(remoteChatDataSource) }

                val messageRepository = remember(context) {
                    val messageLocalDataSource = MessageLocalDataSourceImpl(context)
                    MessageRepositoryImpl(messageLocalDataSource)
                }
                val sessionRepository = remember(context) {
                    val sessionLocalDataSource = SessionLocalDataSourceImpl(context)
                    SessionRepositoryImpl(sessionLocalDataSource)
                }
                val messagePersistenceGateway = remember(messageRepository, sessionRepository) {
                    MessagePersistenceGatewayImpl(messageRepository, sessionRepository)
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

                val conversationLogic = remember(
                    currentSession?.id,
                    uiState,
                    chatViewModel,
                ) {
                    ConversationLogic(
                        uiState = uiState,
                        context = context,
                        authorMe = "me",
                        timeNow = "Now",
                        sendMessageUseCase = sendMessageUseCase,
                        pauseStreamingUseCase = pauseStreamingUseCase,
                        rollbackMessageUseCase = rollbackMessageUseCase,
                        sessionId = currentSession?.id ?: UUID.randomUUID().toString(),
                        getProviderSettings = { providerSettings },
                        persistenceGateway = messagePersistenceGateway,
                        onRenameSession = { sessionId, newName ->
                            chatViewModel.renameSession(sessionId, newName)
                        },
                        onPersistNewChatSession = { sessionId ->
                            chatViewModel.persistNewChatSession(sessionId)
                        },
                        isNewChat = { sessionId ->
                            chatViewModel.isNewChat(sessionId)
                        },
                        onSessionUpdated = { sessionId ->
                            // 刷新会话列表，让 drawer 中的会话按 updatedAt 排序
                            scope.launch {
                                chatViewModel.refreshSessions()
                            }
                        }
                    )
                }

                val convertedMessages = remember(messagesFromDb) {
                    messagesFromDb.map { entity ->
                        convertMessageEntityToMessage(entity)
                    }
                }

                // 当会话或消息变化时，同步数据库消息到 UI 状态
                LaunchedEffect(convertedMessages, currentSession?.id) {
                    currentSession?.let { session ->
                        val sessionUiState = chatViewModel.getOrCreateSessionUiState(session.id, session.name)
                        // 清空现有消息
                        while (sessionUiState.messages.isNotEmpty()) {
                            sessionUiState.removeFirstMessage()
                        }
                        // 添加数据库中的消息
                        convertedMessages.forEach { msg ->
                            sessionUiState.addMessage(msg)
                        }
                    }
                }

                // 当会话名称变化时，更新 UI 状态的 channelName
                LaunchedEffect(currentSession?.name, currentSession?.id) {
                    currentSession?.let { session ->
                        val sessionUiState = chatViewModel.getSessionUiState(session.id)
                        sessionUiState?.channelName = session.name.ifBlank { "新对话" }
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
                        onUpdateSettings = { temp, tokens, stream ->
                            activityViewModel.updateTemperature(temp)
                            activityViewModel.updateMaxTokens(tokens)
                            activityViewModel.updateStreamResponse(stream)
                        },
                        retrieveKnowledge = { query ->
                            activityViewModel.retrieveKnowledge(query)
                        },
                        currentSessionId = currentSession?.id,
                        searchQuery = searchQuery,
                        onSearchQueryChanged = { query -> chatViewModel.searchSessions(query) },
                        searchResults = sessions,
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
            imageUrl = entity.metadata.remoteUrl
        )
    }
}
