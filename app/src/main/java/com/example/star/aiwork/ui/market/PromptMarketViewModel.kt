package com.example.star.aiwork.ui.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.star.aiwork.data.AgentRepository
import com.example.star.aiwork.data.crawler.ExternalPrompt
import com.example.star.aiwork.data.crawler.PromptCrawler
import com.example.star.aiwork.domain.model.Agent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class PromptMarketViewModel(
    private val agentRepository: AgentRepository,
    private val promptCrawler: PromptCrawler = PromptCrawler()
) : ViewModel() {

    private val _prompts = MutableStateFlow<List<ExternalPrompt>>(emptyList())
    val prompts = _prompts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // 用于剪贴板监听
    private val _pendingAgent = MutableStateFlow<Agent?>(null)
    val pendingAgent = _pendingAgent.asStateFlow()

    init {
        // 初始加载，可以加载空或者推荐
        fetchPrompts("")
    }

    fun fetchPrompts(query: String = "") {
        viewModelScope.launch {
            _isLoading.value = true
            // 使用 searchPrompts 方法，即使底层可能 fallback 到 JSON，
            // 但逻辑上我们遵循了“搜索”的语义
            val fetched = promptCrawler.searchPrompts(query)
            _prompts.value = fetched
            _isLoading.value = false
        }
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        // 实时触发搜索
        fetchPrompts(query)
    }

    fun saveAgent(name: String, description: String, prompt: String) {
        viewModelScope.launch {
            val newAgent = Agent(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { "Copied Agent" },
                // 用户希望不要下面的描述，所以默认置空
                description = description, 
                systemPrompt = prompt,
                isDefault = false
            )
            agentRepository.addAgent(newAgent)
        }
    }

    fun saveAgent(externalPrompt: ExternalPrompt) {
        // 保存来自爬虫的 Agent 时，也根据用户偏好可能需要调整描述，但这里保留原样比较好，
        // 或者也可以统一置空。考虑到用户主要是针对"复制保存"的场景提出"不要描述"，
        // 这里暂时保留爬虫的描述，或者如果有 description 就用，没有就空。
        saveAgent(externalPrompt.displayName, externalPrompt.displayDescription, externalPrompt.prompt)
    }

    // 剪贴板处理
    fun onClipboardChanged(text: String) {
        // 简单的启发式规则：如果文本比较长，假设它是一个 Prompt
        if (text.isNotBlank() && text.length > 20) {
             val potentialAgent = Agent(
                id = UUID.randomUUID().toString(),
                name = "New Agent",
                // 用户明确要求不要下面的描述，所以置空
                description = "",
                systemPrompt = text,
                isDefault = false
            )
            _pendingAgent.value = potentialAgent
        }
    }
    
    fun confirmSaveAgent(newName: String? = null) {
        val agent = _pendingAgent.value
        if (agent != null) {
            val finalAgent = if (newName != null) {
                agent.copy(name = newName)
            } else {
                agent
            }
            viewModelScope.launch {
                agentRepository.addAgent(finalAgent)
                _pendingAgent.value = null
            }
        }
    }
    
    fun dismissPendingAgent() {
        _pendingAgent.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                return PromptMarketViewModel(
                    AgentRepository(application)
                ) as T
            }
        }
    }
}
