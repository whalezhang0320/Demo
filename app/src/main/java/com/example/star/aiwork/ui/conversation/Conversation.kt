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
import android.util.Log
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.star.aiwork.R
import com.example.star.aiwork.data.exampleUiState
import com.example.star.aiwork.data.remote.StreamingChatRemoteDataSource
import com.example.star.aiwork.data.repository.AiRepositoryImpl
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.data.repository.MessagePersistenceGatewayImpl
import com.example.star.aiwork.data.local.datasource.MessageLocalDataSourceImpl
import com.example.star.aiwork.domain.model.SessionEntity
import com.example.star.aiwork.domain.usecase.ImageGenerationUseCase
import com.example.star.aiwork.domain.usecase.PauseStreamingUseCase
import com.example.star.aiwork.domain.usecase.RollbackMessageUseCase
import com.example.star.aiwork.domain.usecase.SendMessageUseCase
import com.example.star.aiwork.infra.network.SseClient
import com.example.star.aiwork.infra.network.defaultOkHttpClient
import com.example.star.aiwork.ui.theme.JetchatTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextRange
import java.util.UUID
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import com.example.star.aiwork.domain.usecase.GenerateChatNameUseCase
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

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
 * @param logic [ConversationLogic] 包含处理消息的业务逻辑。
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
 * @param onUpdateFallbackSettings 更新兜底模型设置（启用状态、Provider ID、Model ID）的回调。
 * @param retrieveKnowledge 检索知识库的回调函数。
 * @param currentSessionId 当前会话 ID，用于消息持久化
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationContent(
    uiState: ConversationUiState,
    logic: ConversationLogic,
    navigateToProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNavIconPressed: () -> Unit = { },
    providerSettings: List<ProviderSetting> = emptyList(),
    activeProviderId: String? = null,
    activeModelId: String? = null,
    temperature: Float = 0.7f,
    maxTokens: Int = 2000,
    streamResponse: Boolean = true,
    isFallbackEnabled: Boolean = true,
    fallbackProviderId: String? = null,
    fallbackModelId: String? = null,
    onUpdateSettings: (Float, Int, Boolean) -> Unit = { _, _, _ -> },
    onUpdateFallbackSettings: (Boolean, String?, String?) -> Unit = { _, _, _ -> },
    retrieveKnowledge: suspend (String) -> String = { "" },
    currentSessionId: String? = null,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    searchResults: List<SessionEntity>,
    onSessionSelected: (SessionEntity) -> Unit,
    generateChatNameUseCase: GenerateChatNameUseCase? = null,
    onLoadMoreMessages: () -> Unit
) {
    val authorMe = stringResource(R.string.author_me)
    val timeNow = stringResource(id = R.string.now)
    val context = LocalContext.current

    // ========== 新增初始化逻辑开始 ==========
    // 初始化 uiState 中的 UseCase 和 Provider/Model
    LaunchedEffect(generateChatNameUseCase, activeProviderId, activeModelId, providerSettings) {
        uiState.generateChatNameUseCase = generateChatNameUseCase

        // 查找当前活跃的 Provider 和 Model
        val provider = providerSettings.find { it.id == activeProviderId }
        val model = provider?.models?.find { it.id == activeModelId }
            ?: provider?.models?.firstOrNull()

        uiState.activeProviderSetting = provider
        uiState.activeModel = model

        // ===== 调试日志 =====
        Log.d("ConversationContent", "初始化预览卡片生成条件:")
        Log.d("ConversationContent", "- UseCase: ${generateChatNameUseCase != null}")
        Log.d("ConversationContent", "- Provider: ${provider?.name} (id=${provider?.id})")
        Log.d("ConversationContent", "- Model: ${model?.modelId}")
        Log.d("ConversationContent", "- activeProviderId: $activeProviderId")
        Log.d("ConversationContent", "- activeModelId: $activeModelId")
        Log.d("ConversationContent", "- providerSettings count: ${providerSettings.size}")
// ===== 调试日志结束 =====
    }
    // ========== 新增初始化逻辑结束 ==========

    val scrollState = rememberLazyListState()
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState)
    val scope = rememberCoroutineScope()

    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(temperature, maxTokens, streamResponse, isFallbackEnabled, fallbackProviderId, fallbackModelId) {
        uiState.temperature = temperature
        uiState.maxTokens = maxTokens
        uiState.streamResponse = streamResponse
        uiState.isFallbackEnabled = isFallbackEnabled
        uiState.fallbackProviderId = fallbackProviderId
        uiState.fallbackModelId = fallbackModelId
    }

    var background by remember { mutableStateOf(Color.Transparent) }
    var borderStroke by remember { mutableStateOf(Color.Transparent) }

    if (showSettingsDialog) {
        ModelSettingsDialog(
            uiState = uiState,
            providerSettings = providerSettings,
            onDismissRequest = {
                onUpdateSettings(uiState.temperature, uiState.maxTokens, uiState.streamResponse)
                onUpdateFallbackSettings(uiState.isFallbackEnabled, uiState.fallbackProviderId, uiState.fallbackModelId)
                showSettingsDialog = false
            }
        )
    }

    val dragAndDropCallback = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val clipData = event.toAndroidDragEvent().clipData
                if (clipData.itemCount < 1) return false
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

    val providerSetting = remember(providerSettings, activeProviderId) {
        providerSettings.find { it.id == activeProviderId } ?: providerSettings.firstOrNull()
    }
    val model = remember(providerSetting, activeModelId) {
        providerSetting?.models?.find { it.modelId == activeModelId } ?: providerSetting?.models?.firstOrNull()
    }

    // ====== 语音识别初始化 ======
    val audioRecorder = remember { AudioRecorder(context) }
    var lastPartialLength by remember { mutableIntStateOf(0) }

    val transcriptionListener = remember(scope, uiState) {
        object : YoudaoWebSocket.TranscriptionListener {
            override fun onTranscriptionReceived(text: String, isFinal: Boolean) {
                scope.launch(Dispatchers.Main) {
                    // 更新 pendingTranscription
                    uiState.pendingTranscription = if (isFinal) {
                        uiState.pendingTranscription + text
                    } else {
                        // 部分结果：替换上一次的部分结果
                        val previousFinal = uiState.pendingTranscription.dropLast(lastPartialLength)
                        lastPartialLength = text.length
                        previousFinal + text
                    }
                }
            }

            override fun onError(error: String) {
                Log.e("VoiceInput", "ASR Error: $error")
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "识别错误: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val youdaoWebSocket = remember { YoudaoWebSocket() }
    SideEffect {
        youdaoWebSocket.listener = transcriptionListener
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(context, "Permission granted, press record again", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "需要录音权限才能使用语音功能", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            youdaoWebSocket.close()
            audioRecorder.stopRecording()
            audioRecorder.cleanup()
        }
    }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .map { it == uiState.messages.lastIndex }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                onLoadMoreMessages()
            }
    }

    Scaffold(
        topBar = {
            ChannelNameBar(
                channelName = uiState.channelName,
                channelMembers = uiState.channelMembers,
                onNavIconPressed = onNavIconPressed,
                scrollBehavior = scrollBehavior,
                onSettingsClicked = { showSettingsDialog = true },
                searchQuery = searchQuery,
                onSearchQueryChanged = onSearchQueryChanged,
                searchResults = searchResults,
                onSessionSelected = onSessionSelected
            )
        },
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
                        .contains(ClipDescription.MIMETYPE_TEXT_PLAIN)
                }, target = dragAndDropCallback),
        ) {
            Messages(
                messages = uiState.messages,
                navigateToProfile = navigateToProfile,
                modifier = Modifier.weight(1f),
                scrollState = scrollState,
                logic = logic,
                providerSetting = providerSetting,
                model = model,
                retrieveKnowledge = retrieveKnowledge,
                scope = scope,
                isGenerating = uiState.isGenerating,
                uiState = uiState  // ← 新增这一行
            )

            // ====== 修改后的 UserInput 调用 ======
            UserInput(
                selectedImageUri = uiState.selectedImageUri,
                onImageSelected = { uri -> uiState.selectedImageUri = uri },
                onMessageSent = { content ->
                    uiState.isGenerating = true
                    scope.launch {
                        logic.processMessage(
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
                modifier = Modifier.navigationBarsPadding().imePadding(),
                // ====== 新增的语音模式相关参数 ======
                isVoiceMode = uiState.isVoiceMode,
                onVoiceModeChanged = { uiState.isVoiceMode = it },
                voiceInputStage = uiState.voiceInputStage,
                onVoiceStageChanged = { uiState.voiceInputStage = it },
                pendingTranscription = uiState.pendingTranscription,
                onTranscriptionChanged = { uiState.pendingTranscription = it },
                currentVolume = uiState.currentVolume,
                onVolumeChanged = { uiState.currentVolume = it },
                onStartRecording = {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        uiState.isRecording = true
                        lastPartialLength = 0
                        uiState.pendingTranscription = "" // 清空之前的转写
                        scope.launch(Dispatchers.IO) {
                            youdaoWebSocket.connect()
                            audioRecorder.startRecording(
                                onAudioData = { data, size ->
                                    Log.d("VoiceInput", "📤 Sending $size bytes to Youdao WebSocket")
                                    youdaoWebSocket.sendAudio(data, size)
                                },
                                onError = { error ->
                                    Log.e("VoiceInput", "❌ Recording error: ${error.message}")
                                    scope.launch {
                                        Toast.makeText(context, "录音失败: ${error.message}", Toast.LENGTH_SHORT).show()
                                    }
                                    uiState.isRecording = false
                                },
                                onVolumeChanged = { volume ->
                                    uiState.currentVolume = volume
                                }
                            )
                        }
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStopRecording = {
                    if (uiState.isRecording) {
                        uiState.isRecording = false
                        audioRecorder.stopRecording()
                        youdaoWebSocket.close()
                    }
                },
                isGenerating = uiState.isGenerating,
                onPauseStream = {
                    scope.launch {
                        logic.cancelStreaming()
                    }
                },
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
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        val okHttpClient = remember { defaultOkHttpClient() }
        val sseClient = SseClient(okHttpClient)
        val remoteDataSource = StreamingChatRemoteDataSource(sseClient)
        val aiRepository = AiRepositoryImpl(remoteDataSource, okHttpClient)

        val messageLocalDataSource = MessageLocalDataSourceImpl(context)
        val sessionLocalDataSource = com.example.star.aiwork.data.local.datasource.SessionLocalDataSourceImpl(context)
        val persistenceGateway = MessagePersistenceGatewayImpl(messageLocalDataSource, sessionLocalDataSource)

        val sendMessageUseCase = SendMessageUseCase(aiRepository, persistenceGateway, scope)
        val pauseStreamingUseCase = PauseStreamingUseCase(aiRepository)
        val rollbackMessageUseCase = RollbackMessageUseCase(aiRepository, persistenceGateway)
        val imageGenerationUseCase = ImageGenerationUseCase(aiRepository)

        val previewLogic = ConversationLogic(
            uiState = exampleUiState,
            context = context,
            authorMe = "me",
            timeNow = "now",
            sendMessageUseCase = sendMessageUseCase,
            pauseStreamingUseCase = pauseStreamingUseCase,
            rollbackMessageUseCase = rollbackMessageUseCase,
            imageGenerationUseCase = imageGenerationUseCase,
            sessionId = "123",
            getProviderSettings = { emptyList() },
            persistenceGateway = persistenceGateway,
            onRenameSession = { _, _ -> },
            onPersistNewChatSession = { },
            isNewChat = { false }
        )

        ConversationContent(
            uiState = exampleUiState,
            logic = previewLogic,
            navigateToProfile = { },
            searchQuery = "",
            onSearchQueryChanged = {},
            searchResults = emptyList(),
            onSessionSelected = {},
            generateChatNameUseCase = null,  // ← 新增参数
            onLoadMoreMessages = {}
        )
    }
}