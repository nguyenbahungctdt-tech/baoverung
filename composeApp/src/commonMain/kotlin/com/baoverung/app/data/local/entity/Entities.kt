package com.baoverung.app.data.local.entity

import kotlinx.datetime.Clock

// Chúng ta sẽ sử dụng Room KMP cho các Entity này
// Hiện tại định nghĩa dưới dạng data class thuần túy để chia sẻ logic

data class WaypointEntity(
    val id: Long = 0,
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
    val displayOrder: Int = 0,
    val timestampUtc: Long = Clock.System.now().toEpochMilliseconds(),
    val userEmail: String,
    val category: String = "Lâm nghiệp",
    val displayColorHex: String = "#FF1976D2",
    val isSynced: Boolean = false
)

data class TrackLogEntity(
    val id: Long = 0,
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

data class PatrolLogEntity(
    val id: Long = 0,
    val incidentType: String,
    val discoveryTimeUtc: Long = Clock.System.now().toEpochMilliseconds(),
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
    val onSiteRecordings: String = "",
    val notes: String = "",
    val photoPath: String? = null,
    val violationField: String = "Lâm nghiệp",
    val userEmail: String,
    val isSentEmail: Boolean = false,
    val displayColorHex: String = "#FFD32F2F",
    val isSynced: Boolean = false
)

data class DailyJournalEntity(
    val id: Long = 0,
    val dateStr: String,
    val timestampUtc: Long = Clock.System.now().toEpochMilliseconds(),
    val content: String = "",
    val notes: String = "",
    val userEmail: String,
    val linkedDataJson: String = "",
    val weather: String = "",
    val patrolTeam: String = "",
    val patrolCompartment: String = "",
    val displayColorHex: String = "#FF1976D2",
    val isSynced: Boolean = false
)

data class GisLayerEntity(
    val id: Long = 0,
    val name: String,
    val fileType: String,
    val filePath: String,
    val isVisible: Boolean = true,
    val opacity: Float = 0.8f,
    val strokeColorHex: String = "#FF2E7D32",
    val fillColorHex: String = "#334CAF50",
    val centralMeridian: Double = 107.75,
    val zoneDegrees: Int = 3,
    val priority: Int = 0,
    val coordinateSystem: String = "VN2000",
    val labelColumn: String? = null
)
