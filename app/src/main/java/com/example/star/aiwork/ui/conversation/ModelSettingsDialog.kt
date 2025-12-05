package com.example.star.aiwork.ui.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.star.aiwork.domain.model.ProviderSetting
import kotlin.math.roundToInt

/**
 * 配置 AI 模型设置的对话框。
 *
 * 允许用户调整：
 * - Temperature (温度，创造性 vs 精确性)
 * - Max Tokens (最大 Token 数，响应长度)
 * - Stream Response (流式响应，启用/禁用流式传输)
 * - Auto-Loop 设置
 * - Fallback Mechanism (兜底机制)
 */
@Composable
fun ModelSettingsDialog(
    uiState: ConversationUiState,
    providerSettings: List<ProviderSetting> = emptyList(),
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "模型参数设置",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 温度设置滑块
                Text(
                    text = "温度",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "精确",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format("%.1f", uiState.temperature),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "创意",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = uiState.temperature,
                    onValueChange = { uiState.temperature = it },
                    valueRange = 0f..2f,
                    steps = 19,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 最大 Token 数设置滑块
                Text(
                    text = "最大 Tokens",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                 Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Short",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${uiState.maxTokens}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Long",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = uiState.maxTokens.toFloat(),
                    onValueChange = { uiState.maxTokens = it.roundToInt() },
                    valueRange = 100f..4096f,
                    steps = 39, // (4096-100)/100 约等于 40 步
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 流式响应开关
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "流式输出",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = uiState.streamResponse,
                        onCheckedChange = { uiState.streamResponse = it }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                HorizontalDivider()
                
                Spacer(modifier = Modifier.height(24.dp))

                // 兜底机制开关
                Text(
                    text = "错误兜底机制",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "出错时自动切换模型重试",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = uiState.isFallbackEnabled,
                        onCheckedChange = { uiState.isFallbackEnabled = it }
                    )
                }

                // Fallback Model Selection
                if (uiState.isFallbackEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "兜底模型 (Fallback Model)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "当主模型调用失败时，将尝试使用此模型",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Provider Selection for Fallback
                    var expandedFallbackProvider by remember { mutableStateOf(false) }
                    val selectedFallbackProviderId = uiState.fallbackProviderId
                    val selectedFallbackProvider = providerSettings.find { it.id == selectedFallbackProviderId }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Fallback Provider:", style = MaterialTheme.typography.bodyMedium)
                    Box(modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.TopStart)) {
                        OutlinedButton(onClick = { expandedFallbackProvider = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedFallbackProvider?.name ?: "默认本地ollama")
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = expandedFallbackProvider,
                            onDismissRequest = { expandedFallbackProvider = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("默认本地ollama") },
                                onClick = {
                                    uiState.fallbackProviderId = null
                                    uiState.fallbackModelId = null
                                    expandedFallbackProvider = false
                                }
                            )
                            providerSettings.filter { it.enabled }.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.name) },
                                    onClick = {
                                        uiState.fallbackProviderId = provider.id
                                        // Reset model when provider changes
                                        uiState.fallbackModelId = provider.models.firstOrNull()?.modelId
                                        expandedFallbackProvider = false
                                    }
                                )
                            }
                        }
                    }

                    // Model Selection for Fallback
                    if (selectedFallbackProvider != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Fallback Model:", style = MaterialTheme.typography.bodyMedium)
                        
                        var expandedFallbackModel by remember { mutableStateOf(false) }
                        val selectedFallbackModelId = uiState.fallbackModelId
                        val selectedFallbackModel = selectedFallbackProvider.models.find { it.modelId == selectedFallbackModelId }
                        
                        Box(modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.TopStart)) {
                             OutlinedButton(onClick = { expandedFallbackModel = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(selectedFallbackModel?.displayName ?: selectedFallbackModelId ?: "Select Model")
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = expandedFallbackModel,
                                onDismissRequest = { expandedFallbackModel = false }
                            ) {
                                selectedFallbackProvider.models.forEach { model ->
                                     DropdownMenuItem(
                                        text = { Text(model.displayName.ifBlank { model.modelId }) },
                                        onClick = {
                                            uiState.fallbackModelId = model.modelId
                                            expandedFallbackModel = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                // Auto-Loop 开关
                Text(
                    text = "自动连续追问",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "开启自动连续追问",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = uiState.isAutoLoopEnabled,
                        onCheckedChange = { uiState.isAutoLoopEnabled = it }
                    )
                }

                // Max Loop Count 滑块
                if (uiState.isAutoLoopEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                         Text(
                            text = "Max Loops: ${uiState.maxLoopCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Slider(
                        value = uiState.maxLoopCount.toFloat(),
                        onValueChange = { uiState.maxLoopCount = it.roundToInt() },
                        valueRange = 1f..10f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Planner Model (执行规划的模型)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "如果不选，默认使用当前聊天的模型",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Provider Selection for Auto-Loop
                    var expandedProvider by remember { mutableStateOf(false) }
                    val selectedProviderId = uiState.autoLoopProviderId
                    val selectedProvider = providerSettings.find { it.id == selectedProviderId }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Planner Provider:", style = MaterialTheme.typography.bodyMedium)
                    Box(modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.TopStart)) {
                        OutlinedButton(onClick = { expandedProvider = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedProvider?.name ?: "Default (Current Chat Provider)")
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = expandedProvider,
                            onDismissRequest = { expandedProvider = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Default (Current Chat Provider)") },
                                onClick = {
                                    uiState.autoLoopProviderId = null
                                    uiState.autoLoopModelId = null
                                    expandedProvider = false
                                }
                            )
                            providerSettings.filter { it.enabled }.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.name) },
                                    onClick = {
                                        uiState.autoLoopProviderId = provider.id
                                        // Reset model when provider changes
                                        uiState.autoLoopModelId = provider.models.firstOrNull()?.modelId
                                        expandedProvider = false
                                    }
                                )
                            }
                        }
                    }

                    // Model Selection for Auto-Loop
                    if (selectedProvider != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Planner Model:", style = MaterialTheme.typography.bodyMedium)
                        
                        var expandedModel by remember { mutableStateOf(false) }
                        val selectedModelId = uiState.autoLoopModelId
                        val selectedModel = selectedProvider.models.find { it.modelId == selectedModelId }
                        
                        Box(modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.TopStart)) {
                             OutlinedButton(onClick = { expandedModel = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(selectedModel?.displayName ?: selectedModelId ?: "Select Model")
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = expandedModel,
                                onDismissRequest = { expandedModel = false }
                            ) {
                                selectedProvider.models.forEach { model ->
                                     DropdownMenuItem(
                                        text = { Text(model.displayName.ifBlank { model.modelId }) },
                                        onClick = {
                                            uiState.autoLoopModelId = model.modelId
                                            expandedModel = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text("确认")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}
