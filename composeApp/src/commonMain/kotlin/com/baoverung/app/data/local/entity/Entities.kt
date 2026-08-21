package com.baoverung.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

@Entity(tableName = "waypoints")
@Serializable
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
    val displayOrder: Int = 0,
    val timestampUtc: Long = Clock.System.now().toEpochMilliseconds(),
    val userEmail: String,
    val category: String = "Lâm nghiệp",
    val displayColorHex: String = "#FF1976D2",
    val isSynced: Boolean = false
)

@Entity(tableName = "track_logs")
@Serializable
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
@Serializable
data class PatrolLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

@Entity(tableName = "flora_fauna_logs")
@Serializable
data class FloraFaunaLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val vn2000X: Double = 0.0,
    val vn2000Y: Double = 0.0,
    val accuracy: Float = 0f,
    val satellitesCount: Int = 0,
    val officerName: String,
    val appearanceDescription: String,
    val features: String = "",
    val count: String = "",
    val habitatType: String = "",
    val temperature: String = "",
    val humidity: String = "",
    val canopyCover: String = "",
    val surroundingPlants: String = "",
    val specimens: String = "",
    val photoPath: String? = null,
    val timestampUtc: Long = Clock.System.now().toEpochMilliseconds(),
    val displayColorHex: String = "#FF2E7D32",
    val isSynced: Boolean = false
)

@Entity(tableName = "natural_impact_logs")
@Serializable
data class NaturalImpactLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val vn2000X: Double = 0.0,
    val vn2000Y: Double = 0.0,
    val accuracy: Float = 0f,
    val satellitesCount: Int = 0,
    val officerName: String,
    val cause: String,
    val otherCause: String = "",
    val affectedArea: String = "",
    val statusBefore: String = "",
    val statusAfter: String = "",
    val resourceDamage: String = "",
    val occurrenceTime: String = "",
    val photoPath: String? = null,
    val timestampUtc: Long = Clock.System.now().toEpochMilliseconds(),
    val displayColorHex: String = "#FFFBC02D",
    val isSynced: Boolean = false
)

@Entity(tableName = "polygons")
@Serializable
data class PolygonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val pointsJson: String,
    val areaSquareMeters: Double,
    val perimeterMeters: Double = 0.0,
    val centroidLat: Double,
    val centroidLon: Double,
    val timestampUtc: Long = Clock.System.now().toEpochMilliseconds(),
    val displayColorHex: String = "#FF388E3C",
    val isSynced: Boolean = false
)

@Entity(tableName = "daily_journals")
@Serializable
data class DailyJournalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

@Entity(tableName = "gis_layers")
@Serializable
data class GisLayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
