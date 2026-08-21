package com.baoverung.app.gis

import android.graphics.BitmapFactory
import com.baoverung.app.data.model.GisFeature
import com.baoverung.app.data.model.GisShapeType
import com.baoverung.app.data.model.GpsPoint
import java.io.File

/**
 * Basic Raster Parser for Forestry.
 * Currently handles JPG/PNG with world files (.jgw, .pgw).
 * GeoTIFF support is limited to external world files for now.
 */
object RasterParser {

    data class RasterInfo(
        val bitmapPath: String,
        val topLeft: GpsPoint,
        val bottomRight: GpsPoint
    )

    fun getRasterInfo(file: File): RasterInfo? {
        val dbFile = File(file.parent, file.nameWithoutExtension + ".db")
        if (dbFile.exists() && dbFile.lastModified() >= file.lastModified()) {
            try {
                android.database.sqlite.SQLiteDatabase.openDatabase(dbFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
                    val cursor = db.rawQuery("SELECT * FROM metadata", null)
                    if (cursor.moveToFirst()) {
                        val lat1 = cursor.getDouble(cursor.getColumnIndexOrThrow("latTopLeft"))
                        val lon1 = cursor.getDouble(cursor.getColumnIndexOrThrow("lonTopLeft"))
                        val lat2 = cursor.getDouble(cursor.getColumnIndexOrThrow("latBottomRight"))
                        val lon2 = cursor.getDouble(cursor.getColumnIndexOrThrow("lonBottomRight"))
                        cursor.close()
                        return RasterInfo(file.absolutePath, GpsPoint(lat1, lon1), GpsPoint(lat2, lon2))
                    }
                    cursor.close()
                }
            } catch (e: Exception) {}
        }

        val ext = file.extension.lowercase()
        val worldFileExt = when (ext) {
            "jpg", "jpeg" -> "jgw"
            "png" -> "pgw"
            "tif", "tiff" -> "tfw"
            else -> return null
        }
        
        val worldFile = File(file.parent, file.nameWithoutExtension + "." + worldFileExt)
        if (!worldFile.exists()) return null
        
        try {
            val lines = worldFile.readLines()
            if (lines.size >= 6) {
                val pixelSizeX = lines[0].trim().toDouble()
                val rotationY = lines[1].trim().toDouble()
                val rotationX = lines[2].trim().toDouble()
                val pixelSizeY = lines[3].trim().toDouble()
                val worldX = lines[4].trim().toDouble()
                val worldY = lines[5].trim().toDouble()
                
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, options)
                val width = options.outWidth
                val height = options.outHeight
                
                // Assuming VN2000 if values are large
                val (latTopLeft, lonTopLeft) = if (worldX > 1000) 
                    CoordinateSystemConverter.vn2000ToWgs84(worldX, worldY, 107.75, 3) 
                    else Pair(worldY, worldX)
                
                val worldXBottomRight = worldX + pixelSizeX * width
                val worldYBottomRight = worldY + pixelSizeY * height
                
                val (latBottomRight, lonBottomRight) = if (worldXBottomRight > 1000)
                    CoordinateSystemConverter.vn2000ToWgs84(worldXBottomRight, worldYBottomRight, 107.75, 3)
                    else Pair(worldYBottomRight, worldXBottomRight)
                
                // Save to .db cache
                try {
                    android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile.absolutePath, null).use { db ->
                        db.execSQL("CREATE TABLE IF NOT EXISTS metadata (latTopLeft DOUBLE, lonTopLeft DOUBLE, latBottomRight DOUBLE, lonBottomRight DOUBLE)")
                        db.execSQL("DELETE FROM metadata")
                        db.execSQL("INSERT INTO metadata VALUES (?, ?, ?, ?)", arrayOf(latTopLeft, lonTopLeft, latBottomRight, lonBottomRight))
                    }
                } catch (e: Exception) { e.printStackTrace() }
                    
                return RasterInfo(file.absolutePath, GpsPoint(latTopLeft, lonTopLeft), GpsPoint(latBottomRight, lonBottomRight))
            }
        } catch (e: Exception) { e.printStackTrace() }
        
        return null
    }
}
