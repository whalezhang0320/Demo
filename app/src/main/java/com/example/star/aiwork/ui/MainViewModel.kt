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

package com.example.star.aiwork.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.room.Room
import com.example.star.aiwork.data.AgentRepository
import com.example.star.aiwork.data.UserPreferencesRepository
import com.example.star.aiwork.data.database.AppDatabase
import com.example.star.aiwork.data.database.LocalRAGService
import com.example.star.aiwork.data.local.datasource.SessionLocalDataSourceImpl
import com.example.star.aiwork.domain.model.Agent
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.domain.usecase.session.DeleteAllSessionsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 主 ViewModel，负责在屏幕之间共享数据和通信。
 *
 * 管理全局应用状态，包括：
 * - 侧边栏 (Drawer) 的开闭状态。
 * - 用户配置的 AI 提供商设置 [ProviderSetting]。
 * - 全局 AI 模型参数 (Temperature, Max Tokens, Stream Response)。
 * - 角色市场/Prompt预设
 * - 当前激活的 Provider 和 Model
 *
 * @property userPreferencesRepository 用户偏好仓库，用于持久化数据的读写。
 * @property agentRepository 角色/Prompt仓库。
 */
class MainViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val agentRepository: AgentRepository,
    val ragService: LocalRAGService,
    private val deleteAllSessionsUseCase: DeleteAllSessionsUseCase
) : ViewModel() {

    // 侧边栏状态流
    private val _drawerShouldBeOpened = MutableStateFlow(false)
    val drawerShouldBeOpened = _drawerShouldBeOpened.asStateFlow()

    /**
     * AI 提供商设置列表流。
     * 从 Repository 加载，并在 ViewModel 作用域内共享。
     */
    val providerSettings: StateFlow<List<ProviderSetting>> = userPreferencesRepository.providerSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * AI 温度参数流。
     */
    val temperature: StateFlow<Float> = userPreferencesRepository.temperature
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.7f
        )

    /**
     * AI 最大 Token 数流。
     */
    val maxTokens: StateFlow<Int> = userPreferencesRepository.maxTokens
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 2000
        )

    /**
     * AI 流式响应开关流。
     */
    val streamResponse: StateFlow<Boolean> = userPreferencesRepository.streamResponse
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val agents: StateFlow<List<Agent>> = agentRepository.agents
        .stateIn(
             scope = viewModelScope,
             started = SharingStarted.WhileSubscribed(5000),
             initialValue = emptyList()
        )

    /**
     * 当前激活的 Provider ID。
     */
    val activeProviderId: StateFlow<String?> = userPreferencesRepository.activeProviderId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * 当前激活的 Model ID。
     */
    val activeModelId: StateFlow<String?> = userPreferencesRepository.activeModelId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * 兜底 Provider ID。
     */
    val fallbackProviderId: StateFlow<String?> = userPreferencesRepository.fallbackProviderId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * 兜底 Model ID。
     */
    val fallbackModelId: StateFlow<String?> = userPreferencesRepository.fallbackModelId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * 兜底机制启用状态。
     */
    val isFallbackEnabled: StateFlow<Boolean> = userPreferencesRepository.isFallbackEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )
        
    val knownKnowledgeBases: StateFlow<List<String>> = ragService.knownFiles
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        
    val isRagEnabled: StateFlow<Boolean> = userPreferencesRepository.isRagEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    init {
        viewModelScope.launch {
            agentRepository.loadAgents()
        }
    }

    /**
     * 请求打开侧边栏。
     */
    fun openDrawer() {
        _drawerShouldBeOpened.value = true
    }

    /**
     * 重置侧边栏打开请求状态。
     * 应在 UI 响应打开动作后调用。
     */
    fun resetOpenDrawerAction() {
        _drawerShouldBeOpened.value = false
    }
    
    /**
     * 更新并持久化提供商设置列表。
     */
    fun updateProviderSettings(newSettings: List<ProviderSetting>) {
        viewModelScope.launch {
            userPreferencesRepository.updateProviderSettings(newSettings)
        }
    }

    /**
     * 更新并持久化温度参数。
     */
    fun updateTemperature(newTemperature: Float) {
        viewModelScope.launch {
            userPreferencesRepository.updateTemperature(newTemperature)
        }
    }

    /**
     * 更新并持久化最大 Token 数。
     */
    fun updateMaxTokens(newMaxTokens: Int) {
        viewModelScope.launch {
            userPreferencesRepository.updateMaxTokens(newMaxTokens)
        }
    }

    /**
     * 更新并持久化流式响应开关。
     */
    fun updateStreamResponse(newStreamResponse: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateStreamResponse(newStreamResponse)
        }
    }
    
    /**
     * 更新当前激活的模型。
     */
    fun updateActiveModel(providerId: String, modelId: String) {
        viewModelScope.launch {
            userPreferencesRepository.updateActiveProviderId(providerId)
            userPreferencesRepository.updateActiveModelId(modelId)
            
            // 更新模型的使用时间
            val currentSettings = providerSettings.value
            val provider = currentSettings.find { it.id == providerId }
            if (provider != null) {
                val updatedModels = provider.models.map { model ->
                    if (model.modelId == modelId) {
                        model.copy(lastUsedTime = System.currentTimeMillis())
                    } else {
                        model
                    }
                }
                
                // 只有当模型列表真正发生变化（时间戳更新）时才保存
                // 这里肯定会变化，因为我们更新了 lastUsedTime
                val updatedProvider = when (provider) {
                    is ProviderSetting.OpenAI -> provider.copy(models = updatedModels)
                    is ProviderSetting.Ollama -> provider.copy(models = updatedModels)
                    is ProviderSetting.Google -> provider.copy(models = updatedModels)
                    is ProviderSetting.Claude -> provider.copy(models = updatedModels)
                    is ProviderSetting.Dify -> provider.copy(models = updatedModels)
                }
                
                val newSettings = currentSettings.map {
                    if (it.id == providerId) updatedProvider else it
                }
                userPreferencesRepository.updateProviderSettings(newSettings)
            }
        }
    }
    
    /**
     * 更新兜底模型设置。
     */
    fun updateFallbackModel(providerId: String?, modelId: String?) {
        viewModelScope.launch {
            userPreferencesRepository.updateFallbackProviderId(providerId)
            userPreferencesRepository.updateFallbackModelId(modelId)
        }
    }

    /**
     * 更新兜底机制启用状态。
     */
    fun updateFallbackEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateFallbackEnabled(isEnabled)
        }
    }
    
    fun addAgent(agent: Agent) {
        viewModelScope.launch {
            agentRepository.addAgent(agent)
        }
    }

    fun updateAgent(agent: Agent) {
        viewModelScope.launch {
            agentRepository.updateAgent(agent)
        }
    }

    fun removeAgent(agentId: String) {
        viewModelScope.launch {
            agentRepository.removeAgent(agentId)
        }
    }

    fun indexPdf(uri: Uri) {
        viewModelScope.launch {
             ragService.indexPdf(uri)
        }
    }
    
    fun deleteKnowledgeBase(filename: String) {
        viewModelScope.launch {
            ragService.deleteKnowledgeBase(filename)
        }
    }
    
    fun updateRagEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateRagEnabled(isEnabled)
        }
    }

    fun deleteAllSessions() {
        viewModelScope.launch {
            deleteAllSessionsUseCase()
        }
    }
    
    /**
     * 检索知识库中的相关上下文
     */
    suspend fun retrieveKnowledge(query: String): String {
        return if (isRagEnabled.value) {
            ragService.retrieve(query).context
        } else {
            ""
        }
    }

    /**
     * ViewModel 工厂，用于手动注入依赖项 (UserPreferencesRepository)。
     *
     * 在未使用 Hilt 等依赖注入框架时，通过此工厂从 CreationExtras 中获取 Application 实例，
     * 并创建 Repository 和 ViewModel。
     */
    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                // 从 extras 中获取 Application 对象
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                
                // 初始化数据库 (通常应该在 Application 中做单例，这里简化)
                val db = Room.databaseBuilder(
                    application,
                    AppDatabase::class.java, "aiwork-database"
                ).build()
                
                val ragService = LocalRAGService(application, db.knowledgeDao())

                val sessionLocalDataSource = SessionLocalDataSourceImpl(application)
                val deleteAllSessionsUseCase = DeleteAllSessionsUseCase(sessionLocalDataSource)

                return MainViewModel(
                    UserPreferencesRepository(application),
                    AgentRepository(application),
                    ragService,
                    deleteAllSessionsUseCase
                ) as T
            }
        }
    }
}
