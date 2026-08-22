package com.baoverung.app.gis

import com.baoverung.app.data.model.GisFeature
import com.baoverung.app.data.model.GisShapeType
import com.baoverung.app.data.model.GpsPoint
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.Buffer
import kotlin.math.min

/**
 * Memory-Efficient Shapefile Parser for Forestry.
 * Ported to Kotlin Multiplatform using Okio.
 */
object ShapefileParser {
    private const val MAX_POINTS = 30000

    suspend fun parseShapefileStreaming(
        shpPath: String,
        layerId: Long = 0,
        centralMeridian: Double = 107.75,
        zoneDegrees: Int = 3,
        onProgress: (suspend (Int, Int) -> Unit)? = null,
        onFeature: suspend (GisFeature) -> Unit
    ) {
        val fs = FileSystem.SYSTEM
        val shpFilePath = shpPath.toPath()
        if (!fs.exists(shpFilePath)) return

        var detectedCm = centralMeridian
        val parent = shpFilePath.parent
        val nameBase = shpFilePath.name.substringBeforeLast(".")
        val prjPath = parent?.div("$nameBase.prj")
        
        if (prjPath != null && fs.exists(prjPath)) {
            try {
                val prjText = fs.read(prjPath) { readUtf8() }.uppercase()
                val cmRegex = "PARAMETER\\[\"CENTRAL_MERIDIAN\",\\s*([0-9.]+)\\]".toRegex()
                cmRegex.find(prjText)?.let { detectedCm = it.groupValues[1].toDouble() }
            } catch (e: Exception) {}
        }

        val dbfPath = parent?.div("$nameBase.dbf")
        if (dbfPath == null || !fs.exists(dbfPath)) {
            println("ShapefileParser: Missing .dbf for ${shpFilePath.name}")
            return
        }

        try {
            fs.openReadOnly(shpFilePath).use { shpHandle ->
                fs.openReadOnly(dbfPath).use { dbfHandle ->
                    val shpLen = shpHandle.size()
                    val header = Buffer()
                    shpHandle.read(0, header, 100)
                    if (header.readInt() != 9994) return

                    val dbfHeader = readDbfHeader(dbfHandle)
                    var pos = 100L
                    var recordIdx = 0

                    while (pos + 8 <= shpLen) {
                        val recHeader = Buffer()
                        shpHandle.read(pos, recHeader, 8)
                        // Big Endian
                        val recordNum = recHeader.readInt()
                        val contentWords = recHeader.readInt()
                        val contentLen = contentWords * 2L
                        
                        if (contentLen > 2 * 1024 * 1024 || contentLen <= 0) {
                            pos += 8 + contentLen
                            recordIdx++
                            continue
                        }

                        val recordData = Buffer()
                        shpHandle.read(pos + 8, recordData, contentLen)
                        // Little Endian
                        val shapeType = recordData.readIntLe()

                        val points = mutableListOf<GpsPoint>()
                        var gType = GisShapeType.POINT

                        when (shapeType) {
                            1, 11, 21 -> { // Point
                                gType = GisShapeType.POINT
                                val x = recordData.readDoubleLe()
                                val y = recordData.readDoubleLe()
                                points.add(toGpsPoint(x, y, detectedCm, zoneDegrees))
                            }
                            3, 5, 13, 15, 23, 25 -> { // Polyline/Polygon
                                gType = if (shapeType == 3 || shapeType == 13 || shapeType == 23) GisShapeType.LINE else GisShapeType.POLYGON
                                if (recordData.size >= 40) {
                                    recordData.skip(32) // bbox
                                    val numParts = recordData.readIntLe()
                                    val numPoints = recordData.readIntLe()
                                    recordData.skip(numParts * 4L) // skip parts offsets
                                    
                                    val ptsToRead = min(numPoints, MAX_POINTS)
                                    for (i in 0 until ptsToRead) {
                                        val x = recordData.readDoubleLe()
                                        val y = recordData.readDoubleLe()
                                        points.add(toGpsPoint(x, y, detectedCm, zoneDegrees))
                                    }
                                }
                            }
                        }

                        if (points.isNotEmpty() && recordIdx < dbfHeader.recordCount) {
                            val attrs = readDbfRecord(dbfHandle, dbfHeader, recordIdx)
                            val minLat = points.minOf { it.latitude }
                            val maxLat = points.maxOf { it.latitude }
                            val minLon = points.minOf { it.longitude }
                            val maxLon = points.maxOf { it.longitude }
                            onFeature(GisFeature("shp_$recordIdx", layerId, gType, points, attrs, minLat, maxLat, minLon, maxLon))
                        }

                        pos += 8 + contentLen
                        recordIdx++
                        onProgress?.invoke(pos.toInt(), shpLen.toInt())
                    }
                }
            }
        } catch (e: Exception) { println("ShapefileParser: Parse error: ${e.message}") }
    }

    private fun toGpsPoint(x: Double, y: Double, cm: Double, zd: Int): GpsPoint {
        return if (x > 1000 || y > 1000) {
            val (lat, lon) = CoordinateSystemConverter.vn2000ToWgs84(x, y, cm, zd)
            GpsPoint(lat, lon)
        } else GpsPoint(y, x)
    }

    data class DbfHeader(val recordCount: Int, val headLen: Int, val recLen: Int, val fields: List<Pair<String, Int>>)

    private fun readDbfHeader(handle: okio.FileHandle): DbfHeader {
        val headBuf = Buffer()
        handle.read(0, headBuf, 32)
        headBuf.skip(4)
        val count = headBuf.readIntLe()
        val headLen = headBuf.readShortLe().toInt() and 0xFFFF
        val recLen = headBuf.readShortLe().toInt() and 0xFFFF
        
        val fields = mutableListOf<Pair<String, Int>>()
        var pos = 32L
        while (pos < headLen - 1) {
            val fieldBuf = Buffer()
            handle.read(pos, fieldBuf, 32)
            if (fieldBuf.size == 0L || fieldBuf.get(0) == 0x0D.toByte()) break
            
            val nameBytes = fieldBuf.readByteArray(11)
            val name = nameBytes.decodeToString().trim { it <= ' ' }
            fieldBuf.skip(5)
            val len = fieldBuf.readByte().toInt() and 0xFF
            if (name.isNotEmpty()) fields.add(name to len)
            pos += 32
        }
        return DbfHeader(count, headLen, recLen, fields)
    }

    private fun readDbfRecord(handle: okio.FileHandle, h: DbfHeader, idx: Int): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val offset = h.headLen.toLong() + idx.toLong() * h.recLen.toLong()
            val recBuf = Buffer()
            handle.read(offset, recBuf, h.recLen.toLong())
            
            if (recBuf.size == 0L) return map
            val marker = recBuf.readByte()
            if (marker == '*'.code.toByte()) return map
            
            for ((f, l) in h.fields) {
                val rawBytes = recBuf.readByteArray(l.toLong())
                val raw = rawBytes.decodeToString().trim()
                map[f] = MapInfoEncodingConverter.decode(raw)
            }
        } catch (e: Exception) {}
        return map
    }

    fun getFieldNames(dbfPath: String): List<String> {
        val fs = FileSystem.SYSTEM
        return try {
            fs.openReadOnly(dbfPath.toPath()).use { readDbfHeader(it).fields.map { it.first } }
        } catch (e: Exception) { emptyList() }
    }
}
