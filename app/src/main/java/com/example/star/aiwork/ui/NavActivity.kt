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

package com.example.star.aiwork.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.DrawerValue.Closed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.example.star.aiwork.R
import com.example.star.aiwork.databinding.ContentMainBinding
import com.example.star.aiwork.domain.model.SessionEntity
import com.example.star.aiwork.ui.components.DeleteSessionDialog
import com.example.star.aiwork.ui.components.JetchatDrawer
import com.example.star.aiwork.ui.components.RenameSessionDialog
import com.example.star.aiwork.ui.conversation.ChatViewModel
import com.example.star.aiwork.ui.conversation.Message
import kotlinx.coroutines.launch

/**
 * 应用程序的主 Activity。
 *
 * 这是一个混合架构的 Activity：
 * - 它继承自 [AppCompatActivity] 来支持传统的 Android 组件和 Navigation Component。
 * - 它使用 [ComposeView] 作为根视图，并包含了一个 [JetchatDrawer] (Compose 实现的侧滑菜单)。
 * - 侧滑菜单的内容使用 [AndroidViewBinding] 包裹了一个传统的 XML 布局 (`ContentMainBinding`)，
 *   其中包含一个 `NavHostFragment` 用于基于 Fragment 的导航。
 *
 * 这种结构展示了如何在一个应用中同时使用 Jetpack Compose 和传统的 View 系统。
 */
class NavActivity : AppCompatActivity() {
    private val mainViewModel: MainViewModel by viewModels { MainViewModel.Factory }
    private val chatViewModel: ChatViewModel by viewModels { ChatViewModel.Factory }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // 启用全面屏边缘到边缘显示
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 处理窗口内边距，确保内容不会被系统栏遮挡
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets -> insets }

        setContentView(
            ComposeView(this).apply {
                // 禁用 ComposeView 自动消费窗口内边距，交由内部组件处理
                consumeWindowInsets = false
                setContent {
                    // 记住侧滑菜单的状态 (打开/关闭)
                    val drawerState = rememberDrawerState(initialValue = Closed)
                    val drawerOpen by mainViewModel.drawerShouldBeOpened.collectAsStateWithLifecycle()

                    val agents by mainViewModel.agents.collectAsStateWithLifecycle()
                    val sessions by chatViewModel.sessions.collectAsStateWithLifecycle()
                    val currentSession by chatViewModel.currentSession.collectAsStateWithLifecycle()

                    // 记录当前选中的菜单项
                    var selectedMenu by remember { mutableStateOf("composers") }
                    
                    // 重命名对话框状态
                    var sessionToRename by remember { mutableStateOf<SessionEntity?>(null) }
                    
                    // 删除确认对话框状态
                    var sessionToDelete by remember { mutableStateOf<SessionEntity?>(null) }

                    // PDF 选择器
                    val pdfLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri: Uri? ->
                        if (uri != null) {
                            mainViewModel.indexPdf(uri)
                        }
                    }

                    // 监听 ViewModel 中的打开菜单请求
                    if (drawerOpen) {
                        // 打开菜单并在 ViewModel 中重置状态
                        LaunchedEffect(Unit) {
                            // 使用 try-finally 包装以处理打开菜单时的中断情况
                            try {
                                // sessions Flow 会自动更新，因为 observeSessions() 观察数据库变化
                                drawerState.open()
                            } finally {
                                mainViewModel.resetOpenDrawerAction()
                            }
                        }
                    }

                    // 协程作用域，用于处理 UI 事件中的挂起函数 (如关闭菜单)
                    val scope = rememberCoroutineScope()

                    // 使用 Compose 实现的侧滑菜单布局
                    JetchatDrawer(
                        drawerState = drawerState,
                        selectedMenu = currentSession?.id ?: "",
                        agents = agents,
                        sessions = sessions,
                        onChatClicked = { sessionId ->
                            val session = sessions.find { it.id == sessionId }
                            if (session != null) {
                                chatViewModel.selectSession(session)
                            }
                            findNavController().popBackStack(R.id.nav_home, false)
                            scope.launch {
                                drawerState.close()
                            }
                        },
                        onProfileClicked = { userId ->
                            val bundle = bundleOf("userId" to userId)
                            findNavController().navigate(R.id.nav_profile, bundle)
                            scope.launch {
                                drawerState.close()
                            }
                        },
                        onAgentClicked = { agent ->
                            // 当点击 Agent 时，将其系统提示词作为系统消息添加到当前对话中
                            // 同时更新 UI 状态中的 activeAgent，以便后续请求带上此 Prompt
                            
                            currentSession?.let { session ->
                                val uiState = chatViewModel.getOrCreateSessionUiState(session.id, session.name)
                                uiState.addMessage(
                                    Message(
                                        author = "System",
                                        content = "Applied Agent: ${agent.name}\n${agent.systemPrompt}",
                                        timestamp = "Now"
                                    )
                                )
                                uiState.activeAgent = agent
                            }
                            
                            // 关闭抽屉并导航回聊天
                            findNavController().popBackStack(R.id.nav_home, false)
                            scope.launch {
                                drawerState.close()
                            }
                        },
                        onImportPdfClicked = {
                            pdfLauncher.launch("application/pdf")
                            scope.launch {
                                drawerState.close()
                            }
                        },
                        onNewChatClicked = {
                            scope.launch {
                                // 创建新会话，使用默认名称
                                val sessionName = "New Chat"
                                chatViewModel.createSession(sessionName)

                                // createSession 是异步的，等待一下确保会话创建完成
                                // 然后选择新会话以确保消息和草稿被正确初始化
                                kotlinx.coroutines.delay(200)
                                val newSession = chatViewModel.currentSession.value
                                if (newSession != null) {
                                    chatViewModel.selectSession(newSession)
                                }

                                // 导航到聊天页面
                                findNavController().popBackStack(R.id.nav_home, false)

                                // 关闭抽屉
                                drawerState.close()
                            }
                        },
                        onRenameSession = { sessionId ->
                            val session = sessions.find { it.id == sessionId }
                            if (session != null) {
                                sessionToRename = session
                            }
                        },
                        onArchiveSession = { sessionId ->
                            scope.launch {
                                val session = sessions.find { it.id == sessionId }
                                if (session != null) {
                                    chatViewModel.archiveSession(sessionId, !session.archived)
                                }
                            }
                        },
                        onPinSession = { sessionId ->
                            scope.launch {
                                val session = sessions.find { it.id == sessionId }
                                if (session != null) {
                                    chatViewModel.pinSession(sessionId, !session.pinned)
                                }
                            }
                        },
                        onDeleteSession = { sessionId ->
                            val session = sessions.find { it.id == sessionId }
                            if (session != null) {
                                sessionToDelete = session
                            }
                        }
                    ) {
                        // 侧滑菜单的主要内容区域：嵌入基于 XML 的 Fragment 导航
                        AndroidViewBinding(ContentMainBinding::inflate)
                    }
                    
                    // 重命名对话框
                    sessionToRename?.let { session ->
                        RenameSessionDialog(
                            currentName = session.name,
                            onDismiss = { sessionToRename = null },
                            onConfirm = { newName ->
                                scope.launch {
                                    chatViewModel.renameSession(session.id, newName)
                                }
                                sessionToRename = null
                            }
                        )
                    }
                    
                    // 删除确认对话框
                    sessionToDelete?.let { session ->
                        DeleteSessionDialog(
                            sessionName = session.name,
                            onDismiss = { sessionToDelete = null },
                            onConfirm = {
                                scope.launch {
                                    chatViewModel.deleteSession(session.id)
                                }
                                sessionToDelete = null
                            }
                        )
                    }
                }
            },
        )
    }

    /**
     * 处理向上导航 (返回) 操作。
     *
     * 委托给 Navigation Controller 处理。
     */
    override fun onSupportNavigateUp(): Boolean {
        return findNavController().navigateUp() || super.onSupportNavigateUp()
    }

    /**
     * 查找 NavController。
     *
     * 这是一个辅助方法，用于解决直接查找 NavHostFragment 的一些已知问题。
     * 参见 https://issuetracker.google.com/142847973
     */
    private fun findNavController(): NavController {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        return navHostFragment.navController
    }
}
