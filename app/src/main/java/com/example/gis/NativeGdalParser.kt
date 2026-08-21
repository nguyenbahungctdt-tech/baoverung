package com.baoverung.app.gis

import android.util.Log
import com.baoverung.app.data.model.GisFeature
import com.baoverung.app.data.model.GpsPoint
import com.baoverung.app.data.model.GisShapeType

/**
 * JNI Bridge for GDAL/OGR MapInfo parsing.
 */
object NativeGdalParser {
    private const val TAG = "NativeGdalParser"

    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("gdal")
            System.loadLibrary("gdal_reader")
            isNativeLoaded = true
            Log.d(TAG, "Native GDAL libraries loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            isNativeLoaded = false
            Log.w(TAG, "Native libraries not found. Falling back to Kotlin parser.")
        }
    }

    fun isAvailable() = isNativeLoaded

    /**
     * Reads a MapInfo .TAB file using native GDAL/OGR.
     * Returns a list of features or null if failed.
     */
    fun readMapInfoSafe(path: String): List<GisFeature>? {
        if (!isNativeLoaded) return null
        return try {
            readMapInfo(path)
        } catch (e: Exception) {
            Log.e(TAG, "Native read error: ${e.message}")
            null
        }
    }

    private external fun readMapInfo(path: String): List<GisFeature>?

    /**
     * Helper called from JNI to create GisFeature objects.
     * This avoids complex JNI constructor calls in C++.
     */
    @JvmStatic
    fun createFeature(
        id: String,
        layerId: Long,
        type: Int, // 0: Point, 1: Line, 2: Polygon
        lats: DoubleArray,
        lons: DoubleArray,
        attributeKeys: Array<String>,
        attributeValues: Array<String>,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): GisFeature {
        val shapeType = when(type) {
            0 -> GisShapeType.POINT
            1 -> GisShapeType.LINE
            else -> GisShapeType.POLYGON
        }

        val points = mutableListOf<GpsPoint>()
        for (i in lats.indices) {
            points.add(GpsPoint(latitude = lats[i], longitude = lons[i]))
        }

        val attributes = mutableMapOf<String, String>()
        for (i in attributeKeys.indices) {
            attributes[attributeKeys[i]] = attributeValues[i]
        }

        return GisFeature(
            id = id,
            layerId = layerId,
            shapeType = shapeType,
            points = points,
            attributes = attributes,
            minLat = minLat,
            maxLat = maxLat,
            minLon = minLon,
            maxLon = maxLon
        )
    }
}
