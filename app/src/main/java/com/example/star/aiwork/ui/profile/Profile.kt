/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.star.aiwork.ui.profile

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.star.aiwork.data.provider.GoogleProvider
import com.example.star.aiwork.data.provider.OllamaProvider
import com.example.star.aiwork.data.provider.OpenAIProvider
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ProviderSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.UUID

/**
 * 模型与密钥设置页面。
 * 替代原有的个人资料页面，用于管理 AI 提供商、API Key 和模型。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    providerSettings: List<ProviderSetting>,
    activeProviderId: String?,
    activeModelId: String?,
    onUpdateSettings: (List<ProviderSetting>) -> Unit,
    onSelectModel: (String, String) -> Unit,
    onBack: () -> Unit = {}
) {
    var showAddProviderDialog by remember { mutableStateOf(false) }
    var modelSelectionProvider by remember { mutableStateOf<ProviderSetting?>(null) }

    if (showAddProviderDialog) {
        AddProviderDialog(
            onDismiss = { showAddProviderDialog = false },
            onAdd = { type ->
                val newProvider = when (type) {
                    "OpenAI" -> ProviderSetting.OpenAI(
                        id = UUID.randomUUID().toString(),
                        name = "New OpenAI",
                        apiKey = "",
                        baseUrl = "https://api.openai.com/v1"
                    )
                    "Ollama" -> ProviderSetting.Ollama(
                        id = UUID.randomUUID().toString(),
                        name = "New Ollama",
                        apiKey = "ollama",
                        baseUrl = "http://localhost:11434",
                        chatCompletionsPath = "/api/chat"
                    )
                    "Google" -> ProviderSetting.Google(
                        id = UUID.randomUUID().toString(),
                        name = "New Google",
                        apiKey = "",
                        baseUrl = "https://generativelanguage.googleapis.com/v1beta"
                    )
                    else -> null
                }
                if (newProvider != null) {
                    onUpdateSettings(providerSettings + newProvider)
                }
                showAddProviderDialog = false
            }
        )
    }

    if (modelSelectionProvider != null) {
        ModelListDialog(
            provider = modelSelectionProvider!!,
            activeModelId = if (modelSelectionProvider!!.id == activeProviderId) activeModelId else null,
            onDismiss = { modelSelectionProvider = null },
            onSelect = { modelId ->
                onSelectModel(modelSelectionProvider!!.id, modelId)
                modelSelectionProvider = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型 & 密钥 设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddProviderDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Provider")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(providerSettings, key = { it.id }) { provider ->
                ProviderCard(
                    provider = provider,
                    activeProviderId = activeProviderId,
                    activeModelId = activeModelId,
                    onUpdate = { updated ->
                        onUpdateSettings(providerSettings.map { if (it.id == provider.id) updated else it })
                    },
                    onDelete = {
                        onUpdateSettings(providerSettings.filter { it.id != provider.id })
                    },
                    onOpenModelSelection = {
                        modelSelectionProvider = provider
                    }
                )
            }
        }
    }
}

@Composable
fun AddProviderDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择提供商类型") },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text("OpenAI Compatible") },
                    modifier = Modifier.clickable { onAdd("OpenAI") }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Ollama (Local)") },
                    modifier = Modifier.clickable { onAdd("Ollama") }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Google (Gemini)") },
                    modifier = Modifier.clickable { onAdd("Google") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ModelListDialog(
    provider: ProviderSetting,
    activeModelId: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val groupedModelsState = produceState<Map<String, List<Model>>?>(initialValue = null, provider.models, searchQuery) {
        value = withContext(Dispatchers.Default) {
            val filtered = if (searchQuery.isBlank()) provider.models
            else provider.models.filter { 
                it.displayName.contains(searchQuery, ignoreCase = true) || 
                it.modelId.contains(searchQuery, ignoreCase = true) 
            }
            filtered.groupBy { getModelGroup(it.modelId) }.toSortedMap()
        }
    }
    
    val groupedModels = groupedModelsState.value

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false), // Make it wider
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .fillMaxHeight(0.85f),
        title = {
            Column {
                Text("选择模型 (${provider.name})", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索模型...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        text = {
            Column {
                if (groupedModels == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("加载模型中...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else if (groupedModels.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("未找到模型", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        groupedModels.forEach { (group, models) ->
                            item(key = "header_$group") {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = group,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            
                            items(models, key = { it.modelId }) { model ->
                                val isSelected = model.modelId == activeModelId
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelect(model.modelId) }
                                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                                        .padding(vertical = 8.dp, horizontal = 8.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { onSelect(model.modelId) },
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = model.displayName.ifBlank { model.modelId },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (model.displayName.isNotBlank() && model.displayName != model.modelId) {
                                            Text(
                                                text = model.modelId,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun ProviderCard(
    provider: ProviderSetting,
    activeProviderId: String?,
    activeModelId: String?,
    onUpdate: (ProviderSetting) -> Unit,
    onDelete: () -> Unit,
    onOpenModelSelection: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Edit states
    var name by remember(provider.name) { mutableStateOf(provider.name) }
    var apiKey by remember(provider) { 
        mutableStateOf(
            when (provider) {
                is ProviderSetting.OpenAI -> provider.apiKey
                is ProviderSetting.Ollama -> provider.apiKey
                is ProviderSetting.Google -> provider.apiKey
                is ProviderSetting.Claude -> provider.apiKey
            }
        ) 
    }
    var baseUrl by remember(provider) { 
        mutableStateOf(
            when (provider) {
                is ProviderSetting.OpenAI -> provider.baseUrl
                is ProviderSetting.Ollama -> provider.baseUrl
                is ProviderSetting.Google -> provider.baseUrl
                is ProviderSetting.Claude -> provider.baseUrl
            }
        )
    }

    var isTesting by remember { mutableStateOf(false) }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Removed the Icon(Icons.Default.Check, ...) block as requested
                    
                    // Show active model badge if applicable
                    if (provider.id == activeProviderId) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text("Active", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
                Row {
                    Switch(
                        checked = provider.enabled,
                        onCheckedChange = { enabled ->
                            onUpdate(
                                when (provider) {
                                    is ProviderSetting.OpenAI -> provider.copy(enabled = enabled)
                                    is ProviderSetting.Ollama -> provider.copy(enabled = enabled)
                                    is ProviderSetting.Google -> provider.copy(enabled = enabled)
                                    is ProviderSetting.Claude -> provider.copy(enabled = enabled)
                                }
                            )
                        }
                    )
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand"
                        )
                    }
                }
            }
            
            // Details Section
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    // Edit fields
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Actions
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (!provider.builtIn) {
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(48.dp))
                        }

                        Button(
                            onClick = {
                                isTesting = true
                                scope.launch {
                                    try {
                                        val tempSetting = when (provider) {
                                            is ProviderSetting.OpenAI -> provider.copy(apiKey = apiKey, baseUrl = baseUrl)
                                            is ProviderSetting.Ollama -> provider.copy(apiKey = apiKey, baseUrl = baseUrl)
                                            is ProviderSetting.Google -> provider.copy(apiKey = apiKey, baseUrl = baseUrl)
                                            is ProviderSetting.Claude -> provider.copy(apiKey = apiKey, baseUrl = baseUrl)
                                        }

                                        if (tempSetting is ProviderSetting.OpenAI) {
                                            val client = OkHttpClient()
                                            val openAIProvider = OpenAIProvider(client)
                                            val models = openAIProvider.listModels(tempSetting)
                                            
                                            onUpdate(tempSetting.copy(name = name, models = models))
                                            Toast.makeText(context, "已刷新! 发现 ${models.size} 个模型", Toast.LENGTH_SHORT).show()
                                        } else if (tempSetting is ProviderSetting.Ollama) {
                                            val client = OkHttpClient()
                                            val ollamaProvider = OllamaProvider(client)
                                            val models = ollamaProvider.listModels(tempSetting)
                                            
                                            onUpdate(tempSetting.copy(name = name, models = models))
                                            Toast.makeText(context, "已刷新! 发现 ${models.size} 个模型", Toast.LENGTH_SHORT).show()
                                        } else if (tempSetting is ProviderSetting.Google) {
                                            val client = OkHttpClient()
                                            val googleProvider = GoogleProvider(client)
                                            val models = googleProvider.listModels(tempSetting)
                                            
                                            onUpdate(tempSetting.copy(name = name, models = models))
                                            Toast.makeText(context, "已刷新! 添加了 Gemini 模型", Toast.LENGTH_SHORT).show()
                                        } else {
                                             Toast.makeText(context, "Test not supported for this type yet.", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        Toast.makeText(context, "连接失败: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isTesting = false
                                    }
                                }
                            },
                            // Disabled if testing OR provider is NOT enabled
                            enabled = !isTesting && provider.enabled
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("测试中...")
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("测试 & 保存")
                            }
                        }
                    }
                    
                    // Hide model selection if disabled
                    if (provider.enabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        // Model Selection Trigger
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text("模型列表", style = MaterialTheme.typography.titleMedium)
                                Text("${provider.models.size} 个模型可用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                
                                if (provider.id == activeProviderId && !activeModelId.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("当前: $activeModelId", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            
                            OutlinedButton(onClick = onOpenModelSelection) {
                                Icon(Icons.Default.List, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("选择模型")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getModelGroup(modelId: String): String {
    if (modelId.contains(":")) {
        return modelId.substringBefore(":").uppercase()
    }
    val parts = modelId.split("-")
    if (parts.isEmpty()) return modelId.uppercase()

    val first = parts[0]
    if (parts.size >= 2) {
        val second = parts[1]
        if (second.isNotEmpty() && second[0].isDigit()) {
            return first.uppercase()
        }
        if (first.equals("gpt", ignoreCase = true)) {
            return "$first-$second".uppercase()
        }
    }
    return first.uppercase()
}
