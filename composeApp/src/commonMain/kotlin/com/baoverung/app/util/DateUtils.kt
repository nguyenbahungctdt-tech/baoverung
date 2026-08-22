package com.baoverung.app.util

import kotlinx.datetime.*
import kotlinx.datetime.format.*

fun Long.toDateTimeString(): String {
    val instant = Instant.fromEpochMilliseconds(this)
    val tz = TimeZone.currentSystemDefault()
    val dt = instant.toLocalDateTime(tz)
    return "${dt.dayOfMonth.toString().padStart(2, '0')}/${dt.monthNumber.toString().padStart(2, '0')}/${dt.year} ${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
}

fun Long.toDateString(): String {
    val instant = Instant.fromEpochMilliseconds(this)
    val tz = TimeZone.currentSystemDefault()
    val dt = instant.toLocalDateTime(tz)
    return "${dt.dayOfMonth.toString().padStart(2, '0')}/${dt.monthNumber.toString().padStart(2, '0')}/${dt.year}"
}

fun parseHexColor(hex: String): androidx.compose.ui.graphics.Color {
    return try {
        val cleaned = hex.removePrefix("#")
        val longVal = cleaned.toLong(16)
        if (cleaned.length == 6) androidx.compose.ui.graphics.Color(0xFF000000 or longVal)
        else androidx.compose.ui.graphics.Color(longVal)
    } catch (e: Exception) {
        androidx.compose.ui.graphics.Color.Gray
    }
}
