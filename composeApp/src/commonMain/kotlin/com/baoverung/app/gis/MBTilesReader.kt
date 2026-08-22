package com.baoverung.app.gis

import androidx.compose.ui.graphics.ImageBitmap

expect class MBTilesReader(filePath: String) {
    fun getMaxZoom(): Int
    fun getTileBitmap(z: Int, x: Int, y: Int): ImageBitmap?
    fun getBounds(): DoubleArray?
    fun close()
}
