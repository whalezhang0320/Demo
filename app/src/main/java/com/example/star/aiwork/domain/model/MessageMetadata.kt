package com.example.star.aiwork.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MessageMetadata(
    val localFilePath: String? = null,     // 图片 / 音频 本地路径
    val remoteUrl: String? = null,         // 上传后的远端地址
    val modelName: String? = null,         // 使用模型
    val tokenUsage: Int? = null,           // token 数
    val errorInfo: String? = null          // 错误消息
)
