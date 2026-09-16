package com.markq.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = Migration(1, 2) { db: SupportSQLiteDatabase ->
    db.execSQL("ALTER TABLE entries ADD COLUMN color TEXT")
}

val MIGRATION_2_3 = Migration(2, 3) { db: SupportSQLiteDatabase ->
    db.execSQL("ALTER TABLE entries ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
}

val MIGRATION_3_4 = Migration(3, 4) { db: SupportSQLiteDatabase ->
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS templates (
            id TEXT NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            text TEXT NOT NULL,
            color TEXT,
            tags TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            contentUpdatedAt INTEGER NOT NULL,
            statusUpdatedAt INTEGER NOT NULL,
            createdBy TEXT NOT NULL,
            updatedBy TEXT NOT NULL,
            deleted INTEGER NOT NULL,
            deletedBy TEXT,
            deletedAt INTEGER,
            dirty INTEGER NOT NULL,
            remoteEtag TEXT
        )
        """.trimIndent(),
    )
}

@Database(
    entities = [EntryEntity::class, AttachmentEntity::class, SyncCursorEntity::class, TemplateEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class MarkDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao
    abstract fun attachments(): AttachmentDao
    abstract fun templates(): TemplateDao
    abstract fun cursors(): CursorDao
}
