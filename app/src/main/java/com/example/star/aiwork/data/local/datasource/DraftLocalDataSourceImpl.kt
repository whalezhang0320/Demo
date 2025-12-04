package com.example.star.aiwork.data.local.datasource

import android.content.Context
import com.example.star.aiwork.data.local.DatabaseProvider
import com.example.star.aiwork.domain.model.DraftEntity
import kotlinx.coroutines.flow.Flow

class DraftLocalDataSourceImpl(context: Context) : DraftLocalDataSource {

    private val draftDao = DatabaseProvider.getDatabase(context).draftDao()

    override suspend fun upsertDraft(draft: DraftEntity) {
        draftDao.upsertDraft(draft)
    }

    override suspend fun getDraft(sessionId: String): DraftEntity? {
        return draftDao.getDraft(sessionId)
    }

    override fun observeDraft(sessionId: String): Flow<DraftEntity?> {
        return draftDao.observeDraft(sessionId)
    }

    override suspend fun deleteDraft(sessionId: String) {
        draftDao.deleteDraft(sessionId)
    }
}
