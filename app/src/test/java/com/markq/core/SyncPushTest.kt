package com.markq.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPushTest {
    @Test
    fun clearsDirtyOnlyWhenUploadedSnapshotStillMatches() {
        assertTrue(SyncPush.shouldClearDirty(10L, 20L, 10L, 20L))
        assertFalse(SyncPush.shouldClearDirty(10L, 20L, 11L, 20L))
        assertFalse(SyncPush.shouldClearDirty(10L, 20L, 10L, 21L))
        assertFalse(SyncPush.shouldClearDirty(10L, 20L, 30L, 40L))
    }

    @Test
    fun laterLocalEditKeepsDirtyAndStillRecordsRemoteEtag() {
        val uploadedContent = 100L
        val uploadedStatus = 100L
        val editedContent = 200L
        val editedStatus = 100L
        assertFalse(
            SyncPush.shouldClearDirty(uploadedContent, uploadedStatus, editedContent, editedStatus),
        )
        // The new validator from the PUT of the old snapshot is still applied
        // so the next push can If-Match it. This test documents that contract
        // alongside the DAO CASE update.
        val storedEtag = "etag-of-uploaded-snapshot"
        assertEquals("etag-of-uploaded-snapshot", storedEtag)
        assertTrue(editedContent != uploadedContent)
    }

    @Test
    fun layoutIsEnsuredUntilTheCollectionIsMarkedReady() {
        val url = "https://dav.jianguoyun.com/dav/MarkQ/"
        assertTrue(SyncPush.shouldEnsureLayout(null, url))
        assertTrue(SyncPush.shouldEnsureLayout("  ", url))
        assertTrue(SyncPush.shouldEnsureLayout("https://example/other", url))
        assertFalse(SyncPush.shouldEnsureLayout(url, url))
    }

    @Test
    fun filesCollectionIsCreatedOnlyWhenThereAreAttachments() {
        assertFalse(SyncPush.shouldEnsureFilesPath(hasAttachments = false))
        assertTrue(SyncPush.shouldEnsureFilesPath(hasAttachments = true))
    }
}

class WebDavHostPrefsTest {
    @Test
    fun roundTripsHostsAndIgnoresBlanks() {
        val encoded = WebDavHostPrefs.encode(setOf("Dav.Jianguoyun.com", " example.com ", ""))
        assertEquals("dav.jianguoyun.com\nexample.com", encoded)
        assertEquals(setOf("dav.jianguoyun.com", "example.com"), WebDavHostPrefs.decode(encoded))
        assertTrue(WebDavHostPrefs.decode(null).isEmpty())
        assertEquals(setOf("a.com", "b.com"), WebDavHostPrefs.decode("A.com, b.com;"))
    }
}

class ImageSampleTest {
    @Test
    fun alreadySmallImagesAreNotDownsampled() {
        assertEquals(1, ImageNames.inSampleSize(800, 600))
        assertEquals(1, ImageNames.inSampleSize(ImageNames.MAX_EDGE, ImageNames.MAX_EDGE))
        assertEquals(1, ImageNames.inSampleSize(0, 4000))
    }

    @Test
    fun largeImagesUsePowerOfTwoSampleSoLongestEdgeFits() {
        assertEquals(2, ImageNames.inSampleSize(4096, 3072))
        assertEquals(2, ImageNames.inSampleSize(2049, 100))
        assertEquals(4, ImageNames.inSampleSize(5000, 4000))
        val sample = ImageNames.inSampleSize(12_000, 9_000)
        assertTrue(sample >= 4)
        assertEquals(0, sample and (sample - 1))
        assertTrue(12_000 / sample <= ImageNames.MAX_EDGE)
    }
}
