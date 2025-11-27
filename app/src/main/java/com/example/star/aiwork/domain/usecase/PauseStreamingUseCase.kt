package com.example.star.aiwork.domain.usecase

import com.example.star.aiwork.data.repository.AiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 停止当前流式会话的用例。
 */
class PauseStreamingUseCase(
    private val aiRepository: AiRepository
) {
    suspend operator fun invoke(taskId: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext runCatching {
            aiRepository.cancelStreaming(taskId)
        }
    }
}

