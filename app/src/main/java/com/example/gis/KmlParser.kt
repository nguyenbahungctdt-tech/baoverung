package com.baoverung.app.gis

import android.util.Log
import android.util.Xml
import com.baoverung.app.data.model.GisFeature
import com.baoverung.app.data.model.GisShapeType
import com.baoverung.app.data.model.GpsPoint
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Forestry-grade KML/KMZ Parser.
 * Handles MultiGeometry, Nested Folders, and ExtendedData with streaming efficiency.
 */
object KmlParser {
    private const val TAG = "KmlParser"

    suspend fun parseKmlOrKmzStreaming(
        file: File,
        layerId: Long = 0,
        onProgress: (suspend (Int, Int) -> Unit)? = null,
        onFeature: suspend (GisFeature) -> Unit
    ) {
        try {
            val fileStream = BufferedInputStream(file.inputStream())
            if (file.name.endsWith(".kmz", ignoreCase = true)) {
                val zip = ZipInputStream(fileStream)
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith(".kml", ignoreCase = true)) {
                        parseKmlStream(zip, layerId, onProgress, onFeature)
                        break
                    }
                    entry = zip.nextEntry
                }
                zip.close()
            } else {
                fileStream.use { inputStream ->
                    parseKmlStream(inputStream, layerId, onProgress, onFeature)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "KML/KMZ Parse error: ${e.message}")
        }
    }

    private suspend fun parseKmlStream(
        inputStream: InputStream,
        layerId: Long,
        onProgress: (suspend (Int, Int) -> Unit)? = null,
        onFeature: suspend (GisFeature) -> Unit
    ) {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        val folderStack = mutableListOf<String>()
        var currentName = ""
        var currentDesc = ""
        var currentShapeType: GisShapeType? = null
        val currentAttributes = mutableMapOf<String, String>()
        var totalParsedFeatures = 0
        
        // Depth-based tracking to avoid stack corruption
        var placemarkDepth = -1

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name
            val depth = parser.depth

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when {
                        tagName.equals("Folder", ignoreCase = true) -> {
                            // Folder name is handled by the "name" tag logic below
                        }
                        tagName.equals("Placemark", ignoreCase = true) -> {
                            placemarkDepth = depth
                            currentName = ""
                            currentDesc = ""
                            currentShapeType = null
                            currentAttributes.clear()
                        }
                        tagName.equals("name", ignoreCase = true) -> {
                            val text = try { parser.nextText() } catch (e: Exception) { "" }
                            if (placemarkDepth == -1) {
                                // We are inside a Folder but not yet in a Placemark
                                folderStack.add(text)
                            } else {
                                // We are inside a Placemark
                                currentName = text
                            }
                        }
                        tagName.equals("description", ignoreCase = true) -> {
                            currentDesc = try { parser.nextText() } catch (e: Exception) { "" }
                        }
                        tagName.equals("Point", ignoreCase = true) -> currentShapeType = GisShapeType.POINT
                        tagName.equals("LineString", ignoreCase = true) -> currentShapeType = GisShapeType.LINE
                        tagName.equals("Polygon", ignoreCase = true) -> currentShapeType = GisShapeType.POLYGON
                        
                        tagName.equals("Data", ignoreCase = true) || tagName.equals("SimpleData", ignoreCase = true) -> {
                            val key = parser.getAttributeValue(null, "name") ?: ""
                            val value = try { parser.nextText() } catch (e: Exception) { "" }
                            if (key.isNotEmpty()) currentAttributes[key] = value
                        }
                        
                        tagName.equals("coordinates", ignoreCase = true) -> {
                            val coordsRaw = try { parser.nextText() } catch (e: Exception) { "" }
                            if (coordsRaw.isNotEmpty() && currentShapeType != null) {
                                val points = parseCoordinatesString(coordsRaw)
                                if (points.isNotEmpty()) {
                                    val attributes = mutableMapOf<String, String>()
                                    attributes.putAll(currentAttributes)
                                    
                                    val parentFolders = folderStack.joinToString(" > ")
                                    val fullDisplayName = if (parentFolders.isNotEmpty()) {
                                        if (currentName.isNotEmpty()) "$parentFolders > $currentName" else parentFolders
                                    } else currentName
                                    
                                    if (fullDisplayName.isNotEmpty()) attributes["Tên"] = fullDisplayName
                                    if (currentDesc.isNotEmpty()) attributes["Mô tả"] = currentDesc

                                    var minLat = 90.0; var maxLat = -90.0; var minLon = 180.0; var maxLon = -180.0
                                    for (p in points) {
                                        if (p.latitude < minLat) minLat = p.latitude
                                        if (p.latitude > maxLat) maxLat = p.latitude
                                        if (p.longitude < minLon) minLon = p.longitude
                                        if (p.longitude > maxLon) maxLon = p.longitude
                                    }

                                    totalParsedFeatures++
                                    onFeature(
                                        GisFeature(
                                            id = "kml_$totalParsedFeatures",
                                            layerId = layerId,
                                            shapeType = currentShapeType!!,
                                            points = points,
                                            attributes = attributes,
                                            minLat = minLat,
                                            maxLat = maxLat,
                                            minLon = minLon,
                                            maxLon = maxLon
                                        )
                                    )
                                    if (totalParsedFeatures % 100 == 0) {
                                        onProgress?.invoke(totalParsedFeatures, -1)
                                    }
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when {
                        tagName.equals("Folder", ignoreCase = true) -> {
                            if (folderStack.isNotEmpty()) folderStack.removeAt(folderStack.size - 1)
                        }
                        tagName.equals("Placemark", ignoreCase = true) -> {
                            placemarkDepth = -1
                        }
                    }
                }
            }
            eventType = try { parser.next() } catch (e: Exception) { XmlPullParser.END_DOCUMENT }
        }
        onProgress?.invoke(totalParsedFeatures, totalParsedFeatures)
    }

    private fun parseCoordinatesString(coordsRaw: String): List<GpsPoint> {
        val list = mutableListOf<GpsPoint>()
        val tokens = coordsRaw.trim().split("\\s+".toRegex())
        for (token in tokens) {
            val parts = token.trim().split(",")
            if (parts.size >= 2) {
                try {
                    val lon = parts[0].toDouble()
                    val lat = parts[1].toDouble()
                    val alt = if (parts.size >= 3) parts[2].toDoubleOrNull() ?: 0.0 else 0.0
                    list.add(GpsPoint(latitude = lat, longitude = lon, altitude = alt))
                } catch (e: Exception) { }
            }
        }
        return list
    }
}
