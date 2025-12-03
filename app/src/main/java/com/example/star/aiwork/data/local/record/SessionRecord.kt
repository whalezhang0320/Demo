package com.example.star.aiwork.data.local.record

data class SessionRecord(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean,
    val archived: Boolean,
    val metadata: String? = null // 存储 JSON 格式的元数据
)
