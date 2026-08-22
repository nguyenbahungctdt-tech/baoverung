package com.baoverung.app.gis

import androidx.compose.ui.graphics.ImageBitmap

actual class MBTilesReader actual constructor(filePath: String) {
    actual fun getMaxZoom(): Int = 19
    actual fun getTileBitmap(z: Int, x: Int, y: Int): ImageBitmap? = null
    actual fun getBounds(): DoubleArray? = null
    actual fun close() {}
}
