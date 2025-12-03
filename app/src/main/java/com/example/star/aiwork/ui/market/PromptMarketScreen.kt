package com.example.star.aiwork.ui.market

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptMarketScreen(
    onBack: () -> Unit,
    viewModel: PromptMarketViewModel = viewModel(factory = PromptMarketViewModel.Factory)
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 监听剪贴板变化
    DisposableEffect(Unit) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()
                // 1. 检查是否非空
                // 2. 检查长度 > 20 (简单的防误触发)
                // 3. (可选) 检查是否包含特定关键词，如 "Act as", "Role:", 等，但提示词可能很多样
                if (!text.isNullOrBlank() && text.length > 20) {
                    viewModel.onClipboardChanged(text)
                }
            }
        }
        clipboardManager.addPrimaryClipChangedListener(listener)
        onDispose {
            clipboardManager.removePrimaryClipChangedListener(listener)
        }
    }
    
    // 监听待确认的 Agent
    val pendingAgent by viewModel.pendingAgent.collectAsState()
    var showRenameDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(pendingAgent) {
        pendingAgent?.let { agent ->
             val result = snackbarHostState.showSnackbar(
                message = "Detected copied prompt. Save as Agent?",
                actionLabel = "Save",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                showRenameDialog = true
            } else {
                // 只有当没有点击 Save (即 Snackbar 消失或被 dismiss) 时才清除 pendingAgent
                // 但这里如果只是 dismiss，我们应该清除，否则下次复制可能无法触发？
                // 实际上 LaunchedEffect(pendingAgent) 会在 pendingAgent 变化时触发。
                // 如果用户什么都不做，Snackbar 消失，pendingAgent 还是那个值。
                // 我们需要一种机制来重置。
                // 简单处理：如果用户Dismiss了，我们重置它。
                viewModel.dismissPendingAgent()
            }
        }
    }
    
    if (showRenameDialog && pendingAgent != null) {
        SaveAgentDialog(
            agent = pendingAgent!!,
            onDismiss = {
                showRenameDialog = false
                viewModel.dismissPendingAgent() 
            },
            onConfirm = { newName ->
                viewModel.confirmSaveAgent(newName)
                showRenameDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("角色广场 (AiShort)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        AndroidView(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    webViewClient = WebViewClient()
                    loadUrl("https://www.aishort.top/")
                }
            }
        )
    }
}
