package com.baoverung.app.gis

import com.baoverung.app.data.local.entity.PatrolLogEntity
import com.baoverung.app.data.local.entity.PolygonEntity
import com.baoverung.app.data.local.entity.TrackLogEntity
import com.baoverung.app.data.local.entity.WaypointEntity
import com.baoverung.app.data.model.GpsPoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GpxExporter {
    private val iso8601Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Convert spatial JSON or GeoJSON to GPX for MapSource compatibility
     */
    fun convertSpatialJsonToGpx(inputFile: File, outputFile: File): Boolean {
        return try {
            val content = inputFile.readText()
            val points = mutableListOf<GpsPoint>()
            
            if (content.trim().startsWith("[")) {
                val array = org.json.JSONArray(content)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    points.add(GpsPoint(
                        latitude = obj.optDouble("latitude", 0.0),
                        longitude = obj.optDouble("longitude", 0.0),
                        altitude = obj.optDouble("altitude", 0.0),
                        timestampUtc = obj.optLong("timestampUtc", System.currentTimeMillis())
                    ))
                }
            } else if (content.contains("\"Feature\"")) {
                val root = org.json.JSONObject(content)
                val features = if (root.optString("type") == "FeatureCollection") {
                    root.getJSONArray("features")
                } else {
                    org.json.JSONArray().put(root)
                }
                
                for (i in 0 until features.length()) {
                    val feat = features.getJSONObject(i)
                    val geom = feat.optJSONObject("geometry") ?: continue
                    val type = geom.getString("type")
                    val coords = geom.getJSONArray("coordinates")
                    
                    if (type == "Point") {
                        points.add(GpsPoint(coords.getDouble(1), coords.getDouble(0)))
                    } else if (type == "LineString" || type == "Polygon" || type == "MultiPoint") {
                        val outer = if (type == "Polygon") coords.getJSONArray(0) else coords
                        for (j in 0 until outer.length()) {
                            val pt = outer.getJSONArray(j)
                            points.add(GpsPoint(pt.getDouble(1), pt.getDouble(0)))
                        }
                    }
                }
            }
            
            if (points.isNotEmpty()) {
                val dummyTrack = TrackLogEntity(
                    title = inputFile.nameWithoutExtension,
                    startTimeUtc = points.first().timestampUtc,
                    pointsJson = "",
                    userEmail = ""
                )
                exportTrackLogToGpx(dummyTrack, points, outputFile)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Export TrackLog to GPX file format
     */
    fun exportTrackLogToGpx(trackLog: TrackLogEntity, points: List<GpsPoint>, outputFile: File, creatorName: String = "", creatorPhone: String = ""): File {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Ứng dụng Bảo vệ rừng - Đại Thành\"\n")
        sb.append("  xmlns=\"http://www.topografix.com/GPX/1/1\"\n")
        sb.append("  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n")

        sb.append("  <metadata>\n")
        sb.append("    <name>${escapeXml(trackLog.title)}</name>\n")
        sb.append("    <time>${iso8601Format.format(Date(trackLog.startTimeUtc))}</time>\n")
        val authorText = if (creatorName.isNotEmpty()) "$creatorName - $creatorPhone" else "Cán bộ Kiểm lâm"
        sb.append("    <author><name>${escapeXml(authorText)}</name></author>\n")
        sb.append("  </metadata>\n")

        sb.append("  <trk>\n")
        sb.append("    <name>${escapeXml(trackLog.title)}</name>\n")
        sb.append("    <trkseg>\n")

        for (pt in points) {
            sb.append("      <trkpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\">\n")
            sb.append("        <ele>${pt.altitude}</ele>\n")
            sb.append("        <time>${iso8601Format.format(Date(pt.timestampUtc))}</time>\n")
            sb.append("        <sat>${pt.satellitesCount}</sat>\n")
            sb.append("        <hdop>${pt.accuracy}</hdop>\n")
            sb.append("      </trkpt>\n")
        }

        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>")

        outputFile.writeText(sb.toString())
        return outputFile
    }

    /**
     * Export a list of Waypoints to GPX format (wpt nodes)
     */
    fun exportWaypointsToGpx(waypoints: List<WaypointEntity>, outputFile: File): File {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Ứng dụng Bảo vệ rừng - Đại Thành\"\n")
        sb.append("  xmlns=\"http://www.topografix.com/GPX/1/1\"\n")
        sb.append("  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n")

        sb.append("  <metadata>\n")
        sb.append("    <name>Danh sách điểm khảo sát</name>\n")
        sb.append("    <time>${iso8601Format.format(Date())}</time>\n")
        sb.append("  </metadata>\n")

        for (wp in waypoints) {
            sb.append("  <wpt lat=\"${wp.latitude}\" lon=\"${wp.longitude}\">\n")
            sb.append("    <ele>${wp.altitude}</ele>\n")
            sb.append("    <time>${iso8601Format.format(Date(wp.timestampUtc))}</time>\n")
            sb.append("    <name>${escapeXml(wp.title)}</name>\n")
            sb.append("    <desc>${escapeXml(wp.description)}</desc>\n")
            sb.append("    <category>${escapeXml(wp.category)}</category>\n")
            sb.append("  </wpt>\n")
        }

        sb.append("</gpx>")

        outputFile.writeText(sb.toString())
        return outputFile
    }

    /**
     * Export a Polygon to GPX format (trk node) for MapSource compatibility
     */
    fun exportPolygonToGpx(polygon: PolygonEntity, outputFile: File): File {
        val points = mutableListOf<GpsPoint>()
        try {
            val array = org.json.JSONArray(polygon.pointsJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                points.add(GpsPoint(
                    latitude = obj.optDouble("latitude", 0.0),
                    longitude = obj.optDouble("longitude", 0.0),
                    altitude = obj.optDouble("altitude", 0.0),
                    timestampUtc = obj.optLong("timestampUtc", System.currentTimeMillis())
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Ứng dụng Bảo vệ rừng - Đại Thành\"\n")
        sb.append("  xmlns=\"http://www.topografix.com/GPX/1/1\"\n")
        sb.append("  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n")

        sb.append("  <metadata>\n")
        sb.append("    <name>${escapeXml(polygon.title)}</name>\n")
        sb.append("    <time>${iso8601Format.format(Date(polygon.timestampUtc))}</time>\n")
        sb.append("  </metadata>\n")

        sb.append("  <trk>\n")
        sb.append("    <name>${escapeXml(polygon.title)}</name>\n")
        sb.append("    <desc>${escapeXml(polygon.description)}</desc>\n")
        sb.append("    <trkseg>\n")

        for (pt in points) {
            sb.append("      <trkpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\">\n")
            if (pt.altitude != 0.0) sb.append("        <ele>${pt.altitude}</ele>\n")
            sb.append("        <time>${iso8601Format.format(Date(pt.timestampUtc))}</time>\n")
            sb.append("      </trkpt>\n")
        }

        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>")

        outputFile.writeText(sb.toString())
        return outputFile
    }

    /**
     * Export Patrol Log & Waypoint to GPX / Text Summary
     */
    fun exportPatrolLogSummary(
        patrolLog: PatrolLogEntity,
        outputFile: File,
        centralMeridian: Double = 107.75,
        zoneDegrees: Int = 3,
        officerName: String = "",
        officerUnit: String = "",
        officerDepartment: String = "",
        officerPhone: String = ""
    ): File {
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(patrolLog.discoveryTimeUtc))
        val saveDateStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val displayOfficer = if (officerName.isNotEmpty()) officerName else "Cán bộ chưa xác định"

        val text = """
            CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM
            Độc lập - Tự do - Hạnh phúc
            --------------------------
            
            PHIẾU GHI NHẬN TUẦN TRA BẢO VỆ RỪNG
            (Mã số sự vụ: ${patrolLog.id})
            
            I. THÔNG TIN CHUNG
            - Cán bộ thực hiện: $displayOfficer
            - Đơn vị: ${officerUnit.ifEmpty { "Hạt Kiểm lâm / Ban Quản lý rừng" }}
            - Bộ phận: ${officerDepartment.ifEmpty { "Tổ tuần tra bảo vệ rừng" }}
            - Số điện thoại: ${officerPhone.ifEmpty { "Chưa cập nhật" }}
            - Thời gian phát hiện: $dateStr
            - Thời gian lập báo cáo: $saveDateStr

            II. CHI TIẾT SỰ VỤ
            - Loại hình sự vụ: ${patrolLog.incidentType}
            - Lĩnh vực vi phạm: ${patrolLog.violationField}
            - Thời gian vi phạm dự kiến: ${patrolLog.violationTime}
            - Địa điểm: ${patrolLog.violationLocation}
            
            III. THÔNG TIN VỊ TRÍ (GPS)
            - Hệ tọa độ WGS84: Lat ${String.format("%.6f", patrolLog.latitude)}, Lon ${String.format("%.6f", patrolLog.longitude)}
            - Hệ tọa độ VN2000 (L0=$centralMeridian, Múi $zoneDegrees): X=${String.format("%.2f", patrolLog.vn2000X)}, Y=${String.format("%.2f", patrolLog.vn2000Y)}
            - Cao độ: ${String.format("%.1f", patrolLog.altitude)}m
            - Độ chính xác GPS: ±${patrolLog.accuracy}m (${patrolLog.satellitesCount} vệ tinh)

            IV. DIỄN BIẾN TẠI HIỆN TRƯỜNG
            ${patrolLog.onSiteRecordings.ifEmpty { "Không có ghi nhận đặc biệt" }}

            V. ĐỐI TƯỢNG VÀ TANG VẬT
            - Đối tượng: ${patrolLog.violatorName.ifEmpty { "Chưa xác định" }}
            - CCCD/CMND: ${patrolLog.violatorIdCard.ifEmpty { "Không có" }}
            - Địa chỉ: ${patrolLog.violatorAddress.ifEmpty { "Không có" }}
            - Tang vật, phương tiện: ${patrolLog.confiscatedTools.ifEmpty { "Không có" }}
            
            VI. BIỆN PHÁP XỬ LÝ VÀ GHI CHÚ
            - Xử lý tại chỗ: ${patrolLog.onSiteAction.ifEmpty { "Đã lập biên bản ghi nhận, báo cáo cấp trên" }}
            - Ghi chú: ${patrolLog.notes.ifEmpty { "Không có" }}
            
            ----------------------------------------------------
            Báo cáo được trích xuất từ Hệ thống Quản lý tuần tra Bảo vệ rừng - Đại Thành
        """.trimIndent()

        outputFile.writeText(text)
        return outputFile
    }


    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
