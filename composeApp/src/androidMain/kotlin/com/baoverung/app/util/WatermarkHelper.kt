package com.baoverung.app.util

import android.graphics.*
import java.io.File
import java.io.FileOutputStream
import java.util.*

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
    val source = BitmapFactory.decodeFile(sourcePath) ?: return sourcePath
    val result = drawAndroidWatermark(source, wgs84, vn2000, altitude, time, userName, centralMeridian, accuracy, address, settings)
    
    val outFile = File(sourcePath.substringBeforeLast(".") + "_wm.jpg")
    FileOutputStream(outFile).use { out ->
        result.compress(Bitmap.CompressFormat.JPEG, 90, out)
    }
    return outFile.absolutePath
}

private fun drawAndroidWatermark(
    source: Bitmap,
    wgs84: String,
    vn2000: String,
    altitude: Double,
    time: String,
    userName: String,
    centralMeridian: Double,
    accuracy: Float,
    address: String,
    settings: WatermarkHelper.WatermarkSettings
): Bitmap {
    val workingBitmap = source.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(workingBitmap)
    val width = workingBitmap.width.toFloat()
    val height = workingBitmap.height.toFloat()
    
    val baseTextSize = width / 40f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = baseTextSize
    }
    
    val bgPaint = Paint().apply { color = Color.parseColor("#99000000") }
    
    val margin = width / 40f
    val rowHeight = baseTextSize * 1.5f
    val boxHeight = rowHeight * 7 + margin * 2
    
    canvas.drawRect(0f, height - boxHeight, width * 0.9f, height, bgPaint)
    
    var currentY = height - boxHeight + margin + baseTextSize
    canvas.drawText(userName.uppercase(), margin, currentY, paint)
    currentY += rowHeight
    canvas.drawText(time, margin, currentY, paint)
    currentY += rowHeight
    canvas.drawText(address, margin, currentY, paint)
    currentY += rowHeight
    canvas.drawText(wgs84, margin, currentY, paint)
    currentY += rowHeight
    canvas.drawText(vn2000, margin, currentY, paint)
    
    return workingBitmap
}
