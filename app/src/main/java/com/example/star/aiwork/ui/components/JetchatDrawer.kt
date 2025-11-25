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

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterStart
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.star.aiwork.R
import com.example.star.aiwork.data.colleagueProfile
import com.example.star.aiwork.data.meProfile
import com.example.star.aiwork.domain.model.Agent
import com.example.star.aiwork.ui.theme.JetchatTheme
import com.example.star.aiwork.ui.widget.WidgetReceiver

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
    onAgentClicked: (Agent) -> Unit = {},
    agents: List<Agent> = emptyList(),
    selectedMenu: String = "composers",
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
                        agents = agents,
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
 * - 聊天列表 (Chats)
 * - 最近联系人 (Recent Profiles)
 * - 角色市场 (Agent Market)
 * - 设置选项 (如添加 Widget)
 *
 * @param onProfileClicked 当点击个人资料项时的回调。
 * @param onChatClicked 当点击聊天项时的回调。
 * @param selectedMenu 当前选中的菜单项 ID。
 */
@Composable
fun JetchatDrawerContent(
    onProfileClicked: (String) -> Unit, 
    onChatClicked: (String) -> Unit, 
    onAgentClicked: (Agent) -> Unit,
    agents: List<Agent>,
    selectedMenu: String = "composers"
) {
    // 使用 windowInsetsTopHeight() 添加一个 Spacer，将抽屉内容向下推
    // 以避开状态栏 (Status Bar) 区域
    Column {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        DrawerHeader()
        DividerItem()
        DrawerItemHeader("Chats")
        ChatItem("composers", selectedMenu == "composers") {
            onChatClicked("composers")
        }
        ChatItem("droidcon-nyc", selectedMenu == "droidcon-nyc") {
            onChatClicked("droidcon-nyc")
        }
        
        DividerItem(modifier = Modifier.padding(horizontal = 28.dp))
        DrawerItemHeader("Agents (Prompts)")
        agents.forEach { agent ->
            AgentItem(
                agent = agent,
                selected = false, // Can be updated to track selection
                onAgentClicked = { onAgentClicked(agent) }
            )
        }

        DividerItem(modifier = Modifier.padding(horizontal = 28.dp))
        DrawerItemHeader("Settings")
        SettingsItem(
            "Model Selection & API Settings",
            selectedMenu == meProfile.userId,
        ) {
            onProfileClicked(meProfile.userId)
        }

        if (widgetAddingIsSupported(LocalContext.current)) {
            DividerItem(modifier = Modifier.padding(horizontal = 28.dp))
            DrawerItemHeader("Widget")
            WidgetDiscoverability()
        }
    }
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
            .heightIn(min = 52.dp)
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

/**
 * 聊天列表项组件。
 *
 * @param text 聊天名称。
 * @param selected 是否被选中。
 * @param onChatClicked 点击回调。
 */
@Composable
private fun ChatItem(text: String, selected: Boolean, onChatClicked: () -> Unit) {
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
            .clickable(onClick = onChatClicked),
        verticalAlignment = CenterVertically,
    ) {
        val iconTint = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_jetchat),
            tint = iconTint,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
            contentDescription = null,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * Agent 列表项组件。
 */
@Composable
private fun AgentItem(agent: Agent, selected: Boolean, onAgentClicked: () -> Unit) {
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
        Column(modifier = Modifier.padding(start = 12.dp)) {
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
                JetchatDrawerContent({}, {}, {}, emptyList())
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
                JetchatDrawerContent({}, {}, {}, emptyList())
            }
        }
    }
}

/**
 * Widget 添加选项组件。
 * 仅在支持将 Widget 固定到主屏幕的设备上显示 (Android O 及以上)。
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun WidgetDiscoverability() {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .height(56.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(CircleShape)
            .clickable(onClick = {
                addWidgetToHomeScreen(context)
            }),
        verticalAlignment = CenterVertically,
    ) {
        Text(
            stringResource(id = R.string.add_widget_to_home_page),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * 请求将 App Widget 添加到主屏幕。
 */
@RequiresApi(Build.VERSION_CODES.O)
private fun addWidgetToHomeScreen(context: Context) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val myProvider = ComponentName(context, WidgetReceiver::class.java)
    if (widgetAddingIsSupported(context)) {
        appWidgetManager.requestPinAppWidget(myProvider, null, null)
    }
}

/**
 * 检查是否支持将 Widget 固定到主屏幕。
 */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
private fun widgetAddingIsSupported(context: Context): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
}
