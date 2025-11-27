package com.example.star.aiwork.data.model

/**
 * 简单的 HTTP 头部定义。
 */
data class HttpHeader(
    val name: String,
    val value: String
)

/**
 * JSON Body 中的自定义字段。
 */
data class HttpBodyField(
    val key: String,
    val value: String
)

