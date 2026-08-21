package com.baoverung.app.gis

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.pow

class MBTilesReader(private val mbtilesFile: File) {
    private var db: SQLiteDatabase? = null

    init {
        try {
            if (mbtilesFile.exists()) {
                // Remove journal files that might slow down opening
                val journal = File(mbtilesFile.absolutePath + "-journal")
                if (journal.exists()) journal.delete()
                val wal = File(mbtilesFile.absolutePath + "-wal")
                if (wal.exists()) wal.delete()

                db = SQLiteDatabase.openDatabase(
                    mbtilesFile.absolutePath, 
                    null, 
                    SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                )
                
                // Optimized for forestry: Extremely fast tile retrieval
                db?.execSQL("PRAGMA temp_store = MEMORY")
                db?.execSQL("PRAGMA cache_size = -4000") // 4MB cache
                db?.execSQL("PRAGMA synchronous = OFF")
                db?.execSQL("PRAGMA journal_mode = OFF")
                db?.execSQL("PRAGMA read_uncommitted = TRUE")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Get the maximum zoom level available in this MBTiles file.
     */
    fun getMaxZoom(): Int {
        val database = db ?: return 19
        return try {
            val cursor = database.rawQuery("SELECT MAX(zoom_level) FROM tiles", null)
            var maxZ = 19
            if (cursor.moveToFirst()) {
                maxZ = cursor.getInt(0)
            }
            cursor.close()
            maxZ
        } catch (e: Exception) {
            19
        }
    }

    /**
     * Get tile bitmap for zoom level z, tile x, tile y (Standard XYZ tile coordinates)
     */
    fun getTileBitmap(z: Int, x: Int, y: Int): Bitmap? {
        val database = db ?: return null
        return try {
            // Convert XYZ tile_row to TMS tile_row for MBTiles specification
            val tmsY = ((2.0.pow(z.toDouble())).toInt() - 1 - y)
            val cursor = database.rawQuery(
                "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
                arrayOf(z.toString(), x.toString(), tmsY.toString())
            )
            var bitmap: Bitmap? = null
            if (cursor.moveToFirst()) {
                val blob = cursor.getBlob(0)
                if (blob != null && blob.isNotEmpty()) {
                    bitmap = BitmapFactory.decodeByteArray(blob, 0, blob.size)
                }
            }
            cursor.close()
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get bounding box of tiles in [minLat, minLon, maxLat, maxLon]
     */
    fun getBounds(): DoubleArray? {
        val database = db ?: return null
        return try {
            val cursor = database.rawQuery("SELECT value FROM metadata WHERE name = 'bounds'", null)
            var bounds: DoubleArray? = null
            if (cursor.moveToFirst()) {
                val boundsStr = cursor.getString(0)
                // Format: "minLon,minLat,maxLon,maxLat"
                val parts = boundsStr.split(",").map { it.toDouble() }
                if (parts.size == 4) {
                    bounds = doubleArrayOf(parts[1], parts[0], parts[3], parts[2])
                }
            }
            cursor.close()
            bounds
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun close() {
        try {
            db?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
