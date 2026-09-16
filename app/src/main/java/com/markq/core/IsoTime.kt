package com.markq.core

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object IsoTime {
    private val fmt = DateTimeFormatter.ISO_INSTANT

    fun format(instant: Instant): String = fmt.format(instant)

    fun parse(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            try {
                java.time.OffsetDateTime.parse(value).toInstant()
            } catch (_: DateTimeParseException) {
                try {
                    java.time.LocalDateTime.parse(value).toInstant(ZoneOffset.UTC)
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }

    fun parseRequired(value: String): Instant =
        parse(value) ?: error("Invalid timestamp: $value")
}
