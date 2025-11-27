package com.example.star.aiwork.data.model

/**
 * data/remote 层的统一错误表示，便于向上层传递 AI 请求失败原因。
 */
sealed class LlmError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NetworkError(message: String = "网络连接失败", cause: Throwable? = null) :
        LlmError(message, cause)

    class AuthenticationError(message: String = "认证失败", cause: Throwable? = null) :
        LlmError(message, cause)

    class RateLimitError(message: String = "触发频率限制", cause: Throwable? = null) :
        LlmError(message, cause)

    class ServerError(message: String = "服务器内部错误", cause: Throwable? = null) :
        LlmError(message, cause)

    class RequestError(message: String = "请求参数无效", cause: Throwable? = null) :
        LlmError(message, cause)

    class CancelledError(message: String = "请求已取消", cause: Throwable? = null) :
        LlmError(message, cause)

    class UnknownError(message: String = "未知错误", cause: Throwable? = null) :
        LlmError(message, cause)
}

