package com.example.star.aiwork.domain.usecase

import com.example.star.aiwork.data.repository.AiRepository
import com.example.star.aiwork.domain.ImageGenerationParams
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.ui.ai.ImageGenerationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageGenerationUseCase(
    private val aiRepository: AiRepository
) {
    suspend operator fun invoke(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams
    ): Result<ImageGenerationResult> = withContext(Dispatchers.IO) {
        runCatching {
            aiRepository.generateImage(providerSetting, params)
        }
    }
}
