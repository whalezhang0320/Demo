package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.data.local.datasource.SessionLocalDataSource
import com.example.star.aiwork.domain.model.SessionEntity
import kotlinx.coroutines.flow.Flow

class GetSessionListUseCase(
    private val dataSource: SessionLocalDataSource
) {
    operator fun invoke(): Flow<List<SessionEntity>> = dataSource.observeSessions()
}
