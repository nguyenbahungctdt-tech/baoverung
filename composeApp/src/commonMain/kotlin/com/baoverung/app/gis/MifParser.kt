package com.baoverung.app.gis

import com.baoverung.app.data.model.GisFeature
import com.baoverung.app.data.model.GisShapeType
import com.baoverung.app.data.model.GpsPoint
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import kotlin.math.abs

/**
 * Professional MapInfo Interchange Format (MIF/MID) Parser.
 * Ported to Kotlin Multiplatform using Okio.
 */
object MifParser {

    suspend fun parseMifFileStreaming(
        mifPath: String,
        layerId: Long = 0,
        centralMeridian: Double = 107.75,
        zoneDegrees: Int = 3,
        onProgress: (suspend (Int, Int) -> Unit)? = null,
        onFeatureParsed: suspend (GisFeature) -> Unit
    ) {
        val fs = FileSystem.SYSTEM
        val mifFilePath = mifPath.toPath()
        val midPathStr = if (mifPath.endsWith(".mif", ignoreCase = true)) mifPath.substring(0, mifPath.length - 4) + ".mid" else mifPath + ".mid"
        val midFilePath = midPathStr.toPath()
        
        val attributes = if (fs.exists(midFilePath)) parseMidFile(midPathStr) else emptyList()
        
        try {
            fs.source(mifFilePath).buffer().use { source: okio.BufferedSource ->
                var featureCount = 0
                var inData = false
                
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (line.trim().uppercase() == "DATA") {
                        inData = true
                        break
                    }
                }
                
                if (!inData) return

                while (true) {
                    val line = source.readUtf8Line() ?: break
                    val l = line.trim()
                    if (l.isEmpty()) continue
                    val up = l.uppercase()

                    when {
                        up.startsWith("POINT") -> {
                            val parts = l.split("\\s+".toRegex())
                            if (parts.size >= 3) {
                                val x = parts[1].toDoubleOrNull() ?: 0.0
                                val y = parts[2].toDoubleOrNull() ?: 0.0
                                val pt = toG(x, y, centralMeridian, zoneDegrees)
                                onFeatureParsed(GisFeature("mif_${layerId}_$featureCount", layerId, GisShapeType.POINT, listOf(pt), attributes.getOrNull(featureCount) ?: emptyMap()))
                                featureCount++
                            }
                        }
                        up.startsWith("LINE") -> {
                            val parts = l.split("\\s+".toRegex())
                            if (parts.size >= 5) {
                                val p1 = toG(parts[1].toDoubleOrNull() ?: 0.0, parts[2].toDoubleOrNull() ?: 0.0, centralMeridian, zoneDegrees)
                                val p2 = toG(parts[3].toDoubleOrNull() ?: 0.0, parts[4].toDoubleOrNull() ?: 0.0, centralMeridian, zoneDegrees)
                                onFeatureParsed(GisFeature("mif_${layerId}_$featureCount", layerId, GisShapeType.LINE, listOf(p1, p2), attributes.getOrNull(featureCount) ?: emptyMap()))
                                featureCount++
                            }
                        }
                        up.startsWith("REGION") || up.startsWith("PLINE") -> {
                            val isRegion = up.startsWith("REGION")
                            val parts = l.split("\\s+".toRegex())
                            val numSections = if (parts.size > 1) parts.last().toIntOrNull() ?: 1 else 1
                            val allPoints = mutableListOf<GpsPoint>()
                            
                            repeat(numSections) {
                                val countLine = source.readUtf8Line()?.trim() ?: ""
                                val numPoints = countLine.toIntOrNull() ?: 0
                                repeat(numPoints) {
                                    val pLine = source.readUtf8Line()?.trim() ?: ""
                                    val p = pLine.split("\\s+".toRegex())
                                    if (p.size >= 2) {
                                        val x = p[0].toDoubleOrNull() ?: 0.0
                                        val y = p[1].toDoubleOrNull() ?: 0.0
                                        allPoints.add(toG(x, y, centralMeridian, zoneDegrees))
                                    }
                                }
                            }
                            
                            if (allPoints.isNotEmpty()) {
                                var minLat = 90.0; var maxLat = -90.0; var minLon = 180.0; var maxLon = -180.0
                                for (p in allPoints) {
                                    if (p.latitude < minLat) minLat = p.latitude
                                    if (p.latitude > maxLat) maxLat = p.latitude
                                    if (p.longitude < minLon) minLon = p.longitude
                                    if (p.longitude > maxLon) maxLon = p.longitude
                                }
                                onFeatureParsed(GisFeature("mif_${layerId}_$featureCount", layerId, if (isRegion) GisShapeType.POLYGON else GisShapeType.LINE, allPoints, attributes.getOrNull(featureCount) ?: emptyMap(), minLat, maxLat, minLon, maxLon))
                                featureCount++
                            }
                        }
                    }
                    if (featureCount % 100 == 0) onProgress?.invoke(featureCount, -1)
                }
            }
        } catch (e: Exception) {
            println("MIF Parser error: ${e.message}")
        }
    }

    private fun parseMidFile(midPath: String): List<Map<String, String>> {
        val res = mutableListOf<Map<String, String>>()
        try {
            FileSystem.SYSTEM.source(midPath.toPath()).buffer().use { source ->
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    val parts = line.split(",")
                    val row = mutableMapOf<String, String>()
                    parts.forEachIndexed { idx, s -> row["Col$idx"] = s.trim().removeSurrounding("\"") }
                    res.add(row)
                }
            }
        } catch (e: Exception) {}
        return res
    }

    private fun toG(x: Double, y: Double, cm: Double, zd: Int): GpsPoint {
        return if (abs(x) > 500.0 || abs(y) > 500.0) {
            val (la, lo) = CoordinateSystemConverter.vn2000ToWgs84(x, y, cm, zd)
            GpsPoint(la, lo)
        } else GpsPoint(y, x)
    }
}
