package com.example.star.aiwork.data.remote

import com.example.star.aiwork.data.model.AiMessage
import com.example.star.aiwork.data.model.AiMessageRole
import com.example.star.aiwork.data.model.HttpBodyField
import com.example.star.aiwork.data.model.HttpHeader
import com.example.star.aiwork.data.model.LlmError
import com.example.star.aiwork.data.model.ModelConfig
import com.example.star.aiwork.data.model.NetworkProxy
import com.example.star.aiwork.data.model.OpenAiEndpoint
import com.example.star.aiwork.data.provider.openai.OpenAIChunk
import com.example.star.aiwork.infra.network.NetworkException
import com.example.star.aiwork.infra.network.SseClient
import com.example.star.aiwork.infra.network.defaultOkHttpClient
import com.example.star.aiwork.infra.util.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 统一的流式聊天远程数据源，封装 SSE 通讯细节。
 */
class StreamingChatRemoteDataSource(
    private val sseClient: SseClient,
    private val jsonParser: Json = json
) : RemoteChatDataSource {

    private val clientCache = ConcurrentHashMap<String, OkHttpClient>()

    override fun streamChat(
        history: List<AiMessage>,
        config: ModelConfig
    ): Flow<String> {
        val openAiConfig = config as? ModelConfig.OpenAICompatible
            ?: error("Unsupported config type: ${config::class.simpleName}")

        val request = buildStreamRequest(history, openAiConfig)
        val client = clientFor(openAiConfig.endpoint)

        return sseClient.createStream(request, openAiConfig.taskId, client)
            .filter { it.isNotBlank() && it != DONE_TOKEN }
            .mapNotNull { payload -> parseChunk(payload) }
            .catch { throwable -> throw throwable.toLlmError() }
    }

    override suspend fun cancelStreaming(taskId: String) {
        try {
            sseClient.cancel(taskId)
        } catch (ignored: Exception) {
            throw LlmError.CancelledError()
        }
    }

    private fun buildStreamRequest(
        history: List<AiMessage>,
        config: ModelConfig.OpenAICompatible
    ): Request {
        val endpoint = config.endpoint
        val url = "${endpoint.baseUrl}${endpoint.chatCompletionsPath}"

        val messagesJson = buildJsonArray {
            history.forEach { msg ->
                add(buildJsonObject {
                    put("role", msg.role.apiValue())
                    put("content", msg.content)
                })
            }
        }

        val body = buildJsonObject {
            put("model", config.modelId)
            put("messages", messagesJson)
            put("stream", true)
            config.temperature?.let { put("temperature", it) }
            config.topP?.let { put("top_p", it) }
            config.maxTokens?.let { put("max_tokens", it) }
            mergeBodyFields(config.modelBodies + config.customBody)
        }

        val headers = (config.modelHeaders + config.customHeaders).toHeaders()

        val requestBody = jsonParser.encodeToString(body)
            .toRequestBody(JSON_MEDIA_TYPE)

        return Request.Builder()
            .url(url)
            .headers(headers)
            .addHeader("Authorization", "Bearer ${endpoint.apiKey}")
            .post(requestBody)
            .build()
    }

    private fun JsonObjectBuilder.mergeBodyFields(fields: List<HttpBodyField>) {
        fields.forEach { put(it.key, it.value) }
    }

    private fun List<HttpHeader>.toHeaders(): Headers {
        val builder = Headers.Builder()
        forEach { builder.add(it.name, it.value) }
        return builder.build()
    }

    private fun parseChunk(payload: String): String? {
        return runCatching {
            val chunk = jsonParser.decodeFromString(OpenAIChunk.serializer(), payload)
            val choice = chunk.choices.firstOrNull()
            val delta = choice?.delta ?: choice?.message
            delta?.content
        }.getOrNull()
    }

    private fun clientFor(endpoint: OpenAiEndpoint): OkHttpClient {
        return clientCache.getOrPut(endpoint.providerId) {
            defaultOkHttpClient().newBuilder()
                .applyProxy(endpoint.proxy)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()
        }
    }

    private fun OkHttpClient.Builder.applyProxy(proxy: NetworkProxy): OkHttpClient.Builder {
        when (proxy) {
            is NetworkProxy.None -> Unit
            is NetworkProxy.Http -> {
                val javaProxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxy.host, proxy.port))
                proxy(javaProxy)
            }
        }
        return this
    }

    private fun AiMessageRole.apiValue(): String = this.name.lowercase()

    private fun Throwable.toLlmError(): LlmError {
        return when (this) {
            is LlmError -> this
            is NetworkException.TimeoutException -> LlmError.NetworkError("请求超时", this)
            is NetworkException.ConnectionException -> LlmError.NetworkError(cause = this)
            is NetworkException.HttpException -> mapHttpError(this)
            is NetworkException.UnknownException -> LlmError.UnknownError(cause = this)
            else -> LlmError.UnknownError(cause = this)
        }
    }

    private fun mapHttpError(exception: NetworkException.HttpException): LlmError {
        return when (exception.code) {
            401, 403 -> LlmError.AuthenticationError(cause = exception)
            408 -> LlmError.NetworkError("请求超时", exception)
            409 -> LlmError.RequestError("请求冲突", exception)
            422 -> LlmError.RequestError(cause = exception)
            429 -> LlmError.RateLimitError(cause = exception)
            in 500..599 -> LlmError.ServerError(cause = exception)
            else -> LlmError.RequestError(cause = exception)
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val DONE_TOKEN = "[DONE]"
    }
}

