package com.baoverung.app.gis

import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlin.math.pow

actual class MBTilesReader actual constructor(filePath: String) {
    private var db: SQLiteDatabase? = null

    init {
        try {
            val file = File(filePath)
            if (file.exists()) {
                db = SQLiteDatabase.openDatabase(
                    filePath, 
                    null, 
                    SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    actual fun getMaxZoom(): Int {
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

    actual fun getTileBitmap(z: Int, x: Int, y: Int): ImageBitmap? {
        val database = db ?: return null
        return try {
            val tmsY = ((2.0.pow(z.toDouble())).toInt() - 1 - y)
            val cursor = database.rawQuery(
                "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
                arrayOf(z.toString(), x.toString(), tmsY.toString())
            )
            var bitmap: ImageBitmap? = null
            if (cursor.moveToFirst()) {
                val blob = cursor.getBlob(0)
                if (blob != null && blob.isNotEmpty()) {
                    val androidBitmap = BitmapFactory.decodeByteArray(blob, 0, blob.size)
                    bitmap = androidBitmap?.asImageBitmap()
                }
            }
            cursor.close()
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    actual fun getBounds(): DoubleArray? {
        val database = db ?: return null
        return try {
            val cursor = database.rawQuery("SELECT value FROM metadata WHERE name = 'bounds'", null)
            var bounds: DoubleArray? = null
            if (cursor.moveToFirst()) {
                val boundsStr = cursor.getString(0)
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

    actual fun close() {
        try {
            db?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
