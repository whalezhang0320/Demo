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

package com.example.star.aiwork.ui.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.star.aiwork.R
import com.example.star.aiwork.domain.model.SessionEntity
import com.example.star.aiwork.ui.FunctionalityNotAvailablePopup
import com.example.star.aiwork.ui.components.JetchatAppBar
import com.example.star.aiwork.ui.theme.JetchatTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelNameBar(
    channelName: String,
    channelMembers: Int,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onNavIconPressed: () -> Unit = { },
    onSettingsClicked: () -> Unit = { },
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    searchResults: List<SessionEntity>,
    onSessionSelected: (SessionEntity) -> Unit
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var functionalityNotAvailablePopupShown by remember { mutableStateOf(false) }
    if (functionalityNotAvailablePopupShown) {
        FunctionalityNotAvailablePopup { functionalityNotAvailablePopupShown = false }
    }

    if (isSearchActive) {
        SearchAppBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChanged,
            searchResults = searchResults,
            onSessionSelected = {
                onSessionSelected(it)
                isSearchActive = false
            },
            onCloseSearch = { isSearchActive = false }
        )
    } else {
        JetchatAppBar(
            modifier = modifier,
            scrollBehavior = scrollBehavior,
            onNavIconPressed = onNavIconPressed,
            title = {
                Text(
                    text = channelName,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            actions = {
                // 设置图标
                IconButton(onClick = onSettingsClicked) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescription = "Settings"
                    )
                }
                // 搜索图标
                IconButton(onClick = { isSearchActive = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescription = stringResource(id = R.string.search)
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAppBar(
    query: String,
    onQueryChange: (String) -> Unit,
    searchResults: List<SessionEntity>,
    onSessionSelected: (SessionEntity) -> Unit,
    onCloseSearch: () -> Unit
) {
    var isDropdownExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            ExposedDropdownMenuBox(expanded = isDropdownExpanded, onExpandedChange = { isDropdownExpanded = !it }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        onQueryChange(it)
                        isDropdownExpanded = it.isNotEmpty()
                    },
                    placeholder = { Text("搜索会话...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = isDropdownExpanded && searchResults.isNotEmpty(),
                    onDismissRequest = { isDropdownExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    searchResults.forEach { session ->
                        DropdownMenuItem(
                            text = { Text(session.name) },
                            onClick = {
                                onSessionSelected(session)
                                isDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onCloseSearch) {
                Icon(Icons.Default.ArrowBack, contentDescription = "关闭搜索")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun ChannelBarPrev() {
    JetchatTheme {
        ChannelNameBar(
            channelName = "composers",
            channelMembers = 52,
            searchQuery = "",
            onSearchQueryChanged = {},
            searchResults = emptyList(),
            onSessionSelected = {}
        )
    }
}