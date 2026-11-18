package com.markq.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttachmentLookupTest {
    @Test
    fun indexesOnceAndResolvesEachIdWithoutRescanning() {
        val rows = listOf(
            attachment("a", "entry-1"),
            attachment("b", "entry-1"),
            attachment("c", "entry-1"),
        )
        var reads = 0
        val counting = object : AbstractList<AttachmentEntity>() {
            override val size: Int = rows.size
            override fun get(index: Int): AttachmentEntity {
                reads++
                return rows[index]
            }
        }
        val index = AttachmentLookup.indexById(counting)
        val afterIndex = reads
        assertEquals(3, afterIndex)
        assertEquals("b", index["b"]?.id)
        assertEquals("a", index["a"]?.id)
        assertEquals("c", index["c"]?.id)
        assertNull(index["missing"])
        assertEquals(afterIndex, reads)
    }

    private fun attachment(id: String, entryId: String) = AttachmentEntity(
        id = id,
        entryId = entryId,
        name = "$id.bin",
        mime = "application/octet-stream",
        kind = "file",
        size = 1L,
        sha256 = id.repeat(8),
        localPath = null,
        dirty = false,
        remoteEtag = null,
    )
}
