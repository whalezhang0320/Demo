package com.example.star.aiwork.data.database

import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "knowledge_chunks_fts")
@Fts4(contentEntity = KnowledgeChunk::class)
data class KnowledgeChunkFts(
    val content: String,
    val sourceFilename: String
)
