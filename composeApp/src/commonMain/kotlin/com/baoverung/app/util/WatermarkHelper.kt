package com.baoverung.app.util

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
    val labelColorHex: String = "#FFD700",
    val labelSize: Float = 11f
)
