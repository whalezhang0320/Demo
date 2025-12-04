package com.example.star.aiwork.data.local.datasource

import com.example.star.aiwork.domain.model.DraftEntity
import kotlinx.coroutines.flow.Flow

interface DraftLocalDataSource {

    suspend fun upsertDraft(draft: DraftEntity)

    suspend fun getDraft(sessionId: String): DraftEntity?

    fun observeDraft(sessionId: String): Flow<DraftEntity?>

    suspend fun deleteDraft(sessionId: String)
}
