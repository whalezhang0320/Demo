package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.data.local.datasource.DraftLocalDataSource
import com.example.star.aiwork.data.local.datasource.MessageLocalDataSource
import com.example.star.aiwork.data.local.datasource.SessionLocalDataSource

class DeleteSessionUseCase(
    private val sessionDataSource: SessionLocalDataSource,
    private val messageDataSource: MessageLocalDataSource,
    private val draftDataSource: DraftLocalDataSource
) {
    suspend operator fun invoke(sessionId: String) {
        messageDataSource.deleteMessagesBySession(sessionId)
        draftDataSource.deleteDraft(sessionId)
        sessionDataSource.deleteSession(sessionId)
    }
}
