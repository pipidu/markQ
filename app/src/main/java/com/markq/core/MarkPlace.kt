package com.markq.core

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
