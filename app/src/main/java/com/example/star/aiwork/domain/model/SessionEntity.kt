package com.example.star.aiwork.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val id: String = "",
    val name: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val pinned: Boolean = false,               // 是否置顶
    val archived: Boolean = false,             // 是否归档
    val metadata: SessionMetadata = SessionMetadata()
)
