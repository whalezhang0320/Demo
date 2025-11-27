## 1. 需求概述
- 统一协议、SSE 流式实现、OpenAI + 自定义第三方接入、重试/取消、错误映射。
- 模型与密钥设置页（添加/编辑模型源，填 API Key，测试连通性）的 UI 页面。
Ui 可以考虑后置。
## 2. Infra 层
提供网络通信的基础设施，处理HTTP协议细节
### 2.1 网络相关（Infra/network）
#### 2.1.1 Http 包裹

```kotlin
/**
 * HTTP 客户端接口，封装 HTTP 请求，负责捕获并向上抛出错误
 */
interface HttpClient {
    /**
     * 执行 HTTP 请求
     * @param request OkHttp Request 对象
     * @return HttpResponse 包装响应结果
     * @throws NetworkException 网络异常
     */
    suspend fun execute(request: okhttp3.Request): HttpResponse
}

sealed class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class TimeoutException(message: String = "请求超时", cause: Throwable? = null) : NetworkException(message, cause)
    class ConnectionException(message: String = "网络连接失败", cause: Throwable? = null) : NetworkException(message, cause)
    class HttpException(val code: Int, message: String = "HTTP 错误: $code", cause: Throwable? = null) : NetworkException(message, cause)
    class UnknownException(message: String = "未知网络错误", cause: Throwable? = null) : NetworkException(message, cause)
}
```

这里做包裹主要是不希望上层直接操作 http ，为上层做封装。同时，也方便底层更好的捕获并抛出错误。
#### 2.1.2 SSE
提供一个 SSE client 供 data/remote 调用
```kotlin
class SseClient(
    private val okHttpClient: OkHttpClient = defaultOkHttpClient()
) {
    //根据给定的 HTTP 请求和任务 ID 创建一个 SSE 流
    fun createStream(request: Request, taskId: String): Flow<String>
    //取消指定任务 ID 的 SSE 流连接。
    fun cancel(taskId: String)
    //取消所有活动的 SSE 流
    fun cancelAll()
}
```
## 3. Data 层
### 3.1 数据模型（data/model）
这边的 data/model 主要负责与 data/remote 相关的数据模型。
#### 3.1.1 通用配置模型 (ModelConfig)
抽象了所有 AI 模型通用的参数：provider (厂商)、apiKey、temperature (温度)。
#### 3.1.2 LlmError
具体错误信息也可以参照 message 字段信息。
- networkError：表示网络连接失败的错误。用于网络请求时无法连接到服务器的情况。
- AuthenticationError：表示认证失败的错误。
- RateLimitError：表示请求过于频繁，触发了限流机制。
- ServerError：表示服务器内部错误导致请求无法正常处理。
- RequestError：表示请求参数无效的错误。
- CancelledError：表示请求被取消的错误。
- UnknownError：表示发生了未知错误。
### 3.2 包装（data/repository）
完成 repository 中 /remote 的部分。
#### 3.2.1 统一协议接口 (AiRepository):
  - 定义了 streamChat 方法，接受标准化的 ModelConfig 和消息内容，返回标准化的 Flow<String>
```kotlin
interface AiRepository {
    /**
     * 流式对话接口
     * @param history 上下文历史。队友负责从数据库拿出来传给你。
     * @param config  模型配置。告诉你要用哪个模型 (Kimi/DeepSeek) 和 Key。
     * @return Flow<String> 返回流式文本。
     * 如果出错，Flow 会抛出你定义的 AiError。
     */
    fun streamChat(
        history: List<AiMessage>,
        config: ModelConfig
    ): Flow<String>

    /**
     * 取消流式请求
     * 
     * @param taskId 任务 ID
     */
    suspend fun cancelStreaming(taskId: String)
}
```
3.2.2 协议适配器 (StreamingChatRemoteDataSource):
  - 实现 AiRepository 接口。
  
4. Domain 层
4.1.1 流相关
PauseStreamingUseCase
- 停止流生成。让 repo 通知 remote 停止生成；并更新消息状态（Cancelled）；通知 UI 停止成功。
ResumeStreamingUseCase （暂时先忽略）
- 本条功能在课题要求中有，但是暂时不清楚具体需求是怎么样的

4.1.2 消息相关
SendMessageUseCase
- 从 UI 拿数据，通过 repo 写入 data/local 会话数据，并通过 repo 向 remote 请求 llm 数据。将 repo 返回的数据交给 UI（ViewModel）。
- 这里需要 会话与状态 的同学提供接口，供我们将 message 做持久化。
RollbackMessageUseCase
- 类似 reload，丢弃之前生成的对用户上条消息的回答，然后重新生成