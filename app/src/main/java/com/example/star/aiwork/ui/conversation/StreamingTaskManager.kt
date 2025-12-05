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

package com.example.star.aiwork.ui.conversation

import kotlinx.coroutines.Job

/**
 * 流式任务管理器，用于跟踪和管理每个会话的流式生成任务。
 * 
 * 用于解决在会话切换后，ConversationLogic 重新创建时无法取消之前的流式生成任务的问题。
 * 通过保存 Job 引用，即使 ConversationLogic 被重新创建，也能通过 sessionId 找到并取消任务。
 */
class StreamingTaskManager {
    // 存储每个会话的流式生成任务 Job
    private val streamingJobs = mutableMapOf<String, Job>()
    private val hintTypingJobs = mutableMapOf<String, Job>()
    
    /**
     * 注册流式生成任务的 Job
     * 
     * @param sessionId 会话 ID
     * @param streamingJob 流式收集协程的 Job
     * @param hintTypingJob 提示消息流式显示的 Job
     */
    @Synchronized
    fun registerTasks(sessionId: String, streamingJob: Job?, hintTypingJob: Job?) {
        // 清理旧的任务（如果存在）
        streamingJobs[sessionId]?.cancel()
        hintTypingJobs[sessionId]?.cancel()
        
        // 注册新任务
        streamingJob?.let { streamingJobs[sessionId] = it }
        hintTypingJob?.let { hintTypingJobs[sessionId] = it }
    }
    
    /**
     * 取消指定会话的流式生成任务
     * 
     * @param sessionId 会话 ID
     */
    @Synchronized
    fun cancelTasks(sessionId: String) {
        streamingJobs[sessionId]?.cancel()
        hintTypingJobs[sessionId]?.cancel()
        streamingJobs.remove(sessionId)
        hintTypingJobs.remove(sessionId)
    }
    
    /**
     * 移除指定会话的任务引用（任务已完成）
     * 
     * @param sessionId 会话 ID
     */
    @Synchronized
    fun removeTasks(sessionId: String) {
        streamingJobs.remove(sessionId)
        hintTypingJobs.remove(sessionId)
    }
    
    /**
     * 清空所有任务
     */
    @Synchronized
    fun clear() {
        streamingJobs.values.forEach { it.cancel() }
        hintTypingJobs.values.forEach { it.cancel() }
        streamingJobs.clear()
        hintTypingJobs.clear()
    }
}

