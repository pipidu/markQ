package com.markq.data.local

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheJanitorPolicyTest {
    @Test
    fun fullPruneOnlyWhenAttachmentStoreExceedsCap() {
        assertFalse(CacheJanitor.shouldPruneAttachments(0L))
        assertFalse(CacheJanitor.shouldPruneAttachments(CacheJanitor.MAX_ATTACH_BYTES))
        assertTrue(CacheJanitor.shouldPruneAttachments(CacheJanitor.MAX_ATTACH_BYTES + 1))
    }

    @Test
    fun textOnlySavesDoNotNeedASizeCheck() {
        val addedFiles = false
        val wouldPrune = addedFiles && CacheJanitor.shouldPruneAttachments(CacheJanitor.MAX_ATTACH_BYTES * 2)
        assertFalse(wouldPrune)
    }

    @Test
    fun overLimitPruneDoesNotDeleteAJustWrittenBlobMissingFromTheDatabase() {
        val dir = File.createTempFile("markq-prune", null).apply {
            delete()
            mkdirs()
        }
        try {
            val alreadyStored = File(dir, "aaa111").apply { writeText("old") }
            val justWritten = File(dir, "bbb222").apply { writeText("new") }
            val orphan = File(dir, "ccc333").apply { writeText("junk") }
            val keep = CacheJanitor.keepHashes(
                dbHashes = listOf(alreadyStored.name),
                pendingHashes = listOf(justWritten.name),
            )
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.name !in keep) file.delete()
            }
            assertTrue(justWritten.exists())
            assertTrue(alreadyStored.exists())
            assertFalse(orphan.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
