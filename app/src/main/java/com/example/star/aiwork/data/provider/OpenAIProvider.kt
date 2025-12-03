package com.example.star.aiwork.data.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import com.example.star.aiwork.domain.Provider
import com.example.star.aiwork.domain.ImageGenerationParams
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ModelType
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.data.provider.openai.ChatCompletionsAPI
import com.example.star.aiwork.data.provider.openai.ResponseAPI
import com.example.star.aiwork.ui.ai.ImageAspectRatio
import com.example.star.aiwork.ui.ai.ImageGenerationItem
import com.example.star.aiwork.ui.ai.ImageGenerationResult
import com.example.star.aiwork.ui.ai.MessageChunk
import com.example.star.aiwork.ui.ai.UIMessage
import com.example.star.aiwork.infra.util.KeyRoulette
import com.example.star.aiwork.infra.util.configureClientWithProxy
import com.example.star.aiwork.infra.util.getByKey
import com.example.star.aiwork.infra.util.json
import com.example.star.aiwork.infra.util.mergeCustomBody
import com.example.star.aiwork.infra.util.toHeaders
import com.example.star.aiwork.infra.util.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OpenAI 提供商实现。
 *
 * 此类实现了 [Provider] 接口，用于与 OpenAI 及其兼容 API（如 SiliconCloud, Moonshot 等）进行交互。
 * 它支持：
 * - 列出模型
 * - 获取账户余额
 * - 文本生成（流式和非流式）
 * - 图像生成 (DALL-E)
 *
 * 文本生成的具体逻辑委托给了 [ChatCompletionsAPI] (用于标准 Chat 接口) 和 [ResponseAPI] (用于特定的 Response 格式接口)。
 */
class OpenAIProvider(
    private val client: OkHttpClient
) : Provider<ProviderSetting.OpenAI> {
    // API Key 管理工具，用于负载均衡
    private val keyRoulette = KeyRoulette.default()

    // 标准 Chat Completions API 实现
    private val chatCompletionsAPI = ChatCompletionsAPI(client = client, keyRoulette = keyRoulette)
    // 特定的 Response 格式 API 实现 (可能用于调试或特殊模型)
    private val responseAPI = ResponseAPI(client = client)

    /**
     * 获取 OpenAI 兼容接口的模型列表。
     *
     * 发送 GET /v1/models 请求。
     */
    override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<Model> =
        withContext(Dispatchers.IO) {
            val key = keyRoulette.next(providerSetting.apiKey)
            val request = Request.Builder()
                .url("${providerSetting.baseUrl}/models")
                .addHeader("Authorization", "Bearer $key")
                .get()
                .build()

            val response =
                client.configureClientWithProxy(providerSetting.proxy).newCall(request).await()
            if (!response.isSuccessful) {
                error("Failed to get models: ${response.code} ${response.body?.string()}")
            }

            val bodyStr = response.body?.string()
            if (bodyStr.isNullOrBlank()) return@withContext emptyList()
            
            val bodyJson = try {
                json.parseToJsonElement(bodyStr).jsonObject
            } catch (e: Exception) {
                return@withContext emptyList()
            }
            
            val data = bodyJson["data"]?.jsonArray ?: return@withContext emptyList()

            data.mapNotNull { modelJson ->
                val modelObj = modelJson.jsonObject
                val id = modelObj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null

                // 简单的规则：如果 ID 包含 "dall-e" 或 "image" 等关键词，则认为是图像生成模型
                val type = if (id.contains("dall-e", ignoreCase = true) || 
                               id.contains("image", ignoreCase = true) ||
                               id.contains("kwai", ignoreCase = true) ||
                               id.contains("kolors", ignoreCase = true) ||
                               id.contains("flux", ignoreCase = true)) {
                    ModelType.IMAGE
                } else {
                    ModelType.CHAT
                }

                Model(
                    modelId = id,
                    displayName = id,
                    type = type
                )
            }
        }

    /**
     * 获取账户余额。
     *
     * 根据 balanceOption 配置的 API 路径和 JSON 路径解析余额。
     */
    override suspend fun getBalance(providerSetting: ProviderSetting.OpenAI): String = withContext(Dispatchers.IO) {
        val key = keyRoulette.next(providerSetting.apiKey)
        val url = if (providerSetting.balanceOption.apiPath.startsWith("http")) {
            providerSetting.balanceOption.apiPath
        } else {
            "${providerSetting.baseUrl}${providerSetting.balanceOption.apiPath}"
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $key")
            .get()
            .build()
        val response = client.configureClientWithProxy(providerSetting.proxy).newCall(request).await()
        if (!response.isSuccessful) {
            error("Failed to get balance: ${response.code} ${response.body?.string()}")
        }

        val bodyStr = response.body?.string()
        if (bodyStr.isNullOrBlank()) error("Empty response body for balance")

        val bodyJson = try {
            json.parseToJsonElement(bodyStr).jsonObject
        } catch (e: Exception) {
             throw RuntimeException("Invalid JSON response for balance: $bodyStr", e)
        }
        
        val value = bodyJson.getByKey(providerSetting.balanceOption.resultPath)
        val digitalValue = value.toFloatOrNull()
        if(digitalValue != null) {
            "%.2f".format(digitalValue)
        } else {
            value
        }
    }

    /**
     * 流式生成文本。
     *
     * 根据配置选择使用 ResponseAPI 或 ChatCompletionsAPI。
     */
    override suspend fun streamText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<MessageChunk> = if (providerSetting.useResponseApi) {
        responseAPI.streamText(
            providerSetting = providerSetting,
            messages = messages,
            params = params
        )
    } else {
        chatCompletionsAPI.streamText(
            providerSetting = providerSetting,
            messages = messages,
            params = params
        )
    }

    /**
     * 非流式生成文本。
     */
    override suspend fun generateText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): MessageChunk = if (providerSetting.useResponseApi) {
        responseAPI.generateText(
            providerSetting = providerSetting,
            messages = messages,
            params = params
        )
    } else {
        chatCompletionsAPI.generateText(
            providerSetting = providerSetting,
            messages = messages,
            params = params
        )
    }

    /**
     * 生成图像 (DALL-E)。
     *
     * 发送 POST /v1/images/generations 请求。
     */
    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams
    ): ImageGenerationResult = withContext(Dispatchers.IO) {
        require(providerSetting is ProviderSetting.OpenAI) {
            "Expected OpenAI provider setting"
        }

        val key = keyRoulette.next(providerSetting.apiKey)

        // 构建请求体
        val requestBody = json.encodeToString(
            buildJsonObject {
                put("model", params.model.modelId)
                put("prompt", params.prompt)
                if (params.numOfImages > 1) {
                    put("n", params.numOfImages)
                }
                // 默认为 URL，以提高兼容性。
                
                // 只有 DALL-E 模型才强制添加 size 参数
                // 其他模型（如 SiliconFlow 上的开源模型）可能不支持 size 或使用默认值
                if (params.model.modelId.contains("dall-e", ignoreCase = true)) {
                    put(
                        "size", when (params.aspectRatio) {
                            ImageAspectRatio.SQUARE -> "1024x1024"
                            ImageAspectRatio.LANDSCAPE -> "1536x1024"
                            ImageAspectRatio.PORTRAIT -> "1024x1536"
                        }
                    )
                }
            }.mergeCustomBody(params.customBody)
        )

        val request = Request.Builder()
            .url("${providerSetting.baseUrl}/images/generations")
            .headers(params.customHeaders.toHeaders())
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.configureClientWithProxy(providerSetting.proxy).newCall(request).await()
        if (!response.isSuccessful) {
            error("Failed to generate image: ${response.code} ${response.body?.string()}")
        }

        val bodyStr = response.body?.string()
        if (bodyStr.isNullOrBlank()) error("Empty response body for image generation")

        val bodyJson = try {
            json.parseToJsonElement(bodyStr).jsonObject
        } catch (e: Exception) {
            throw RuntimeException("Invalid JSON response for image generation: $bodyStr", e)
        }
        
        val data = bodyJson["data"]?.jsonArray ?: error("No data in response")

        // 解析返回的图片数据
        val items = data.map { imageJson ->
            val imageObj = imageJson.jsonObject
            val b64Json = imageObj["b64_json"]?.jsonPrimitive?.contentOrNull
            val url = imageObj["url"]?.jsonPrimitive?.contentOrNull

            if (b64Json != null) {
                ImageGenerationItem(
                    data = b64Json,
                    mimeType = "image/png"
                )
            } else if (url != null) {
                ImageGenerationItem(
                    data = url,
                    mimeType = "image/png"
                )
            } else {
                error("No b64_json or url in response")
            }
        }

        ImageGenerationResult(items = items)
    }
}
