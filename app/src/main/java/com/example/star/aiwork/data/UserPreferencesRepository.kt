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

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.star.aiwork.domain.model.ProviderSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// 创建 DataStore 实例，用于存储键值对数据
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * 用户偏好设置仓库。
 *
 * 负责管理应用的用户配置，包括：
 * - AI 提供商设置列表 (序列化为 JSON 存储)
 * - AI 模型参数 (Temperature, Max Tokens, Stream Response)
 * - 当前选中的 Provider 和 Model
 *
 * 使用 Jetpack DataStore 进行异步、持久化的数据存储。
 */
class UserPreferencesRepository(private val context: Context) {

    // JSON 序列化工具，配置为忽略未知键并包含默认值
    private val json = Json { 
        ignoreUnknownKeys = true
        classDiscriminator = "type" // 用于多态序列化
        encodeDefaults = true
    }

    // DataStore 键定义
    private object PreferencesKeys {
        val PROVIDER_SETTINGS = stringPreferencesKey("provider_settings")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val STREAM_RESPONSE = booleanPreferencesKey("stream_response")
        val ACTIVE_PROVIDER_ID = stringPreferencesKey("active_provider_id")
        val ACTIVE_MODEL_ID = stringPreferencesKey("active_model_id")
    }

    /**
     * 提供商设置流。
     * 从 DataStore 读取并反序列化为 [ProviderSetting] 列表。
     * 如果数据为空或解析失败，回退到 [freeProviders] 默认值。
     */
    val providerSettings: Flow<List<ProviderSetting>> = context.dataStore.data
        .map { preferences ->
            val settingsJson = preferences[PreferencesKeys.PROVIDER_SETTINGS]
            if (settingsJson.isNullOrEmpty()) {
                freeProviders
            } else {
                try {
                    json.decodeFromString(settingsJson)
                } catch (e: Exception) {
                    e.printStackTrace()
                    freeProviders
                }
            }
        }

    /**
     * 更新提供商设置。
     * 将新的设置列表序列化为 JSON 并保存。
     */
    suspend fun updateProviderSettings(newSettings: List<ProviderSetting>) {
        val settingsJson = json.encodeToString(newSettings)
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PROVIDER_SETTINGS] = settingsJson
        }
    }

    /**
     * 温度参数流 (0.0 - 2.0)。
     * 控制 AI 生成内容的随机性。
     */
    val temperature: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.TEMPERATURE] ?: 0.7f
        }

    /**
     * 更新温度参数。
     */
    suspend fun updateTemperature(newTemperature: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TEMPERATURE] = newTemperature
        }
    }

    /**
     * 最大 Token 数流。
     * 限制 AI 生成内容的长度。
     */
    val maxTokens: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.MAX_TOKENS] ?: 2000
        }

    /**
     * 更新最大 Token 数。
     */
    suspend fun updateMaxTokens(newMaxTokens: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MAX_TOKENS] = newMaxTokens
        }
    }

    /**
     * 流式响应开关流。
     * 决定是否以打字机效果逐字显示 AI 回复。
     */
    val streamResponse: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.STREAM_RESPONSE] ?: true
        }

    /**
     * 更新流式响应开关。
     */
    suspend fun updateStreamResponse(newStreamResponse: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.STREAM_RESPONSE] = newStreamResponse
        }
    }

    /**
     * 当前选中的 Provider ID 流。
     */
    val activeProviderId: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.ACTIVE_PROVIDER_ID]
        }

    /**
     * 更新当前选中的 Provider ID。
     */
    suspend fun updateActiveProviderId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACTIVE_PROVIDER_ID] = id
        }
    }

    /**
     * 当前选中的 Model ID 流。
     */
    val activeModelId: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.ACTIVE_MODEL_ID]
        }

    /**
     * 更新当前选中的 Model ID。
     */
    suspend fun updateActiveModelId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACTIVE_MODEL_ID] = id
        }
    }
}
