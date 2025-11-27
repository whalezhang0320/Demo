package com.example.star.aiwork.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.star.aiwork.data.database.KnowledgeChunk
import com.example.star.aiwork.data.database.KnowledgeChunkFts
import com.example.star.aiwork.data.database.KnowledgeDao

@Database(
    entities = [KnowledgeChunk::class, KnowledgeChunkFts::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun knowledgeDao(): KnowledgeDao
}
