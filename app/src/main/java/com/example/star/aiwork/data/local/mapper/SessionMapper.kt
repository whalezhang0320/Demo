package com.example.star.aiwork.data.local.mapper

import com.example.star.aiwork.data.local.record.SessionRecord
import com.example.star.aiwork.domain.model.SessionEntity
import com.example.star.aiwork.domain.model.SessionMetadata
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object SessionMapper {

    private val json = Json { ignoreUnknownKeys = true }

    fun toEntity(record: SessionRecord): SessionEntity {
        val metadata = record.metadata?.let {
            try {
                json.decodeFromString<SessionMetadata>(it)
            } catch (e: Exception) {
                SessionMetadata()
            }
        } ?: SessionMetadata()

        return SessionEntity(
            id = record.id,
            name = record.name,
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
            pinned = record.pinned,
            archived = record.archived,
            metadata = metadata
        )
    }

    fun toRecord(entity: SessionEntity): SessionRecord {
        val metadataStr = try {
            json.encodeToString(entity.metadata)
        } catch (e: Exception) {
            null
        }

        return SessionRecord(
            id = entity.id,
            name = entity.name,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            pinned = entity.pinned,
            archived = entity.archived,
            metadata = metadataStr
        )
    }
}
