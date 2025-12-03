package com.example.star.aiwork.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SessionMetadata(
    val defaultModel: String? = null,          // 默认使用的模型
    val lastDraft: String? = null,             // 最近草稿
    val lastMessagePreview: String? = null,    // 会话列表预览文案
    val agentId: String? = null                // 关联的 Agent ID
)
