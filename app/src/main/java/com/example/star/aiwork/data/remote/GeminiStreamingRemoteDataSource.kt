package com.example.star.aiwork.data.remote

import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.infra.network.SseClient
import com.example.star.aiwork.infra.util.KeyRoulette
import com.example.star.aiwork.infra.util.json
import com.example.star.aiwork.ui.ai.UIMessage
import com.example.star.aiwork.ui.ai.UIMessagePart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 使用 infra 层的 [SseClient] 调用 Google Gemini 的流式接口。
 *
 * data/remote 层不直接依赖 OkHttpClient，只负责构造 Request 与解析 SSE payload。
 */
class GeminiStreamingRemoteDataSource(
    private val sseClient: SseClient,
    private val jsonParser: Json = json,
    private val keyRoulette: KeyRoulette = KeyRoulette.default()
) : GeminiRemoteDataSource {

    override fun streamChat(
        history: List<ChatDataItem>,
        providerSetting: ProviderSetting.Google,
        params: TextGenerationParams,
        taskId: String
    ): Flow<String> {
        val uiMessages = history.map { it.toUIMessage() }

        val key = keyRoulette.next(providerSetting.apiKey)
        val modelId = params.model.modelId
        val baseUrl = providerSetting.baseUrl.trimEnd('/')

        // API 端点: streamGenerateContent (支持 SSE)
        val url = "$baseUrl/models/$modelId:streamGenerateContent?alt=sse&key=$key"

        val requestBody = buildGeminiRequestBody(uiMessages, params)

        val request = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        return sseClient.createStream(request, taskId)
            .filter { it.isNotBlank() && it != DONE_TOKEN }
            .mapNotNull { payload -> parseChunk(payload, modelId) }
    }

    /**
     * 解析单个 Gemini SSE payload，提取文本内容。
     */
    private fun parseChunk(payload: String, modelId: String): String? {
        return try {
            val root = jsonParser.parseToJsonElement(payload).jsonObject
            val candidates = root["candidates"]?.jsonArray
            val candidate = candidates?.firstOrNull()?.jsonObject

            val parts = candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray
            parts
                ?.firstOrNull()
                ?.jsonObject
                ?.get("text")
                ?.jsonPrimitive
                ?.contentOrNull
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 将 ChatDataItem 转为 UIMessage，并简单解析 [image:...] 标记为 UIMessagePart.Image。
     *
     * 注意：为了避免 data 层直接依赖 UI，这里只是过渡实现，后续可以抽到独立 mapper 模块。
     */
    private fun ChatDataItem.toUIMessage(): UIMessage {
        val roleEnum = when (role.lowercase()) {
            "user" -> MessageRole.USER
            "assistant" -> MessageRole.ASSISTANT
            "system" -> MessageRole.SYSTEM
            "tool" -> MessageRole.TOOL
            else -> MessageRole.USER
        }

        val parts = mutableListOf<UIMessagePart>()
        val textContent = content
        val markerStart = "[image:data:image/"

        if (textContent.contains(markerStart)) {
            var currentIndex = 0
            while (currentIndex < textContent.length) {
                val startIndex = textContent.indexOf(markerStart, currentIndex)
                if (startIndex == -1) {
                    val remainingText = textContent.substring(currentIndex)
                    if (remainingText.isNotEmpty()) {
                        parts.add(UIMessagePart.Text(remainingText))
                    }
                    break
                }

                if (startIndex > currentIndex) {
                    val textSegment = textContent.substring(currentIndex, startIndex)
                    if (textSegment.isNotEmpty()) {
                        parts.add(UIMessagePart.Text(textSegment))
                    }
                }

                val endIndex = textContent.indexOf("]", startIndex)
                if (endIndex != -1) {
                    // 去掉 "[image:" 前缀
                    val imageUrl = textContent.substring(startIndex + 7, endIndex)
                    parts.add(UIMessagePart.Image(imageUrl))
                    currentIndex = endIndex + 1
                } else {
                    val remainingText = textContent.substring(currentIndex)
                    parts.add(UIMessagePart.Text(remainingText))
                    break
                }
            }
        } else {
            parts.add(UIMessagePart.Text(textContent))
        }

        return UIMessage(
            role = roleEnum,
            parts = parts
        )
    }

    /**
     * 构造 Gemini generateContent 请求体。
     *
     * 逻辑参考 data/provider/GoogleProvider 中的实现。
     */
    private fun buildGeminiRequestBody(
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): String {
        return buildJsonObject {
            put("contents", buildJsonArray {
                messages.forEach { msg ->
                    add(buildJsonObject {
                        // Google 角色映射: user -> user, assistant -> model
                        put("role", if (msg.role == MessageRole.USER) "user" else "model")
                        put("parts", buildJsonArray {
                            msg.parts.forEach { part ->
                                when (part) {
                                    is UIMessagePart.Text -> {
                                        add(buildJsonObject {
                                            put("text", part.text)
                                        })
                                    }

                                    is UIMessagePart.Image -> {
                                        if (part.url.startsWith("data:")) {
                                            val base64Data = part.url.substringAfter("base64,")
                                            val mimeType = part.url.substringAfter("data:").substringBefore(";")
                                            add(buildJsonObject {
                                                put("inline_data", buildJsonObject {
                                                    put("mime_type", mimeType)
                                                    put("data", base64Data)
                                                })
                                            })
                                        }
                                    }

                                    else -> {}
                                }
                            }
                        })
                    })
                }
            })

            put("generationConfig", buildJsonObject {
                params.temperature?.let { put("temperature", it) }
                params.maxTokens?.let { put("maxOutputTokens", it) }
                params.topP?.let { put("topP", it) }
            })
        }.toString()
    }

    companion object {
        private const val DONE_TOKEN = "[DONE]"
    }
}


