package com.example.star.aiwork.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * 删除会话确认对话框。
 *
 * @param sessionName 要删除的会话名称。
 * @param onDismiss 取消对话框时的回调。
 * @param onConfirm 确认删除时的回调。
 */
@Composable
fun DeleteSessionDialog(
    sessionName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "删除会话",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = "确定要删除会话「$sessionName」吗？\n此操作无法撤销",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Row(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = androidx.compose.ui.Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "取消",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onConfirm,
                    modifier = androidx.compose.ui.Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "删除",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(8.dp)
    )
}

