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
    fun colorFollowsContentTimestamp() {
        val local = base().copy(
            color = "#F6C945",
            contentUpdatedAt = java.time.Instant.parse("2026-01-01T12:00:00Z"),
        )
        val remote = base().copy(
            color = "#5B8DEF",
            contentUpdatedAt = java.time.Instant.parse("2026-01-01T11:00:00Z"),
        )
        assertEquals("#F6C945", EntryMerge.merge(local, remote).color)
    }

    @Test
    fun tagsFollowContentTimestamp() {
        val local = base().copy(
            tags = listOf("work"),
            contentUpdatedAt = java.time.Instant.parse("2026-01-01T12:00:00Z"),
        )
        val remote = base().copy(
            tags = listOf("home"),
            contentUpdatedAt = java.time.Instant.parse("2026-01-01T11:00:00Z"),
        )
        assertEquals(listOf("work"), EntryMerge.merge(local, remote).tags)
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

    @Test
    fun locationFollowsContentTimestamp() {
        val local = base().copy(
            latitude = 31.23,
            longitude = 121.47,
            placeName = "上海",
            contentUpdatedAt = java.time.Instant.parse("2026-01-01T12:00:00Z"),
        )
        val remote = base().copy(
            latitude = 39.90,
            longitude = 116.40,
            placeName = "北京",
            contentUpdatedAt = java.time.Instant.parse("2026-01-01T11:00:00Z"),
        )
        val merged = EntryMerge.merge(local, remote)
        assertEquals(31.23, merged.latitude)
        assertEquals(121.47, merged.longitude)
        assertEquals("上海", merged.placeName)
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

class ByteFormatTest {
    @Test
    fun formatsSpeed() {
        assertEquals("500 B/s", ByteFormat.speed(500))
        assertEquals("12 KB/s", ByteFormat.speed(12_000))
        assertEquals("1.5 MB/s", ByteFormat.speed(1_500_000))
    }
}

class MarkColorTest {
    @Test
    fun parsesAndNormalizesHex() {
        assertEquals(0xFFF6C945L, MarkColor.parseArgb("#F6C945"))
        assertEquals("#5B8DEF", MarkColor.normalize("5b8def"))
        assertEquals(null, MarkColor.parseArgb("not-a-color"))
    }

    @Test
    fun lightVersusDarkInk() {
        assertTrue(MarkColor.isLight("#FFFFFF"))
        assertTrue(MarkColor.isLight(UiThemeDefaults.BACKGROUND))
        assertTrue(MarkColor.isLight("#F6C945"))
        assertFalse(MarkColor.isLight("#000000"))
        assertFalse(MarkColor.isLight(UiThemeDefaults.BAR))
        assertFalse(MarkColor.isLight("#0B6E4F"))
    }

    @Test
    fun themeDefaults() {
        assertEquals("#0B6E4F", UiThemeDefaults.BAR)
        assertEquals("#FFFFFF", UiThemeDefaults.BACKGROUND)
        assertEquals("#FFFFFF", UiThemeDefaults.FAB)
        assertEquals("#0B6E4F", UiThemeDefaults.CARD_BORDER)
    }
}

class MarkTagsTest {
    @Test
    fun normalizesAndDedupes() {
        assertEquals(
            listOf("Work", "home"),
            MarkTags.normalize(listOf(" Work ", "work", "home", "", "  ")),
        )
        assertEquals("Work\u001Fhome", MarkTags.encode(listOf("Work", "home")))
        assertEquals(listOf("Work", "home"), MarkTags.decode("Work\u001Fhome"))
        assertTrue(MarkTags.contains("Work\u001Fhome", "work"))
        assertFalse(MarkTags.contains("Work\u001Fhome", "other"))
    }
}

class ImageNamesTest {
    @Test
    fun webpFileNameKeepsBase() {
        assertEquals("photo.webp", ImageNames.webpFileName("photo.jpg"))
        assertEquals("photo.webp", ImageNames.webpFileName("photo"))
        assertEquals("image.webp", ImageNames.webpFileName("   "))
        assertEquals(60, ImageNames.WEBP_QUALITY)
        assertEquals("image/webp", ImageNames.WEBP_MIME)
    }
}

class TemplateMergeTest {
    private fun base() = MarkTemplate(
        id = "t1",
        name = "standup",
        text = "daily notes",
        color = "#5B8DEF",
        tags = listOf("work"),
        createdAt = java.time.Instant.parse("2026-01-01T10:00:00Z"),
        contentUpdatedAt = java.time.Instant.parse("2026-01-01T10:00:00Z"),
        statusUpdatedAt = java.time.Instant.parse("2026-01-01T10:00:00Z"),
        createdBy = "ann",
        updatedBy = "ann",
    )

    @Test
    fun contentFollowsNewerTimestamp() {
        val local = base().copy(
            text = "local text",
            tags = listOf("home"),
            contentUpdatedAt = java.time.Instant.parse("2026-01-01T12:00:00Z"),
        )
        val remote = base().copy(
            text = "remote text",
            tags = listOf("work"),
            contentUpdatedAt = java.time.Instant.parse("2026-01-01T11:00:00Z"),
        )
        val merged = TemplateMerge.merge(local, remote)
        assertEquals("local text", merged.text)
        assertEquals(listOf("home"), merged.tags)
        assertEquals("standup", merged.name)
    }

    @Test
    fun deleteFollowsStatusTimestamp() {
        val local = base().copy(
            text = "keep",
            contentUpdatedAt = java.time.Instant.parse("2026-01-01T12:00:00Z"),
        )
        val remote = base().copy(
            deleted = true,
            deletedBy = "bob",
            deletedAt = java.time.Instant.parse("2026-01-01T13:00:00Z"),
            statusUpdatedAt = java.time.Instant.parse("2026-01-01T13:00:00Z"),
            updatedBy = "bob",
        )
        val merged = TemplateMerge.merge(local, remote)
        assertTrue(merged.deleted)
        assertEquals("bob", merged.deletedBy)
        assertEquals("keep", merged.text)
    }
}

class MarkPlaceTest {
    @Test
    fun prefersReadableName() {
        assertEquals("上海", MarkPlace.format(31.2304, 121.4737, "上海"))
        assertEquals("31.23040, 121.47370", MarkPlace.format(31.2304, 121.4737, "  "))
        assertTrue(MarkPlace.hasFix(31.23, 121.47))
        assertFalse(MarkPlace.hasFix(null, 121.47))
        assertEquals(null, MarkPlace.formatOrNull(null, null, "上海"))
    }

    @Test
    fun mapUrisUseLatLngNotEncodedAddressQuery() {
        val amap = MarkPlace.amapRouteUri(31.2304, 121.4737, "上海")
        assertTrue(amap.startsWith("amapuri://route/plan/"))
        assertTrue(amap.contains("dlat=31.230400"))
        assertTrue(amap.contains("dlon=121.473700"))
        assertTrue(amap.contains("dev=1"))
        assertFalse(amap.contains("%25"))
        assertFalse(amap.contains("q="))
        val encodedOnce = MarkPlace.amapRouteUri(31.2304, 121.4737, "上海")
        assertTrue(encodedOnce.contains("dname=%E4%B8%8A%E6%B5%B7") || encodedOnce.contains("dname=上海"))

        val navi = MarkPlace.amapNaviUri(31.2304, 121.4737, "上海市黄浦区南京东路")
        assertTrue(navi.startsWith("androidamap://navi?"))
        assertTrue(navi.contains("lat=31.230400"))
        assertTrue(navi.contains("lon=121.473700"))

        val baidu = MarkPlace.baiduMarkerUri(31.2304, 121.4737, "上海")
        assertTrue(baidu.startsWith("baidumap://map/marker?"))
        assertTrue(baidu.contains("location=31.230400,121.473700"))
        assertTrue(baidu.contains("coord_type=wgs84"))
        assertTrue(baidu.contains("title=%E4%B8%8A%E6%B5%B7") || baidu.contains("title=上海"))
        assertFalse(baidu.contains("navi"))
        assertFalse(baidu.contains("query="))
        assertFalse(baidu.contains("%25"))
        assertEquals(
            "baidumap://map/geocoder?location=31.230400,121.473700&coord_type=wgs84&src=andr.markq.app",
            MarkPlace.baiduGeocoderUri(31.2304, 121.4737),
        )

        assertEquals("位置", MarkPlace.shortPoiName(null))
        assertEquals("位置", MarkPlace.shortPoiName("  "))
        assertEquals("East Nanjing Road", MarkPlace.shortPoiName("East Nanjing Road, Huangpu, Shanghai"))
    }
}

class NominatimPlaceTest {
    @Test
    fun cacheKeyRoundsToFourDecimals() {
        assertEquals("31.2304,121.4737", NominatimPlace.cacheKey(31.23041, 121.47370))
        assertEquals(
            NominatimPlace.cacheKey(31.23040, 121.47371),
            NominatimPlace.cacheKey(31.23044, 121.47370),
        )
    }

    @Test
    fun prefersChineseComposedAddress() {
        val parts = NominatimAddressParts(
            road = "南京东路",
            suburb = "黄浦区",
            city = "上海市",
            state = "上海市",
        )
        assertEquals(
            "上海市黄浦区南京东路",
            NominatimPlace.format("East Nanjing Road, Huangpu, Shanghai, China", parts),
        )
    }

    @Test
    fun fallsBackToDisplayName() {
        assertEquals(
            " Trafalgar Square, London, UK ".trim(),
            NominatimPlace.format(" Trafalgar Square, London, UK ", NominatimAddressParts()),
        )
        assertEquals(
            "London",
            NominatimPlace.format(null, NominatimAddressParts(city = "London")),
        )
    }
}

class MarkListVisibilityTest {
    @Test
    fun completedOnlyInCompletedCategory() {
        assertFalse(MarkListVisibility.include(true, listOf("work"), MarkListFilter.All))
        assertTrue(MarkListVisibility.include(true, listOf("work"), MarkListFilter.Completed))
        assertFalse(MarkListVisibility.include(true, listOf("work"), MarkListFilter.Tag("work")))
        assertTrue(MarkListVisibility.include(false, listOf("work"), MarkListFilter.All))
        assertFalse(MarkListVisibility.include(false, listOf("work"), MarkListFilter.Completed))
        assertTrue(MarkListVisibility.include(false, listOf("work"), MarkListFilter.Tag("work")))
        assertFalse(MarkListVisibility.include(false, listOf("home"), MarkListFilter.Tag("work")))
    }
}
