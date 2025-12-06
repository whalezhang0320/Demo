package com.example.star.aiwork.ui.conversation

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.star.aiwork.R
import com.example.star.aiwork.data.remote.StreamingChatRemoteDataSource
import com.example.star.aiwork.data.repository.AiRepositoryImpl
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ModelType
import com.example.star.aiwork.infra.network.SseClient
import com.example.star.aiwork.infra.network.defaultOkHttpClient
import com.example.star.aiwork.ui.MainViewModel
import com.example.star.aiwork.ui.theme.JetchatTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

class RealtimeConversationFragment : Fragment() {

    private val activityViewModel: MainViewModel by activityViewModels()

    private var youdaoWebSocket: YoudaoWebSocket? = null
    private var audioRecorder: AudioRecorder? = null
    private var mediaPlayer: MediaPlayer? = null

    private val _transcription = mutableStateOf("")
    private val _aiResponse = mutableStateOf("")
    private val _isListening = mutableStateOf(false)
    private val _isProcessing = mutableStateOf(false)
    private val _isSpeaking = mutableStateOf(false)
    private val _currentVoice = mutableStateOf("youxiaozhi") // 默认使用男声
    
    // 存储对话历史，用于上下文
    private val conversationHistory = mutableListOf<Pair<String, String>>()
    
    // 用于 TTS 播放队列
    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()
    private var isPlayingQueue = false
    
    // 当前生成的文本缓冲区，用于检测句子边界
    private val ttsTextBuffer = StringBuilder()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startListening()
            } else {
                Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                JetchatTheme {
                    RealtimeChatScreen(
                        transcription = _transcription.value,
                        aiResponse = _aiResponse.value,
                        isListening = _isListening.value,
                        isProcessing = _isProcessing.value,
                        isSpeaking = _isSpeaking.value,
                        currentVoice = _currentVoice.value,
                        availableVoices = youdaoWebSocket?.availableVoices ?: emptyMap(),
                        onToggleListening = { toggleListening() },
                        onVoiceChanged = { newVoice ->
                            _currentVoice.value = newVoice
                            youdaoWebSocket?.currentVoiceName = newVoice
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        audioRecorder = AudioRecorder(requireContext())
        initYoudao()
    }

    private fun initYoudao() {
        youdaoWebSocket = YoudaoWebSocket().apply {
            // 初始化时设置默认发音人
            currentVoiceName = _currentVoice.value

            listener = object : YoudaoWebSocket.TranscriptionListener {
                override fun onTranscriptionReceived(text: String, isFinal: Boolean) {
                    lifecycleScope.launch {
                        _transcription.value = text
                        if (isFinal) {
                            Log.d("RealtimeChat", "Final transcription: $text")
                            stopListening() // 停止录音，开始处理
                            processUserMessage(text)
                        }
                    }
                }

                override fun onError(error: String) {
                    lifecycleScope.launch {
                        // 忽略一些常见的无关紧要的错误，或者只在非录音停止时提示
                        if (_isListening.value) {
                             Toast.makeText(context, "ASR Error: $error", Toast.LENGTH_SHORT).show()
                             _isListening.value = false
                             stopListening()
                        }
                    }
                }
            }

            ttsListener = object : YoudaoWebSocket.TtsListener {
                override fun onTtsSuccess(audioData: ByteArray) {
                    lifecycleScope.launch {
                        Log.d("RealtimeChat", "TTS audio received, size: ${audioData.size}")
                        audioQueue.offer(audioData)
                        playNextInQueue()
                    }
                }

                override fun onTtsError(error: String) {
                    lifecycleScope.launch {
                        Log.e("RealtimeChat", "TTS Error: $error")
                    }
                }
            }
        }
    }

    private fun toggleListening() {
        if (_isListening.value) {
            stopListening()
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startListening()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startListening() {
        _isListening.value = true
        _isSpeaking.value = false // 如果正在说话，打断它
        stopAudioPlayback()
        audioQueue.clear()
        ttsTextBuffer.clear()
        
        _transcription.value = ""
        _aiResponse.value = ""

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                youdaoWebSocket?.connect()
                // 给 WebSocket 一点时间连接
                kotlinx.coroutines.delay(500) 
                
                withContext(Dispatchers.Main) {
                    audioRecorder?.startRecording(
                        onAudioData = { data, size ->
                            youdaoWebSocket?.sendAudio(data, size)
                        },
                        onError = { e ->
                            Log.e("RealtimeChat", "Audio Recorder Error", e)
                            lifecycleScope.launch {
                                _isListening.value = false
                                Toast.makeText(context, "Recorder Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("RealtimeChat", "Start listening failed", e)
                withContext(Dispatchers.Main) {
                    _isListening.value = false
                }
            }
        }
    }

    private fun stopListening() {
        _isListening.value = false
        audioRecorder?.stopRecording()
        youdaoWebSocket?.close()
    }

    private fun processUserMessage(text: String) {
        _isProcessing.value = true
        _aiResponse.value = "" // 清空，准备显示思考动画或流式文本

        val okHttpClient = defaultOkHttpClient()
        val sseClient = SseClient(okHttpClient)
        val remoteDataSource = StreamingChatRemoteDataSource(sseClient)
        val repository = AiRepositoryImpl(remoteDataSource, okHttpClient)

        val providerSettings = activityViewModel.providerSettings.value
        val activeProviderId = activityViewModel.activeProviderId.value
        val activeModelId = activityViewModel.activeModelId.value
        
        val provider = providerSettings.find { it.id == activeProviderId }
        
        if (provider == null || activeModelId.isNullOrEmpty()) {
            _aiResponse.value = "请先在侧边栏选择 AI 模型"
            _isProcessing.value = false
            speak("请先在侧边栏选择 AI 模型")
            return
        }

        // 构建简单的上下文
        val history = conversationHistory.flatMap { (userMsg, assistantMsg) ->
            listOf(
                ChatDataItem(MessageRole.USER.value, userMsg),
                ChatDataItem(MessageRole.ASSISTANT.value, assistantMsg)
            )
        }.toMutableList()
        history.add(ChatDataItem(MessageRole.USER.value, text))

        lifecycleScope.launch {
            val responseBuilder = StringBuilder()
            
            try {
                val model = Model(
                    modelId = activeModelId,
                    displayName = activeModelId,
                    type = ModelType.CHAT
                )
                
                val params = TextGenerationParams(
                    model = model,
                    temperature = 0.7f,
                    maxTokens = 1000
                )
                
                repository.streamChat(
                    history = history,
                    providerSetting = provider,
                    params = params,
                    taskId = UUID.randomUUID().toString()
                ).collect { chunk ->
                    responseBuilder.append(chunk)
                    _aiResponse.value = responseBuilder.toString()
                    
                    // 处理流式 TTS
                    processStreamForTts(chunk)
                }
                
                // 流结束，处理剩余的文本
                val remainingText = ttsTextBuffer.toString().trim()
                if (remainingText.isNotEmpty()) {
                    speak(remainingText)
                    ttsTextBuffer.clear()
                }

                val fullResponse = responseBuilder.toString()
                _aiResponse.value = fullResponse
                _isProcessing.value = false

                // 保存到简易历史 (仅保留最近几轮，避免过长)
                conversationHistory.add(text to fullResponse)
                if (conversationHistory.size > 5) {
                    conversationHistory.removeAt(0)
                }

            } catch (e: Exception) {
                Log.e("RealtimeChat", "AI Request failed", e)
                _aiResponse.value = "Error: ${e.message}"
                _isProcessing.value = false
            }
        }
    }
    
    /**
     * 处理流式文本，检测句子边界并触发 TTS
     */
    private fun processStreamForTts(chunk: String) {
        ttsTextBuffer.append(chunk)
        
        // 简单的标点符号分割逻辑
        val bufferContent = ttsTextBuffer.toString()
        // 增加一些分割符
        val sentenceEndings = listOf("。", "！", "？", "\n", ".", "!", "?", "；", ";", "，", ",")
        
        var lastIndex = -1
        for (ending in sentenceEndings) {
            val index = bufferContent.lastIndexOf(ending)
            if (index > lastIndex) {
                // 如果是逗号，句子太短也不切分，防止说话太碎
                if ((ending == "，" || ending == ",") && index < 10) {
                    continue
                }
                lastIndex = index
            }
        }
        
        if (lastIndex != -1) {
            // 提取完整句子
            val sentence = bufferContent.substring(0, lastIndex + 1).trim()
            if (sentence.isNotEmpty()) {
                speak(sentence)
            }
            
            // 移除已处理部分
            ttsTextBuffer.delete(0, lastIndex + 1)
        }
    }
    
    private fun speak(text: String) {
        _isSpeaking.value = true
        Log.d("RealtimeChat", "Speaking: $text")
        youdaoWebSocket?.synthesize(text)
    }

    /**
     * 播放队列中的下一个音频
     */
    private fun playNextInQueue() {
        if (isPlayingQueue) return
        
        val audioData = audioQueue.poll()
        if (audioData != null) {
            isPlayingQueue = true
            playAudio(audioData)
        } else {
            // 队列为空，如果AI处理已完成且没有剩余文本，则停止说话状态
            if (!_isProcessing.value) {
                _isSpeaking.value = false
            }
        }
    }

    private fun playAudio(audioData: ByteArray) {
        try {
            val tempFile = File.createTempFile("tts_audio", ".mp3", requireContext().cacheDir)
            val fos = FileOutputStream(tempFile)
            fos.write(audioData)
            fos.close()

            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    isPlayingQueue = false
                    it.release()
                    mediaPlayer = null
                    playNextInQueue()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("RealtimeChat", "MediaPlayer error: $what, $extra")
                    isPlayingQueue = false
                    playNextInQueue()
                    true
                }
            }
        } catch (e: Exception) {
            Log.e("RealtimeChat", "Audio playback failed", e)
            isPlayingQueue = false
            playNextInQueue()
        }
    }
    
    private fun stopAudioPlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
        audioQueue.clear()
        isPlayingQueue = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopListening()
        stopAudioPlayback()
        audioRecorder?.cleanup()
    }
}

/**
 * 模拟 Live2D 效果的 Avatar 组件
 */
@Composable
fun AnimatedAvatar(
    isListening: Boolean,
    isProcessing: Boolean,
    isSpeaking: Boolean,
    avatarResId: Int,
    avatarUri: Uri? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "AvatarAnimation")

    // 1. 呼吸效果 (基础缩放)
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "Breath"
    )

    // 2. 说话效果 (快速垂直震动)
    val speakOffsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isSpeaking) -15f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "Speak"
    )

    // 3. 聆听效果 (轻微旋转/倾斜)
    val listenRotation by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = if (isListening) 2f else -2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "Listen"
    )

    // 4. 思考效果 (透明度脉冲)
    val thinkingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = if (isProcessing && !isSpeaking) 1f else 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "Think"
    )

    // 组合动画状态
    val currentScale = if (isSpeaking) 1.1f else breathScale
    val currentRotation = if (isListening) listenRotation else 0f
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(280.dp) // 头像框整体大小
    ) {
        // 外层光晕/边框
        Box(
            modifier = Modifier
                .size(280.dp)
                .scale(if (isListening) 1.1f else 1f)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // 核心头像
        Surface(
            modifier = Modifier
                .size(200.dp)
                .graphicsLayer {
                    scaleX = currentScale
                    scaleY = currentScale
                    rotationZ = currentRotation
                    translationY = speakOffsetY
                    alpha = if (isProcessing && !isSpeaking) thinkingAlpha else 1f
                }
                .shadow(elevation = 12.dp, shape = CircleShape)
                .clickable(onClick = onClick),
            shape = CircleShape,
            border = BorderStroke(4.dp, MaterialTheme.colorScheme.primaryContainer),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            if (avatarUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(avatarUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "AI Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // 使用用户提供的图片资源
                Image(
                    painter = painterResource(id = avatarResId),
                    contentDescription = "AI Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        // 状态指示标 (右下角)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 40.dp, bottom = 20.dp)
                .size(24.dp)
                .background(
                    color = when {
                        isListening -> Color.Red
                        isSpeaking -> Color.Green
                        isProcessing -> Color.Yellow
                        else -> Color.Gray
                    },
                    shape = CircleShape
                )
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
        )
    }
}

@Composable
fun RealtimeChatScreen(
    transcription: String,
    aiResponse: String,
    isListening: Boolean,
    isProcessing: Boolean,
    isSpeaking: Boolean,
    currentVoice: String,
    availableVoices: Map<String, String>,
    onToggleListening: () -> Unit,
    onVoiceChanged: (String) -> Unit
) {
    var showVoiceDialog by remember { mutableStateOf(false) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            avatarUri = uri
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. 顶部工具栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showVoiceDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.SettingsVoice,
                        contentDescription = "选择音色",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. 核心区域：AI 头像和动画
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedAvatar(
                    isListening = isListening,
                    isProcessing = isProcessing,
                    isSpeaking = isSpeaking,
                    avatarResId = R.drawable.widget_icon, // 替换为您想要的图片资源 ID
                    avatarUri = avatarUri,
                    onClick = { 
                        // 点击头像打开图片选择器
                        imagePickerLauncher.launch("image/*") 
                    }
                )
            }

            // 3. 字幕/文本区域 (悬浮气泡风格)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .heightIn(min = 100.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (transcription.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = transcription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                if (aiResponse.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                        shadowElevation = 2.dp
                    ) {
                        Text(
                            text = aiResponse,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            textAlign = TextAlign.Center,
                            maxLines = 5
                        )
                    }
                } else if (isProcessing && !isSpeaking) {
                    Text(
                        text = "正在思考...",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // 4. 底部控制按钮
            FloatingActionButton(
                onClick = onToggleListening,
                containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isListening) "停止" else "开始",
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = if (isListening) "点击停止" else "点击对话",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        
        // 音色选择对话框
        if (showVoiceDialog) {
            AlertDialog(
                onDismissRequest = { showVoiceDialog = false },
                title = { Text("选择发音人") },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        availableVoices.forEach { (id, name) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clickable {
                                        onVoiceChanged(id)
                                        showVoiceDialog = false
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = currentVoice == id,
                                    onClick = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showVoiceDialog = false }) {
                        Text("关闭")
                    }
                }
            )
        }
    }
}
