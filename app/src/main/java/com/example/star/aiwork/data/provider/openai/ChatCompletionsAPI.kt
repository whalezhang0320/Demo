package com.example.star.aiwork.data.provider.openai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.ui.ai.MessageChunk
import com.example.star.aiwork.ui.ai.UIMessage
import com.example.star.aiwork.ui.ai.UIMessagePart
import com.example.star.aiwork.infra.util.KeyRoulette
import com.example.star.aiwork.infra.util.await
import com.example.star.aiwork.infra.util.configureClientWithProxy
import com.example.star.aiwork.infra.util.json
import com.example.star.aiwork.infra.util.mergeCustomBody
import com.example.star.aiwork.infra.util.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

/**
 * OpenAI Chat Completions API 的具体实现。
 *
 * 负责处理与 OpenAI 风格的 /v1/chat/completions 接口的直接交互。
 * 支持流式 (Stream) 和非流式 (Non-Stream) 的文本生成请求。
 *
 * @property client OkHttpClient 实例，用于发送网络请求。
 * @property keyRoulette API Key 轮盘赌工具，用于密钥负载均衡。
 */
class ChatCompletionsAPI(
    private val client: OkHttpClient,
    private val keyRoulette: KeyRoulette
) {

    /**
     * 非流式生成文本。
     *
     * 发送 POST 请求并等待完整响应，然后解析为 MessageChunk。
     *
     * @param providerSetting OpenAI 兼容提供商设置。
     * @param messages 聊天历史消息列表。
     * @param params 文本生成参数。
     * @return 包含生成内容的 MessageChunk。
     */
    suspend fun generateText(
        providerSetting: ProviderSetting.OpenAICompatible,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): MessageChunk {
        val request = buildRequest(providerSetting, messages, params, stream = false)
        val response = client.configureClientWithProxy(providerSetting.proxy).newCall(request).await()
        if (!response.isSuccessful) {
            error("Failed to generate text: ${response.code} ${response.body?.string()}")
        }
        val bodyStr = response.body?.string() ?: error("Empty response body")
        
        // Decode into OpenAIChunk DTO first, then convert to MessageChunk
        val openAIChunk = json.decodeFromString<OpenAIChunk>(bodyStr)
        return openAIChunk.toMessageChunk()
    }

    /**
     * 流式生成文本。
     *
     * 发送请求并建立 SSE (Server-Sent Events) 连接，逐行读取响应数据。
     *
     * @param providerSetting OpenAI 兼容提供商设置。
     * @param messages 聊天历史消息列表。
     * @param params 文本生成参数。
     * @return 发出 MessageChunk 的 Flow 数据流。
     */
    suspend fun streamText(
        providerSetting: ProviderSetting.OpenAICompatible,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<MessageChunk> = flow {
        // 1. 构建流式请求：关键在于设置 stream = true，告诉服务器我们要长连接推送
        val request = buildRequest(providerSetting, messages, params, stream = true)
        
        // 2. 发送请求并配置超时
        // 这里必须将 readTimeout 设置为 0 (无限)，因为 AI 生成长文本可能需要数分钟，
        // 默认的 30s 超时会导致连接中断。
        val response = client.configureClientWithProxy(providerSetting.proxy)
            .newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
            .newCall(request)
            .await()
            
        if (!response.isSuccessful) {
            error("Failed to stream text: ${response.code} ${response.body?.string()}")
        }

        // 3. 获取原始字节流并包装为字符缓冲流，方便按行读取 SSE 数据
        val source = response.body?.source() ?: error("Empty response body")
        val reader = BufferedReader(source.inputStream().reader())

        try {
            // 4. 循环读取 SSE 事件流
            var line: String? = reader.readLine()
            while (line != null) {
                // SSE 协议规定有效负载以 "data: " 开头，忽略心跳包或注释
                if (line.startsWith("data: ")) {
                    val data = line.substring(6).trim()
                    
                    // 5. 检测结束信号：OpenAI 协议规定以 [DONE] 标记流结束
                    if (data == "[DONE]") break
                    
                    try {
                        // 6. 反序列化：将 JSON 字符串解析为 OpenAIChunk DTO
                        val chunk = json.decodeFromString<OpenAIChunk>(data)
                        // 7. 实时发射：将数据块转换为 UI 模型并通过 Flow 推送给上层
                        emit(chunk.toMessageChunk())
                    } catch (e: Exception) {
                        // 容错处理：忽略解析失败的脏数据包，保证整个流不中断
                        // e.printStackTrace() // Uncomment for debug
                    }
                }
                line = reader.readLine() // 读取下一行
            }
        } finally {
            // 8. 资源释放：确保网络流被正确关闭，防止内存泄漏
            reader.close()
            response.close()
        }
    }

    /**
     * 构建 OkHttp 请求对象。
     *
     * 组装 URL、Header 和 JSON Body。
     *
     * @param stream 是否启用流式传输。
     */
    private fun buildRequest(
        providerSetting: ProviderSetting.OpenAICompatible,
        messages: List<UIMessage>,
        params: TextGenerationParams,
        stream: Boolean
    ): Request {
        val key = keyRoulette.next(providerSetting.apiKey)
        val url = "${providerSetting.baseUrl}${providerSetting.chatCompletionsPath}"

        val messagesJson = buildJsonArray {
            messages.forEach { msg ->
                add(buildJsonObject {
                    put("role", msg.role.name.lowercase())
                    
                    // 检查是否包含多模态内容 (非纯文本)
                    val hasNonTextParts = msg.parts.any { it !is UIMessagePart.Text }
                    
                    if (hasNonTextParts) {
                        put("content", buildJsonArray {
                            // 处理结构化的多模态消息
                            msg.parts.forEach { part ->
                                when (part) {
                                    is UIMessagePart.Text -> add(buildJsonObject {
                                        put("type", "text")
                                        put("text", part.text)
                                    })
                                    is UIMessagePart.Image -> add(buildJsonObject {
                                        put("type", "image_url")
                                        put("image_url", buildJsonObject {
                                            put("url", part.url)
                                        })
                                    })
                                    else -> {}
                                }
                            }
                        })
                    } else {
                        // 纯文本消息
                        put("content", msg.toText())
                    }
                })
            }
        }

        val jsonBody = buildJsonObject {
            put("model", params.model.modelId)
            put("messages", messagesJson)
            put("stream", stream)
            if (params.temperature != null) put("temperature", params.temperature)
            if (params.topP != null) put("top_p", params.topP)
            if (params.maxTokens != null) put("max_tokens", params.maxTokens)
        }.mergeCustomBody(params.customBody)

        val requestBody = json.encodeToString(jsonBody)
            .toRequestBody("application/json".toMediaType())

        return Request.Builder()
            .url(url)
            .headers(params.customHeaders.toHeaders())
            .addHeader("Authorization", "Bearer $key")
            .post(requestBody)
            .build()
    }
}
