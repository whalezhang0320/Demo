package com.example.star.aiwork.data.local.converter

import androidx.room.TypeConverter
import com.example.star.aiwork.domain.model.MessageMetadata
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.MessageStatus
import com.example.star.aiwork.domain.model.MessageType
import com.example.star.aiwork.domain.model.SessionMetadata
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun fromSessionMetadata(metadata: SessionMetadata): String {
        return Json.encodeToString(metadata)
    }

    @TypeConverter
    fun toSessionMetadata(metadata: String): SessionMetadata {
        return Json.decodeFromString(metadata)
    }

    @TypeConverter
    fun fromMessageMetadata(metadata: MessageMetadata): String {
        return Json.encodeToString(metadata)
    }

    @TypeConverter
    fun toMessageMetadata(metadata: String): MessageMetadata {
        return Json.decodeFromString(metadata)
    }

    @TypeConverter
    fun fromMessageRole(role: MessageRole): String {
        return role.name
    }

    @TypeConverter
    fun toMessageRole(role: String): MessageRole {
        return MessageRole.valueOf(role)
    }

    @TypeConverter
    fun fromMessageType(type: MessageType): String {
        return type.name
    }

    @TypeConverter
    fun toMessageType(type: String): MessageType {
        return MessageType.valueOf(type)
    }

    @TypeConverter
    fun fromMessageStatus(status: MessageStatus): String {
        return status.name
    }

    @TypeConverter
    fun toMessageStatus(status: String): MessageStatus {
        return MessageStatus.valueOf(status)
    }
}
