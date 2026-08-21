package com.baoverung.app.util

/**
 * Triển khai định dạng số an toàn cho iOS
 */
actual fun formatNumber(value: Double, decimals: Int): String {
    // Sử dụng cách chuyển đổi đơn giản để tránh lỗi variadic arguments trong Kotlin/Native
    val power = 10.0.pow(decimals.toDouble())
    val rounded = (value * power).toLong().toDouble() / power
    return rounded.toString()
}

private fun Double.pow(exponent: Double): Double {
    var result = 1.0
    repeat(exponent.toInt()) { result *= this }
    return result
}
