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

package com.example.star.aiwork.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterStart
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.star.aiwork.R
import com.example.star.aiwork.data.colleagueProfile
import com.example.star.aiwork.data.meProfile
import com.example.star.aiwork.domain.model.Agent
import com.example.star.aiwork.domain.model.SessionEntity
import com.example.star.aiwork.ui.theme.JetchatTheme

/**
 * Jetchat 的模态导航抽屉 (Modal Navigation Drawer)。
 *
 * @param drawerState 抽屉的状态 (打开/关闭)。
 * @param onChatClicked 聊天项点击回调。
 * @param onProfileClicked 个人资料项点击回调。
 * @param content 抽屉关闭时显示的主要内容 (Scaffold)。
 */
@Composable
fun JetchatDrawer(
    drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed),
    onChatClicked: (String) -> Unit,
    onProfileClicked: (String) -> Unit,
    onAgentClicked: (Agent) -> Unit = { },
    onAgentDelete: (Agent) -> Unit = { },
    onPromptMarketClicked: () -> Unit = { },
    onImportPdfClicked: () -> Unit = { },
    onDeleteKnowledgeBase: (String) -> Unit = { },
    onNewChatClicked: () -> Unit = { },
    onRenameSession: (String) -> Unit = { },
    onArchiveSession: (String) -> Unit = { },
    onPinSession: (String) -> Unit = { },
    onDeleteSession: (String) -> Unit = { },
    onDeleteAllSessions: () -> Unit = { },
    onRagEnabledChanged: (Boolean) -> Unit = { },
    onRealtimeChatClicked: () -> Unit = { },
    agents: List<Agent> = emptyList(),
    sessions: List<SessionEntity> = emptyList(),
    knownKnowledgeBases: List<String> = emptyList(),
    isRagEnabled: Boolean = true,
    selectedMenu: String = "",
    content: @Composable () -> Unit,
) {
    JetchatTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    JetchatDrawerContent(
                        onProfileClicked = onProfileClicked,
                        onChatClicked = onChatClicked,
                        onAgentClicked = onAgentClicked,
                        onAgentDelete = onAgentDelete,
                        onPromptMarketClicked = onPromptMarketClicked,
                        onImportPdfClicked = onImportPdfClicked,
                        onDeleteKnowledgeBase = onDeleteKnowledgeBase,
                        onNewChatClicked = onNewChatClicked,
                        onRenameSession = onRenameSession,
                        onArchiveSession = onArchiveSession,
                        onPinSession = onPinSession,
                        onDeleteSession = onDeleteSession,
                        onDeleteAllSessions = onDeleteAllSessions,
                        onRagEnabledChanged = onRagEnabledChanged,
                        onRealtimeChatClicked = onRealtimeChatClicked,
                        agents = agents,
                        sessions = sessions,
                        knownKnowledgeBases = knownKnowledgeBases,
                        isRagEnabled = isRagEnabled,
                        selectedMenu = selectedMenu
                    )
                }
            },
            content = content,
        )
    }
}

/**
 * Jetchat 导航抽屉的具体内容。
 *
 * 包含：
 * - 头部 Logo
 * - New Chat 按钮
 * - 角色市场 (Agent Market)
 * - 知识库 (Knowledge Base)
 * - 设置选项
 * - 聊天列表 (Chats)
 *
 * @param onProfileClicked 当点击个人资料项时的回调。
 * @param onChatClicked 当点击聊天项时的回调。
 * @param onNewChatClicked 当点击新建聊天时的回调。
 * @param selectedMenu 当前选中的菜单项 ID。
 */
@Composable
fun JetchatDrawerContent(
    onProfileClicked: (String) -> Unit,
    onChatClicked: (String) -> Unit,
    onAgentClicked: (Agent) -> Unit,
    onAgentDelete: (Agent) -> Unit,
    onPromptMarketClicked: () -> Unit,
    onImportPdfClicked: () -> Unit,
    onDeleteKnowledgeBase: (String) -> Unit,
    onNewChatClicked: () -> Unit,
    onRenameSession: (String) -> Unit,
    onArchiveSession: (String) -> Unit,
    onPinSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onDeleteAllSessions: () -> Unit,
    onRagEnabledChanged: (Boolean) -> Unit,
    onRealtimeChatClicked: () -> Unit,
    agents: List<Agent>,
    sessions: List<SessionEntity>,
    knownKnowledgeBases: List<String>,
    isRagEnabled: Boolean,
    selectedMenu: String
) {
    // 使用 windowInsetsTopHeight() 添加一个 Spacer，将抽屉内容向下推
    // 以避开状态栏 (Status Bar) 区域
    // 使用 verticalScroll 使内容可滚动，确保所有会话都能访问
    val scrollState = rememberScrollState()
    var isAgentsExpanded by remember { mutableStateOf(false) }
    var isKnowledgeExpanded by remember { mutableStateOf(false) }
    var showArchivedSessions by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.verticalScroll(scrollState)
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        DrawerHeader()
        DividerItem()
        NewChatItem(onNewChatClicked = onNewChatClicked)
        
        // 实时语音通话入口
        RealtimeChatItem(onRealtimeChatClicked)

        DividerItem(modifier = Modifier.padding(horizontal = 30.dp))
        DrawerItemHeader("角色市场")
        MarketItem(onPromptMarketClicked)

        if (agents.isNotEmpty()) {
            CollapsibleDrawerItemHeader("我的智能体", isAgentsExpanded) {
                isAgentsExpanded = !isAgentsExpanded
            }
            if (isAgentsExpanded) {
                agents.forEach { agent ->
                    AgentItem(
                        agent = agent,
                        selected = false, // Can be updated to track selection
                        onAgentClicked = { onAgentClicked(agent) },
                        onAgentDelete = { onAgentDelete(agent) }
                    )
                }
            }
        }

        DividerItem(modifier = Modifier.padding(horizontal = 30.dp))
        DrawerItemHeader("知识库")

        RagSwitchItem(
            checked = isRagEnabled,
            onCheckedChange = onRagEnabledChanged
        )

        KnowledgeItem(
            "PDF 导入",
            false
        ) {
            onImportPdfClicked()
        }

        if (knownKnowledgeBases.isNotEmpty()) {
            CollapsibleDrawerItemHeader("已导入知识库", isKnowledgeExpanded) {
                isKnowledgeExpanded = !isKnowledgeExpanded
            }
            if (isKnowledgeExpanded) {
                knownKnowledgeBases.forEach { filename ->
                    KnowledgeBaseItem(
                        filename = filename,
                        onDelete = { onDeleteKnowledgeBase(filename) }
                    )
                }
            }
        }

        DividerItem(modifier = Modifier.padding(horizontal = 30.dp))
        DrawerItemHeader("设置")
        SettingsItem(
            "模型选择",
            selectedMenu == meProfile.userId,
        ) {
            onProfileClicked(meProfile.userId)
        }

        DividerItem(modifier = Modifier.padding(horizontal = 30.dp))

        val (archivedSessions, activeSessions) = remember(sessions) {
            sessions.partition { it.archived }
        }
        
        // 将会话分为置顶和非置顶两部分
        val (pinnedSessions, unpinnedSessions) = remember(activeSessions) {
            val pinned = activeSessions
                .filter { it.pinned }
                .sortedByDescending { it.updatedAt } // 置顶会话按更新时间降序排序
            val unpinned = activeSessions
                .filter { !it.pinned }
                .sortedByDescending { it.updatedAt } // 非置顶会话按更新时间降序排序
            Pair(pinned, unpinned)
        }

        // 显示置顶会话区域（在 Chats 区域上方）
        if (pinnedSessions.isNotEmpty()) {
            DrawerItemHeader("置顶的聊天")
            pinnedSessions.forEach { session ->
                ChatItem(
                    text = session.name,
                    selected = selectedMenu == session.id,
                    pinned = session.pinned,
                    onChatClicked = { onChatClicked(session.id) },
                    onRename = { onRenameSession(session.id) },
                    onArchive = { onArchiveSession(session.id) },
                    onPin = { onPinSession(session.id) },
                    onDelete = { onDeleteSession(session.id) }
                )
            }
        }

        // 如果有置顶会话且也有非置顶会话，在它们之间添加一条横线（填满整个 drawer）
        if (pinnedSessions.isNotEmpty() && unpinnedSessions.isNotEmpty()) {
            DividerItem(modifier = Modifier.padding(horizontal = 30.dp))
        }

        // "聊天" header, always visible
         Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            verticalAlignment = CenterVertically
        ) {
            Text(
                text = "聊天",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                 modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showArchivedSessions = true }) {
                Icon(
                    imageVector = Icons.Default.Archive,
                    contentDescription = "Archived Sessions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDeleteAllSessions) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除所有会话",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 显示非置顶会话区域
        if (unpinnedSessions.isNotEmpty()) {
            unpinnedSessions.forEach { session ->
                ChatItem(
                    text = session.name,
                    selected = selectedMenu == session.id,
                    pinned = session.pinned,
                    onChatClicked = { onChatClicked(session.id) },
                    onRename = { onRenameSession(session.id) },
                    onArchive = { onArchiveSession(session.id) },
                    onPin = { onPinSession(session.id) },
                    onDelete = { onDeleteSession(session.id) }
                )
            }
        }

        if (showArchivedSessions) {
            ArchivedSessionsDialog(
                archivedSessions = archivedSessions,
                onDismiss = { showArchivedSessions = false },
                onSessionClicked = { sessionId ->
                    onArchiveSession(sessionId) // Unarchive
                    onChatClicked(sessionId) // Load session
                    showArchivedSessions = false
                }
            )
        }
    }
}

@Composable
fun ArchivedSessionsDialog(
    archivedSessions: List<SessionEntity>,
    onDismiss: () -> Unit,
    onSessionClicked: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Archived Sessions") },
        text = {
            Column {
                if (archivedSessions.isEmpty()) {
                    Text("No archived sessions.")
                } else {
                    archivedSessions.forEach { session ->
                        Text(
                            text = session.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSessionClicked(session.id) }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * 抽屉头部组件。
 * 显示 Jetchat 图标和 Logo 文字。
 */
@Composable
private fun DrawerHeader() {
    Row(modifier = Modifier.padding(16.dp), verticalAlignment = CenterVertically) {
        JetchatIcon(
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Image(
            painter = painterResource(id = R.drawable.jetchat_logo),
            contentDescription = null,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * 抽屉列表分组标题。
 *
 * @param text 标题文本。
 */
@Composable
private fun DrawerItemHeader(text: String) {
    Box(
        modifier = Modifier
            .heightIn(min = 32.dp)
            .padding(horizontal = 28.dp),
        contentAlignment = CenterStart,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CollapsibleDrawerItemHeader(
    text: String,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .heightIn(min = 32.dp)
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 28.dp, vertical = 4.dp),
        verticalAlignment = CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * 新建聊天项组件。
 *
 * @param onNewChatClicked 点击回调。
 */
@Composable
private fun NewChatItem(onNewChatClicked: () -> Unit) {
    Row(
        modifier = Modifier
            .height(56.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(CircleShape)
            .clickable(onClick = onNewChatClicked),
        verticalAlignment = CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
            contentDescription = null,
        )
        Text(
            "新聊天",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * 实时语音对话项组件。
 *
 * @param onRealtimeChatClicked 点击回调。
 */
@Composable
private fun RealtimeChatItem(onRealtimeChatClicked: () -> Unit) {
    Row(
        modifier = Modifier
            .height(56.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(CircleShape)
            .clickable(onClick = onRealtimeChatClicked),
        verticalAlignment = CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Call,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
            contentDescription = null,
        )
        Text(
            "实时语音",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}


/**
 * 角色广场项组件。
 *
 * @param onClick 点击回调。
 */
@Composable
private fun MarketItem(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(56.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        verticalAlignment = CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.ShoppingCart,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
            contentDescription = null,
        )
        Text(
            "角色广场",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * 聊天列表项组件。
 *
 * @param text 聊天名称。
 * @param selected 是否被选中。
 * @param pinned 是否置顶。
 * @param onChatClicked 点击回调。
 * @param onRename 重命名回调。
 * @param onArchive 归档回调。
 * @param onPin 置顶回调。
 * @param onDelete 删除回调。
 */
@Composable
private fun ChatItem(
    text: String,
    selected: Boolean,
    pinned: Boolean = false,
    onChatClicked: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    val background = when {
        selected -> Modifier.background(MaterialTheme.colorScheme.primaryContainer)
        //pinned -> Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
        else -> Modifier
    }
    Row(
        modifier = Modifier
            .height(56.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(background)
            .clickable(onClick = onChatClicked),
        verticalAlignment = CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f),
        )

        ChatItemMenu(
            onMenuActionSelected = { action ->
                when (action) {
                    ChatItemMenuAction.RENAME -> onRename()
                    ChatItemMenuAction.ARCHIVE -> onArchive()
                    ChatItemMenuAction.PIN -> onPin()
                    ChatItemMenuAction.DELETE -> onDelete()
                }
            }
        )
    }
}

/**
 * Agent 列表项组件。
 */
@Composable
private fun AgentItem(
    agent: Agent,
    selected: Boolean,
    onAgentClicked: () -> Unit,
    onAgentDelete: () -> Unit
) {
    val background = if (selected) {
        Modifier.background(MaterialTheme.colorScheme.primaryContainer)
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .height(56.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(CircleShape)
            .then(background)
            .clickable(onClick = onAgentClicked),
        verticalAlignment = CenterVertically,
    ) {
        val iconTint = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        Icon(
            imageVector = Icons.Default.Person, // Using a default person icon for Agents
            tint = iconTint,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
            contentDescription = null,
        )
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
        ) {
            Text(
                text = agent.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = agent.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        if (!agent.isDefault) {
            IconButton(onClick = onAgentDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = "Delete Agent"
                )
            }
        }
    }
}


/**
 * 设置列表项组件。
 *
 * @param text 文本。
 * @param selected 是否被选中。
 * @param onProfileClicked 点击回调。
 */
@Composable
private fun SettingsItem(text: String, selected: Boolean = false, onProfileClicked: () -> Unit) {
    val background = if (selected) {
        Modifier.background(MaterialTheme.colorScheme.primaryContainer)
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .height(56.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(CircleShape)
            .then(background)
            .clickable(onClick = onProfileClicked),
        verticalAlignment = CenterVertically,
    ) {
        val paddingSizeModifier = Modifier
            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
            .size(24.dp)

        Icon(
            imageVector = Icons.Default.Settings,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = paddingSizeModifier,
            contentDescription = null
        )

        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun KnowledgeItem(text: String, selected: Boolean = false, onClick: () -> Unit) {
    val background = if (selected) {
        Modifier.background(MaterialTheme.colorScheme.primaryContainer)
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .height(56.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(CircleShape)
            .then(background)
            .clickable(onClick = onClick),
        verticalAlignment = CenterVertically,
    ) {
        val paddingSizeModifier = Modifier
            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
            .size(24.dp)

        Icon(
            imageVector = Icons.Default.Description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = paddingSizeModifier,
            contentDescription = null
        )

        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun KnowledgeBaseItem(
    filename: String,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(48.dp) // Slightly smaller than main items
            .fillMaxWidth()
            .padding(horizontal = 24.dp), // More indented
        verticalAlignment = CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
            contentDescription = null,
        )

        Text(
            text = filename,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
            maxLines = 1
        )

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
                contentDescription = "Delete Knowledge Base"
            )
        }
    }
}

@Composable
private fun RagSwitchItem(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .height(56.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = CenterVertically) {
            val paddingSizeModifier = Modifier
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
                .size(24.dp)

            // 使用一个代表知识库/数据库的图标，或者复用 Description
            Icon(
                imageVector = Icons.Default.Description,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = paddingSizeModifier,
                contentDescription = null
            )

            Text(
                "启用知识库 (RAG)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(end = 16.dp)
        )
    }
}

/**
 * 分隔线组件。
 */
@Composable
fun DividerItem(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    )
}

/**
 * 抽屉内容的预览。
 */
@Composable
@Preview
fun DrawerPreview() {
    JetchatTheme {
        Surface {
            Column {
                JetchatDrawerContent(
                    onProfileClicked = {},
                    onChatClicked = {},
                    onAgentClicked = {},
                    onAgentDelete = {},
                    onPromptMarketClicked = {},
                    onImportPdfClicked = {},
                    onDeleteKnowledgeBase = {},
                    onNewChatClicked = {},
                    onRenameSession = {},
                    onArchiveSession = {},
                    onPinSession = {},
                    onDeleteSession = {},
                    onDeleteAllSessions = {},
                    onRagEnabledChanged = {},
                    onRealtimeChatClicked = {},
                    agents = emptyList(),
                    sessions = emptyList(),
                    knownKnowledgeBases = listOf("doc1.pdf", "report_final.pdf"),
                    isRagEnabled = true,
                    selectedMenu = ""
                )
            }
        }
    }
}

/**
 * 暗色主题下抽屉内容的预览。
 */
@Composable
@Preview
fun DrawerPreviewDark() {
    JetchatTheme(isDarkTheme = true) {
        Surface {
            Column {
                JetchatDrawerContent(
                    onProfileClicked = {},
                    onChatClicked = {},
                    onAgentClicked = {},
                    onAgentDelete = {},
                    onPromptMarketClicked = {},
                    onImportPdfClicked = {},
                    onDeleteKnowledgeBase = {},
                    onNewChatClicked = {},
                    onRenameSession = {},
                    onArchiveSession = {},
                    onPinSession = {},
                    onDeleteSession = {},
                    onDeleteAllSessions = {},
                    onRagEnabledChanged = {},
                    onRealtimeChatClicked = {},
                    agents = emptyList(),
                    sessions = emptyList(),
                    knownKnowledgeBases = listOf("doc1.pdf", "report_final.pdf"),
                    isRagEnabled = true,
                    selectedMenu = ""
                )
            }
        }
    }
}
