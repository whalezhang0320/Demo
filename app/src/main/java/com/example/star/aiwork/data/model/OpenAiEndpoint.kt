package com.example.star.aiwork.data.model

/**
 * OpenAI 兼容端点配置。
 */
data class OpenAiEndpoint(
    val providerId: String,
    val apiKey: String,
    val baseUrl: String,
    val chatCompletionsPath: String,
    val proxy: NetworkProxy = NetworkProxy.None
)

