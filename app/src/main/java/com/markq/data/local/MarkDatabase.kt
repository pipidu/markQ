package com.markq.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = Migration(1, 2) { db: SupportSQLiteDatabase ->
    db.execSQL("ALTER TABLE entries ADD COLUMN color TEXT")
}

@Database(
    entities = [EntryEntity::class, AttachmentEntity::class, SyncCursorEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class MarkDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao
    abstract fun attachments(): AttachmentDao
    abstract fun cursors(): CursorDao
}
