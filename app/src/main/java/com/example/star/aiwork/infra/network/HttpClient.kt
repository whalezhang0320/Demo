package com.example.star.aiwork.infra.network

import com.example.star.aiwork.infra.util.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * HTTP 客户端接口，封装底层实现细节，提供统一的异常语义。
 */
interface HttpClient {
    /**
     * 执行 HTTP 请求并返回包装后的响应。
     *
     * @throws NetworkException 当出现网络层错误或 HTTP 错误码时抛出。
     */
    suspend fun execute(request: Request): HttpResponse
}

/**
 * 统一的 HTTP 响应包装。
 */
data class HttpResponse(
    val code: Int,
    val headers: Headers,
    val body: String
)

/**
 * 网络错误抽象，向上层屏蔽 OkHttp/IO 细节。
 */
sealed class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class TimeoutException(message: String = "请求超时", cause: Throwable? = null) :
        NetworkException(message, cause)

    class ConnectionException(message: String = "网络连接失败", cause: Throwable? = null) :
        NetworkException(message, cause)

    class HttpException(val code: Int, body: String? = null, message: String = "HTTP 错误: $code") :
        NetworkException(body ?: message)

    class UnknownException(message: String = "未知网络错误", cause: Throwable? = null) :
        NetworkException(message, cause)
}

/**
 * 默认的 OkHttp 实现，提供协程友好的 execute 能力。
 */
class OkHttpHttpClient(
    private val okHttpClient: OkHttpClient = defaultOkHttpClient()
) : HttpClient {

    override suspend fun execute(request: Request): HttpResponse = withContext(Dispatchers.IO) {
        try {
            val response = okHttpClient.newCall(request).await()
            response.use {
                val body = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    throw NetworkException.HttpException(code = it.code, body = body)
                }
                HttpResponse(
                    code = it.code,
                    headers = it.headers,
                    body = body
                )
            }
        } catch (timeout: SocketTimeoutException) {
            throw NetworkException.TimeoutException(cause = timeout)
        } catch (connect: UnknownHostException) {
            throw NetworkException.ConnectionException(cause = connect)
        } catch (connect: ConnectException) {
            throw NetworkException.ConnectionException(cause = connect)
        } catch (io: IOException) {
            throw NetworkException.UnknownException(message = "网络读写异常", cause = io)
        } catch (e: NetworkException) {
            throw e
        } catch (e: Exception) {
            throw NetworkException.UnknownException(cause = e)
        }
    }
}

/**
 * 提供给 Infra 默认使用的 OkHttpClient。
 */
fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(DEFAULT_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
    .readTimeout(DEFAULT_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
    .writeTimeout(DEFAULT_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
    .build()

private const val DEFAULT_TIMEOUT_MS = 30_000L

