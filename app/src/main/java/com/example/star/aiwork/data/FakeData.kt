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

package com.example.star.aiwork.data

import com.example.star.aiwork.R
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.ui.conversation.ConversationUiState
import com.example.star.aiwork.ui.conversation.Message
import com.example.star.aiwork.data.EMOJIS.EMOJI_CLOUDS
import com.example.star.aiwork.data.EMOJIS.EMOJI_FLAMINGO
import com.example.star.aiwork.data.EMOJIS.EMOJI_MELTING
import com.example.star.aiwork.data.EMOJIS.EMOJI_PINK_HEART
import com.example.star.aiwork.data.EMOJIS.EMOJI_POINTS
import com.example.star.aiwork.ui.profile.ProfileScreenState

/**
 * 初始消息列表。
 * 包含用于演示的假对话数据。
 */
val initialMessages = emptyList<Message>()

/**
 * 未读消息列表（用于演示）。
 */
val unreadMessages = initialMessages.filter { it.author != "me" }

/**
 * 示例 UI 状态。
 */
val exampleUiState = ConversationUiState(
    initialMessages = initialMessages,
    channelName = "#composers",
    channelMembers = 42,
)

/**
 * 同事个人资料示例。
 */
val colleagueProfile = ProfileScreenState(
    userId = "12345",
    photo = R.drawable.someone_else,
    name = "Taylor Brooks",
    status = "Away",
    displayName = "taylor",
    position = "Senior Android Dev at Openlane",
    twitter = "twitter.com/taylorbrookscodes",
    timeZone = "12:25 AM local time (Eastern Daylight Time)",
    commonChannels = "2",
)

/**
 * "我" 的个人资料示例。
 */
val meProfile = ProfileScreenState(
    userId = "me",
    photo = R.drawable.ali,
    name = "Ali Conors",
    status = "Online",
    displayName = "aliconors",
    position = "Senior Android Dev at Yearin\nGoogle Developer Expert",
    twitter = "twitter.com/aliconors",
    timeZone = "In your timezone",
    commonChannels = null,
)

/**
 * 表情符号常量对象。
 * 包含各种 Android 版本和 Emoji 版本中引入的特殊字符。
 */
object EMOJIS {
    // EMOJI 15
    const val EMOJI_PINK_HEART = "\uD83E\uDE77"

    // EMOJI 14 🫠
    const val EMOJI_MELTING = "\uD83E\uDEE0"

    // ANDROID 13.1 😶‍🌫️
    const val EMOJI_CLOUDS = "\uD83D\uDE36\u200D\uD83C\uDF2B️"

    // ANDROID 12.0 🦩
    const val EMOJI_FLAMINGO = "\uD83E\uDDA9"

    // ANDROID 12.0  👉
    const val EMOJI_POINTS = " \uD83D\uDC49"
}

/**
 * 免费提供商配置列表。
 *
 * 包含默认配置的 AI 服务提供商，如 SiliconFlow 和 DeepSeek。
 * 这些配置用于演示目的，并在用户首次启动应用时作为默认设置加载。
 */
val freeProviders = listOf(
    ProviderSetting.OpenAI(
        id = "silicon_cloud",
        name = "SiliconFlow",
        baseUrl = "https://api.siliconflow.cn/v1",
        // 请在这里填入您的 SiliconFlow API Key
        apiKey = "sk-sjsubcwdyqrqwzuvaepkgciiwxupgjjulpwuynwrpjkpohgx",
        models = listOf(
            Model(
                modelId = "Qwen/Qwen3-8B",
                displayName = "Qwen 3 8B"
            ),
            Model(
                modelId = "THUDM/GLM-4.1V-9B-Thinking",
                displayName = "GLM-4.1V 9B"
            ),
             // 1. 添加小型模型 (假设 SiliconFlow 有 1.5B 或类似的小模型，这里以 Qwen 1.5B 为例作为示意)
            Model(
                modelId = "Qwen/Qwen2.5-7B-Instruct", // 请确认实际的模型 ID
                displayName = "Qwen 2.5 7B"
            ),
        )
    )
)
