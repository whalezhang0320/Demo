package com.example.star.aiwork.infra.network

import com.example.star.aiwork.infra.util.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * SSE 客户端，负责长连接读取与统一异常处理。
 */
class SseClient(
    private val okHttpClient: OkHttpClient = defaultOkHttpClient(),
    private val charset: Charset = Charsets.UTF_8
) {

    private val activeCalls = ConcurrentHashMap<String, Call>()

    /**
     * 根据请求创建 SSE 流。
     */
    fun createStream(
        request: Request,
        taskId: String,
        clientOverride: OkHttpClient? = null
    ): Flow<String> = flow {
        val callClient = clientOverride ?: okHttpClient
        val call = callClient.newCall(request)
        activeCalls[taskId]?.cancel()
        activeCalls[taskId] = call

        try {
            val response = call.await()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errorBody = resp.body?.string().orEmpty()
                    throw NetworkException.HttpException(resp.code, errorBody)
                }
                val body = resp.body ?: throw NetworkException.UnknownException("SSE 响应体为空")
                body.source().inputStream().use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream, charset))
                    reader.useLines { sequence ->
                        for (line in sequence) {
                            coroutineContext.ensureActive()
                            val payload = line.parseSseData() ?: continue
                            emit(payload)
                        }
                    }
                }
            }
        } catch (io: IOException) {
            throw mapIOException(io)
        } finally {
            activeCalls.remove(taskId)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 取消指定任务。
     */
    fun cancel(taskId: String) {
        activeCalls.remove(taskId)?.cancel()
    }

    /**
     * 取消所有活动任务。
     */
    fun cancelAll() {
        activeCalls.entries.forEach { (_, call) ->
            call.cancel()
        }
        activeCalls.clear()
    }

    private fun String.parseSseData(): String? {
        // 忽略心跳或注释
        if (isBlank() || startsWith(":")) return null
        return if (startsWith("data:")) {
            substringAfter("data:").trimStart()
        } else {
            this
        }
    }
}

private fun mapIOException(e: IOException): NetworkException {
    return when (e) {
        is SocketTimeoutException -> NetworkException.TimeoutException(cause = e)
        is UnknownHostException -> NetworkException.ConnectionException(cause = e)
        is ConnectException -> NetworkException.ConnectionException(cause = e)
        else -> NetworkException.UnknownException(message = "SSE 读取失败", cause = e)
    }
}

