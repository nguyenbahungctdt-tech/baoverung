package com.baoverung.app.gis

import android.util.Log
import com.baoverung.app.data.model.GisFeature
import com.baoverung.app.data.model.GisShapeType
import com.baoverung.app.data.model.GpsPoint
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Memory-Efficient Shapefile Parser for Forestry.
 * Streams geometry from .shp and attributes from .dbf on-demand.
 */
object ShapefileParser {
    private const val MAX_POINTS = 30000

    suspend fun parseShapefileStreaming(
        shpFile: File,
        layerId: Long = 0,
        centralMeridian: Double = 107.75,
        zoneDegrees: Int = 3,
        onProgress: (suspend (Int, Int) -> Unit)? = null,
        onFeature: suspend (GisFeature) -> Unit
    ) {
        if (!shpFile.exists()) return

        var detectedCm = centralMeridian
        val prjFile = File(shpFile.parent, shpFile.nameWithoutExtension + ".prj")
        if (prjFile.exists()) {
            try {
                val prjText = prjFile.readText().uppercase()
                val cmRegex = "PARAMETER\\[\"CENTRAL_MERIDIAN\",\\s*([0-9.]+)\\]".toRegex()
                cmRegex.find(prjText)?.let { detectedCm = it.groupValues[1].toDouble() }
            } catch (e: Exception) {}
        }

        val dbfFile = File(shpFile.parent, shpFile.nameWithoutExtension + ".dbf")
        if (!dbfFile.exists()) {
            Log.e("ShapefileParser", "Missing .dbf for ${shpFile.name}")
            return
        }

        try {
            RandomAccessFile(shpFile, "r").use { shpRaf ->
                RandomAccessFile(dbfFile, "r").use { dbfRaf ->
                    val shpLen = shpRaf.length().toInt()
                    val header = ByteArray(100); shpRaf.readFully(header)
                    val hb = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
                    if (hb.getInt(0) != 9994) return

                    val dbfHeader = readDbfHeader(dbfRaf)
                    var pos = 100
                    var recordIdx = 0

                    while (pos + 8 <= shpLen) {
                        shpRaf.seek(pos.toLong())
                        val recordNum = shpRaf.readInt() 
                        val contentWords = shpRaf.readInt()
                        val contentLen = contentWords * 2
                        
                        // Safety: Max record size 2MB
                        if (contentLen > 2 * 1024 * 1024 || contentLen <= 0) {
                            pos += 8 + contentLen
                            recordIdx++
                            continue
                        }

                        val recordData = ByteArray(contentLen)
                        shpRaf.readFully(recordData)
                        val cb = ByteBuffer.wrap(recordData).order(ByteOrder.LITTLE_ENDIAN)
                        val shapeType = cb.getInt(0)

                        val points = mutableListOf<GpsPoint>()
                        var gType = GisShapeType.POINT

                        when (shapeType) {
                            1, 11, 21 -> { // Point
                                gType = GisShapeType.POINT
                                points.add(toGpsPoint(cb.getDouble(4), cb.getDouble(12), detectedCm, zoneDegrees))
                            }
                            3, 5, 13, 15, 23, 25 -> { // Polyline/Polygon
                                gType = if (shapeType == 3 || shapeType == 13 || shapeType == 23) GisShapeType.LINE else GisShapeType.POLYGON
                                if (cb.capacity() >= 44) {
                                    val numParts = cb.getInt(36)
                                    val numPoints = cb.getInt(40)
                                    val ptsToRead = min(numPoints, MAX_POINTS)
                                    val dataStart = 44 + numParts * 4
                                    for (i in 0 until ptsToRead) {
                                        val pOffset = dataStart + i * 16
                                        if (pOffset + 16 <= cb.capacity()) {
                                            points.add(toGpsPoint(cb.getDouble(pOffset), cb.getDouble(pOffset + 8), detectedCm, zoneDegrees))
                                        }
                                    }
                                }
                            }
                        }

                        if (points.isNotEmpty() && recordIdx < dbfHeader.recordCount) {
                            val attrs = readDbfRecord(dbfRaf, dbfHeader, recordIdx)
                            val minLat = points.minOf { it.latitude }; val maxLat = points.maxOf { it.latitude }
                            val minLon = points.minOf { it.longitude }; val maxLon = points.maxOf { it.longitude }
                            onFeature(GisFeature("shp_$recordIdx", layerId, gType, points, attrs, minLat, maxLat, minLon, maxLon))
                        }

                        pos += 8 + contentLen
                        recordIdx++
                        onProgress?.invoke(pos, shpLen)
                    }
                }
            }
        } catch (e: Exception) { Log.e("ShapefileParser", "Parse error: ${e.message}") }
    }

    private fun toGpsPoint(x: Double, y: Double, cm: Double, zd: Int): GpsPoint {
        return if (x > 1000 || y > 1000) {
            val (lat, lon) = CoordinateSystemConverter.vn2000ToWgs84(x, y, cm, zd)
            GpsPoint(lat, lon)
        } else GpsPoint(y, x)
    }

    // --- DBF RE-IMPLEMENTATION ---
    data class DbfHeader(val recordCount: Int, val headLen: Int, val recLen: Int, val fields: List<Pair<String, Int>>)

    private fun readDbfHeader(raf: RandomAccessFile): DbfHeader {
        val header = ByteArray(32); raf.seek(0); raf.readFully(header)
        val hb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val count = hb.getInt(4)
        val headLen = hb.getShort(8).toInt() and 0xFFFF
        val recLen = hb.getShort(10).toInt() and 0xFFFF
        val fields = mutableListOf<Pair<String, Int>>()
        var pos = 32
        while (pos < headLen - 1) {
            val fd = ByteArray(32); raf.seek(pos.toLong()); raf.readFully(fd)
            if (fd[0] == 0x0D.toByte()) break
            val name = String(fd, 0, 11).trim { it <= ' ' }
            val len = fd[16].toInt() and 0xFF
            if (name.isNotEmpty()) fields.add(name to len)
            pos += 32
        }
        return DbfHeader(count, headLen, recLen, fields)
    }

    private fun readDbfRecord(raf: RandomAccessFile, h: DbfHeader, idx: Int): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val offset = h.headLen.toLong() + idx.toLong() * h.recLen.toLong()
            val bytes = ByteArray(h.recLen); raf.seek(offset); raf.readFully(bytes)
            if (bytes[0] == '*'.code.toByte()) return map
            var off = 1
            for ((f, l) in h.fields) {
                if (off + l <= h.recLen) {
                    val raw = String(bytes, off, l, Charsets.ISO_8859_1).trim()
                    map[f] = MapInfoEncodingConverter.decode(raw)
                }
                off += l
            }
        } catch (e: Exception) {}
        return map
    }

    fun getFieldNames(dbfFile: File): List<String> {
        return try { RandomAccessFile(dbfFile, "r").use { readDbfHeader(it).fields.map { it.first } } } catch (e: Exception) { emptyList() }
    }
}
