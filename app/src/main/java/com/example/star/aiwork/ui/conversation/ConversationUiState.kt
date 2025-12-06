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

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.text.input.TextFieldValue
import com.example.star.aiwork.R
import com.example.star.aiwork.domain.model.Agent
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.domain.usecase.GenerateChatNameUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 对话屏幕的 UI 状态容器。
 *
 * 管理对话界面的所有可变状态，包括消息列表、输入框状态、录音状态以及 AI 模型参数。
 * 每个会话持有自己的协程作用域，用于管理该会话的所有协程（如 processMessage、rollbackAndRegenerate）。
 *
 * @property channelName 频道名称。
 * @property channelMembers 频道成员数量。
 * @property initialMessages 初始消息列表。
 * @property coroutineScope 该会话的协程作用域，用于管理所有与该会话相关的协程。
 */
class ConversationUiState(
    channelName: String,
    val channelMembers: Int,
    initialMessages: List<Message>,
    val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    // 频道名称使用可变状态，以便根据当前会话动态更新
    var channelName: String by mutableStateOf(channelName)

    // 使用 SnapshotStateList 来存储消息，确保列表变更时能触发 Compose 重组
    private val _messages: MutableList<Message> = initialMessages.toMutableStateList()
    val messages: List<Message> = _messages

    // 分页加载状态
    var isLoadingMore: Boolean by mutableStateOf(false)
    var allMessagesLoaded: Boolean by mutableStateOf(false)

    // AI 模型参数状态
    var temperature: Float by mutableFloatStateOf(0.7f)
    var maxTokens: Int by mutableIntStateOf(2000)
    var streamResponse: Boolean by mutableStateOf(true)

    // Auto-Agent Loop (轻量自动化循环) 状态
    var isAutoLoopEnabled: Boolean by mutableStateOf(false)
    var maxLoopCount: Int by mutableIntStateOf(3)

    // Auto-Loop Planner 模型选择 (如果为 null，则使用当前对话模型)
    var autoLoopProviderId: String? by mutableStateOf(null)
    var autoLoopModelId: String? by mutableStateOf(null)

    // 兜底机制配置
    var isFallbackEnabled: Boolean by mutableStateOf(true)
    var fallbackProviderId: String? by mutableStateOf(null)
    var fallbackModelId: String? by mutableStateOf(null)

    // 当前激活的 Agent
    var activeAgent: Agent? by mutableStateOf(null)

    // ====== 语音输入模式状态 ======
    var isVoiceMode: Boolean by mutableStateOf(false) // 是否处于语音输入模式（替换文本输入框为"按住说话"按钮）

    // 录音状态
    var isRecording: Boolean by mutableStateOf(false)
    var isTranscribing: Boolean by mutableStateOf(false) // 是否正在转换文字
    var pendingTranscription: String by mutableStateOf("") // 暂存转写文本（录音时实时显示）

    // 语音面板状态
    var voiceInputStage: VoiceInputStage by mutableStateOf(VoiceInputStage.IDLE) // 语音输入阶段
    var isCancelGesture: Boolean by mutableStateOf(false) // 是否处于取消手势状态（上滑）
    var currentVolume: Float by mutableFloatStateOf(0f) // 当前音量（用于波形动画）

    // AI 生成状态
    var isGenerating: Boolean by mutableStateOf(false) // 是否正在生成回答

    // 流式生成任务状态
    var activeTaskId: String? by mutableStateOf(null) // 当前活跃的流式生成任务ID

    // 输入框文本状态
    var textFieldValue: TextFieldValue by mutableStateOf(TextFieldValue())

    // 暂存选中的图片 URI
    var selectedImageUri: Uri? by mutableStateOf(null)

    /**
     * 用于生成预览卡片标题的UseCase
     */
    var generateChatNameUseCase: GenerateChatNameUseCase? = null

    /**
     * 当前活跃的Provider设置
     */
    var activeProviderSetting: ProviderSetting? = null

    /**
     * 当前活跃的Model
     */
    var activeModel: Model? = null

    /**
     * 添加一条新消息到列表顶部。
     */
    fun addMessage(msg: Message) {
        _messages.add(0, msg) // Add to the beginning of the list
    }

    /**
     * 添加历史消息到列表末尾
     */
    fun addOlderMessages(olderMessages: List<Message>) {
        _messages.addAll(olderMessages)
    }

    /**
     * 移除列表顶部的一条消息。
     * 用于在发送失败等场景下回滚 UI。
     */
    fun removeFirstMessage() {
        if (_messages.isNotEmpty()) {
            _messages.removeAt(0)
        }
    }

    /**
     * 清空所有消息。
     * 用于在会话切换时清理不属于当前会话的消息。
     */
    fun clearMessages() {
        _messages.clear()
        isLoadingMore = false
        allMessagesLoaded = false
    }

    /**
     * 将内容追加到最新一条消息中。
     * 通常用于流式显示 AI 的回复。
     */
    fun appendToLastMessage(content: String) {
        if (_messages.isNotEmpty()) {
            val lastMsg = _messages[0]
            _messages[0] = lastMsg.copy(
                content = lastMsg.content + content,
                isLoading = false  // ✅ 有内容后，取消加载状态
            )
        }
    }

    /**
     * ✅ 新增：更新最后一条消息的加载状态
     */
    fun updateLastMessageLoadingState(isLoading: Boolean) {
        if (_messages.isNotEmpty()) {
            val lastMsg = _messages[0]
            _messages[0] = lastMsg.copy(isLoading = isLoading)
        }
    }

    /**
     * 移除最后一条助手消息（用于回滚功能）
     */
    fun removeLastAssistantMessage(authorMe: String) {
        val index = _messages.indexOfFirst {
            it.author != authorMe && it.author != "System"
        }
        if (index >= 0) {
            _messages.removeAt(index)
        }
    }

    /**
     * 替换最后一条消息的内容（用于非流式模式下取消时清空内容）
     */
    fun replaceLastMessageContent(newContent: String) {
        if (_messages.isNotEmpty()) {
            val lastMsg = _messages[0]
            _messages[0] = lastMsg.copy(
                content = newContent,
                isLoading = false
            )
        }
    }

    /**
     * 取消该会话的所有协程。
     * 当会话被移除或切换时，应该调用此方法清理协程资源。
     */
    fun cancelAllCoroutines() {
        coroutineScope.cancel()
    }
}

/**
 * 消息数据模型。
 *
 * 表示聊天中的单条消息。
 * 标记为 @Immutable 以优化 Compose 重组性能。
 *
 * @property author 消息作者名称。
 * @property content 消息文本内容。
 * @property timestamp 消息时间戳字符串。
 * @property image 可选的附件图片资源 ID。
 * @property imageUrl 可选的附件图片 URI 字符串（用户上传或网络图片）。
 * @property authorImage 作者头像资源 ID。
 */
@Immutable
data class Message(
    val author: String,
    val content: String,
    val timestamp: String,
    val image: Int? = null,
    val imageUrl: String? = null,
    val authorImage: Int = if (author == "me") R.drawable.ali else R.drawable.someone_else,
    val isLoading: Boolean = false  // ✅ 新增：标记是否正在加载
)
