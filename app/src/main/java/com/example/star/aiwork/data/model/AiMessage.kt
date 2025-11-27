package com.example.star.aiwork.data.model

/**
 * 统一的对话消息实体，供 data/remote 层发起请求时使用。
 */
data class AiMessage(
    val role: AiMessageRole,
    val content: String
)

enum class AiMessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

