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

@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.star.aiwork.ui.conversation

import android.Manifest
import android.content.ClipDescription
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.star.aiwork.R
import com.example.star.aiwork.data.exampleUiState
import com.example.star.aiwork.data.remote.StreamingChatRemoteDataSource
import com.example.star.aiwork.data.repository.AiRepositoryImpl
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.domain.usecase.NoOpMessagePersistenceGateway
import com.example.star.aiwork.domain.usecase.PauseStreamingUseCase
import com.example.star.aiwork.domain.usecase.RollbackMessageUseCase
import com.example.star.aiwork.domain.usecase.SendMessageUseCase
import com.example.star.aiwork.infra.network.SseClient
import com.example.star.aiwork.ui.theme.JetchatTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 对话屏幕的入口点。
 *
 * 这个可组合函数协调主要的对话 UI，包括：
 * - 显示消息历史记录。
 * - 处理用户输入（文本和语音）。
 * - 管理 AI 模型交互（文本生成）。
 * - 处理设置对话框和导航。
 *
 * @param uiState [ConversationUiState] 包含要显示的消息和 UI 状态。
 * @param navigateToProfile 请求导航到用户个人资料时的回调。
 * @param modifier 应用于此布局节点的 [Modifier]。
 * @param onNavIconPressed 当按下导航图标（汉堡菜单）时的回调。
 * @param providerSettings 可用的 AI 提供商设置列表。
 * @param activeProviderId 当前选中的提供商 ID。
 * @param activeModelId 当前选中的模型 ID。
 * @param temperature 当前的 AI 文本生成温度设置 (0.0 - 2.0)。
 * @param maxTokens 生成的最大 Token 数。
 * @param streamResponse 是否流式传输 AI 响应或等待完整响应。
 * @param onUpdateSettings 更新模型设置（温度、最大 Token 数、流式响应）的回调。
 * @param retrieveKnowledge 检索知识库的回调函数。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationContent(
    uiState: ConversationUiState,
    navigateToProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNavIconPressed: () -> Unit = { },
    providerSettings: List<ProviderSetting> = emptyList(),
    activeProviderId: String? = null,
    activeModelId: String? = null,
    temperature: Float = 0.7f,
    maxTokens: Int = 2000,
    streamResponse: Boolean = true,
    onUpdateSettings: (Float, Int, Boolean) -> Unit = { _, _, _ -> },
    retrieveKnowledge: suspend (String) -> String = { "" }
) {
    val authorMe = stringResource(R.string.author_me)
    val timeNow = stringResource(id = R.string.now)
    val context = LocalContext.current

    // 列表滚动和顶部应用栏行为的状态
    val scrollState = rememberLazyListState()
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState)
    val scope = rememberCoroutineScope()
    
    // 显示模型设置对话框的状态
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // 将从 ViewModel 传递的参数与 UiState 同步
    // 这确保了 UI 反映持久化的设置
    LaunchedEffect(temperature, maxTokens, streamResponse) {
        uiState.temperature = temperature
        uiState.maxTokens = maxTokens
        uiState.streamResponse = streamResponse
    }

    // 拖放视觉状态
    var background by remember {
        mutableStateOf(Color.Transparent)
    }

    var borderStroke by remember {
        mutableStateOf(Color.Transparent)
    }
    
    // 如果请求，显示模型设置对话框
    if (showSettingsDialog) {
        ModelSettingsDialog(
            uiState = uiState,
            onDismissRequest = { 
                // 当对话框关闭时保存设置
                onUpdateSettings(uiState.temperature, uiState.maxTokens, uiState.streamResponse)
                showSettingsDialog = false 
            }
        )
    }

    // 拖放回调处理
    val dragAndDropCallback = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val clipData = event.toAndroidDragEvent().clipData

                if (clipData.itemCount < 1) {
                    return false
                }

                // 将拖放的文本添加为新消息
                uiState.addMessage(
                    Message(authorMe, clipData.getItemAt(0).text.toString(), timeNow),
                )

                return true
            }

            override fun onStarted(event: DragAndDropEvent) {
                super.onStarted(event)
                borderStroke = Color.Red
            }

            override fun onEntered(event: DragAndDropEvent) {
                super.onEntered(event)
                background = Color.Red.copy(alpha = .3f)
            }

            override fun onExited(event: DragAndDropEvent) {
                super.onExited(event)
                background = Color.Transparent
            }

            override fun onEnded(event: DragAndDropEvent) {
                super.onEnded(event)
                background = Color.Transparent
                borderStroke = Color.Transparent
            }
        }
    }

    // 根据 ID 选择当前的 ProviderSetting 和 Model
    val providerSetting = remember(providerSettings, activeProviderId) { 
        providerSettings.find { it.id == activeProviderId } ?: providerSettings.firstOrNull() 
    }
    val model = remember(providerSetting, activeModelId) { 
        providerSetting?.models?.find { it.modelId == activeModelId } ?: providerSetting?.models?.firstOrNull() 
    }

    val sessionId = remember { UUID.randomUUID().toString() }
    val sseClient = remember { SseClient() }
    val remoteChatDataSource = remember { StreamingChatRemoteDataSource(sseClient) }
    val aiRepository = remember { AiRepositoryImpl(remoteChatDataSource) }
    val messagePersistenceGateway = remember { NoOpMessagePersistenceGateway }
    val sendMessageUseCase = remember(aiRepository, messagePersistenceGateway, scope) {
        SendMessageUseCase(aiRepository, messagePersistenceGateway, scope)
    }
    val pauseStreamingUseCase = remember(aiRepository) {
        PauseStreamingUseCase(aiRepository)
    }
    val rollbackMessageUseCase = remember(aiRepository) {
        RollbackMessageUseCase(aiRepository, messagePersistenceGateway)
    }

    // Initialize Business Logic
    val conversationLogic = remember(
        uiState,
        context,
        authorMe,
        timeNow,
        sendMessageUseCase,
        pauseStreamingUseCase,
        rollbackMessageUseCase,
        sessionId
    ) {
        ConversationLogic(
            uiState = uiState,
            context = context,
            authorMe = authorMe,
            timeNow = timeNow,
            sendMessageUseCase = sendMessageUseCase,
            pauseStreamingUseCase = pauseStreamingUseCase,
            rollbackMessageUseCase = rollbackMessageUseCase,
            sessionId = sessionId
        )
    }

    // 初始化用于语音转文本的音频录制器和 WebSocket
    val audioRecorder = remember { AudioRecorder(context) }
    
    // 跟踪挂起的部分文本长度，以便在实时转录期间正确替换它
    var lastPartialLength by remember { mutableIntStateOf(0) }
    
    // 处理 ASR 结果的转录监听器
    val transcriptionListener = remember(scope, uiState) {
        object : YoudaoWebSocket.TranscriptionListener {
            override fun onResult(text: String, isFinal: Boolean) {
                scope.launch(Dispatchers.Main) {
                    val currentText = uiState.textFieldValue.text
                    
                    // 删除以前的部分文本（如果有），以便使用新的部分或最终结果进行更新
                    val safeCurrentText = if (currentText.length >= lastPartialLength) {
                        currentText.dropLast(lastPartialLength)
                    } else {
                        currentText // 通常不应该发生
                    }
                    
                    val newText = safeCurrentText + text
                    
                    uiState.textFieldValue = uiState.textFieldValue.copy(
                        text = newText,
                        selection = TextRange(newText.length)
                    )
                    
                    // 更新 lastPartialLength：如果是最终结果，重置为 0，否则存储当前长度
                    lastPartialLength = if (isFinal) 0 else text.length
                }
            }

            override fun onError(t: Throwable) {
                android.util.Log.e("Conversation", "ASR Error", t)
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val youdaoWebSocket = remember { YoudaoWebSocket(transcriptionListener) }

    // 音频录制的权限启动器
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
             // Permission granted, trying to start recording again... 
             Toast.makeText(context, "Permission granted, press record again", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "需要录音权限才能使用语音功能", Toast.LENGTH_SHORT).show()
        }
    }

    // 在 dispose 时清理资源
    DisposableEffect(Unit) {
        onDispose {
            youdaoWebSocket.close()
            audioRecorder.stopRecording()
        }
    }

    Scaffold(
        topBar = {
            ChannelNameBar(
                channelName = uiState.channelName,
                channelMembers = uiState.channelMembers,
                onNavIconPressed = onNavIconPressed,
                scrollBehavior = scrollBehavior,
                onSettingsClicked = { showSettingsDialog = true }
            )
        },
        // 排除 ime 和导航栏内边距，以便由 UserInput composable 添加
        contentWindowInsets = ScaffoldDefaults
            .contentWindowInsets
            .exclude(WindowInsets.navigationBars)
            .exclude(WindowInsets.ime),
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { paddingValues ->
        Column(
            Modifier.fillMaxSize().padding(paddingValues)
                .background(color = background)
                .border(width = 2.dp, color = borderStroke)
                .dragAndDropTarget(shouldStartDragAndDrop = { event ->
                    event
                        .mimeTypes()
                        .contains(
                            ClipDescription.MIMETYPE_TEXT_PLAIN,
                        )
                }, target = dragAndDropCallback),
        ) {
            // 消息列表
            Messages(
                messages = uiState.messages,
                navigateToProfile = navigateToProfile,
                modifier = Modifier.weight(1f),
                scrollState = scrollState,
            )
            
            // 用户输入区域
            UserInput(
                selectedImageUri = uiState.selectedImageUri,
                onImageSelected = { uri -> uiState.selectedImageUri = uri },
                onMessageSent = { content ->
                    scope.launch {
                        conversationLogic.processMessage(
                            inputContent = content,
                            providerSetting = providerSetting,
                            model = model,
                            retrieveKnowledge = retrieveKnowledge
                        )
                    }
                },
                resetScroll = {
                    scope.launch {
                        scrollState.scrollToItem(0)
                    }
                },
                // 让此元素处理填充，以便将 elevation 显示在导航栏后面
                modifier = Modifier.navigationBarsPadding().imePadding(),
                onStartRecording = {
                    // 检查权限并开始录音
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        uiState.isRecording = true
                        lastPartialLength = 0 // 重置部分长度跟踪器
                        scope.launch(Dispatchers.IO) {
                            youdaoWebSocket.connect()
                            audioRecorder.startRecording(
                                onAudioData = { data, len ->
                                    youdaoWebSocket.sendAudio(data, len)
                                },
                                onError = { e ->
                                    android.util.Log.e("Conversation", "AudioRecorder Error", e)
                                    scope.launch(Dispatchers.Main) {
                                        uiState.isRecording = false
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStopRecording = {
                    // 停止录音并关闭 socket
                    if (uiState.isRecording) {
                        uiState.isRecording = false
                        audioRecorder.stopRecording()
                        youdaoWebSocket.close()
                    }
                },
                isRecording = uiState.isRecording,
                textFieldValue = uiState.textFieldValue,
                onTextChanged = { uiState.textFieldValue = it }
            )
        }
    }
}

@Preview
@Composable
fun ConversationPreview() {
    JetchatTheme {
        ConversationContent(
            uiState = exampleUiState,
            navigateToProfile = { },
        )
    }
}
