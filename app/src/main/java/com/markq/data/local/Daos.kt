package com.markq.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Transaction
    @Query("SELECT * FROM entries WHERE deleted = 0 ORDER BY occurredAt DESC")
    fun observeActive(): Flow<List<EntryWithAttachments>>

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :id")
    fun observeById(id: String): Flow<EntryWithAttachments?>

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun get(id: String): EntryWithAttachments?

    @Transaction
    @Query("SELECT * FROM entries WHERE dirty = 1")
    suspend fun getDirty(): List<EntryWithAttachments>

    @Query("SELECT COUNT(*) FROM entries WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Transaction
    @Query("SELECT * FROM entries WHERE deleted = 0 ORDER BY occurredAt DESC")
    suspend fun listActive(): List<EntryWithAttachments>

    @Transaction
    @Query("SELECT * FROM entries WHERE deleted = 1")
    suspend fun getDeleted(): List<EntryWithAttachments>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: EntryEntity)

    @Query("UPDATE entries SET dirty = :dirty, remoteEtag = :etag WHERE id = :id")
    suspend fun markPushed(id: String, dirty: Boolean, etag: String?)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun hardDelete(id: String)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE entryId = :entryId")
    suspend fun forEntry(entryId: String): List<AttachmentEntity>

    @Query("SELECT sha256 FROM attachments")
    suspend fun allHashes(): List<String>

    @Query("SELECT sha256 FROM attachments WHERE entryId != :entryId")
    suspend fun hashesExceptEntry(entryId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE entryId = :entryId AND id NOT IN (:keepIds)")
    suspend fun deleteMissing(entryId: String, keepIds: List<String>)

    @Query("DELETE FROM attachments WHERE entryId = :entryId")
    suspend fun deleteForEntry(entryId: String)

    @Query("UPDATE attachments SET dirty = 0, remoteEtag = :etag WHERE id = :id")
    suspend fun markPushed(id: String, etag: String?)
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates WHERE deleted = 0 ORDER BY contentUpdatedAt DESC")
    fun observeActive(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun get(id: String): TemplateEntity?

    @Query("SELECT * FROM templates WHERE dirty = 1")
    suspend fun getDirty(): List<TemplateEntity>

    @Query("SELECT COUNT(*) FROM templates WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Query("SELECT * FROM templates WHERE deleted = 0 ORDER BY contentUpdatedAt DESC")
    suspend fun listActive(): List<TemplateEntity>

    @Query("SELECT * FROM templates WHERE deleted = 1")
    suspend fun getDeleted(): List<TemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(template: TemplateEntity)

    @Query("UPDATE templates SET dirty = :dirty, remoteEtag = :etag WHERE id = :id")
    suspend fun markPushed(id: String, dirty: Boolean, etag: String?)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun hardDelete(id: String)
}

@Dao
interface CursorDao {
    @Query("SELECT * FROM sync_cursors WHERE path = :path")
    suspend fun get(path: String): SyncCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cursor: SyncCursorEntity)

    @Query("DELETE FROM sync_cursors WHERE path = :path")
    suspend fun delete(path: String)
}
