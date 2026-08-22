package com.baoverung.app.util

actual fun drawWatermarkOnPlatform(
    sourcePath: String,
    wgs84: String,
    vn2000: String,
    altitude: Double,
    time: String,
    userName: String,
    centralMeridian: Double,
    accuracy: Float,
    address: String,
    settings: WatermarkHelper.WatermarkSettings
): String {
    // TODO: Implement using CoreGraphics or similar on iOS
    return sourcePath
}
