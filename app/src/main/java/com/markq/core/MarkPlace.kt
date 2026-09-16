package com.markq.core

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object MarkPlace {
    const val AMAP_PACKAGE = "com.autonavi.minimap"
    const val BAIDU_PACKAGE = "com.baidu.BaiduMap"
    const val SOURCE_APP = "MarkQ"
    const val BAIDU_SRC = "andr.markq.app"

    fun hasFix(latitude: Double?, longitude: Double?): Boolean =
        latitude != null && longitude != null &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    fun format(latitude: Double, longitude: Double, placeName: String?): String {
        val name = placeName?.trim().orEmpty()
        if (name.isNotEmpty()) return name
        return String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
    }

    fun formatOrNull(latitude: Double?, longitude: Double?, placeName: String?): String? {
        if (!hasFix(latitude, longitude)) return null
        return format(latitude!!, longitude!!, placeName)
    }

    fun coord(value: Double): String = String.format(Locale.US, "%.6f", value)

    /** Short pin label only — never used as a geocode search string. */
    fun shortPoiName(placeName: String?): String {
        val t = placeName?.trim().orEmpty()
        if (t.isEmpty()) return "位置"
        val first = t.substringBefore(',').trim()
        val base = first.ifEmpty { t }
        return if (base.length <= 32) base else base.take(32)
    }

    fun amapRouteUri(latitude: Double, longitude: Double, placeName: String?): String {
        val dname = encodeOnce(shortPoiName(placeName))
        return "amapuri://route/plan/?sourceApplication=$SOURCE_APP" +
            "&dlat=${coord(latitude)}&dlon=${coord(longitude)}" +
            "&dname=$dname&dev=1&t=0"
    }

    fun amapNaviUri(latitude: Double, longitude: Double, placeName: String?): String {
        val poi = encodeOnce(shortPoiName(placeName))
        return "androidamap://navi?sourceApplication=$SOURCE_APP" +
            "&poiname=$poi&lat=${coord(latitude)}&lon=${coord(longitude)}&dev=1&style=2"
    }

    fun amapViewMapUri(latitude: Double, longitude: Double, placeName: String?): String {
        val poi = encodeOnce(shortPoiName(placeName))
        return "androidamap://viewMap?sourceApplication=$SOURCE_APP" +
            "&poiname=$poi&lat=${coord(latitude)}&lon=${coord(longitude)}&dev=1"
    }

    fun amapUris(latitude: Double, longitude: Double, placeName: String?): List<String> = listOf(
        amapViewMapUri(latitude, longitude, placeName),
        amapRouteUri(latitude, longitude, placeName),
        amapNaviUri(latitude, longitude, placeName),
    )

    fun baiduMarkerUri(latitude: Double, longitude: Double, placeName: String?): String {
        val title = encodeOnce(shortPoiName(placeName))
        return "baidumap://map/marker?location=${coord(latitude)},${coord(longitude)}" +
            "&coord_type=wgs84&title=$title&src=$BAIDU_SRC"
    }

    fun baiduGeocoderUri(latitude: Double, longitude: Double): String =
        "baidumap://map/geocoder?location=${coord(latitude)},${coord(longitude)}" +
            "&coord_type=wgs84&src=$BAIDU_SRC"

    fun baiduUris(latitude: Double, longitude: Double, placeName: String?): List<String> = listOf(
        baiduMarkerUri(latitude, longitude, placeName),
        baiduGeocoderUri(latitude, longitude),
    )

    private fun encodeOnce(plain: String): String =
        URLEncoder.encode(plain, StandardCharsets.UTF_8.name()).replace("+", "%20")
}

sealed class MarkListFilter {
    data object All : MarkListFilter()
    data object Completed : MarkListFilter()
    data class Tag(val name: String) : MarkListFilter()
}

object MarkListVisibility {
    fun include(completed: Boolean, tags: List<String>, filter: MarkListFilter): Boolean =
        when (filter) {
            MarkListFilter.All -> !completed
            MarkListFilter.Completed -> completed
            is MarkListFilter.Tag -> !completed && MarkTags.contains(
                MarkTags.encode(tags),
                filter.name,
            )
        }
}
