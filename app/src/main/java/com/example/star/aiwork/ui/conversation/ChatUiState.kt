package com.example.star.aiwork.ui.conversation

import com.example.star.aiwork.domain.model.MessageEntity
import com.example.star.aiwork.domain.model.SessionEntity

data class ChatUiState(
    val sessions: List<SessionEntity> = emptyList(),
    val currentSession: SessionEntity? = null,
    val messages: List<MessageEntity> = emptyList(),
    val draft: String? = null
)