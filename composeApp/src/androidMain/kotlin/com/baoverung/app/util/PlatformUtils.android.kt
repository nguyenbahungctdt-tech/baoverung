package com.baoverung.app.util

actual fun formatNumber(value: Double, decimals: Int): String {
    return "%.${decimals}f".format(value)
}
