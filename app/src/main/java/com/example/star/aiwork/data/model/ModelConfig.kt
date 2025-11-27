package com.example.star.aiwork.data.model

import java.util.UUID

/**
 * 统一的模型配置抽象，封装 provider 级别与模型级别的配置。
 */
sealed class ModelConfig {
    abstract val modelId: String
    abstract val temperature: Float?
    abstract val topP: Float?
    abstract val maxTokens: Int?
    abstract val customHeaders: List<HttpHeader>
    abstract val customBody: List<HttpBodyField>
    abstract val taskId: String

    /**
     * 针对 OpenAI 兼容协议的配置。
     */
    data class OpenAICompatible(
        val endpoint: OpenAiEndpoint,
        override val modelId: String,
        val modelHeaders: List<HttpHeader> = emptyList(),
        val modelBodies: List<HttpBodyField> = emptyList(),
        override val temperature: Float? = null,
        override val topP: Float? = null,
        override val maxTokens: Int? = null,
        override val customHeaders: List<HttpHeader> = emptyList(),
        override val customBody: List<HttpBodyField> = emptyList(),
        override val taskId: String = UUID.randomUUID().toString()
    ) : ModelConfig()
}

