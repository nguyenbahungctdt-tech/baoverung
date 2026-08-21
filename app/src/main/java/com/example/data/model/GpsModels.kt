package com.baoverung.app.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GpsPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val speed: Float = 0f,
    val accuracy: Float = 0f,
    val satellitesCount: Int = 0,
    val timestampUtc: Long = System.currentTimeMillis()
)

data class SatelliteInfo(
    val svid: Int,
    val constellationType: Int,
    val azimuth: Float,
    val elevation: Float,
    val cn0DbHz: Float,
    val usedInFix: Boolean
)

enum class GisShapeType {
    POINT, LINE, POLYGON
}

data class GisFeature(
    val id: String = "",
    val layerId: Long = 0,
    val shapeType: GisShapeType,
    val points: List<GpsPoint>,
    val attributes: Map<String, String> = emptyMap(),
    val minLat: Double = 0.0,
    val maxLat: Double = 0.0,
    val minLon: Double = 0.0,
    val maxLon: Double = 0.0,
    val centroidLat: Double = 0.0,
    val centroidLon: Double = 0.0
)

data class TrackLogUiModel(
    val id: Long,
    val title: String,
    val fullPoints: List<GpsPoint>,
    val sampledPoints: List<GpsPoint>,
    val displayColorHex: String,
    val totalDistanceMeters: Double,
    val startTimeUtc: Long,
    val category: String = "GPX",
    val minLat: Double = 0.0,
    val maxLat: Double = 0.0,
    val minLon: Double = 0.0,
    val maxLon: Double = 0.0
)

data class PolygonUiModel(
    val id: Long,
    val title: String,
    val points: List<GpsPoint>,
    val centroidLat: Double,
    val centroidLon: Double,
    val centroidVn2000X: Double = 0.0,
    val centroidVn2000Y: Double = 0.0,
    val areaSquareMeters: Double,
    val displayColorHex: String,
    val minLat: Double = 0.0,
    val maxLat: Double = 0.0,
    val minLon: Double = 0.0,
    val maxLon: Double = 0.0
)
