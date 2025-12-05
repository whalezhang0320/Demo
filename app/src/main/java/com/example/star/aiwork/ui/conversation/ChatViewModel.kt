package com.example.star.aiwork.ui.conversation

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.star.aiwork.data.local.datasource.DraftLocalDataSourceImpl
import com.example.star.aiwork.data.local.datasource.MessageLocalDataSourceImpl
import com.example.star.aiwork.data.local.datasource.SessionLocalDataSourceImpl
import com.example.star.aiwork.domain.model.MessageEntity
import com.example.star.aiwork.domain.model.MessageMetadata
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.MessageType
import com.example.star.aiwork.domain.model.SessionEntity
import com.example.star.aiwork.domain.model.SessionMetadata
import com.example.star.aiwork.domain.usecase.draft.GetDraftUseCase
import com.example.star.aiwork.domain.usecase.draft.UpdateDraftUseCase
import com.example.star.aiwork.domain.usecase.message.RollbackMessageUseCase
import com.example.star.aiwork.domain.usecase.message.SendMessageUseCase
import com.example.star.aiwork.domain.usecase.message.ObserveMessagesUseCase
import com.example.star.aiwork.domain.usecase.session.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.util.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce

class ChatViewModel(
    private val getSessionListUseCase: GetSessionListUseCase,
    private val createSessionUseCase: CreateSessionUseCase,
    private val renameSessionUseCase: RenameSessionUseCase,
    private val deleteSessionUseCase: DeleteSessionUseCase,
    private val pinSessionUseCase: PinSessionUseCase,
    private val archiveSessionUseCase: ArchiveSessionUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val rollbackMessageUseCase: RollbackMessageUseCase,
    private val observeMessagesUseCase: ObserveMessagesUseCase,
    private val getDraftUseCase: GetDraftUseCase,
    private val updateDraftUseCase: UpdateDraftUseCase,
    private val searchSessionsUseCase: SearchSessionsUseCase,
    private val getTopSessionsUseCase: GetTopSessionsUseCase
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<SessionEntity>>(emptyList())
    val sessions: StateFlow<List<SessionEntity>> = _sessions.asStateFlow()

    private val _currentSession = MutableStateFlow<SessionEntity?>(null)
    val currentSession: StateFlow<SessionEntity?> = _currentSession.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // 独立的搜索结果列表，不会影响drawer中的sessions列表
    private val _searchResults = MutableStateFlow<List<SessionEntity>>(emptyList())
    val searchResults: StateFlow<List<SessionEntity>> = _searchResults.asStateFlow()
    
    // 跟踪临时创建的会话（isNewChat标记）
    private val _newChatSessions = MutableStateFlow<Set<String>>(emptySet())
    val newChatSessions: StateFlow<Set<String>> = _newChatSessions.asStateFlow()

    // 使用 flatMapLatest 自动根据 currentSession 切换消息流
    val messages: StateFlow<List<MessageEntity>> = _currentSession
        .flatMapLatest { session ->
            if (session != null) {
                observeMessagesUseCase(session.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _draft = MutableStateFlow<String?>(null)
    val draft: StateFlow<String?> = _draft.asStateFlow()

    // 使用 LRU Cache 管理每个会话的 ConversationUiState，最多缓存 5 个
    private val uiStateCache = ConversationUiStateCache(maxSize = 5)
    
    /**
     * 获取或创建指定会话的 ConversationUiState
     * 使用 LRU Cache 管理，当缓存满时会自动移除最久未使用的状态
     * 每个会话的协程作用域是 viewModelScope 的子 scope，确保在 ViewModel 销毁时自动取消
     */
    fun getOrCreateSessionUiState(sessionId: String, sessionName: String): ConversationUiState {
        return uiStateCache.getOrCreate(sessionId, sessionName) { id, name ->
            // 为每个会话创建独立的协程作用域，作为 viewModelScope 的子 scope
            // 这样当 ViewModel 被销毁时，所有会话的协程都会被自动取消
            val sessionScope = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob())
            ConversationUiState(
                channelName = name.ifBlank { "新对话" },
                channelMembers = 1,
                initialMessages = emptyList(),
                coroutineScope = sessionScope
            )
        }
    }
    
    /**
     * 获取指定会话的 ConversationUiState（如果不存在则返回 null）
     * 访问时会自动更新 LRU 顺序
     */
    fun getSessionUiState(sessionId: String): ConversationUiState? {
        return uiStateCache.get(sessionId)
    }

    fun clearAllUiStates() {
        uiStateCache.clear()
    }

    /**
     * 预热 LRU Cache：从数据库获取前 5 条会话历史数据并预加载到缓存中
     */
    fun warmupCache() {
        viewModelScope.launch {
            try {
                val topSessions = getTopSessionsUseCase(5)
                topSessions.forEach { session ->
                    // 为每个会话创建 UI 状态并添加到缓存中
                    getOrCreateSessionUiState(session.id, session.name)
                }
                Log.d("ChatViewModel", "LRU Cache warmed up with ${topSessions.size} sessions")
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to warmup cache", e)
            }
        }
    }

    init {
        loadSessions()
        // 启动时如果没有当前会话，创建一个临时会话
        // 这样用户可以直接在空对话页面发送消息，消息不会丢失
        if (_currentSession.value == null) {
            createTemporarySession("新聊天")
        }
        
        // 使用 debounce 和 flatMapLatest 操作符处理搜索查询，确保旧的搜索会被自动取消
        viewModelScope.launch {
            _searchQuery
                .debounce(300L)
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        flowOf(emptyList())
                    } else {
                        searchSessionsUseCase(query)
                    }
                }
                .collect { results ->
                    _searchResults.value = results
                }
        }
    }

    fun loadSessions() {
        viewModelScope.launch {
            getSessionListUseCase().collect { list ->
                _sessions.value = list
                // 不再自动选择第一个会话，始终显示空对话页面
                // if (_currentSession.value == null) {
                //     _currentSession.value = list.firstOrNull()
                // }
            }
        }
    }
    
    /**
     * 手动刷新会话列表（用于在会话更新后刷新 drawer 中的列表）
     */
    suspend fun refreshSessions() {
        getSessionListUseCase().firstOrNull()?.let { list ->
            _sessions.value = list
        }
    }
    
    /**
     * 获取最新的会话列表（一次性获取，用于 drawer 打开时刷新）
     */
    suspend fun getSessionsList(): List<SessionEntity> {
        return getSessionListUseCase().firstOrNull() ?: emptyList()
    }
    
    fun searchSessions(query: String) {
        _searchQuery.value = query
    }

    fun createSession(name: String) {
        viewModelScope.launch {
            val session = SessionEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                pinned = false,
                archived = false,
                metadata = SessionMetadata()
            )
            createSessionUseCase(session)
            _currentSession.value = session
            // messages 会自动通过 flatMapLatest 加载，无需手动调用
            loadDraft()
        }
    }

    /**
     * 创建临时session（仅在内存中，不保存到数据库）
     * 只有当用户发送第一条消息时，才会真正保存到数据库
     */
    fun createTemporarySession(name: String) {
        val session = SessionEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            pinned = false,
            archived = false,
            metadata = SessionMetadata()
        )
        _currentSession.value = session
        // 将 sessionId 添加到 _newChatSessions，标记为新会话
        _newChatSessions.value = _newChatSessions.value + session.id
        // 临时session不加载草稿，因为还没有保存到数据库
        _draft.value = null
    }
    
    /**
     * 更新会话的关联 Agent
     */
    fun updateSessionAgent(sessionId: String, agentId: String?) {
        viewModelScope.launch {
            // 优先使用 currentSession，如果不是当前会话则从数据库查询
            val session = if (_currentSession.value?.id == sessionId) {
                _currentSession.value
            } else {
                // 从数据库查询会话（更可靠，不依赖内存中的 sessions 列表）
                getSessionListUseCase().firstOrNull()?.find { it.id == sessionId }
            }

            if (session != null) {
                val newMetadata = session.metadata.copy(agentId = agentId)
                val updatedSession = session.copy(metadata = newMetadata, updatedAt = System.currentTimeMillis())
                
                createSessionUseCase(updatedSession) // 使用 createSessionUseCase 进行更新 (upsert)
                
                if (_currentSession.value?.id == sessionId) {
                    _currentSession.value = updatedSession
                }
                // 更新 UI State 中的 agent (由 NavActivity 观察并设置，但这里最好也触发一下)
                // NavActivity 会监听 currentSession 的变化并处理 UI 更新
            }
        }
    }

    fun renameSession(newName: String) {
        viewModelScope.launch {
            val session = _currentSession.value ?: return@launch
            renameSessionUseCase(session.id, newName)
            _currentSession.value = session.copy(name = newName)
            // 更新 UI 状态的 channelName
            getSessionUiState(session.id)?.channelName = newName.ifBlank { "新对话" }
        }
    }

    fun renameSession(sessionId: String, newName: String) {
        viewModelScope.launch {
            renameSessionUseCase(sessionId, newName)
            // 如果重命名的是当前会话，更新当前会话状态
            val currentSession = _currentSession.value
            if (currentSession?.id == sessionId) {
                _currentSession.value = currentSession.copy(name = newName)
            }
            // 更新 UI 状态的 channelName
            getSessionUiState(sessionId)?.channelName = newName.ifBlank { "新对话" }
            // 刷新会话列表
            loadSessions()
        }
    }

    fun deleteCurrentSession() {
        viewModelScope.launch {
            val session = _currentSession.value ?: return@launch
            deleteSessionUseCase(session.id)
            _currentSession.value = null
            // messages 会自动清空（通过 flatMapLatest 返回 emptyList）
            _draft.value = null
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            deleteSessionUseCase(sessionId)
            // 如果删除的是当前会话，清空当前会话状态
            val currentSession = _currentSession.value
            if (currentSession?.id == sessionId) {
                _currentSession.value = null
                // messages 会自动清空（通过 flatMapLatest 返回 emptyList）
                _draft.value = null
            }
            // 清理该会话的 UI 状态（从 LRU Cache 中移除）
            uiStateCache.remove(sessionId)
            // 刷新会话列表
            loadSessions()
        }
    }

    fun pinSession(session: SessionEntity, pinned: Boolean) {
        viewModelScope.launch {
            pinSessionUseCase(session.id, pinned)
        }
    }

    fun pinSession(sessionId: String, pinned: Boolean) {
        viewModelScope.launch {
            pinSessionUseCase(sessionId, pinned)
            // 刷新会话列表
            loadSessions()
        }
    }

    fun archiveSession(sessionId: String, archived: Boolean) {
        viewModelScope.launch {
            archiveSessionUseCase(sessionId, archived)
            // 刷新会话列表
            loadSessions()
        }
    }

    @Deprecated("Use StreamingResponseHandler instead")
    fun sendMessage(content: String) {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
//            // 检查session是否已保存到数据库（通过检查sessions列表中是否包含当前session）
//            var isSessionSaved = _sessions.value.any { it.id == session.id }
//
//            // 如果session还未保存，先保存到数据库
//            if (!isSessionSaved) {
//                createSessionUseCase(session)
//                // 等待Flow更新，确保session被包含在列表中
//                // 使用first()等待sessions列表的下一次更新
//                getSessionListUseCase().first { list -> list.any { it.id == session.id } }
//                isSessionSaved = true
//            }
            
            val message = MessageEntity(
                id = UUID.randomUUID().toString(),
                sessionId = session.id,
                role = MessageRole.USER,
                type = MessageType.TEXT,
                content = content,
                metadata = MessageMetadata(
                    localFilePath = null,
                    remoteUrl = null,
                    modelName = null,
                    tokenUsage = null,
                    errorInfo = null
                ),
                parentMessageId = null,
                createdAt = System.currentTimeMillis(),
                status = com.example.star.aiwork.domain.model.MessageStatus.SENDING
            )
            sendMessageUseCase(message)
        }
    }

    @Deprecated("use RollbackHandler instead")
    fun rollbackLastMessage() {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            val lastMessage = messages.value.lastOrNull() ?: return@launch
            rollbackMessageUseCase(lastMessage.id)
        }
    }

    // loadMessages 方法已不再需要，因为 messages 通过 flatMapLatest 自动加载
    // 保留此方法以保持向后兼容，但实际不会执行任何操作
    @Deprecated("Messages are now automatically loaded via flatMapLatest", ReplaceWith(""))
    fun loadMessages(sessionId: String) {
        // 消息现在通过 flatMapLatest 自动加载，无需手动调用
    }

    fun saveDraft(content: String) {
        val session = _currentSession.value ?: return
//        viewModelScope.launch {
//            // 只有当session已保存到数据库时，才保存草稿
//            val isSessionSaved = _sessions.value.any { it.id == session.id }
//            if (isSessionSaved) {
//                updateDraftUseCase(session.id, content)
//                _draft.value = content
//            } else {
//                // 临时session的草稿只保存在内存中
//                _draft.value = content
//            }
//        }
        viewModelScope.launch {
            updateDraftUseCase(session.id, content)
            _draft.value = content
        }
    }

    fun loadDraft() {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            val draftEntity = getDraftUseCase(session.id)
            _draft.value = draftEntity?.content
        }
    }

    fun selectSession(session: SessionEntity) {
        _currentSession.value = session
        // messages 会自动通过 flatMapLatest 加载，无需手动调用 loadMessages
        // 确保会话的 UI 状态已创建
        getOrCreateSessionUiState(session.id, session.name)
        loadDraft()
    }

    /**
     * 检查会话是否为新创建的临时会话
     */
    fun isNewChat(sessionId: String): Boolean {
        return _newChatSessions.value.contains(sessionId)
    }

    /**
     * 持久化新会话并取消isNewChat标记
     */
    suspend fun persistNewChatSession(sessionId: String) {
        val session = _currentSession.value
        if (session != null && session.id == sessionId && _newChatSessions.value.contains(sessionId)) {
            // 持久化会话
            createSessionUseCase(session)
            // 注意：不需要手动更新 _sessions，因为 loadSessions() 已经订阅了 getSessionListUseCase() 的 Flow
            // 数据库更新后，Flow 会自动触发更新，_sessions 会自动更新
            // 取消isNewChat标记
            _newChatSessions.value = _newChatSessions.value - sessionId
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as Application

                // Create DataSources
                val sessionLocalDataSource = SessionLocalDataSourceImpl(application)
                val messageLocalDataSource = MessageLocalDataSourceImpl(application)
                val draftLocalDataSource = DraftLocalDataSourceImpl(application)

                // Create UseCases
                val getSessionListUseCase = GetSessionListUseCase(sessionLocalDataSource)
                val createSessionUseCase = CreateSessionUseCase(sessionLocalDataSource)
                val renameSessionUseCase = RenameSessionUseCase(sessionLocalDataSource)
                val deleteSessionUseCase = DeleteSessionUseCase(sessionLocalDataSource, messageLocalDataSource, draftLocalDataSource)
                val pinSessionUseCase = PinSessionUseCase(sessionLocalDataSource)
                val archiveSessionUseCase = ArchiveSessionUseCase(sessionLocalDataSource)
                val searchSessionsUseCase = SearchSessionsUseCase(sessionLocalDataSource)

                val sendMessageUseCase = SendMessageUseCase(messageLocalDataSource, sessionLocalDataSource)
                val rollbackMessageUseCase = RollbackMessageUseCase(messageLocalDataSource)
                val observeMessagesUseCase = ObserveMessagesUseCase(messageLocalDataSource)

                val getDraftUseCase = GetDraftUseCase(draftLocalDataSource)
                val updateDraftUseCase = UpdateDraftUseCase(draftLocalDataSource)
                val getTopSessionsUseCase = GetTopSessionsUseCase(sessionLocalDataSource)

                return ChatViewModel(
                    getSessionListUseCase,
                    createSessionUseCase,
                    renameSessionUseCase,
                    deleteSessionUseCase,
                    pinSessionUseCase,
                    archiveSessionUseCase,
                    sendMessageUseCase,
                    rollbackMessageUseCase,
                    observeMessagesUseCase,
                    getDraftUseCase,
                    updateDraftUseCase,
                    searchSessionsUseCase,
                    getTopSessionsUseCase
                ) as T
            }
        }
    }
}
