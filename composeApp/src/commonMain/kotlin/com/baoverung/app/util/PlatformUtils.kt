package com.baoverung.app.util

/**
 * Các tiện ích đặc thù nền tảng cần triển khai (Expect/Actual)
 */
expect fun formatNumber(value: Double, decimals: Int): String

fun Double.format(decimals: Int): String = formatNumber(this, decimals)
