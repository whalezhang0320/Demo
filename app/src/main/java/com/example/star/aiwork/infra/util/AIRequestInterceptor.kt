package com.example.star.aiwork.infra.util

import com.example.star.aiwork.infra.util.json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.nio.charset.Charset

/**
 * AI 请求拦截器。
 *
 * 这是一个 OkHttp 拦截器，用于在发送请求前修改请求头或进行其他处理。
 * 主要用途是拦截发往特定 AI 服务提供商（如 SiliconCloud）的请求，
 * 并为免费模型或未配置 API Key 的请求自动注入 fallback API Key。
 */
class AIRequestInterceptor : Interceptor {
    // 免费模型列表
    // 在实际应用中，这些配置应来自 FirebaseRemoteConfig 或类似的远程配置服务
    private val freeModels = listOf("Qwen/Qwen2.5-7B-Instruct", "THUDM/glm-4-9b-chat")
    
    // 后备 API Key，用于演示或免费模型
    // 注意：在生产环境中不应将敏感 Key 硬编码
    // 这个 Key 已经失效，现在用于检测是否使用了旧的策略
    private val invalidatedFallbackApiKey = "sk-kvvjdrxnhqicbrjdbvbgwuyyvstssgmeqgufhuqwpjqvjuyg"

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val host = request.url.host

        // 针对 SiliconCloud 的请求进行特殊处理
        if (host == "api.siliconflow.cn") {
            request = processSiliconCloudRequest(request)
        }

        return chain.proceed(request)
    }

    /**
     * 处理发往 SiliconCloud 的请求。
     *
     * 检查 Authorization 头，如果缺失或无效，且请求的是支持的免费模型，
     * 则自动注入 fallbackApiKey。
     */
    private fun processSiliconCloudRequest(request: Request): Request {
        val authHeader = request.header("Authorization")
        val path = request.url.encodedPath

        // 如果没有设置 api token (或仅有 Bearer 前缀)，且路径匹配
        // 或者使用了已经失效的 fallbackApiKey
        val isAuthMissingOrInvalid = authHeader.isNullOrBlank() || 
                                     authHeader.trim() == "Bearer" || 
                                     authHeader.trim() == "Bearer sk-" ||
                                     authHeader.contains(invalidatedFallbackApiKey)

        if (isAuthMissingOrInvalid && path in listOf("/v1/chat/completions", "/v1/models")) {
            // 读取请求体以获取请求的模型名称
            val bodyJson = request.readBodyAsJson()
            val model = bodyJson?.jsonObject?.get("model")?.jsonPrimitive?.content
            
            // 如果模型为空 (可能是列出模型列表的请求) 或者是已知的免费模型
            // 则使用 fallbackApiKey
            if (model.isNullOrEmpty() || model in freeModels) {
                // 之前的兜底策略失效，现在抛出异常以便上层捕获并切换到 Ollama
                throw IOException("SiliconCloud fallback strategy invalidated. Switching to local Ollama.")
            }
        }

        return request
    }
    
    /**
     * 读取请求体并解析为 JsonElement。
     *
     * 注意：此操作会克隆请求体，以避免消耗原始请求流，但这仍可能消耗内存。
     * 仅适用于 application/json 类型的请求。
     */
    private fun Request.readBodyAsJson(): JsonElement? {
        val contentType = body?.contentType()
        if (contentType?.type == "application" && contentType.subtype == "json") {
            // 我们需要克隆 body，因为读取它会消耗流
            val buffer = Buffer()
            body?.writeTo(buffer)
            
            val charset = contentType.charset(Charset.forName("UTF-8")) ?: Charset.forName("UTF-8")
            val jsonString = buffer.readString(charset)
            return try {
                json.parseToJsonElement(jsonString)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }
}
