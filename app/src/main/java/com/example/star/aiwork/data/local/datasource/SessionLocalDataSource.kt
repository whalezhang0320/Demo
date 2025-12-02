package com.example.star.aiwork.data.local.datasource

import com.example.star.aiwork.data.local.record.SessionRecord
import kotlinx.coroutines.flow.Flow

interface SessionLocalDataSource {

    /**
     * 创建会话
     */
    suspend fun insertSession(session: SessionRecord)

    /**
     * 更新会话（名称、preview、置顶等）
     */
    suspend fun updateSession(session: SessionRecord)

    /**
     * 获取单个会话
     */
    suspend fun getSession(id: String): SessionRecord?

    /**
     * 获取所有会话列表（会话列表 UI 用）
     */
    fun observeSessions(): Flow<List<SessionRecord>>

    /**
     * 删除会话
     */
    suspend fun deleteSession(id: String)

    /**
     * 搜索会话
     */
    fun searchSessions(query: String): Flow<List<SessionRecord>>
}