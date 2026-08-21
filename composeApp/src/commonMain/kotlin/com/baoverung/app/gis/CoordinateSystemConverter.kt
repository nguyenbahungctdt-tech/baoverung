package com.baoverung.app.gis

import kotlin.math.*

data class CoordinateSystem(
    val id: String,
    val name: String,
    val projection: String,
    val centralMeridian: Double = 107.75,
    val zoneDegrees: Int = 3
)

data class DatumShift(
    val dx: Double, val dy: Double, val dz: Double,
    val rx: Double = 0.0, val ry: Double = 0.0, val rz: Double = 0.0,
    val ds: Double = 0.0, val is7Param: Boolean = false
)

data class Vn2000Metadata(
    val centralMeridian: Double,
    val zoneDegrees: Int,
    val datumShift: DatumShift? = null
)

object CoordinateSystemConverter {
    
    private val DEFAULT_SHIFT = DatumShift(
        dx = -191.90441429, dy = -39.30318279, dz = -111.45032835,
        rx = -0.00928836 * (PI / (180 * 3600)), ry = 0.01975479 * (PI / (180 * 3600)), rz = -0.00427372 * (PI / (180 * 3600)),
        ds = 0.252906278 * 1e-6, is7Param = true
    )

    const val A_WGS84 = 6378137.0
    const val F_WGS84 = 1.0 / 298.257223563

    val SYSTEMS = mutableListOf<CoordinateSystem>()

    /**
     * Khởi tạo các hệ tọa độ từ chuỗi JSON.
     * Trong Compose Multiplatform, dữ liệu này sẽ được nạp qua Res.readBytes(...).decodeToString()
     */
    fun initializeFromJson(jsonString: String) {
        // Tạm thời để trống hoặc dùng regex đơn giản nếu không muốn thêm dependency JSON ngay lập tức
        // Hoặc yêu cầu caller tự parse và add vào SYSTEMS.
        if (SYSTEMS.isNotEmpty()) return
        // Triển khai thực tế sẽ dùng kotlinx-serialization
    }

    fun generateProj4Json(cm: Double, zone: Int, description: String = ""): String {
        val epsg = 4756 
        val proj4 = "+proj=tmerc +lat_0=0 +lon_0=$cm +k=0.999${if(zone==3) "9" else "6"} +x_0=500000 +y_0=0 +ellps=WGS84 +towgs84=-191.90441429,-39.30318279,-111.45032835,-0.00928836,0.01975479,-0.00427372,0.252906278 +units=m +no_defs"
        val desc = description.ifEmpty { "VN-2000 / UTM zone ${if(zone==3) "3" else "6"} degree" }
        
        return """
        {
          "EPSG": $epsg,
          "PROJ4": "$proj4",
          "DESC": "$desc"
        }
        """.trimIndent()
    }

    fun extractVn2000Metadata(coordSys: String): Vn2000Metadata? {
        try {
            val upper = coordSys.uppercase().replace("\\s+".toRegex(), " ")
            if (!upper.contains("PROJECTION 8")) return null
            
            val parts = upper.split(",").map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
            val p8Idx = parts.indexOfFirst { it.contains("PROJECTION 8") }
            if (p8Idx == -1) return null

            var cur = p8Idx + 1
            if (cur >= parts.size) return null
            val datumCode = parts[cur++].toIntOrNull() ?: 0
            var shift: DatumShift? = null
            
            when {
                datumCode == 999 -> if (cur + 3 < parts.size) { 
                    shift = DatumShift(
                        parts[cur + 1].toDoubleOrNull() ?: DEFAULT_SHIFT.dx, 
                        parts[cur + 2].toDoubleOrNull() ?: DEFAULT_SHIFT.dy, 
                        parts[cur + 3].toDoubleOrNull() ?: DEFAULT_SHIFT.dz, 
                        is7Param = false
                    ); cur += 4 
                }
                datumCode >= 1000 -> if (cur + 7 < parts.size) {
                    shift = DatumShift(
                        parts[cur + 1].toDoubleOrNull() ?: DEFAULT_SHIFT.dx, 
                        parts[cur + 2].toDoubleOrNull() ?: DEFAULT_SHIFT.dy, 
                        parts[cur + 3].toDoubleOrNull() ?: DEFAULT_SHIFT.dz,
                        (parts[cur + 4].toDoubleOrNull() ?: 0.0) * (PI / (180 * 3600)), 
                        (parts[cur + 5].toDoubleOrNull() ?: 0.0) * (PI / (180 * 3600)), 
                        (parts[cur + 6].toDoubleOrNull() ?: 0.0) * (PI / (180 * 3600)),
                        (parts[cur + 7].toDoubleOrNull() ?: 0.0) * 1e-6, 
                        is7Param = true
                    ); cur += 8
                }
                datumCode == 104 -> { 
                    shift = DEFAULT_SHIFT
                }
            }
            
            cur++
            
            if (cur < parts.size) {
                val cm = parts[cur].toDoubleOrNull() ?: 107.75
                cur += 2
                val k0 = if (cur < parts.size) parts[cur].toDoubleOrNull() ?: 0.9999 else 0.9999
                return Vn2000Metadata(cm, if (k0 < 0.9997) 6 else 3, shift ?: DEFAULT_SHIFT)
            }
        } catch (e: Exception) {
            println("CoordConv: Failed to extract metadata from: $coordSys")
        }
        return null
    }

    fun extractBoundsFromCoordSys(coordSys: String): RectD? {
        try {
            val pattern = "BOUNDS\\s*\\(\\s*([0-9.E+-]+)\\s*,\\s*([0-9.E+-]+)\\s*\\)\\s*\\(\\s*([0-9.E+-]+)\\s*,\\s*([0-9.E+-]+)\\s*\\)".toRegex(RegexOption.IGNORE_CASE)
            pattern.find(coordSys)?.let { 
                val x1 = it.groupValues[1].toDouble()
                val y1 = it.groupValues[2].toDouble()
                val x2 = it.groupValues[3].toDouble()
                val y2 = it.groupValues[4].toDouble()
                return RectD(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
            }
        } catch (e: Exception) {
            println("CoordConv: Failed to parse Bounds from string: ${e.message}")
        }
        return null
    }

    data class RectD(val xMin: Double, val yMin: Double, val xMax: Double, val yMax: Double)

    fun vn2000ToWgs84(eX: Double, nY: Double, cm: Double, zd: Int, shift: DatumShift? = null): Pair<Double, Double> {
        val (lLat, lLon) = inverseTransverseMercator(eX, nY, cm, if (zd == 6) 0.9996 else 0.9999, A_WGS84, F_WGS84)
        return applyDatumShift(lLat, lLon, false, shift ?: DEFAULT_SHIFT)
    }

    fun wgs84ToVn2000(lat: Double, lon: Double, cm: Double, zd: Int): Pair<Double, Double> {
        val (lLat, lLon) = applyDatumShift(lat, lon, true, DEFAULT_SHIFT)
        return transverseMercator(lLat, lLon, cm, if (zd == 6) 0.9996 else 0.9999, A_WGS84, F_WGS84)
    }

    fun toWgs84(x: Double, y: Double, system: CoordinateSystem): Pair<Double, Double> {
        return when (system.projection) {
            "WGS84" -> Pair(y, x)
            "VN2000" -> vn2000ToWgs84(x, y, system.centralMeridian, system.zoneDegrees)
            else -> Pair(0.0, 0.0)
        }
    }

    fun fromWgs84(lat: Double, lon: Double, system: CoordinateSystem): Pair<Double, Double> {
        return when (system.projection) {
            "WGS84" -> Pair(lon, lat)
            "VN2000" -> wgs84ToVn2000(lat, lon, system.centralMeridian, system.zoneDegrees)
            else -> Pair(0.0, 0.0)
        }
    }

    fun formatDecimalToDms(decimal: Double): String {
        val absV = abs(decimal); val d = absV.toInt(); val m = ((absV - d) * 60).toInt()
        val s = ((absV - d - m / 60.0) * 3600).let { if (it < 0) 0.0 else it }
        return "${if (decimal < 0) "-" else ""}${d}°${m.toString().padStart(2, '0')}'${s.toInt().toString().padStart(2, '0')}\""
    }

    fun formatCoordinateDisplay(x: Double, y: Double, sys: CoordinateSystem, prov: String = ""): String {
        val base = when (sys.projection) {
            "WGS84" -> if (sys.id == "WGS84_DMS") "Lat: ${formatDecimalToDms(y)}, Lon: ${formatDecimalToDms(x)}"
                       else "Lat: ${y.toString().take(9)}, Lon: ${x.toString().take(9)}"
            "VN2000" -> "X=${x.toString().take(10)}, Y=${y.toString().take(10)}"
            else -> "X=${x.toString().take(10)}, Y=${y.toString().take(10)}"
        }
        return if (sys.projection == "VN2000") {
            val cmS = sys.centralMeridian.toString().replace(".0", "").replace(".", "°") + "'"
            "VN2000 Mui ${sys.zoneDegrees}° - $cmS ($prov): $base"
        } else "${sys.name}: $base"
    }

    private fun applyDatumShift(lat: Double, lon: Double, inverse: Boolean, shift: DatumShift): Pair<Double, Double> {
        val sign = if (inverse) -1.0 else 1.0
        val phi = lat * PI / 180.0; val lam = lon * PI / 180.0; val b = A_WGS84 * (1.0 - F_WGS84)
        val e2 = (A_WGS84 * A_WGS84 - b * b) / (A_WGS84 * A_WGS84)
        val n = A_WGS84 / sqrt(1.0 - e2 * sin(phi) * sin(phi))
        val x = n * cos(phi) * cos(lam); val y = n * cos(phi) * sin(lam); val z = n * (1.0 - e2) * sin(phi)
        val dx = sign * shift.dx; val dy = sign * shift.dy; val dz = sign * shift.dz; val s = 1.0 + sign * shift.ds
        val nx: Double; val ny: Double; val nz: Double
        if (shift.is7Param) {
            val rx = sign * shift.rx; val ry = sign * shift.ry; val rz = sign * shift.rz
            nx = dx + s * (x - rz * y + ry * z); ny = dy + s * (rz * x + y - rx * z); nz = dz + s * (-ry * x + rx * y + z)
        } else { nx = x + dx; ny = y + dy; nz = z + dz }
        val nlam = atan2(ny, nx); val p = sqrt(nx * nx + ny * ny); var nphi = atan2(nz, p * (1.0 - e2))
        repeat(10) { val nn = A_WGS84 / sqrt(1.0 - e2 * sin(nphi) * sin(nphi)); nphi = atan2(nz + e2 * nn * sin(nphi), p) }
        return Pair(nphi * 180.0 / PI, nlam * 180.0 / PI)
    }

    private fun transverseMercator(lat: Double, lon: Double, cm: Double, k0: Double, a: Double, f: Double): Pair<Double, Double> {
        val e2 = 2 * f - f * f; val ep2 = e2 / (1 - e2); val phi = lat * PI / 180.0; val lam = lon * PI / 180.0; val lam0 = cm * PI / 180.0
        val n = a / sqrt(1.0 - e2 * sin(phi) * sin(phi)); val t = tan(phi) * tan(phi); val c = ep2 * cos(phi) * cos(phi); val aa = (lam - lam0) * cos(phi)
        val m = a * ((1.0 - e2 / 4.0 - 3.0 * e2 * e2 / 64.0 - 5.0 * e2 * e2 * e2 / 256.0) * phi - (3.0 * e2 / 8.0 + 3.0 * e2 * e2 / 32.0 + 45.0 * e2 * e2 * e2 / 1024.0) * sin(2.0 * phi) + (15.0 * e2 * e2 / 256.0 + 45.0 * e2 * e2 * e2 / 1024.0) * sin(4.0 * phi) - (35.0 * e2 * e2 / 3072.0) * sin(6.0 * phi))
        val x = 500000.0 + k0 * n * (aa + (1.0 - t + c) * aa * aa * aa / 6.0 + (5.0 - 18.0 * t + t * t + 72.0 * c - 58.0 * ep2) * aa * aa * aa * aa * aa / 120.0)
        val y = k0 * (m + n * tan(phi) * (aa * aa / 2.0 + (5.0 - t + 9.0 * c + 4.0 * c * c) * aa * aa * aa / 24.0 + (61.0 - 58.0 * t + t * t + 600.0 * c - 330.0 * ep2) * aa * aa * aa * aa * aa * aa / 720.0))
        return Pair(x, y)
    }

    private fun inverseTransverseMercator(x: Double, y: Double, cm: Double, k0: Double, a: Double, f: Double): Pair<Double, Double> {
        val e2 = 2 * f - f * f; val ep2 = e2 / (1 - e2); val lam0 = cm * PI / 180.0; val xx = x - 500000.0; val m = y / k0
        val mu = m / (a * (1.0 - e2 / 4.0 - 3.0 * e2 * e2 / 64.0 - 5.0 * e2 * e2 * e2 / 256.0)); val e1 = (1.0 - sqrt(1.0 - e2)) / (1.0 + sqrt(1.0 - e2))
        val p1 = mu + (3.0 * e1 / 2.0 - 27.0 * e1 * e1 * e1 / 32.0) * sin(2.0 * mu) + (21.0 * e1 * e1 / 16.0 - 55.0 * e1 * e1 * e1 * e1 / 32.0) * sin(4.0 * mu) + (151.0 * e1 * e1 * e1 / 96.0) * sin(6.0 * mu)
        val n1 = a / sqrt(1.0 - e2 * sin(p1) * sin(p1)); val t1 = tan(p1) * tan(p1); val c1 = ep2 * cos(p1) * cos(p1); val r1 = a * (1.0 - e2) / (1.0 - e2 * sin(p1) * sin(p1)).pow(1.5); val d = xx / (n1 * k0)
        val lat = p1 - (n1 * tan(p1) / r1) * (d * d / 2.0 - (5.0 + 3.0 * t1 + 10.0 * c1 - 4.0 * c1 * c1 - 9.0 * ep2) * d * d * d * d / 24.0 + (61.0 + 90.0 * t1 + 298.0 * c1 + 45.0 * t1 * t1 - 252.0 * ep2 - 3.0 * c1 * c1) * d * d * d * d * d * d / 720.0)
        val lon = lam0 + (d - (1.0 + 2.0 * t1 + c1) * d * d * d / 6.0 + (5.0 - 2.0 * c1 + 28.0 * t1 - 3.0 * c1 * c1 + 8.0 * ep2 + 24.0 * t1 * t1) * d * d * d * d * d / 120.0) / cos(p1)
        return Pair(lat * 180.0 / PI, lon * 180.0 / PI)
    }
}
