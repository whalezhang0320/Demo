package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.data.local.datasource.SessionLocalDataSource
import kotlinx.coroutines.flow.map
class GetSessionByNameUseCase(
    private val dataSource: SessionLocalDataSource
) {
    operator fun invoke(name: String) =
        dataSource.observeSessions().map { list ->
            list.firstOrNull { it.name == name }
        }
}
