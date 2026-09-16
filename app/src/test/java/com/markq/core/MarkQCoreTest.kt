package com.markq.core

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareCodeTest {
    @Test
    fun roundTripUrlOnly() {
        val code = ShareCode.encode("https://dav.example.com/markq")
        assertTrue(code.startsWith(ShareCode.PREFIX))
        val payload = ShareCode.decode(code)
        assertEquals("https://dav.example.com/markq", payload.url)
        assertEquals("", payload.username)
        assertEquals("", payload.password)
    }

    @Test
    fun roundTripWithCredentials() {
        val code = ShareCode.encode("https://cloud.example/remote.php/dav/files/u/Q", "user", "s3cret")
        val payload = ShareCode.decode("  $code  ")
        assertEquals("https://cloud.example/remote.php/dav/files/u/Q", payload.url)
        assertEquals("user", payload.username)
        assertEquals("s3cret", payload.password)
    }

    @Test
    fun roundTripWithDirectory() {
        val code = ShareCode.encode(
            url = "https://dav.jianguoyun.com/dav/",
            username = "a@b.com",
            password = "app-pass",
            remoteDir = "MarkQ",
        )
        val payload = ShareCode.decode(code)
        assertEquals("https://dav.jianguoyun.com/dav/", payload.url)
        assertEquals("MarkQ", payload.remoteDir)
        assertEquals("a@b.com", payload.username)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownPrefix() {
        ShareCode.decode("ABC_not-a-code")
    }
}

class NutstoreDavTest {
    @Test
    fun joinsDefaultFolder() {
        assertEquals(
            "https://dav.jianguoyun.com/dav/MarkQ/",
            NutstoreDav.collectionUrl(NutstoreDav.DEFAULT_SERVER, NutstoreDav.DEFAULT_DIR),
        )
    }

    @Test
    fun emptyDirKeepsServerRoot() {
        assertEquals(
            "https://dav.jianguoyun.com/dav/",
            NutstoreDav.collectionUrl(NutstoreDav.DEFAULT_SERVER, ""),
        )
    }
}

class EntryMergeTest {
    private fun base() = MarkEntry(
        id = "e1",
        occurredAt = java.time.Instant.parse("2026-01-01T10:00:00Z"),
        createdAt = java.time.Instant.parse("2026-01-01T10:00:00Z"),
        contentUpdatedAt = java.time.Instant.parse("2026-01-01T10:00:00Z"),
        statusUpdatedAt = java.time.Instant.parse("2026-01-01T10:00:00Z"),
        text = "buy milk",
        createdBy = "ann",
        updatedBy = "ann",
    )

    @Test
    fun mergesContentAndStatusIndependently() {
        val local = base().copy(
            text = "buy oat milk",
            contentUpdatedAt = java.time.Instant.parse("2026-01-01T12:00:00Z"),
            updatedBy = "ann",
        )
        val remote = base().copy(
            completed = true,
            completedBy = "bob",
            completedAt = java.time.Instant.parse("2026-01-01T11:00:00Z"),
            statusUpdatedAt = java.time.Instant.parse("2026-01-01T11:00:00Z"),
            updatedBy = "bob",
        )
        val merged = EntryMerge.merge(local, remote)
        assertEquals("buy oat milk", merged.text)
        assertTrue(merged.completed)
        assertEquals("bob", merged.completedBy)
        assertEquals("ann", merged.createdBy)
    }

    @Test
    fun newerDeleteWinsStatus() {
        val local = base().copy(
            completed = true,
            statusUpdatedAt = java.time.Instant.parse("2026-01-01T11:00:00Z"),
        )
        val remote = base().copy(
            deleted = true,
            deletedBy = "cam",
            deletedAt = java.time.Instant.parse("2026-01-01T12:00:00Z"),
            statusUpdatedAt = java.time.Instant.parse("2026-01-01T12:00:00Z"),
            updatedBy = "cam",
        )
        val merged = EntryMerge.merge(local, remote)
        assertTrue(merged.deleted)
        assertEquals("cam", merged.deletedBy)
        assertFalse(merged.completed)
    }
}

class SemVerTest {
    @Test
    fun detectsNewerRelease() {
        assertTrue(SemVer.isNewer("v1.0.1", "1.0.0"))
        assertFalse(SemVer.isNewer("1.0.0", "1.0.0"))
        assertFalse(SemVer.isNewer("1.0.0", "1.0.1"))
    }
}

class WebDavCursorTest {
    @Test
    fun skipsMatchingEtag() {
        val url = "https://example.com/entries/a.json".toHttpUrl()
        val remote = com.markq.data.remote.DavResource(
            href = "/entries/a.json",
            url = url,
            isCollection = false,
            etag = "abc",
            lastModified = "Wed, 16 Sep 2026 09:00:00 GMT",
        )
        assertFalse(com.markq.data.remote.WebDavClient.needsDownload("abc", null, remote))
        assertTrue(com.markq.data.remote.WebDavClient.needsDownload("old", null, remote))
    }

    @Test
    fun normalizesQuotedEtag() {
        assertEquals("abc", com.markq.data.remote.WebDavClient.normalizeEtag("W/\"abc\""))
        assertEquals("abc", com.markq.data.remote.WebDavClient.normalizeEtag("\"abc\""))
    }
}

class WebDavMkcolTest {
    private val nutstore = "https://dav.jianguoyun.com/dav/"

    @Test
    fun skipsDavRootAndCreatesSaveFolder() {
        val urls = com.markq.data.remote.WebDavClient.collectionsToMkcol(
            nutstore,
            "https://dav.jianguoyun.com/dav/MarkQ/".toHttpUrl(),
        )
        assertEquals(
            listOf(listOf("dav", "MarkQ")),
            urls.map { it.pathSegments.filter { segment -> segment.isNotEmpty() } },
        )
        assertTrue(urls.none { com.markq.data.remote.WebDavClient.shouldSkipMkcol(nutstore, it) })
    }

    @Test
    fun emptySaveDirDoesNotMkcolDavRoot() {
        val urls = com.markq.data.remote.WebDavClient.collectionsToMkcol(
            nutstore,
            "https://dav.jianguoyun.com/dav/".toHttpUrl(),
        )
        assertTrue(urls.isEmpty())
    }

    @Test
    fun nestedEntriesAfterSaveFolder() {
        val urls = com.markq.data.remote.WebDavClient.collectionsToMkcol(
            nutstore,
            "https://dav.jianguoyun.com/dav/MarkQ/entries/".toHttpUrl(),
        )
        assertEquals(
            listOf(
                listOf("dav", "MarkQ"),
                listOf("dav", "MarkQ", "entries"),
            ),
            urls.map { it.pathSegments.filter { segment -> segment.isNotEmpty() } },
        )
    }

    @Test
    fun skipsDavRootAndSlash() {
        assertTrue(
            com.markq.data.remote.WebDavClient.shouldSkipMkcol(
                nutstore,
                "https://dav.jianguoyun.com/dav/".toHttpUrl(),
            ),
        )
        assertTrue(
            com.markq.data.remote.WebDavClient.shouldSkipMkcol(
                nutstore,
                "https://dav.jianguoyun.com/".toHttpUrl(),
            ),
        )
        assertFalse(
            com.markq.data.remote.WebDavClient.shouldSkipMkcol(
                nutstore,
                "https://dav.jianguoyun.com/dav/MarkQ/".toHttpUrl(),
            ),
        )
        assertFalse(
            com.markq.data.remote.WebDavClient.shouldSkipMkcol(
                nutstore,
                "https://dav.jianguoyun.com/dav/MarkQ/entries/".toHttpUrl(),
            ),
        )
    }
}

class UpdateProgressTest {
    @Test
    fun computesPercentFromBytes() {
        val state = com.markq.data.UpdateUiState(
            downloading = true,
            downloadBytes = 25L,
            downloadTotal = 100L,
            downloadVersion = "1.0.3",
        )
        assertEquals(25, state.progressPercent)
        assertTrue(state.showUpdateDialog)
        assertEquals("1.0.3", state.versionLabel)
    }
}

class ApkFileTest {
    @Test
    fun acceptsZipMagic() {
        val file = java.io.File.createTempFile("markq", ".apk")
        file.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00))
        assertTrue(com.markq.data.UpdateChecker.isApkFile(file))
        file.delete()
    }

    @Test
    fun rejectsHtml() {
        val file = java.io.File.createTempFile("markq", ".apk")
        file.writeText("<html>not an apk</html>")
        assertFalse(com.markq.data.UpdateChecker.isApkFile(file))
        file.delete()
    }
}

class SyncErrorsTest {
    @Test
    fun hidesPreconditionFailed() {
        assertTrue(SyncErrors.isSilent("Precondition failed"))
        assertTrue(SyncErrors.isSilent("PUT /dav/MarkQ/entries/a.json failed (412)"))
        assertFalse(SyncErrors.isSilent("PUT /dav/MarkQ/entries/a.json failed (500)"))
        assertFalse(SyncErrors.isSilent(null as String?))
    }
}
