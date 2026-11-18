package com.markq.data.local

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
}
