package com.markq.core

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object MarkPlace {
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

    fun geoUri(latitude: Double, longitude: Double, placeName: String?): String {
        val coords = String.format(Locale.US, "%.6f,%.6f", latitude, longitude)
        val label = placeName?.trim().orEmpty()
        val q = if (label.isEmpty()) {
            coords
        } else {
            val encoded = URLEncoder.encode(label, StandardCharsets.UTF_8.name()).replace("+", "%20")
            "$coords($encoded)"
        }
        return "geo:$coords?q=$q"
    }
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
