package com.markq.core

import java.util.Locale

data class NominatimAddressParts(
    val houseNumber: String? = null,
    val road: String? = null,
    val pedestrian: String? = null,
    val neighbourhood: String? = null,
    val suburb: String? = null,
    val quarter: String? = null,
    val cityDistrict: String? = null,
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val county: String? = null,
    val state: String? = null,
)

object NominatimPlace {
    /** ~11 m; same cell shares one cached reverse result. */
    fun cacheKey(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.4f,%.4f", latitude, longitude)

    fun format(displayName: String?, parts: NominatimAddressParts?): String? {
        val composed = compose(parts)
        val display = displayName?.trim()?.ifBlank { null }
        val composedCjk = composed != null && hasCjk(composed)
        val displayCjk = display != null && hasCjk(display)
        return when {
            composedCjk -> composed
            displayCjk -> display
            display != null -> display
            else -> composed
        }
    }

    fun compose(parts: NominatimAddressParts?): String? {
        if (parts == null) return null
        val city = first(parts.city, parts.town, parts.village)
        val district = first(parts.cityDistrict, parts.suburb, parts.neighbourhood, parts.quarter, parts.county)
            ?.takeIf { it != city }
        val roadBase = first(parts.road, parts.pedestrian)
        val road = when {
            roadBase == null && parts.houseNumber.isNullOrBlank() -> null
            roadBase == null -> parts.houseNumber?.trim()
            parts.houseNumber.isNullOrBlank() -> roadBase
            hasCjk(roadBase) || hasCjk(parts.houseNumber) -> "$roadBase${parts.houseNumber}"
            else -> "$roadBase ${parts.houseNumber}"
        }
        val chunks = listOfNotNull(
            parts.state?.trim()?.ifBlank { null }?.takeIf { it != city },
            city,
            district,
            road,
        ).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (chunks.isEmpty()) return null
        return if (chunks.any { hasCjk(it) }) chunks.joinToString("") else chunks.joinToString(", ")
    }

    fun hasCjk(value: String): Boolean = value.any { ch ->
        Character.UnicodeScript.of(ch.code) == Character.UnicodeScript.HAN
    }

    private fun first(vararg values: String?): String? {
        for (value in values) {
            val trimmed = value?.trim()?.ifBlank { null } ?: continue
            return trimmed
        }
        return null
    }
}
