package com.baoverung.app.util

import platform.Foundation.NSString
import platform.Foundation.stringWithFormat

actual fun formatNumber(value: Double, decimals: Int): String {
    return NSString.stringWithFormat("%.${decimals}f", value)
}
