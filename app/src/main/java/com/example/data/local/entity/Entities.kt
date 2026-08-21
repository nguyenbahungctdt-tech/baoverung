package com.baoverung.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "waypoints")
data class WaypointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val vn2000X: Double,
    val vn2000Y: Double,
    val accuracy: Float,
    val satellitesCount: Int,
    val photoPath: String? = null,
    val displayOrder: Int = 0, // Order for images
    val timestampUtc: Long = System.currentTimeMillis(),
    val userEmail: String,
    val category: String = "Lâm nghiệp",
    val displayColorHex: String = "#FF1976D2",
    val isSynced: Boolean = false
)

@Entity(tableName = "track_logs")
data class TrackLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startTimeUtc: Long,
    val endTimeUtc: Long = 0,
    val totalDistanceMeters: Double = 0.0,
    val pointsJson: String,
    val sampledPointsJson: String? = null,
    val userEmail: String,
    val gpxFilePath: String? = null,
    val isExportedGpx: Boolean = false,
    val isSentEmail: Boolean = false,
    val displayColorHex: String = "#FFFF3D00",
    val category: String = "GPX",
    val isSynced: Boolean = false
)

@Entity(tableName = "patrol_logs")
data class PatrolLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val incidentType: String, // "Khai thác gỗ trái phép", "Lấn chiếm đất rừng", "Cháy rừng", "Bẫy/Săn bắt", etc.
    val discoveryTimeUtc: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val vn2000X: Double,
    val vn2000Y: Double,
    val accuracy: Float,
    val satellitesCount: Int,
    val leaderName: String,
    val violationTime: String = "",
    val violationLocation: String = "",
    val violatorName: String = "",
    val violatorIdCard: String = "",
    val violatorAddress: String = "",
    val violatorPhone: String = "",
    val confiscatedTools: String = "",
    val relatedPersons: String = "",
    val onSiteAction: String = "",
    val onSiteRecordings: String = "", // Một số ghi nhận tại hiện trường
    val notes: String = "",
    val photoPath: String? = null, // Can contain multiple paths separated by |
    val violationField: String = "Lâm nghiệp", // "Lâm nghiệp" or "Đất đai"
    val userEmail: String,
    val isSentEmail: Boolean = false,
    val displayColorHex: String = "#FFD32F2F",
    val isSynced: Boolean = false
)

@Entity(tableName = "daily_journals")
data class DailyJournalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateStr: String, // YYYY-MM-DD
    val timestampUtc: Long = System.currentTimeMillis(),
    val content: String = "",
    val notes: String = "",
    val userEmail: String,
    val linkedDataJson: String = "", // Summary of linked GPXs, Waypoints, PatrolLogs
    val weather: String = "", // Tình hình thời tiết
    val patrolTeam: String = "", // Thành phần đoàn tuần tra
    val patrolCompartment: String = "", // Tiểu khu/khoảnh tuần tra
    val displayColorHex: String = "#FF1976D2",
    val isSynced: Boolean = false
)

@Entity(tableName = "email_queue")
data class EmailQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipientEmail: String = "nguyenbahung.ctdt@gmail.com",
    val senderEmail: String,
    val subject: String,
    val body: String,
    val attachmentPath: String? = null,
    val createdAtUtc: Long = System.currentTimeMillis(),
    val isSent: Boolean = false,
    val attemptCount: Int = 0,
    val lastError: String? = null
)

@Entity(tableName = "gis_layers")
data class GisLayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val fileType: String, // "MBTILES", "SHP", "TAB", "KML", "GPX"
    val filePath: String,
    val isVisible: Boolean = true,
    val opacity: Float = 0.8f,
    val strokeColorHex: String = "#FF2E7D32",
    val fillColorHex: String = "#334CAF50",
    val centralMeridian: Double = 107.75,
    val zoneDegrees: Int = 3,
    val priority: Int = 0,
    val coordinateSystem: String = "VN2000", // "VN2000", "UTM", "WGS84", "HN72"
    val labelColumn: String? = null
)

@Entity(tableName = "polygons")
data class PolygonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val pointsJson: String, // List of GpsPoint encoded as JSON
    val areaSquareMeters: Double = 0.0,
    val perimeterMeters: Double = 0.0,
    val centroidLat: Double = 0.0,
    val centroidLon: Double = 0.0,
    val centroidVn2000X: Double = 0.0,
    val centroidVn2000Y: Double = 0.0,
    val timestampUtc: Long = System.currentTimeMillis(),
    val userEmail: String,
    val displayColorHex: String = "#FF1976D2",
    val isSynced: Boolean = false
)

@Entity(tableName = "flora_fauna_logs")
data class FloraFaunaLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val officerName: String,
    val timestampUtc: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val vn2000X: Double,
    val vn2000Y: Double,
    val accuracy: Float,
    val satellitesCount: Int,
    val appearanceDescription: String,
    val features: String,
    val count: String,
    val habitatType: String,
    val temperature: String = "",
    val humidity: String = "",
    val canopyCover: String = "",
    val surroundingPlants: String = "",
    val specimens: String = "",
    val photoPath: String? = null,
    val userEmail: String,
    val displayColorHex: String = "#FF2E7D32",
    val isSynced: Boolean = false
)

@Entity(tableName = "natural_impact_logs")
data class NaturalImpactLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val officerName: String,
    val timestampUtc: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val vn2000X: Double,
    val vn2000Y: Double,
    val accuracy: Float,
    val satellitesCount: Int,
    val cause: String,
    val otherCause: String = "",
    val affectedArea: String = "",
    val statusBefore: String = "",
    val statusAfter: String = "",
    val resourceDamage: String = "",
    val occurrenceTime: String = "",
    val photoPath: String? = null,
    val userEmail: String,
    val displayColorHex: String = "#FFFBC02D",
    val isSynced: Boolean = false
)
