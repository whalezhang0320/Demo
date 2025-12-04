package com.example.star.aiwork.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey
    val sessionId: String = "",
    val content: String = "",
    val updatedAt: Long = 0L,
)
