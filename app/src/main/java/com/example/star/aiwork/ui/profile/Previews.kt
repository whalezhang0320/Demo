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

package com.example.star.aiwork.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.star.aiwork.data.freeProviders
import com.example.star.aiwork.ui.theme.JetchatTheme

/**
 * 模型设置屏幕的各种预览配置。
 *
 * 这些预览展示了在不同宽度、不同主题（亮色/暗色）下的 UI 表现。
 */

@Preview(widthDp = 340, name = "340 width")
@Composable
fun ProfilePreview340() {
    JetchatTheme {
        ProfileScreen(
            providerSettings = freeProviders,
            activeProviderId = null,
            activeModelId = null,
            onUpdateSettings = {},
            onSelectModel = { _, _ -> }
        )
    }
}

@Preview(widthDp = 480, name = "480 width")
@Composable
fun ProfilePreview480() {
    JetchatTheme {
        ProfileScreen(
            providerSettings = freeProviders,
            activeProviderId = "silicon_cloud",
            activeModelId = "Qwen/Qwen2.5-7B-Instruct",
            onUpdateSettings = {},
            onSelectModel = { _, _ -> }
        )
    }
}

@Preview(widthDp = 340, name = "340 width - Dark")
@Composable
fun ProfilePreview340Dark() {
    JetchatTheme(isDarkTheme = true) {
        ProfileScreen(
            providerSettings = freeProviders,
            activeProviderId = null,
            activeModelId = null,
            onUpdateSettings = {},
            onSelectModel = { _, _ -> }
        )
    }
}

@Preview(widthDp = 480, name = "480 width - Dark")
@Composable
fun ProfilePreview480Dark() {
    JetchatTheme(isDarkTheme = true) {
        ProfileScreen(
            providerSettings = freeProviders,
            activeProviderId = "deepseek",
            activeModelId = "deepseek-chat",
            onUpdateSettings = {},
            onSelectModel = { _, _ -> }
        )
    }
}
