package com.simplemobiletools.smsmessenger.extensions

import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.Temporal
import java.util.Date

fun Date.format(pattern: String): String {
    return DateFormat.format(pattern, this).toString()
}

fun Temporal.format(pattern: String): String {
    val instant = when (this) {
        is Instant -> this
        is LocalDate -> atStartOfDay(ZoneId.systemDefault()).toInstant()
        is LocalDateTime -> atZone(ZoneId.systemDefault()).toInstant()
        else -> Instant.from(this)
    }
    return Date.from(instant).format(pattern)
}
