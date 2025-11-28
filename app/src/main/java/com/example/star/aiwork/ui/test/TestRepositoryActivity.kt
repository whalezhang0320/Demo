package com.example.star.aiwork.ui.test

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.star.aiwork.data.local.datasource.*
import com.example.star.aiwork.data.repository.*
import com.example.star.aiwork.domain.model.*
import com.example.star.aiwork.domain.usecase.draft.GetDraftUseCase
import com.example.star.aiwork.domain.usecase.draft.UpdateDraftUseCase
import com.example.star.aiwork.domain.usecase.message.RollbackMessageUseCase
import com.example.star.aiwork.domain.usecase.message.SendMessageUseCase
import com.example.star.aiwork.domain.usecase.session.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class TestRepositoryActivity : AppCompatActivity() {

    private val TAG = "TestRepositoryActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {

            Log.d(TAG, "🚀 开始 Repository + UseCase 全量测试...")

            // --- 1. 初始化 LocalDataSource ---
            val sessionDS = SessionLocalDataSourceImpl(this@TestRepositoryActivity)
            val messageDS = MessageLocalDataSourceImpl(this@TestRepositoryActivity)
            val draftDS = DraftLocalDataSourceImpl(this@TestRepositoryActivity)

            // --- 2. 初始化 Repository ---
            val sessionRepo = SessionRepositoryImpl(sessionDS)
            val messageRepo = MessageRepositoryImpl(messageDS)
            val draftRepo = DraftRepositoryImpl(draftDS)

            // --- 3. 初始化 UseCase ---
            val createSession = CreateSessionUseCase(sessionRepo)
            val getSession = GetSessionByIdUseCase(sessionRepo)
            val renameSession = RenameSessionUseCase(sessionRepo)
            val deleteSession = DeleteSessionUseCase(
                sessionRepository = sessionRepo,
                messageRepository = messageRepo,
                draftRepository = draftRepo
            )
            val getSessionList = GetSessionListUseCase(sessionRepo)

            val sendMessage = SendMessageUseCase(
                messageRepository = messageRepo,
                sessionRepository = sessionRepo
            )
            val rollbackMessage = RollbackMessageUseCase(messageRepo)

            val updateDraft = UpdateDraftUseCase(draftRepo)
            val getDraft = GetDraftUseCase(draftRepo)

            // =============== SESSION TEST ===============
            val sessionId = UUID.randomUUID().toString()
            val createdSession = SessionEntity(
                id = sessionId,
                name = "Test 会话",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                pinned = false,
                archived = false
            )

            createSession(createdSession)
            Log.d(TAG, "🟢 已创建会话: $createdSession")

            val fetchedSession = getSession(sessionId)
            Log.d(TAG, "🔍 查询会话: $fetchedSession")

            renameSession(sessionId, "重命名后的会话")
            Log.d(TAG, "✏️ 已重命名会话")

            val allSessions = getSessionList().first()
            Log.d(TAG, "📚 会话列表: $allSessions")

            // =============== MESSAGE TEST ===============
            val msgId = UUID.randomUUID().toString()

            val message = MessageEntity(
                id = msgId,
                sessionId = sessionId,
                role = MessageRole.USER,
                type = MessageType.TEXT,
                content = "你好，这是测试消息。",
                createdAt = System.currentTimeMillis(),
                status = MessageStatus.SENDING,
                parentMessageId = null,
                metadata = MessageMetadata()
            )

            sendMessage(message)
            Log.d(TAG, "✉️ 已发送消息: $message")

            val messagesInSession = messageRepo.getMessages(sessionId)
            Log.d(TAG, "📨 当前会话的消息: $messagesInSession")

            rollbackMessage(msgId)
            Log.d(TAG, "⏪ 已回滚消息 $msgId")

            val messagesAfterRollback = messageRepo.getMessages(sessionId)
            Log.d(TAG, "📨 回滚后的消息列表: $messagesAfterRollback")

            // =============== DRAFT TEST ===============
            updateDraft(sessionId, "这是草稿内容测试。")
            Log.d(TAG, "📝 已保存草稿")

            val draft = getDraft(sessionId)
            Log.d(TAG, "🔍 获取草稿: $draft")

            // =============== DELETE TEST ===============
            deleteSession(sessionId)
            Log.d(TAG, "❌ 已删除会话 id=$sessionId")

            val afterDelete = getSession(sessionId)
            Log.d(TAG, "🔍 删除后查询会话: $afterDelete")

            val listAfterDelete = getSessionList().first()
            Log.d(TAG, "📚 删除后的会话列表: $listAfterDelete")

            Log.d(TAG, "🎉 全部测试完成!")
        }
    }
}
