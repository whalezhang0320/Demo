package com.example.star.aiwork.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

/**
 * 重命名会话对话框。
 *
 * @param currentName 当前会话名称。
 * @param onDismiss 取消对话框时的回调。
 * @param onConfirm 确认重命名时的回调，参数为新名称。
 */
@Composable
fun RenameSessionDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember(currentName) { mutableStateOf(currentName) }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "重命名会话",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("会话名称") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "取消",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onConfirm(newName.trim())
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    enabled = newName.isNotBlank() && newName.trim() != currentName
                ) {
                    Text("确认")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )

    // 自动聚焦到输入框
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

