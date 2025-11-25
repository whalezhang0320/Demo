package com.example.star.aiwork.infra.util

import android.util.Base64

/**
 * 将 ByteArray 转换为 Base64 字符串 (No Wrap)。
 */
fun ByteArray.toBase64(): String {
    return Base64.encodeToString(this, Base64.NO_WRAP)
}
