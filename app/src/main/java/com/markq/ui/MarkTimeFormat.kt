package com.markq.ui

import com.markq.LocaleHelper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val dateFmt: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(LocaleHelper.appLocale)
        .withZone(ZoneId.systemDefault())

/** Local medium datetime, same style as a mark's occurred-at time. */
fun formatWhen(epochMs: Long): String = dateFmt.format(Instant.ofEpochMilli(epochMs))
