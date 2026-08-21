package com.baoverung.app.gis

import android.util.Log
import com.baoverung.app.data.model.GisFeature
import com.baoverung.app.data.model.GisShapeType
import com.baoverung.app.data.model.GpsPoint
import java.io.File
import kotlin.math.abs

/**
 * Professional MapInfo Interchange Format (MIF/MID) Parser.
 * True streaming implementation using line-by-line reading for efficiency.
 */
object MifParser {
    private const val TAG = "MifParser"

    suspend fun parseMifFileStreaming(
        mifFile: File,
        layerId: Long = 0,
        centralMeridian: Double = 107.75,
        zoneDegrees: Int = 3,
        onProgress: (suspend (Int, Int) -> Unit)? = null,
        onFeatureParsed: suspend (GisFeature) -> Unit
    ) {
        val midFile = File(mifFile.absolutePath.replace(".mif", ".mid", ignoreCase = true))
        val attributes = if (midFile.exists()) parseMidFile(midFile) else emptyList()
        
        try {
            val charset = try { java.nio.charset.Charset.forName("windows-1258") } catch(e: Exception) { Charsets.UTF_8 }
            
            mifFile.bufferedReader(charset).use { reader ->
                var featureCount = 0
                var inData = false
                
                var line: String? = reader.readLine()
                while (line != null) {
                    if (line.trim().uppercase() == "DATA") {
                        inData = true
                        break
                    }
                    line = reader.readLine()
                }
                
                if (!inData) return

                line = reader.readLine()
                while (line != null) {
                    val l = line.trim()
                    if (l.isEmpty()) { line = reader.readLine(); continue }
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
                                val countLine = reader.readLine()?.trim() ?: ""
                                val numPoints = countLine.toIntOrNull() ?: 0
                                repeat(numPoints) {
                                    val pLine = reader.readLine()?.trim() ?: ""
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
                    line = reader.readLine()
                }
            }
        } catch (e: Exception) { Log.e(TAG, "MIF error", e) }
    }

    private fun parseMidFile(file: File): List<Map<String, String>> {
        val res = mutableListOf<Map<String, String>>()
        try {
            val charset = try { java.nio.charset.Charset.forName("windows-1258") } catch(e: Exception) { Charsets.UTF_8 }
            file.forEachLine(charset) { line ->
                val parts = line.split(",")
                val row = mutableMapOf<String, String>()
                parts.forEachIndexed { idx, s -> row["Col$idx"] = s.trim().removeSurrounding("\"") }
                res.add(row)
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

    fun getFieldNames(file: File): List<String> {
        val res = mutableListOf<String>()
        try {
            file.useLines { lines ->
                val iterator = lines.iterator()
                while (iterator.hasNext()) {
                    val line = iterator.next().trim().uppercase()
                    if (line.startsWith("COLUMNS")) {
                        val count = line.split("\\s+".toRegex()).last().toIntOrNull() ?: 0
                        repeat(count) {
                            if (iterator.hasNext()) {
                                val name = iterator.next().trim().split("\\s+".toRegex())[0]
                                res.add(name)
                            }
                        }
                        break
                    }
                }
            }
        } catch (e: Exception) {}
        return res
    }
}
