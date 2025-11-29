// ChatViewModel.kt
package com.example.star.aiwork.ui.conversation

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.star.aiwork.data.local.datasource.DraftLocalDataSourceImpl
import com.example.star.aiwork.data.local.datasource.MessageLocalDataSourceImpl
import com.example.star.aiwork.data.local.datasource.SessionLocalDataSourceImpl
import com.example.star.aiwork.data.repository.DraftRepositoryImpl
import com.example.star.aiwork.data.repository.MessageRepositoryImpl
import com.example.star.aiwork.data.repository.SessionRepositoryImpl
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
import java.util.*

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
    private val updateDraftUseCase: UpdateDraftUseCase
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<SessionEntity>>(emptyList())
    val sessions: StateFlow<List<SessionEntity>> = _sessions.asStateFlow()

    private val _currentSession = MutableStateFlow<SessionEntity?>(null)
    val currentSession: StateFlow<SessionEntity?> = _currentSession.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    private val _draft = MutableStateFlow<String?>(null)
    val draft: StateFlow<String?> = _draft.asStateFlow()

    init {
        loadSessions()
    }

    fun loadSessions() {
        viewModelScope.launch {
            getSessionListUseCase().collect { list ->
                _sessions.value = list
                if (_currentSession.value == null) {
                    _currentSession.value = list.firstOrNull()
                }
            }
        }
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
        }
    }

    fun renameSession(newName: String) {
        viewModelScope.launch {
            val session = _currentSession.value ?: return@launch
            renameSessionUseCase(session.id, newName)
            _currentSession.value = session.copy(name = newName)
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
            // 刷新会话列表
            loadSessions()
        }
    }

    fun deleteCurrentSession() {
        viewModelScope.launch {
            val session = _currentSession.value ?: return@launch
            deleteSessionUseCase(session.id)
            _currentSession.value = null
            _messages.value = emptyList()
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
                _messages.value = emptyList()
                _draft.value = null
            }
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

    fun sendMessage(content: String) {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
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

    fun rollbackLastMessage() {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            val lastMessage = _messages.value.lastOrNull() ?: return@launch
            rollbackMessageUseCase(lastMessage.id)
        }
    }

    fun loadMessages(sessionId: String) {
        viewModelScope.launch {
            observeMessagesUseCase(sessionId).collect { list ->
                _messages.value = list
            }
        }
    }

    fun saveDraft(content: String) {
        val session = _currentSession.value ?: return
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
        loadMessages(session.id)
        loadDraft()
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

                // Create Repositories
                val sessionRepository = SessionRepositoryImpl(sessionLocalDataSource)
                val messageRepository = MessageRepositoryImpl(messageLocalDataSource)
                val draftRepository = DraftRepositoryImpl(draftLocalDataSource)

                // Create UseCases
                val getSessionListUseCase = GetSessionListUseCase(sessionRepository)
                val createSessionUseCase = CreateSessionUseCase(sessionRepository)
                val renameSessionUseCase = RenameSessionUseCase(sessionRepository)
                val deleteSessionUseCase = DeleteSessionUseCase(sessionRepository, messageRepository, draftRepository)
                val pinSessionUseCase = PinSessionUseCase(sessionRepository)
                val archiveSessionUseCase = ArchiveSessionUseCase(sessionRepository)

                val sendMessageUseCase = SendMessageUseCase(messageRepository, sessionRepository)
                val rollbackMessageUseCase = RollbackMessageUseCase(messageRepository)
                val observeMessagesUseCase = ObserveMessagesUseCase(messageRepository)

                val getDraftUseCase = GetDraftUseCase(draftRepository)
                val updateDraftUseCase = UpdateDraftUseCase(draftRepository)

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
                    updateDraftUseCase
                ) as T
            }
        }
    }
}
