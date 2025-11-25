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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.star.aiwork.data.provider.OpenAIProvider
import com.example.star.aiwork.domain.model.ProviderSetting
import kotlinx.coroutines.launch
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model & Key Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val newProvider = ProviderSetting.OpenAI(
                            id = UUID.randomUUID().toString(),
                            name = "New Provider",
                            apiKey = "",
                            baseUrl = "https://api.openai.com/v1"
                        )
                        onUpdateSettings(providerSettings + newProvider)
                    }) {
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
                    onSelectModel = { modelId ->
                        onSelectModel(provider.id, modelId)
                    }
                )
            }
        }
    }
}

@Composable
fun ProviderCard(
    provider: ProviderSetting,
    activeProviderId: String?,
    activeModelId: String?,
    onUpdate: (ProviderSetting) -> Unit,
    onDelete: () -> Unit,
    onSelectModel: (String) -> Unit
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
                is ProviderSetting.Google -> provider.apiKey
                is ProviderSetting.Claude -> provider.apiKey
            }
        ) 
    }
    var baseUrl by remember(provider) { 
        mutableStateOf(
            when (provider) {
                is ProviderSetting.OpenAI -> provider.baseUrl
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
                    if (provider.enabled) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Enabled",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(16.dp)
                        )
                    }
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
            
            // Always show models if expanded, or if it's the active provider (optional, maybe just stick to expanded)
            // Let's just use expanded for details.

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
                                            else -> provider
                                        }

                                        if (tempSetting is ProviderSetting.OpenAI) {
                                            val client = OkHttpClient()
                                            val openAIProvider = OpenAIProvider(client)
                                            val models = openAIProvider.listModels(tempSetting)
                                            
                                            onUpdate(tempSetting.copy(name = name, models = models))
                                            Toast.makeText(context, "Connected! Found ${models.size} models.", Toast.LENGTH_SHORT).show()
                                        } else {
                                             Toast.makeText(context, "Test not supported for this type yet.", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        Toast.makeText(context, "Connection Failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isTesting = false
                                    }
                                }
                            },
                            enabled = !isTesting
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Testing...")
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Test & Save")
                            }
                        }
                    }
                    
                    // Models List with Selection
                    if (provider.models.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Available Models:", style = MaterialTheme.typography.titleSmall)
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            provider.models.forEach { model ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectModel(model.modelId) }
                                        .padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = (provider.id == activeProviderId && model.modelId == activeModelId),
                                        onClick = { onSelectModel(model.modelId) }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(text = model.displayName, style = MaterialTheme.typography.bodyMedium)
                                        Text(text = model.modelId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
