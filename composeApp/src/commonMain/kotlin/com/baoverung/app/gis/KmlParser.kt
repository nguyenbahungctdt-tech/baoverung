package com.baoverung.app.gis

import com.baoverung.app.data.model.GisFeature
import com.baoverung.app.data.model.GisShapeType
import com.baoverung.app.data.model.GpsPoint
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Portable KML Parser for KMP using Regex for lightweight XML parsing.
 */
object KmlParser {

    suspend fun parseKmlStreaming(
        kmlPath: String,
        layerId: Long = 0,
        onFeature: suspend (GisFeature) -> Unit
    ) {
        try {
            val fs = FileSystem.SYSTEM
            val xml = fs.read(kmlPath.toPath()) { readUtf8() }
            
            // Basic Placemark parser
            val placemarkRegex = "<Placemark>.*?</Placemark>".toRegex(RegexOption.DOT_MATCHES_ALL)
            placemarkRegex.findAll(xml).forEachIndexed { idx, match ->
                val content = match.value
                val name = "<name>(.*?)</name>".toRegex().find(content)?.groupValues?.get(1) ?: ""
                val coordsRaw = "<coordinates>(.*?)</coordinates>".toRegex(RegexOption.DOT_MATCHES_ALL).find(content)?.groupValues?.get(1)?.trim() ?: ""
                
                if (coordsRaw.isNotEmpty()) {
                    val pts = coordsRaw.split("\\s+".toRegex()).mapNotNull { token ->
                        val parts = token.trim().split(",")
                        if (parts.size >= 2) {
                            try {
                                GpsPoint(parts[1].toDouble(), parts[0].toDouble())
                            } catch (e: Exception) { null }
                        } else null
                    }
                    
                    if (pts.isNotEmpty()) {
                        val type = when {
                            content.contains("<Polygon>") -> GisShapeType.POLYGON
                            content.contains("<LineString>") -> GisShapeType.LINE
                            else -> GisShapeType.POINT
                        }
                        
                        var minLat = 90.0; var maxLat = -90.0; var minLon = 180.0; var maxLon = -180.0
                        for (p in pts) {
                            if (p.latitude < minLat) minLat = p.latitude
                            if (p.latitude > maxLat) maxLat = p.latitude
                            if (p.longitude < minLon) minLon = p.longitude
                            if (p.longitude > maxLon) maxLon = p.longitude
                        }

                        onFeature(GisFeature(
                            id = "kml_$idx",
                            layerId = layerId,
                            shapeType = type,
                            points = pts,
                            attributes = mapOf("Tên" to name),
                            minLat = minLat,
                            maxLat = maxLat,
                            minLon = minLon,
                            maxLon = maxLon
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            println("KmlParser error: ${e.message}")
        }
    }
}
