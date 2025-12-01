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
import com.example.star.aiwork.data.exampleUiState
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
import com.example.star.aiwork.data.local.datasource.MessageLocalDataSourceImpl
import com.example.star.aiwork.domain.usecase.PauseStreamingUseCase
import com.example.star.aiwork.domain.usecase.RollbackMessageUseCase
import com.example.star.aiwork.domain.usecase.SendMessageUseCase
import com.example.star.aiwork.infra.network.SseClient
import java.util.UUID

/**
 * 承载聊天界面的 Fragment。
 *
 * 它是应用主要导航图的一部分，负责：
 * 1. 托管 Compose UI 内容。
 * 2. 获取和订阅 ViewModel 数据（包括用户配置）。
 * 3. 处理 Fragment 级别的导航事件。
 */
class ConversationFragment : Fragment() {

    // 获取 Activity 范围的 ViewModel 实例，以共享数据
    private val activityViewModel: MainViewModel by activityViewModels()
    private val chatViewModel: ChatViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)

            setContent {
                // 收集来自 ViewModel 的状态流，确保 UI 随数据更新而重组
                val providerSettings by activityViewModel.providerSettings.collectAsStateWithLifecycle()
                val temperature by activityViewModel.temperature.collectAsStateWithLifecycle()
                val maxTokens by activityViewModel.maxTokens.collectAsStateWithLifecycle()
                val streamResponse by activityViewModel.streamResponse.collectAsStateWithLifecycle()
                val activeProviderId by activityViewModel.activeProviderId.collectAsStateWithLifecycle()
                val activeModelId by activityViewModel.activeModelId.collectAsStateWithLifecycle()
                
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                // 从 ChatViewModel 获取当前会话和消息
                val currentSession by chatViewModel.currentSession.collectAsStateWithLifecycle()
                val messagesFromDb by chatViewModel.messages.collectAsStateWithLifecycle()
                
                val sseClient = remember { SseClient() }
                val remoteChatDataSource = remember { StreamingChatRemoteDataSource(sseClient) }
                val aiRepository = remember { AiRepositoryImpl(remoteChatDataSource) }
                
                // 创建 MessageRepository 和 MessagePersistenceGateway
                val messageRepository = remember(context) {
                    val messageLocalDataSource = MessageLocalDataSourceImpl(context)
                    MessageRepositoryImpl(messageLocalDataSource)
                }
                val messagePersistenceGateway = remember(messageRepository) {
                    MessagePersistenceGatewayImpl(messageRepository)
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
                    currentSession,
                    // ADD ANY OTHER NECESSARY KEYS HERE
                ) {
                    ConversationLogic(
                        uiState = exampleUiState,
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
                        }
                    )
                }
                
                // 将 MessageEntity 转换为 Message
                val convertedMessages = remember(messagesFromDb) {
                    messagesFromDb.map { entity ->
                        convertMessageEntityToMessage(entity)
                    }
                }
                
                // 同步消息到 UI State
                LaunchedEffect(convertedMessages, currentSession?.id) {
                    // 清空现有消息
                    while (exampleUiState.messages.isNotEmpty()) {
                        exampleUiState.removeFirstMessage()
                    }
                    // 按从旧到新的顺序添加消息（旧消息先添加，会在列表顶部）
                    // 由于 reverseLayout = true，显示时旧消息会在顶部，新消息在底部
                    convertedMessages.forEach { msg ->
                        exampleUiState.addMessage(msg)
                    }
                }
                
                // 根据当前会话更新标题
                LaunchedEffect(currentSession?.name, currentSession?.id) {
                    currentSession?.let { session ->
                        exampleUiState.channelName = session.name.ifBlank { "新对话" }
                    } ?: run {
                        exampleUiState.channelName = "#composers"
                    }
                }

                JetchatTheme {
                    ConversationContent(
                        uiState = exampleUiState, // 示例 UI 状态
                        logic = conversationLogic,
                        navigateToProfile = { user ->
                            // 导航到个人资料页面的回调
                            val bundle = bundleOf("userId" to user)
                            findNavController().navigate(
                                R.id.nav_profile,
                                bundle,
                            )
                        },
                        onNavIconPressed = {
                            // 打开侧边栏
                            activityViewModel.openDrawer()
                        },
                        // 传递从 ViewModel 获取的配置参数
                        providerSettings = providerSettings,
                        activeProviderId = activeProviderId,
                        activeModelId = activeModelId,
                        temperature = temperature,
                        maxTokens = maxTokens,
                        streamResponse = streamResponse,
                        // 处理配置更新事件，回调 ViewModel 进行保存
                        onUpdateSettings = { temp, tokens, stream ->
                            activityViewModel.updateTemperature(temp)
                            activityViewModel.updateMaxTokens(tokens)
                            activityViewModel.updateStreamResponse(stream)
                        },
                        retrieveKnowledge = { query ->
                            activityViewModel.retrieveKnowledge(query)
                        },
                        currentSessionId = currentSession?.id
                    )
            }
        }
    }
    
    /**
     * 将 MessageEntity 转换为 Message
     */
    private fun convertMessageEntityToMessage(entity: MessageEntity): Message {
        val author = when (entity.role) {
            MessageRole.USER -> "me"
            MessageRole.ASSISTANT -> "assistant"
            MessageRole.SYSTEM -> "system"
            MessageRole.TOOL -> "tool"
        }
        
        // 格式化时间戳
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
