package com.example.star.aiwork.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.star.aiwork.data.local.converter.Converters
import com.example.star.aiwork.data.local.dao.DraftDao
import com.example.star.aiwork.data.local.dao.MessageDao
import com.example.star.aiwork.data.local.dao.SessionDao
import com.example.star.aiwork.domain.model.DraftEntity
import com.example.star.aiwork.domain.model.MessageEntity
import com.example.star.aiwork.domain.model.MessageFtsEntity
import com.example.star.aiwork.domain.model.SessionEntity
import com.example.star.aiwork.domain.model.SessionFtsEntity

@Database(entities = [SessionEntity::class, MessageEntity::class, DraftEntity::class, SessionFtsEntity::class, MessageFtsEntity::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun draftDao(): DraftDao
}
