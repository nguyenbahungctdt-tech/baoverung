package com.baoverung.app.util

import kotlinx.serialization.Serializable

/**
 * Platform-independent Watermark Helper.
 */
object WatermarkHelper {
    @Serializable
    data class WatermarkSettings(
        val showInfo: Boolean = true,
        val position: String = "BOTTOM_LEFT",
        val showOfficer: Boolean = true,
        val showTime: Boolean = true,
        val showAddress: Boolean = true,
        val showWgs84: Boolean = true,
        val showVn2000: Boolean = true,
        val showAltitude: Boolean = true,
        val showAccuracy: Boolean = true,
        val labelColor: Int = -26317, // #FFD700
        val labelSize: Float = 11f
    )
}

expect fun drawWatermarkOnPlatform(
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
): String
