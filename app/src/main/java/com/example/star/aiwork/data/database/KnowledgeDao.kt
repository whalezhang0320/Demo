package com.example.star.aiwork.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface KnowledgeDao {
    @Insert
    suspend fun insertChunks(chunks: List<KnowledgeChunk>)

    // 核心：利用 BM25 算法（FTS 标准）进行相关性搜索
    // matchInfo 用于排序，snippet 用于预览（可选）
    @Query("""
        SELECT * FROM knowledge_chunks 
        JOIN knowledge_chunks_fts ON knowledge_chunks.id = knowledge_chunks_fts.docid 
        WHERE knowledge_chunks_fts MATCH :query 
        LIMIT 5
    """)
    suspend fun search(query: String): List<KnowledgeChunk>
    
    @Query("DELETE FROM knowledge_chunks")
    suspend fun clearAll()
}
