package com.example.star.aiwork.data.local.datasource

import com.example.star.aiwork.domain.model.SessionEntity
import kotlinx.coroutines.flow.Flow

interface SessionLocalDataSource {

    /**
     * 创建或更新会话
     */
    suspend fun upsertSession(session: SessionEntity)

    /**
     * 获取单个会话
     */
    suspend fun getSession(id: String): SessionEntity?

    /**
     * 获取所有会话列表（会话列表 UI 用）
     */
    fun observeSessions(): Flow<List<SessionEntity>>

    /**
     * 删除会话
     */
    suspend fun deleteSession(id: String)

    /**
     * 搜索会话
     */
    fun searchSessions(query: String): Flow<List<SessionEntity>>
}
