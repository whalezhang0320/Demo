package com.example.star.aiwork.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 聊天项菜单选项枚举。
 */
enum class ChatItemMenuAction(
    val label: String,
    val icon: ImageVector,
    val isDestructive: Boolean = false
) {
    RENAME("重命名", Icons.Default.Edit),
    ARCHIVE("归档", Icons.Default.Archive),
    PIN("置顶", Icons.Default.PushPin),
    DELETE("删除", Icons.Default.Delete, isDestructive = true)
}

/**
 * 聊天项菜单组件。
 * 封装了聊天项功能菜单按钮和下拉菜单。
 *
 * @param onMenuActionSelected 菜单项选择回调，参数为选中的菜单项。
 */
@Composable
fun ChatItemMenu(
    onMenuActionSelected: (ChatItemMenuAction) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    androidx.compose.foundation.layout.Box {
        IconButton(
            onClick = { showMenu = true },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "更多选项",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            ChatItemMenuAction.values().forEach { action ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (action.isDestructive) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                text = action.label,
                                color = if (action.isDestructive) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    },
                    onClick = {
                        showMenu = false
                        onMenuActionSelected(action)
                    }
                )
            }
        }
    }
}

