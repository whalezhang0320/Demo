package com.example.star.aiwork.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "knowledge_chunks")
data class KnowledgeChunk(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sourceFilename: String, // 来源文件名
    val content: String,        // 切片内容
    val createdAt: Long = System.currentTimeMillis()
)
