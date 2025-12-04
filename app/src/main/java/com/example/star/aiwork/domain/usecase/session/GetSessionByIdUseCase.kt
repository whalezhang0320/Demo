package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.data.local.datasource.SessionLocalDataSource
import com.example.star.aiwork.domain.model.SessionEntity

class GetSessionByIdUseCase(
    private val dataSource: SessionLocalDataSource
) {
    suspend operator fun invoke(id: String): SessionEntity? = dataSource.getSession(id)
}
