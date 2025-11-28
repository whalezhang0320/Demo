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

/**
 * 对话屏幕的 UI 状态容器。
 *
 * 管理对话界面的所有可变状态，包括消息列表、输入框状态、录音状态以及 AI 模型参数。
 *
 * @property channelName 频道名称。
 * @property channelMembers 频道成员数量。
 * @property initialMessages 初始消息列表。
 */
class ConversationUiState(
    val channelName: String, 
    val channelMembers: Int, 
    initialMessages: List<Message>
) {
    // 使用 SnapshotStateList 来存储消息，确保列表变更时能触发 Compose 重组
    private val _messages: MutableList<Message> = initialMessages.toMutableStateList()
    val messages: List<Message> = _messages

    // AI 模型参数状态
    var temperature: Float by mutableFloatStateOf(0.7f)
    var maxTokens: Int by mutableIntStateOf(2000)
    var streamResponse: Boolean by mutableStateOf(true)
    
    // Auto-Agent Loop (轻量自动化循环) 状态
    var isAutoLoopEnabled: Boolean by mutableStateOf(false)
    var maxLoopCount: Int by mutableIntStateOf(3)
    
    // 当前激活的 Agent
    var activeAgent: Agent? by mutableStateOf(null)

    // 录音状态
    var isRecording: Boolean by mutableStateOf(false)
    // 输入框文本状态
    var textFieldValue: TextFieldValue by mutableStateOf(TextFieldValue())
    
    // 暂存选中的图片 URI
    var selectedImageUri: Uri? by mutableStateOf(null)

    /**
     * 添加一条新消息到列表顶部。
     */
    fun addMessage(msg: Message) {
        _messages.add(0, msg) // Add to the beginning of the list
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
     * 将内容追加到最新一条消息中。
     * 通常用于流式显示 AI 的回复。
     */
    fun appendToLastMessage(content: String) {
        if (_messages.isNotEmpty()) {
            val lastMsg = _messages[0]
            _messages[0] = lastMsg.copy(content = lastMsg.content + content)
        }
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
)
