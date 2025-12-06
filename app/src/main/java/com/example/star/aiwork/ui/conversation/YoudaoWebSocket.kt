package com.example.star.aiwork.ui.conversation

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 有道语音识别 WebSocket 客户端
 *
 * 负责与有道语音识别 API 建立 WebSocket 连接,
 * 发送音频数据并接收识别结果。
 *
 * 也包含 TTS (语音合成) 功能。
 */
class YoudaoWebSocket {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val appKey = "1fa9647ca43dd17a"
    private val appSecret = "adcF7pXU5MK2yfzVRN5OfJSSUVsIpLEg"
    
    // 默认发音人
    var currentVoiceName = "youxiaozhi"

    var listener: TranscriptionListener? = null
    var ttsListener: TtsListener? = null
    
    /**
     * 可用的发音人列表
     * 键为发音人代号，值为显示名称
     */
    val availableVoices = mapOf(
        "youxiaozhi" to "有小智 (男/常见语种)",
        "youxiaoxun" to "有小薰 (女/常见语种)",
        "youxiaoqin" to "有小沁 (女/常见语种)",
        "youxiaofu" to "有小芙 (女/常见语种)",
        "youyuting" to "有雨婷 (女/常见语种)",
        "youxiaohao" to "有小浩 (男/常见语种)",
        "youxiaonan" to "有小楠 (男/常见语种)"
    )

    /**
     * 连接到有道语音识别服务
     */
    fun connect() {
        Log.d(TAG, "🔌 Attempting to connect to Youdao WebSocket...")

        val salt = UUID.randomUUID().toString()
        val curtime = (System.currentTimeMillis() / 1000).toString()
        val signStr = appKey + salt + curtime + appSecret
        val sign = sha256(signStr)

        Log.d(TAG, "🔑 Auth params - appKey: $appKey, salt: $salt, curtime: $curtime")

        val url = "wss://openapi.youdao.com/stream_asropenapi" +
                "?appKey=$appKey" +
                "&salt=$salt" +
                "&curtime=$curtime" +
                "&sign=$sign" +
                "&signType=v4" +
                "&format=wav" +
                "&rate=16000" +
                "&langType=zh-CHS" +
                "&channel=1" +
                "&version=v1" +
                "&pointParam=yes"

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "✅ WebSocket OPENED successfully!")
                Log.d(TAG, "📡 Response code: ${response.code}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "📩 ========== RECEIVED MESSAGE ==========")
                Log.d(TAG, "📩 Raw text: $text")

                try {
                    val json = JSONObject(text)

                    // 打印格式化的 JSON
                    Log.d(TAG, "📝 Formatted JSON:\n${json.toString(2)}")

                    val action = json.optString("action")
                    Log.d(TAG, "🎬 Action type: '$action'")

                    when (action) {
                        "started" -> {
                            Log.d(TAG, "▶️ Recognition STARTED")
                        }
                        "recognition", "result" -> {
                            // 尝试作为 JSON 数组解析 (新的 recognition 格式)
                            val resultArray = json.optJSONArray("result")

                            if (resultArray != null && resultArray.length() > 0) {
                                val item = resultArray.getJSONObject(0)
                                val segId = item.optInt("seg_id")
                                val st = item.optJSONObject("st")
                                val sentence = st?.optString("sentence") ?: ""
                                val type = st?.optInt("type")
                                val isPartial = st?.optBoolean("partial") ?: false

                                Log.d(TAG, "✅ ========== RECOGNITION RESULT ==========")
                                Log.d(TAG, "✅ Action: $action")
                                Log.d(TAG, "✅ Segment ID: $segId")
                                Log.d(TAG, "✅ Type: $type, Partial: $isPartial")
                                Log.d(TAG, "✅ Transcription: '$sentence'")
                                Log.d(TAG, "✅ =========================================")

                                if (sentence.isNotEmpty()) {
                                    listener?.onTranscriptionReceived(sentence, !isPartial)
                                    Log.d(TAG, "📤 Sent result to listener")
                                }
                            } else {
                                // 兼容旧格式 (如果 result 是字符串)
                                val result = json.optString("result")
                                // 忽略如果是空数组的字符串表示 "[]"
                                if (result.isNotEmpty() && result != "[]") {
                                    val segId = json.optInt("seg_id")
                                    val isFinal = json.optBoolean("isEnd", false)

                                    Log.d(TAG, "✅ ========== RECOGNITION RESULT (Legacy) ==========")
                                    Log.d(TAG, "✅ Action: $action")
                                    Log.d(TAG, "✅ Segment ID: $segId")
                                    Log.d(TAG, "✅ Is Final: $isFinal")
                                    Log.d(TAG, "✅ Transcription: '$result'")
                                    Log.d(TAG, "✅ =========================================")

                                    listener?.onTranscriptionReceived(result, isFinal)
                                    Log.d(TAG, "📤 Sent result to listener")
                                } else {
                                     Log.w(TAG, "⚠️ Empty result received")
                                }
                            }
                        }
                        "error" -> {
                            val errorCode = json.optString("errorCode")
                            val descCN = json.optString("descCN")
                            val descEN = json.optString("desc")

                            Log.e(TAG, "❌ ========== ERROR RECEIVED ==========")
                            Log.e(TAG, "❌ Error Code: $errorCode")
                            Log.e(TAG, "❌ Description (CN): $descCN")
                            Log.e(TAG, "❌ Description (EN): $descEN")
                            Log.e(TAG, "❌ =====================================")

                            listener?.onError(descCN.ifEmpty { descEN })
                        }
                        else -> {
                            Log.w(TAG, "⚠️ Unknown action type: '$action'")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "💥 Failed to parse JSON message", e)
                    Log.e(TAG, "💥 Original message: $text")
                }

                Log.d(TAG, "📩 ========== END MESSAGE ==========")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "💥 ========== WebSocket FAILED ==========")
                Log.e(TAG, "💥 Error: ${t.message}")
                Log.e(TAG, "💥 Response: ${response?.message}")
                Log.e(TAG, "💥 =======================================", t)
                listener?.onError(t.message ?: "Connection failed")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "🔒 WebSocket CLOSED - Code: $code, Reason: '$reason'")
            }
        })
    }

    /**
     * 发送音频数据
     * @param audioData PCM 音频数据
     * @param size 数据大小
     */
    fun sendAudio(audioData: ByteArray, size: Int) {
        webSocket?.let {
            val data = audioData.copyOf(size)
            val sent = it.send(ByteString.of(*data))

            if (sent) {
                // 每秒只打印一次,避免日志刷屏
                if (System.currentTimeMillis() % 1000 < 50) {
                    Log.d(TAG, "📤 Sent $size bytes of audio data")
                }
            } else {
                Log.e(TAG, "❌ Failed to send audio data (size: $size)")
            }
        } ?: run {
            Log.e(TAG, "❌ Cannot send audio: WebSocket is null!")
        }
    }

    /**
     * 关闭连接
     */
    fun close() {
        Log.d(TAG, "🔌 Closing WebSocket connection...")
        webSocket?.close(1000, "User stopped")
        webSocket = null
        Log.d(TAG, "✅ WebSocket closed")
    }

    /**
     * 语音合成 (TTS)
     * @param text 待合成的文本
     */
    fun synthesize(text: String) {
        Log.d(TAG, "🗣️ Starting TTS synthesis for: '$text', voice: $currentVoiceName")

        val salt = UUID.randomUUID().toString()
        val curtime = (System.currentTimeMillis() / 1000).toString()
        
        // 签名 input 计算规则
        val input = if (text.length <= 20) {
            text
        } else {
            "${text.substring(0, 10)}${text.length}${text.substring(text.length - 10)}"
        }

        val signStr = appKey + input + salt + curtime + appSecret
        val sign = sha256(signStr)

        val formBody = FormBody.Builder()
            .add("q", text)
            .add("appKey", appKey)
            .add("salt", salt)
            .add("sign", sign)
            .add("signType", "v3")
            .add("curtime", curtime)
            .add("format", "mp3")
            .add("speed", "1")
            .add("volume", "1.00")
            .add("voiceName", currentVoiceName) // 使用当前选择的发音人
            .build()

        val request = Request.Builder()
            .url("https://openapi.youdao.com/ttsapi")
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "❌ TTS Request failed", e)
                ttsListener?.onTtsError(e.message ?: "TTS request failed")
            }

            override fun onResponse(call: Call, response: Response) {
                val contentType = response.header("Content-Type")
                Log.d(TAG, "🗣️ TTS Response Code: ${response.code}, Content-Type: $contentType")

                if (response.isSuccessful && contentType?.contains("audio") == true) {
                    val bytes = response.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        Log.d(TAG, "✅ TTS Audio received: ${bytes.size} bytes")
                        ttsListener?.onTtsSuccess(bytes)
                    } else {
                        Log.e(TAG, "❌ TTS Response body is empty")
                        ttsListener?.onTtsError("Empty audio response")
                    }
                } else {
                    val jsonStr = response.body?.string()
                    Log.e(TAG, "❌ TTS Error response: $jsonStr")
                    ttsListener?.onTtsError(jsonStr ?: "Unknown error")
                }
            }
        })
    }

    /**
     * SHA-256 加密
     */
    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * 识别结果监听器
     */
    interface TranscriptionListener {
        fun onTranscriptionReceived(text: String, isFinal: Boolean)
        fun onError(error: String)
    }

    /**
     * TTS 结果监听器
     */
    interface TtsListener {
        fun onTtsSuccess(audioData: ByteArray)
        fun onTtsError(error: String)
    }

    companion object {
        private const val TAG = "YoudaoWebSocket"
    }
}
