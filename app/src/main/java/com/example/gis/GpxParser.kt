package com.baoverung.app.gis

import android.util.Xml
import com.baoverung.app.data.model.GisFeature
import com.baoverung.app.data.model.GisShapeType
import com.baoverung.app.data.model.GpsPoint
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream

object GpxParser {

    fun parseGpx(file: File, layerId: Long = 0): List<GisFeature> {
        val features = mutableListOf<GisFeature>()
        try {
            val inputStream = FileInputStream(file)
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)

            var eventType = parser.eventType
            var currentTrackName = ""
            val trackPoints = mutableListOf<GpsPoint>()
            var recordIdx = 0

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (name) {
                            "trk" -> {
                                trackPoints.clear()
                                currentTrackName = ""
                            }
                            "name" -> {
                                if (currentTrackName.isEmpty()) {
                                    currentTrackName = parser.nextText()
                                }
                            }
                            "trkpt" -> {
                                val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                                val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                                trackPoints.add(GpsPoint(lat, lon))
                            }
                            "wpt" -> {
                                val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                                val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                                features.add(
                                    GisFeature(
                                        id = "gpx_wpt_${recordIdx++}",
                                        layerId = layerId,
                                        shapeType = GisShapeType.POINT,
                                        points = listOf(GpsPoint(lat, lon)),
                                        attributes = mapOf("name" to (parser.nextText() ?: "Waypoint"))
                                    )
                                )
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name == "trk") {
                            if (trackPoints.isNotEmpty()) {
                                features.add(
                                    GisFeature(
                                        id = "gpx_trk_${recordIdx++}",
                                        layerId = layerId,
                                        shapeType = GisShapeType.LINE,
                                        points = trackPoints.toList(),
                                        attributes = mapOf("name" to (if (currentTrackName.isEmpty()) "Track" else currentTrackName))
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return features
    }
}
