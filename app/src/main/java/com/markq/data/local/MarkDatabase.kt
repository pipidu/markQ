package com.markq.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [EntryEntity::class, AttachmentEntity::class, SyncCursorEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MarkDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao
    abstract fun attachments(): AttachmentDao
    abstract fun cursors(): CursorDao
}
