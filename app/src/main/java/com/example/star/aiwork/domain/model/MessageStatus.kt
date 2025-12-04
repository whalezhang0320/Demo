package com.example.star.aiwork.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class MessageStatus {
    SENDING,        // 用户消息发送中
    STREAMING,      // Assistant 正在流式回复
    DONE,           // 结束
    ERROR,          // 出错
    CANCELLED       // 已取消
}
