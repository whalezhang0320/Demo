package com.example.star.aiwork.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.star.aiwork.domain.model.DraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftDao {
    @Upsert
    suspend fun upsertDraft(draft: DraftEntity)

    @Query("SELECT * FROM drafts WHERE sessionId = :sessionId")
    suspend fun getDraft(sessionId: String): DraftEntity?

    @Query("SELECT * FROM drafts WHERE sessionId = :sessionId")
    fun observeDraft(sessionId: String): Flow<DraftEntity?>

    @Query("DELETE FROM drafts WHERE sessionId = :sessionId")
    suspend fun deleteDraft(sessionId: String)
}
