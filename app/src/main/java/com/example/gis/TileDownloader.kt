package com.baoverung.app.gis

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.math.*

/**
 * Utility for downloading online map areas to MBTiles (SQLite) using standard Android engine.
 */
object TileDownloader {
    private const val TAG = "TileDownloader"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun downloadArea(
        outputFile: File,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        minZoom: Int,
        maxZoom: Int,
        urlTemplate: String,
        onProgress: (Int, Int) -> Unit
    ) {
        val totalTiles = countTiles(minLat, maxLat, minLon, maxLon, minZoom, maxZoom)
        var downloadedCount = 0

        if (outputFile.exists()) outputFile.delete()
        
        val db = try {
            SQLiteDatabase.openOrCreateDatabase(outputFile.absolutePath, null)
        } catch (e: Exception) {
            Log.e(TAG, "Could not create database: ${e.message}")
            return
        }

        try {
            db.execSQL("CREATE TABLE metadata (name text, value text)")
            db.execSQL("CREATE TABLE tiles (zoom_level integer, tile_column integer, tile_row integer, tile_data blob)")
            db.execSQL("CREATE UNIQUE INDEX tile_index ON tiles (zoom_level, tile_column, tile_row)")

            val metadata = mutableListOf(
                "name" to outputFile.nameWithoutExtension,
                "format" to "jpg",
                "bounds" to "$minLon,$minLat,$maxLon,$maxLat",
                "type" to "baselayer",
                "version" to "1.1"
            )
            
            for ((name, value) in metadata) {
                val cv = ContentValues()
                cv.put("name", name)
                cv.put("value", value)
                db.insert("metadata", null, cv)
            }

            for (z in minZoom..maxZoom) {
                val xMin = lonToTileX(minLon, z); val xMax = lonToTileX(maxLon, z)
                val yMin = latToTileY(maxLat, z); val yMax = latToTileY(minLat, z)

                for (x in xMin..xMax) {
                    for (y in yMin..yMax) {
                        kotlinx.coroutines.yield() // Allow cancellation
                        val url = urlTemplate.replace("{z}", z.toString()).replace("{x}", x.toString()).replace("{y}", y.toString())
                        try {
                            val request = Request.Builder()
                                .url(url)
                                .header("User-Agent", "Mozilla/5.0 (Android; vToolSurveyGIS/1.1)")
                                .build()
                                
                            val response = client.newCall(request).execute()
                            if (response.isSuccessful) {
                                val data = response.body?.bytes()
                                if (data != null) {
                                    val cv = ContentValues()
                                    cv.put("zoom_level", z)
                                    cv.put("tile_column", x)
                                    // Convert XYZ tile_row to TMS tile_row for MBTiles spec
                                    cv.put("tile_row", (1 shl z) - 1 - y)
                                    cv.put("tile_data", data)
                                    db.insertWithOnConflict("tiles", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                                }
                            }
                            response.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "Tile download error at $z/$x/$y: ${e.message}")
                        }
                        downloadedCount++
                        onProgress(downloadedCount, totalTiles)
                    }
                }
            }
        } finally {
            db.close()
        }
    }

    private fun countTiles(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, minZ: Int, maxZ: Int): Int {
        var count = 0
        for (z in minZ..maxZ) {
            val xMin = lonToTileX(minLon, z); val xMax = lonToTileX(maxLon, z)
            val yMin = latToTileY(maxLat, z); val yMax = latToTileY(minLat, z)
            count += (xMax - xMin + 1) * (yMax - yMin + 1)
        }
        return count
    }

    private fun lonToTileX(lon: Double, z: Int) = floor((lon + 180) / 360 * (1 shl z)).toInt()
    private fun latToTileY(lat: Double, z: Int) = floor((1 - ln(tan(Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))) + 1 / cos(Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878)))) / PI) / 2 * (1 shl z)).toInt()
}
