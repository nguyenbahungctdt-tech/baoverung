package com.baoverung.app.gis

import android.util.Log
import com.baoverung.app.data.model.GisFeature
import com.baoverung.app.data.model.GisShapeType
import com.baoverung.app.data.model.GpsPoint
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.*

/**
 * Advanced MapInfo Native Parser for Forestry GIS.
 * Handles: 512-byte block spanning, physical-to-logical translation, and point chains.
 */
object MapInfoTabParser {
    private const val TAG = "MapInfoTabParser"
    private const val MAX_PTS = 200000
    private const val BLOCK_SIZE = 512

    data class MapInfoParams(
        val resX: Double, val resY: Double,
        val centerX: Double, val centerY: Double,
        val cm: Double, val zd: Int
    )

    /**
     * MapInfoBuffer wraps a ByteBuffer and provides "Logical" access to data,
     * automatically skipping the 4-byte block headers that appear every 512 bytes.
     */
    class MapInfoBuffer(private val buffer: ByteBuffer) {
        private val temp = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)

        fun order(o: ByteOrder) { buffer.order(o); temp.order(o) }
        fun capacity(): Int = buffer.capacity()

        /**
         * Translates a Logical offset (continuous data stream) to a Physical file offset.
         * The first 512 bytes are continuous. Every subsequent 512-byte block has a 4-byte header.
         */
        private fun lp(logical: Int): Int {
            if (logical < BLOCK_SIZE) return logical
            val dataOffset = logical - BLOCK_SIZE
            val blockIdx = (dataOffset / 508) + 1
            val offsetInBlock = dataOffset % 508
            return (blockIdx * BLOCK_SIZE) + 4 + offsetInBlock
        }

        fun getByte(logical: Int): Byte {
            val p = lp(logical)
            return if (p in 0 until buffer.capacity()) buffer.get(p) else 0
        }

        fun getInt(logical: Int): Int {
            val p = lp(logical)
            // If the 4-byte int crosses a 512-byte block boundary
            if ((p % BLOCK_SIZE) > 508) {
                temp.clear()
                for (i in 0 until 4) temp.put(buffer.get(lp(logical + i)))
                return temp.getInt(0)
            }
            return if (p + 3 < buffer.capacity()) buffer.getInt(p) else 0
        }

        fun getShort(logical: Int): Short {
            val p = lp(logical)
            if ((p % BLOCK_SIZE) > 510) {
                temp.clear()
                for (i in 0 until 2) temp.put(buffer.get(lp(logical + i)))
                return temp.getShort(0)
            }
            return if (p + 1 < buffer.capacity()) buffer.getShort(p) else 0
        }

        fun getDouble(logical: Int): Double {
            val p = lp(logical)
            if ((p % BLOCK_SIZE) > 504) {
                temp.clear()
                for (i in 0 until 8) temp.put(buffer.get(lp(logical + i)))
                return temp.getDouble(0)
            }
            return if (p + 7 < buffer.capacity()) buffer.getDouble(p) else 0.0
        }
    }

    /**
     * Converts a Physical file offset to a Logical data offset.
     */
    private fun pl(physical: Int): Int {
        if (physical < BLOCK_SIZE) return physical
        val blockIdx = physical / BLOCK_SIZE
        val offsetInBlock = physical % BLOCK_SIZE
        val dataSoFar = (blockIdx - 1) * 508
        return BLOCK_SIZE + dataSoFar + (if (offsetInBlock < 4) 0 else offsetInBlock - 4)
    }

    suspend fun parseTabFileStreaming(
        file: File, layerId: Long = 0,
        centralMeridian: Double = 107.75, zoneDegrees: Int = 3,
        skipConversion: Boolean = false,
        onProgress: (suspend (Int, Int) -> Unit)? = null,
        onFeatureParsed: suspend (GisFeature) -> Unit
    ) {
        val base = file.nameWithoutExtension; val parent = file.parentFile ?: return
        val mapF = findVariedFile(parent, base, "map"); val idF = findVariedFile(parent, base, "id"); val datF = findVariedFile(parent, base, "dat")
        if (mapF == null || idF == null || datF == null) return

        var activeCm = centralMeridian; var activeZd = zoneDegrees
        var tabBounds: CoordinateSystemConverter.RectD? = null
        try {
            val tabText = file.readText()
            CoordinateSystemConverter.extractVn2000Metadata(tabText)?.let { activeCm = it.centralMeridian; activeZd = it.zoneDegrees }
            tabBounds = CoordinateSystemConverter.extractBoundsFromCoordSys(tabText)
        } catch (e: Exception) {}

        RandomAccessFile(mapF, "r").use { mapRaf ->
            RandomAccessFile(datF, "r").use { datRaf ->
                val mapLen = mapRaf.length().toInt()
                val mapBuf = mapRaf.channel.map(FileChannel.MapMode.READ_ONLY, 0, mapLen.toLong())
                val reader = MapInfoBuffer(mapBuf)
                
                reader.order(ByteOrder.BIG_ENDIAN)
                val xMin = tabBounds?.xMin ?: reader.getDouble(150)
                val yMin = tabBounds?.yMin ?: reader.getDouble(158)
                val xMax = tabBounds?.xMax ?: reader.getDouble(166)
                val yMax = tabBounds?.yMax ?: reader.getDouble(174)
                
                Log.d(TAG, "Map Setup: Bounds ($xMin,$yMin) to ($xMax,$yMax) CM=$activeCm")

                val resX = if (xMax > xMin) (xMax - xMin) / 2000000000.0 else 1.0
                val resY = if (yMax > yMin) (yMax - yMin) / 2000000000.0 else 1.0
                val centerX = (xMax + xMin) / 2.0
                val centerY = (yMax + yMin) / 2.0
                val params = MapInfoParams(resX, resY, centerX, centerY, activeCm, activeZd)

                val idMeta = detectIdMetadata(idF, reader)
                val offsets = readIdFile(idF, idMeta)
                val datH = readDbfHeader(datRaf)
                
                var count = 0
                offsets.forEachIndexed { idx, physOff ->
                    if (physOff <= 0 || physOff >= mapLen) return@forEachIndexed
                    try {
                        // MapInfo .ID file stores PHYSICAL offsets
                        val logOff = pl(physOff)
                        val features = readObject(reader, logOff, idx, layerId, params, skipConversion)
                        if (features.isNotEmpty()) {
                            val attrs = readDbfRecord(datRaf, datH, idx)
                            features.forEach { onFeatureParsed(it.copy(attributes = attrs)) }
                            count++
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error at offset $physOff: ${e.message}")
                    }
                    if (idx % 200 == 0 || idx == offsets.size - 1) onProgress?.invoke(idx + 1, offsets.size)
                }
                Log.i(TAG, "Successfully loaded $count features.")
            }
        }
    }

    private fun readObject(
        r: MapInfoBuffer, logOff: Int, index: Int, layerId: Long,
        p: MapInfoParams, skip: Boolean
    ): List<GisFeature> {
        val typeByte = r.getByte(logOff).toInt()
        val type = typeByte and 0x7F 
        val feats = mutableListOf<GisFeature>()
        
        when (type) {
            1, 2 -> { // Point
                val pts = listOf(toGps(r.getInt(logOff + 4), r.getInt(logOff + 8), p, skip))
                if (isValid(pts)) feats.add(wrapFeat(index, 0, layerId, GisShapeType.POINT, pts))
            }
            3 -> { // Line
                val pts = listOf(toGps(r.getInt(logOff + 4), r.getInt(logOff + 8), p, skip), toGps(r.getInt(logOff + 12), r.getInt(logOff + 16), p, skip))
                if (isValid(pts)) feats.add(wrapFeat(index, 0, layerId, GisShapeType.LINE, pts))
            }
            4, 6 -> { // Pline / Arc
                val n = r.getInt(logOff + 20)
                if (n in 1..MAX_PTS) {
                    val pts = (0 until n).map { toGps(r.getInt(logOff + 24 + it * 8), r.getInt(logOff + 28 + it * 8), p, skip) }
                    if (isValid(pts)) feats.add(wrapFeat(index, 0, layerId, GisShapeType.LINE, pts))
                }
            }
            5, 11 -> { // Rectangle
                val x1 = r.getInt(logOff+4); val y1 = r.getInt(logOff+8); val x2 = r.getInt(logOff+12); val y2 = r.getInt(logOff+16)
                val pts = listOf(toGps(x1,y1,p,skip), toGps(x2,y1,p,skip), toGps(x2,y2,p,skip), toGps(x1,y2,p,skip), toGps(x1,y1,p,skip))
                if (isValid(pts)) feats.add(wrapFeat(index, 0, layerId, GisShapeType.POLYGON, pts))
            }
            7 -> { // Multi-Pline
                val nTotal = r.getInt(logOff + 20); val fChain = r.getInt(logOff + 28)
                if (nTotal in 1..MAX_PTS && fChain > 0) {
                    val pts = mutableListOf<GpsPoint>()
                    readPointChain(r, pl(fChain), nTotal, pts, p, skip, false, 0, 0)
                    if (isValid(pts)) feats.add(wrapFeat(index, 0, layerId, GisShapeType.LINE, pts))
                }
            }
            8, 9, 13 -> { // Region
                val nRings = r.getInt(logOff + 20); val nTotal = r.getInt(logOff + 24)
                if (nRings in 1..2000 && nTotal in 1..MAX_PTS) {
                    val rSizes = (0 until nRings).map { r.getInt(logOff + 28 + it * 4) }
                    val fChain = r.getInt(logOff + 28 + nRings * 4)
                    val comp = (type == 13)
                    val bx = if (comp) r.getInt(logOff + 4) else 0
                    val by = if (comp) r.getInt(logOff + 8) else 0
                    
                    val allPts = mutableListOf<GpsPoint>()
                    readPointChain(r, pl(fChain), nTotal, allPts, p, skip, comp, bx, by)
                    
                    var start = 0
                    for (i in 0 until nRings) {
                        val count = rSizes[i]
                        if (start + count <= allPts.size) {
                            val ring = allPts.subList(start, start + count).toList()
                            if (isValid(ring)) feats.add(wrapFeat(index, i, layerId, GisShapeType.POLYGON, ring))
                        }
                        start += count
                    }
                }
            }
        }
        return feats
    }

    private fun readPointChain(r: MapInfoBuffer, logOff: Int, total: Int, pts: MutableList<GpsPoint>, p: MapInfoParams, skip: Boolean, comp: Boolean, bx: Int, by: Int) {
        var cLog = logOff; var read = 0
        while (read < total && cLog > 0 && cLog < r.capacity()) {
            val blockSize = r.getInt(cLog); val nextPhys = r.getInt(cLog + 4)
            if (blockSize <= 8) break
            
            if (comp) {
                val n = min(total - read, (blockSize - 8) / 4)
                for (i in 0 until n) {
                    val dx = r.getShort(cLog + 8 + i * 4).toInt()
                    val dy = r.getShort(cLog + 10 + i * 4).toInt()
                    pts.add(toGps(bx + dx, by + dy, p, skip))
                }
                read += n
            } else {
                val n = min(total - read, (blockSize - 8) / 8)
                for (i in 0 until n) {
                    val ix = r.getInt(cLog + 8 + i * 8)
                    val iy = r.getInt(cLog + 12 + i * 8)
                    pts.add(toGps(ix, iy, p, skip))
                }
                read += n
            }
            if (nextPhys > 0 && read < total) cLog = pl(nextPhys) else break
        }
    }

    private fun toGps(ix: Int, iy: Int, p: MapInfoParams, skip: Boolean): GpsPoint {
        val x = p.centerX + (ix.toDouble() * p.resX)
        val y = p.centerY + (iy.toDouble() * p.resY)
        if (skip) return GpsPoint(y, x)
        // Fail-safe check: MapInfo integer coordinates usually stay within +/- 1 billion.
        // Extreme values indicate garbage reading.
        if (abs(ix) > 1100000000 || abs(iy) > 1100000000) return GpsPoint(0.0, 0.0)
        val (lat, lon) = CoordinateSystemConverter.vn2000ToWgs84(x, y, p.cm, p.zd)
        return GpsPoint(lat, lon)
    }

    private fun isValid(pts: List<GpsPoint>): Boolean {
        if (pts.isEmpty()) return false
        return pts.any { it.latitude != 0.0 && it.longitude != 0.0 && it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
    }

    private fun wrapFeat(idx: Int, ring: Int, lid: Long, t: GisShapeType, pts: List<GpsPoint>): GisFeature {
        val validPts = pts.filter { it.latitude != 0.0 }
        if (validPts.isEmpty()) return GisFeature(id = "err", shapeType = t, points = emptyList())
        val minLat = validPts.minOf { it.latitude }; val maxLat = validPts.maxOf { it.latitude }
        val minLon = validPts.minOf { it.longitude }; val maxLon = validPts.maxOf { it.longitude }
        return GisFeature("mi_${idx}_$ring", lid, t, validPts, emptyMap(), minLat, maxLat, minLon, maxLon, (minLat+maxLat)/2.0, (minLon+maxLon)/2.0)
    }

    data class IdMeta(val order: ByteOrder, val start: Int, val isDiv4: Boolean)

    private fun detectIdMetadata(idFile: File, r: MapInfoBuffer): IdMeta {
        val bytes = idFile.readBytes(); if (bytes.size < 4) return IdMeta(ByteOrder.BIG_ENDIAN, 0, false)
        val buf = ByteBuffer.wrap(bytes)
        fun score(order: ByteOrder, start: Int, div4: Boolean): Int {
            buf.order(order); var s = 0; var p = start; var checked = 0
            while (p + 3 < bytes.size && checked < 20) {
                var off = buf.getInt(p); if (div4) off *= 4
                if (off in BLOCK_SIZE until r.capacity()) {
                    val type = r.getByte(pl(off)).toInt() and 0x7F
                    if (type in 1..21) s++
                }
                p += 4; checked++
            }
            return s
        }
        val configs = listOf(IdMeta(ByteOrder.BIG_ENDIAN, 0, false), IdMeta(ByteOrder.LITTLE_ENDIAN, 0, false), IdMeta(ByteOrder.BIG_ENDIAN, 512, false), IdMeta(ByteOrder.BIG_ENDIAN, 0, true))
        var best = configs[0]; var maxS = -1
        for (c in configs) { val s = score(c.order, c.start, c.isDiv4); if (s > maxS) { maxS = s; best = c } }
        return best
    }

    private fun readIdFile(file: File, meta: IdMeta): List<Int> {
        val offsets = mutableListOf<Int>()
        try { RandomAccessFile(file, "r").use { raf ->
            val b = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length()).order(meta.order)
            if (raf.length() > meta.start) b.position(meta.start)
            while (b.remaining() >= 4) { var off = b.int; if (meta.isDiv4) off *= 4; if (off > 0) offsets.add(off) }
        } } catch (e: Exception) {}
        return offsets
    }

    data class DbfH(val hl: Int, val rl: Int, val fs: List<Pair<String, Int>>)
    private fun readDbfHeader(raf: RandomAccessFile): DbfH {
        val b = ByteArray(32); raf.seek(0); raf.readFully(b); val buf = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        val hl = buf.getShort(8).toInt() and 0xFFFF; val rl = buf.getShort(10).toInt() and 0xFFFF
        val fs = mutableListOf<Pair<String, Int>>(); var p = 32
        while (p < hl - 1) {
            val fb = ByteArray(32); raf.seek(p.toLong()); raf.readFully(fb)
            if (fb[0] == 0x0D.toByte()) break
            val n = String(fb, 0, 11).trim { it <= ' ' }; val l = fb[16].toInt() and 0xFF
            if (n.isNotEmpty()) fs.add(n to l); p += 32
        }
        return DbfH(hl, rl, fs)
    }

    private fun readDbfRecord(raf: RandomAccessFile, h: DbfH, idx: Int): Map<String, String> {
        val m = mutableMapOf<String, String>()
        try {
            val o = h.hl.toLong() + idx.toLong() * h.rl.toLong(); val b = ByteArray(h.rl); raf.seek(o); raf.readFully(b)
            var cur = 1; for ((f, l) in h.fs) { if (cur + l <= h.rl) m[f] = MapInfoEncodingConverter.decode(String(b, cur, l, Charsets.ISO_8859_1).trim()); cur += l }
        } catch (e: Exception) {}
        return m
    }

    private fun findVariedFile(p: File, b: String, e: String): File? {
        val vs = listOf("$b.$e", "$b.${e.uppercase()}", "${b.lowercase()}.$e", "${b.uppercase()}.$e")
        return vs.map { File(p, it) }.firstOrNull { it.exists() }
    }

    fun getFieldNames(file: File): List<String> {
        val df = findVariedFile(file.parentFile!!, file.nameWithoutExtension, "dat") ?: return emptyList()
        return try { RandomAccessFile(df, "r").use { readDbfHeader(it).fs.map { it.first } } } catch (e: Exception) { emptyList() }
    }
}
