package com.example.star.aiwork.domain.model

import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "sessions_fts")
@Fts4(contentEntity = SessionEntity::class)
data class SessionFtsEntity(
    val name: String
)
