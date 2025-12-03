package com.example.star.aiwork.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ChatDatabase(context: Context) :
    SQLiteOpenHelper(context, "chat_app.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        // 会话表
        db.execSQL("""
            CREATE TABLE sessions(
                id TEXT PRIMARY KEY,
                name TEXT,
                createdAt INTEGER,
                updatedAt INTEGER,
                pinned INTEGER,
                archived INTEGER,
                metadata TEXT
            )
        """)

        // 消息表
        db.execSQL("""
            CREATE TABLE messages(
                id TEXT PRIMARY KEY,
                sessionId TEXT,
                role TEXT,
                content TEXT,
                createdAt INTEGER,
                status INTEGER,
                parentMessageId TEXT
            )
        """)

        // 草稿表
        db.execSQL("""
            CREATE TABLE drafts(
                sessionId TEXT PRIMARY KEY,
                content TEXT,
                updatedAt INTEGER
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN metadata TEXT")
        }
    }
}
