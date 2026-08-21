package com.baoverung.app.util

import android.graphics.*
import java.text.SimpleDateFormat
import java.util.*

object WatermarkHelper {
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
        val labelColor: Int = Color.parseColor("#FFD700"), // Use Int color for graphics API
        val labelSize: Float = 11f
    ) : java.io.Serializable

    /**
     * Draws a professional forestry watermark on the captured bitmap.
     * Redesigned according to the new forestry template.
     */
    fun drawWatermark(
        source: Bitmap,
        wgs84: String,
        vn2000: String,
        altitude: Double,
        time: String,
        direction: Float,
        userName: String,
        unitName: String,
        centralMeridian: Double,
        accuracy: Float,
        logo: Bitmap? = null,
        address: String = "",
        settings: WatermarkSettings = WatermarkSettings(),
        maxDimension: Int = 2400
    ): Bitmap {
        val maxDim = maxDimension
        val originalWidth = source.width
        val originalHeight = source.height
        
        val scale = if (originalWidth > maxDim || originalHeight > maxDim) {
            maxDim.toFloat() / Math.max(originalWidth, originalHeight)
        } else 1.0f
        
        val workingBitmap = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(source, (originalWidth * scale).toInt(), (originalHeight * scale).toInt(), true)
        } else {
            source.copy(Bitmap.Config.ARGB_8888, true)
        }

        if (!settings.showInfo) return workingBitmap

        val isLandscape = workingBitmap.width > workingBitmap.height
        val finalBitmap = if (isLandscape) {
            val matrix = Matrix().apply { postRotate(90f) }
            Bitmap.createBitmap(workingBitmap, 0, 0, workingBitmap.width, workingBitmap.height, matrix, true)
        } else workingBitmap
        
        val canvas = Canvas(finalBitmap)
        val width = finalBitmap.width.toFloat()
        val height = finalBitmap.height.toFloat()
        
        val baseTextSize = width / (40f - (settings.labelSize - 11f)) // Adjust based on labelSize
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = baseTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val headerPaint = Paint(paint).apply {
            color = Color.parseColor("#4ade80") // Greenish
            textSize = baseTextSize * 1.1f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        
        val boldPaint = Paint(paint).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = settings.labelColor // Use custom color
        }

        val bgPaint = Paint().apply {
            color = Color.parseColor("#99000000") // Semi-transparent black
        }

        val margin = width / 40f
        val lineSpacing = baseTextSize * 0.6f
        val rowHeight = baseTextSize + lineSpacing
        
        // Count visible lines
        var visibleLines = 0
        if (settings.showOfficer) visibleLines++
        if (settings.showTime) visibleLines++
        if (settings.showAddress) visibleLines++
        if (settings.showWgs84) visibleLines++
        if (settings.showVn2000) visibleLines++
        if (settings.showAccuracy || settings.showAltitude) visibleLines++
        visibleLines++ // Logo footer

        val boxHeight = (rowHeight * (visibleLines + 0.5f)) + margin * 2
        val boxWidth = width * 0.95f

        val startX = if (settings.position.endsWith("RIGHT")) width - boxWidth else 0f
        val startY = if (settings.position.startsWith("TOP")) 0f else height - boxHeight

        // 1. Draw Background
        canvas.drawRect(startX, startY, startX + boxWidth, startY + boxHeight, bgPaint)

        var currentY = startY + margin + baseTextSize

        // 2. Top Header: OFFICER NAME
        if (settings.showOfficer) {
            canvas.drawText(userName.uppercase(), startX + margin, currentY, headerPaint)
            currentY += rowHeight
        }

        // 3. Time row with clock icon
        if (settings.showTime) {
            drawClockIcon(canvas, startX + margin, currentY - baseTextSize/2, baseTextSize * 0.8f)
            canvas.drawText(time, startX + margin + baseTextSize * 1.2f, currentY, paint)
            currentY += rowHeight
        }

        // 4. Separator Line (Centered horizontal line between Time and Address)
        val linePaint = Paint().apply {
            color = Color.WHITE
            strokeWidth = width / 500f
            alpha = 200
        }
        if (settings.showTime && settings.showAddress) {
            val lineWidth = boxWidth * 0.9f
            val lineStartX = startX + (boxWidth - lineWidth) / 2
            canvas.drawLine(lineStartX, currentY - rowHeight * 0.45f, lineStartX + lineWidth, currentY - rowHeight * 0.45f, linePaint)
        }

        // 5. Address Row
        if (settings.showAddress) {
            drawLocationIcon(canvas, startX + margin, currentY - baseTextSize/2, baseTextSize * 0.8f)
            canvas.drawText(address, startX + margin + baseTextSize * 1.2f, currentY, paint)
            currentY += rowHeight
        }

        // 6. WGS84 Row
        if (settings.showWgs84) {
            drawGlobeIcon(canvas, startX + margin, currentY - baseTextSize/2, baseTextSize * 0.8f)
            canvas.drawText(wgs84, startX + margin + baseTextSize * 1.2f, currentY, paint)
            currentY += rowHeight
        }

        // 7. VN2000 Row
        if (settings.showVn2000) {
            drawHashIcon(canvas, startX + margin, currentY - baseTextSize/2, baseTextSize * 0.8f)
            canvas.drawText("$vn2000 KTT ${String.format("%.2f", centralMeridian)}", startX + margin + baseTextSize * 1.2f, currentY, boldPaint)
            currentY += rowHeight
        }

        // 8. Accuracy & Altitude Row
        if (settings.showAccuracy || settings.showAltitude) {
            var iconX = startX + margin
            if (settings.showAccuracy) {
                drawTargetIcon(canvas, iconX, currentY - baseTextSize/2, baseTextSize * 0.8f)
                canvas.drawText("±${String.format("%.1f", accuracy)} m", iconX + baseTextSize * 1.2f, currentY, boldPaint)
                iconX += baseTextSize * 1.5f + boldPaint.measureText("±${String.format("%.1f", accuracy)} m") + baseTextSize
            }
            
            if (settings.showAltitude) {
                drawAltIcon(canvas, iconX, currentY - baseTextSize/2, baseTextSize * 0.8f)
                canvas.drawText("${String.format("%.0f", altitude)} m", iconX + baseTextSize * 1.2f, currentY, boldPaint)
            }
            currentY += rowHeight
        }

        // 9. Logo & Footer
        val logoSize = baseTextSize * 1.8f
        if (logo != null) {
            val scaledLogo = Bitmap.createScaledBitmap(logo, logoSize.toInt(), logoSize.toInt(), true)
            canvas.drawBitmap(scaledLogo, startX + margin, currentY - baseTextSize * 0.8f, null)
            canvas.drawText("Ứng dụng Bảo vệ rừng - Đại Thành", startX + margin + logoSize + baseTextSize * 0.5f, currentY + baseTextSize * 0.4f, paint)
        } else {
            canvas.drawText("Ứng dụng Bảo vệ rừng - Đại Thành", startX + margin, currentY + baseTextSize * 0.4f, paint)
        }

        return finalBitmap
    }



    private fun drawClockIcon(canvas: Canvas, x: Float, y: Float, size: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = size / 8f
        }
        canvas.drawCircle(x + size/2, y, size/2, paint)
        canvas.drawLine(x + size/2, y, x + size/2, y - size/3, paint)
        canvas.drawLine(x + size/2, y, x + size*0.8f, y, paint)
    }

    private fun drawLocationIcon(canvas: Canvas, x: Float, y: Float, size: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3b82f6") // Blueish
            style = Paint.Style.FILL
        }
        val path = Path()
        path.moveTo(x + size/2, y + size/2)
        path.cubicTo(x, y - size/4, x, y - size, x + size/2, y - size)
        path.cubicTo(x + size, y - size, x + size, y - size/4, x + size/2, y + size/2)
        canvas.drawPath(path, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(x + size/2, y - size/2, size/5, paint)
    }

    private fun drawGlobeIcon(canvas: Canvas, x: Float, y: Float, size: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3b82f6")
            style = Paint.Style.STROKE
            strokeWidth = size / 10f
        }
        canvas.drawCircle(x + size/2, y, size/2, paint)
        canvas.drawOval(x + size*0.3f, y - size/2, x + size*0.7f, y + size/2, paint)
        canvas.drawLine(x, y, x + size, y, paint)
    }

    private fun drawHashIcon(canvas: Canvas, x: Float, y: Float, size: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFD700")
            strokeWidth = size / 6f
        }
        canvas.drawLine(x + size*0.2f, y - size/2, x + size*0.2f, y + size/2, paint)
        canvas.drawLine(x + size*0.8f, y - size/2, x + size*0.8f, y + size/2, paint)
        canvas.drawLine(x, y - size*0.2f, x + size, y - size*0.2f, paint)
        canvas.drawLine(x, y + size*0.2f, x + size, y + size*0.2f, paint)
    }

    private fun drawTargetIcon(canvas: Canvas, x: Float, y: Float, size: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFD700")
            style = Paint.Style.STROKE
            strokeWidth = size / 8f
        }
        canvas.drawCircle(x + size/2, y, size/2, paint)
        canvas.drawCircle(x + size/2, y, size/4, paint)
        canvas.drawLine(x + size/2, y - size/2, x + size/2, y + size/2, paint)
        canvas.drawLine(x, y, x + size, y, paint)
    }

    private fun drawAltIcon(canvas: Canvas, x: Float, y: Float, size: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4ade80")
            style = Paint.Style.STROKE
            strokeWidth = size / 6f
        }
        val path = Path()
        path.moveTo(x, y + size/2)
        path.lineTo(x + size/3, y - size/2)
        path.lineTo(x + size*2/3, y)
        path.lineTo(x + size, y - size/3)
        canvas.drawPath(path, paint)
    }
}
