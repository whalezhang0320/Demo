package com.example.star.aiwork.domain.model

import androidx.compose.runtime.Composable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID

/**
 * 提供商设置 (Provider Setting)。
 * 定义 AI 提供商的配置信息，包括认证、API 地址、模型列表等。
 * 这是一个密封类，具体的提供商实现（如 OpenAI, Google）继承自此类。
 *
 * @property id 提供商设置的唯一标识。
 * @property enabled 是否启用该提供商。
 * @property name 提供商显示名称。
 * @property models 该提供商下配置的模型列表。
 * @property proxy 网络代理设置。
 * @property balanceOption 余额查询设置。
 * @property builtIn 是否为内置提供商（不可删除）。
 * @property description 详细描述（Composable UI）。
 * @property shortDescription 简短描述（Composable UI）。
 */
@Serializable
sealed class ProviderSetting {
    abstract val id: String
    abstract val enabled: Boolean
    abstract val name: String
    abstract val models: List<Model>
    abstract val proxy: ProviderProxy
    abstract val balanceOption: BalanceOption

    abstract val builtIn: Boolean
    @Transient open val description: @Composable () -> Unit = {}
    @Transient open val shortDescription: @Composable () -> Unit = {}

    abstract fun addModel(model: Model): ProviderSetting
    abstract fun editModel(model: Model): ProviderSetting
    abstract fun delModel(model: Model): ProviderSetting
    abstract fun moveMove(from: Int, to: Int): ProviderSetting
    abstract fun copyProvider(
        id: String = this.id,
        enabled: Boolean = this.enabled,
        name: String = this.name,
        models: List<Model> = this.models,
        proxy: ProviderProxy = this.proxy,
        balanceOption: BalanceOption = this.balanceOption,
        builtIn: Boolean = this.builtIn,
        description: @Composable (() -> Unit) = this.description,
        shortDescription: @Composable (() -> Unit) = this.shortDescription,
    ): ProviderSetting

    interface OpenAICompatible {
        val id: String
        val apiKey: String
        val baseUrl: String
        val chatCompletionsPath: String
        val proxy: ProviderProxy
    }

    /**
     * OpenAI 兼容提供商设置。
     * 适用于官方 OpenAI API 以及所有兼容 OpenAI 接口格式的第三方服务（如 DeepSeek, Moonshot 等）。
     *
     * @property apiKey API 密钥。
     * @property baseUrl API 基础地址。
     * @property chatCompletionsPath 聊天接口路径（默认为 /chat/completions）。
     * @property useResponseApi 是否使用 Response 格式解析（某些非标接口可能需要）。
     */
    @Serializable
    @SerialName("openai")
    data class OpenAI(
        override var id: String = UUID.randomUUID().toString(),
        override var enabled: Boolean = true,
        override var name: String = "OpenAI",
        override var models: List<Model> = emptyList(),
        override var proxy: ProviderProxy = ProviderProxy.None,
        override val balanceOption: BalanceOption = BalanceOption(),
        @Transient override val builtIn: Boolean = false,
        @Transient override val description: @Composable (() -> Unit) = {},
        @Transient override val shortDescription: @Composable (() -> Unit) = {},
        override var apiKey: String = "",
        override var baseUrl: String = "https://api.openai.com/v1",
        override var chatCompletionsPath: String = "/chat/completions",
        var useResponseApi: Boolean = false,
    ) : ProviderSetting(), OpenAICompatible {
        override fun addModel(model: Model): ProviderSetting {
            return copy(models = models + model)
        }

        override fun editModel(model: Model): ProviderSetting {
            return copy(models = models.map { if (it.id == model.id) model.copy() else it })
        }

        override fun delModel(model: Model): ProviderSetting {
            return copy(models = models.filter { it.id != model.id })
        }

        override fun moveMove(
            from: Int,
            to: Int
        ): ProviderSetting {
            return copy(models = models.toMutableList().apply {
                val model = removeAt(from)
                add(to, model)
            })
        }

        override fun copyProvider(
            id: String,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            proxy: ProviderProxy,
            balanceOption: BalanceOption,
            builtIn: Boolean,
            description: @Composable (() -> Unit),
            shortDescription: @Composable (() -> Unit),
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models,
                builtIn = builtIn,
                description = description,
                proxy = proxy,
                balanceOption = balanceOption,
                shortDescription = shortDescription
            )
        }
    }

    /**
     * Ollama 提供商设置。
     * 专用于接入本地 Ollama 服务。
     */
    @Serializable
    @SerialName("ollama")
    data class Ollama(
        override var id: String = UUID.randomUUID().toString(),
        override var enabled: Boolean = true,
        override var name: String = "Ollama",
        override var models: List<Model> = emptyList(),
        override var proxy: ProviderProxy = ProviderProxy.None,
        override val balanceOption: BalanceOption = BalanceOption(),
        @Transient override val builtIn: Boolean = false,
        @Transient override val description: @Composable (() -> Unit) = {},
        @Transient override val shortDescription: @Composable (() -> Unit) = {},
        override var apiKey: String = "ollama", // Ollama typically doesn't require a key, but some libs expect one
        override var baseUrl: String = "http://172.16.48.147:8080", // Android Emulator loopback address
        override var chatCompletionsPath: String = "/api/chat",
    ) : ProviderSetting(), OpenAICompatible {
        override fun addModel(model: Model): ProviderSetting {
            return copy(models = models + model)
        }

        override fun editModel(model: Model): ProviderSetting {
            return copy(models = models.map { if (it.id == model.id) model.copy() else it })
        }

        override fun delModel(model: Model): ProviderSetting {
            return copy(models = models.filter { it.id != model.id })
        }

        override fun moveMove(
            from: Int,
            to: Int
        ): ProviderSetting {
            return copy(models = models.toMutableList().apply {
                val model = removeAt(from)
                add(to, model)
            })
        }

        override fun copyProvider(
            id: String,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            proxy: ProviderProxy,
            balanceOption: BalanceOption,
            builtIn: Boolean,
            description: @Composable (() -> Unit),
            shortDescription: @Composable (() -> Unit),
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models,
                builtIn = builtIn,
                description = description,
                shortDescription = shortDescription,
                proxy = proxy,
                balanceOption = balanceOption
            )
        }
    }

    /**
     * Google (Gemini/Vertex AI) 提供商设置。
     * 支持 Google AI Studio (Gemini API) 和 Google Cloud Vertex AI。
     *
     * @property apiKey Google AI Studio 的 API Key。
     * @property baseUrl 基础地址。
     * @property vertexAI 是否启用 Vertex AI 模式。
     * @property privateKey Vertex AI 私钥。
     * @property serviceAccountEmail Vertex AI 服务账号邮箱。
     * @property location Vertex AI 区域 (如 us-central1)。
     * @property projectId Google Cloud 项目 ID。
     */
    @Serializable
    @SerialName("google")
    data class Google(
        override var id: String = UUID.randomUUID().toString(),
        override var enabled: Boolean = true,
        override var name: String = "Google",
        override var models: List<Model> = emptyList(),
        override var proxy: ProviderProxy = ProviderProxy.None,
        override val balanceOption: BalanceOption = BalanceOption(),
        @Transient override val builtIn: Boolean = false,
        @Transient override val description: @Composable (() -> Unit) = {},
        @Transient override val shortDescription: @Composable (() -> Unit) = {},
        var apiKey: String = "",
        var baseUrl: String = "https://generativelanguage.googleapis.com/v1beta", // 仅用于 Google AI
        var vertexAI: Boolean = false,
        var privateKey: String = "", // 仅用于 Vertex AI
        var serviceAccountEmail: String = "", // 仅用于 Vertex AI
        var location: String = "us-central1", // 仅用于 Vertex AI
        var projectId: String = "", // 仅用于 Vertex AI
    ) : ProviderSetting() {
        override fun addModel(model: Model): ProviderSetting {
            return copy(models = models + model)
        }

        override fun editModel(model: Model): ProviderSetting {
            return copy(models = models.map { if (it.id == model.id) model.copy() else it })
        }

        override fun delModel(model: Model): ProviderSetting {
            return copy(models = models.filter { it.id != model.id })
        }

        override fun moveMove(
            from: Int,
            to: Int
        ): ProviderSetting {
            return copy(models = models.toMutableList().apply {
                val model = removeAt(from)
                add(to, model)
            })
        }

        override fun copyProvider(
            id: String,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            proxy: ProviderProxy,
            balanceOption: BalanceOption,
            builtIn: Boolean,
            description: @Composable (() -> Unit),
            shortDescription: @Composable (() -> Unit),
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models,
                builtIn = builtIn,
                description = description,
                shortDescription = shortDescription,
                proxy = proxy,
                balanceOption = balanceOption
            )
        }
    }

    /**
     * Anthropic (Claude) 提供商设置。
     * 专用于接入 Claude API。
     */
    @Serializable
    @SerialName("claude")
    data class Claude(
        override var id: String = UUID.randomUUID().toString(),
        override var enabled: Boolean = true,
        override var name: String = "Claude",
        override var models: List<Model> = emptyList(),
        override var proxy: ProviderProxy = ProviderProxy.None,
        override val balanceOption: BalanceOption = BalanceOption(),
        @Transient override val builtIn: Boolean = false,
        @Transient override val description: @Composable (() -> Unit) = {},
        @Transient override val shortDescription: @Composable (() -> Unit) = {},
        var apiKey: String = "",
        var baseUrl: String = "https://api.anthropic.com/v1",
    ) : ProviderSetting() {
        override fun addModel(model: Model): ProviderSetting {
            return copy(models = models + model)
        }

        override fun editModel(model: Model): ProviderSetting {
            return copy(models = models.map { if (it.id == model.id) model.copy() else it })
        }

        override fun delModel(model: Model): ProviderSetting {
            return copy(models = models.filter { it.id != model.id })
        }

        override fun moveMove(
            from: Int,
            to: Int
        ): ProviderSetting {
            return copy(models = models.toMutableList().apply {
                val model = removeAt(from)
                add(to, model)
            })
        }

        override fun copyProvider(
            id: String,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            proxy: ProviderProxy,
            balanceOption: BalanceOption,
            builtIn: Boolean,
            description: @Composable (() -> Unit),
            shortDescription: @Composable (() -> Unit),
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models,
                proxy = proxy,
                balanceOption = balanceOption,
                builtIn = builtIn,
                description = description,
                shortDescription = shortDescription,
            )
        }
    }

    companion object {
        val Types by lazy {
            listOf(
                OpenAI::class,
                Ollama::class,
                Google::class,
                Claude::class,
            )
        }
    }
}
