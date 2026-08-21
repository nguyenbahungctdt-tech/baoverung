package com.baoverung.app.util

import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Triển khai định dạng số sử dụng thư viện chuẩn Kotlin
 */
actual fun formatNumber(value: Double, decimals: Int): String {
    if (value.isNaN()) return "0"
    val power = 10.0.pow(decimals)
    val rounded = (value * power).roundToLong().toDouble() / power
    return rounded.toString()
}
