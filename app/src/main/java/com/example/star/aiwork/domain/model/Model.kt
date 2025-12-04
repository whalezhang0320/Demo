package com.example.star.aiwork.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * AI 模型配置。
 * 定义了一个具体的 AI 模型实例，包括其标识符、显示名称、类型、支持的模态以及自定义请求参数。
 *
 * @property modelId 提供商 API 中实际使用的模型 ID字符串 (例如 "gpt-4-turbo")。
 * @property displayName 在应用 UI 中显示的友好名称。
 * @property id 本地数据库或配置中该模型条目的唯一标识符 (UUID)。
 * @property type 模型类型 (聊天、绘图、嵌入)。
 * @property customHeaders 调用该模型时附加的 HTTP 请求头。
 * @property customBodies 调用该模型时附加的 HTTP 请求体参数。
 * @property inputModalities 模型支持的输入模态 (文本、图片等)。
 * @property outputModalities 模型支持的输出模态 (文本、图片等)。
 * @property abilities 模型具备的特殊能力 (工具调用、推理等)。
 * @property tools 模型支持的内置工具集 (如联网搜索)。
 * @property providerOverwrite 覆盖提供商级别的默认设置 (例如使用特定的 API Key 或 Base URL)。
 * @property lastUsedTime 上次使用时间戳。
 */
@Serializable
data class Model(
    val modelId: String = "",
    val displayName: String = "",
    val id: String = UUID.randomUUID().toString(),
    val type: ModelType = ModelType.CHAT,
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBodies: List<CustomBody> = emptyList(),
    val inputModalities: List<Modality> = listOf(Modality.TEXT),
    val outputModalities: List<Modality> = listOf(Modality.TEXT),
    val abilities: List<ModelAbility> = emptyList(),
    val tools: Set<BuiltInTools> = emptySet(),
    val providerOverwrite: ProviderSetting? = null,
    val lastUsedTime: Long = 0,
)

/**
 * 模型类型枚举。
 */
@Serializable
enum class ModelType {
    /** 聊天模型 (LLM) */
    CHAT,

    /** 图像生成模型 */
    IMAGE,

    /** 文本嵌入模型 */
    EMBEDDING,
}

/**
 * 模态 (Modality) 枚举。
 * 定义输入或输出的数据类型。
 */
@Serializable
enum class Modality {
    /** 文本模态 */
    TEXT,

    /** 图像模态 */
    IMAGE,
}

/**
 * 模型能力 (Model Ability) 枚举。
 * 标识模型是否支持某些高级特性。
 */
@Serializable
enum class ModelAbility {
    /** 支持 Function Calling (工具调用) */
    TOOL,

    /** 支持思维链/推理 (Reasoning) */
    REASONING,
}

/**
 * 内置工具 (Built-in Tools) 选项。
 * 定义某些模型原生支持的特殊功能，如联网搜索或读取 URL 内容。
 */
@Serializable
sealed class BuiltInTools {
    /**
     * Google Search 等内置搜索工具。
     * 参见: https://ai.google.dev/gemini-api/docs/google-search?hl=zh-cn
     */
    @Serializable
    @SerialName("search")
    data object Search : BuiltInTools()

    /**
     * 读取和解析 URL 上下文的能力。
     * 参见: https://ai.google.dev/gemini-api/docs/url-context?hl=zh-cn
     */
    @Serializable
    @SerialName("url_context")
    data object UrlContext : BuiltInTools()
}
