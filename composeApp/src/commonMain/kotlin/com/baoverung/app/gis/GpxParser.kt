package com.baoverung.app.gis

import com.baoverung.app.data.model.GisFeature
import com.baoverung.app.data.model.GisShapeType
import com.baoverung.app.data.model.GpsPoint
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Portable GPX Parser for KMP using Regex for lightweight XML parsing.
 */
object GpxParser {

    fun parseGpx(gpxPath: String, layerId: Long = 0): List<GisFeature> {
        val features = mutableListOf<GisFeature>()
        try {
            val fs = FileSystem.SYSTEM
            val xml = fs.read(gpxPath.toPath()) { readUtf8() }
            
            // Parse waypoints
            val wptRegex = "<wpt lat=\"([0-9.-]+)\" lon=\"([0-9.-]+)\">.*?<name>(.*?)</name>.*?</wpt>".toRegex(RegexOption.DOT_MATCHES_ALL)
            wptRegex.findAll(xml).forEachIndexed { idx, match ->
                val lat = match.groupValues[1].toDouble()
                val lon = match.groupValues[2].toDouble()
                val name = match.groupValues[3]
                features.add(GisFeature("gpx_wpt_$idx", layerId, GisShapeType.POINT, listOf(GpsPoint(lat, lon)), mapOf("name" to name)))
            }
            
            // Parse tracks
            val trkRegex = "<trk>.*?<name>(.*?)</name>.*?<trkseg>(.*?)</trkseg>.*?</trk>".toRegex(RegexOption.DOT_MATCHES_ALL)
            trkRegex.findAll(xml).forEachIndexed { idx, match ->
                val name = match.groupValues[1]
                val seg = match.groupValues[2]
                val ptRegex = "<trkpt lat=\"([0-9.-]+)\" lon=\"([0-9.-]+)\"".toRegex()
                val pts = ptRegex.findAll(seg).map { ptMatch ->
                    GpsPoint(ptMatch.groupValues[1].toDouble(), ptMatch.groupValues[2].toDouble())
                }.toList()
                if (pts.isNotEmpty()) {
                    features.add(GisFeature("gpx_trk_$idx", layerId, GisShapeType.LINE, pts, mapOf("name" to name)))
                }
            }
        } catch (e: Exception) {
            println("GpxParser error: ${e.message}")
        }
        return features
    }
}
