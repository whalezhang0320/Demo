package com.example.star.aiwork.domain.model

import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "messages_fts")
@Fts4(contentEntity = MessageEntity::class)
data class MessageFtsEntity(
    val content: String
)
