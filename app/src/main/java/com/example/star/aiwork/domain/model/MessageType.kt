package com.example.star.aiwork.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class MessageType {
    TEXT,
    IMAGE,
    AUDIO,
    SYSTEM
}
