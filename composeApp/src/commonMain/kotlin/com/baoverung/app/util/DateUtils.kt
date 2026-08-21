package com.baoverung.app.util

import kotlinx.datetime.*

fun Long.toDateTimeString(): String {
    val instant = Instant.fromEpochMilliseconds(this)
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dt.dayOfMonth.pad()}/${dt.monthNumber.pad()}/${dt.year} ${dt.hour.pad()}:${dt.minute.pad()}:${dt.second.pad()}"
}

private fun Int.pad() = this.toString().padStart(2, '0')
