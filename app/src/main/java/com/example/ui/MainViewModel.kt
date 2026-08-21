package com.baoverung.app.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baoverung.app.data.local.entity.*
import com.baoverung.app.data.model.GisFeature
import com.baoverung.app.data.model.GpsPoint
import com.baoverung.app.data.model.UserSession
import com.baoverung.app.data.model.TrackLogUiModel
import com.baoverung.app.data.model.PolygonUiModel
import com.baoverung.app.gis.GisAreaCalculator
import com.baoverung.app.gis.GpxExporter
import com.baoverung.app.gis.CoordinateSystemConverter
import com.baoverung.app.repository.SurveyRepository
import com.baoverung.app.service.GpxTrackingService
import com.baoverung.app.util.EmailSenderHelper
import com.baoverung.app.util.WordExportHelper
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MeasurementMode {
    NONE, DISTANCE, AREA, NAVIGATION, GPX_DISTANCE, GPX_AREA, MAP_DOWNLOAD
}

enum class SyncStatus {
    SYNCED,    // All items synced
    PENDING,   // Some items waiting for sync
    SYNCING    // Sync in progress
}

class MainViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {
    val repository = SurveyRepository(application)
    private val cloudRepository = com.baoverung.app.repository.CloudSyncRepository()
    private var activeKeyMonitoringListener: com.google.firebase.database.ValueEventListener? = null
    private var currentMonitoredKey: String? = null

    private val _userSession = MutableStateFlow(repository.getUserSession())
    val userSession: StateFlow<UserSession> = _userSession.asStateFlow()

    private val _compassAzimuth = MutableStateFlow(0f)
    val compassAzimuth: StateFlow<Float> = _compassAzimuth.asStateFlow()

    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val _currentLocation = MutableStateFlow<GpsPoint?>(null)
    val currentLocation: StateFlow<GpsPoint?> = _currentLocation.asStateFlow()

    private val _satellitesCount = MutableStateFlow(0)
    val satellitesCount: StateFlow<Int> = _satellitesCount.asStateFlow()

    private val _satellitesVisible = MutableStateFlow(0)
    val satellitesVisible: StateFlow<Int> = _satellitesVisible.asStateFlow()

    private val _satelliteDetails = MutableStateFlow<List<com.baoverung.app.data.model.SatelliteInfo>>(emptyList())
    val satelliteDetails: StateFlow<List<com.baoverung.app.data.model.SatelliteInfo>> = _satelliteDetails.asStateFlow()

    private val _accelerometerValues = MutableStateFlow(floatArrayOf(0f, 0f, 0f))
    val accelerometerValues: StateFlow<FloatArray> = _accelerometerValues.asStateFlow()

    private val _gravityValues = MutableStateFlow(floatArrayOf(0f, 0f, 0f))
    val gravityValues: StateFlow<FloatArray> = _gravityValues.asStateFlow()

    val waypoints = repository.waypointsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val trackLogs = repository.trackLogsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val trackLogsUi = trackLogs.map { list ->
        list.map { entity ->
            val pts = repository.parseTrackPoints(entity.pointsJson)
            val sPts = repository.parseTrackPoints(entity.sampledPointsJson ?: "")
            TrackLogUiModel(
                id = entity.id,
                title = entity.title,
                fullPoints = pts,
                sampledPoints = sPts,
                displayColorHex = entity.displayColorHex,
                totalDistanceMeters = entity.totalDistanceMeters,
                startTimeUtc = entity.startTimeUtc,
                category = entity.category.ifEmpty { "GPX" },
                minLat = if (pts.isEmpty()) 0.0 else pts.minOf { it.latitude },
                maxLat = if (pts.isEmpty()) 0.0 else pts.maxOf { it.latitude },
                minLon = if (pts.isEmpty()) 0.0 else pts.minOf { it.longitude },
                maxLon = if (pts.isEmpty()) 0.0 else pts.maxOf { it.longitude }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val patrolLogs = repository.patrolLogsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val floraFaunaLogs = repository.floraFaunaLogsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val naturalImpactLogs = repository.naturalImpactLogsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val polygons = repository.polygonsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val polygonsUi = polygons.map { list ->
        val cm = _vn2000CentralMeridian.value
        val zd = _vn2000ZoneDegrees.value
        list.map { entity ->
            val pts = repository.parseTrackPoints(entity.pointsJson)
            val (vx, vy) = com.baoverung.app.gis.CoordinateSystemConverter.wgs84ToVn2000(entity.centroidLat, entity.centroidLon, cm, zd)
            PolygonUiModel(
                id = entity.id,
                title = entity.title,
                points = pts,
                centroidLat = entity.centroidLat,
                centroidLon = entity.centroidLon,
                centroidVn2000X = vx,
                centroidVn2000Y = vy,
                areaSquareMeters = entity.areaSquareMeters,
                displayColorHex = entity.displayColorHex,
                minLat = if (pts.isEmpty()) 0.0 else pts.minOf { it.latitude },
                maxLat = if (pts.isEmpty()) 0.0 else pts.maxOf { it.latitude },
                minLon = if (pts.isEmpty()) 0.0 else pts.minOf { it.longitude },
                maxLon = if (pts.isEmpty()) 0.0 else pts.maxOf { it.longitude }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyJournals = repository.dailyJournalsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val emailQueue = repository.emailQueueFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val gisLayers = repository.gisLayersFlow.map { layers ->
        layers.sortedBy { it.priority }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeCoordinateSystem = MutableStateFlow(repository.prefs.activeCoordinateSystem)
    val activeCoordinateSystem: StateFlow<String> = _activeCoordinateSystem.asStateFlow()

    private val _gisRefreshTrigger = MutableStateFlow(0)
    private val _mapBounds = MutableStateFlow<CoordinateSystemConverter.RectD?>(null)

    val gisFeaturesMap = combine(gisLayers, _mapBounds, _gisRefreshTrigger) { layers, bounds, _ ->
        val featMap = mutableMapOf<Long, List<GisFeature>>()
        for (layer in layers) {
            if (layer.isVisible && layer.fileType != "MBTILES" && layer.fileType != "SQLITE") {
                if (bounds != null) {
                    featMap[layer.id] = repository.getFeaturesInBounds(layer.id, bounds.yMin, bounds.yMax, bounds.xMin, bounds.xMax)
                } else {
                    // Requirement: Early View - Load first 1,000 objects instantly for immediate feedback
                    featMap[layer.id] = repository.getFeaturesForLayer(layer).take(1000)
                }
            }
        }
        featMap
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _mbtilesReaders = MutableStateFlow<Map<Long, com.baoverung.app.gis.MBTilesReader>>(emptyMap())
    val mbtilesReaders: StateFlow<Map<Long, com.baoverung.app.gis.MBTilesReader>> = _mbtilesReaders.asStateFlow()

    private val _measurementMode = MutableStateFlow(MeasurementMode.NONE)
    val measurementMode: StateFlow<MeasurementMode> = _measurementMode.asStateFlow()

    private val _measurementPoints = MutableStateFlow<List<GpsPoint>>(emptyList())
    val measurementPoints: StateFlow<List<GpsPoint>> = _measurementPoints.asStateFlow()

    private val _targetNavPoint = MutableStateFlow<GpsPoint?>(null)
    val targetNavPoint: StateFlow<GpsPoint?> = _targetNavPoint.asStateFlow()

    val selectedMapSource = MutableStateFlow(repository.prefs.selectedMapSource)

    private val _isTrackingGpx = MutableStateFlow(false)
    val isTrackingGpx: StateFlow<Boolean> = _isTrackingGpx.asStateFlow()

    private val _trackedPoints = MutableStateFlow<List<GpsPoint>>(emptyList())
    val trackedPoints: StateFlow<List<GpsPoint>> = _trackedPoints.asStateFlow()

    private val _previewTrackPoints = MutableStateFlow<List<GpsPoint>>(emptyList())
    val previewTrackPoints: StateFlow<List<GpsPoint>> = _previewTrackPoints.asStateFlow()

    private val _mapFocusRequest = MutableSharedFlow<Triple<Double, Double, Float>>()
    val mapFocusRequest = _mapFocusRequest.asSharedFlow()

    private val _vn2000CentralMeridian = MutableStateFlow(repository.prefs.vn2000CentralMeridian)
    val vn2000CentralMeridian: StateFlow<Double> = _vn2000CentralMeridian.asStateFlow()

    private val _vn2000ZoneDegrees = MutableStateFlow(repository.prefs.vn2000ZoneDegrees)
    val vn2000ZoneDegrees: StateFlow<Int> = _vn2000ZoneDegrees.asStateFlow()

    private val _vn2000ProvinceName = MutableStateFlow(repository.prefs.vn2000ProvinceName)
    val vn2000ProvinceName: StateFlow<String> = _vn2000ProvinceName.asStateFlow()

    // Map UI States
    private val _showZoomControls: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showZoomControls)
    val showZoomControls: StateFlow<Boolean> = _showZoomControls.asStateFlow()

    private val _showRotationControls: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showRotationControls)
    val showRotationControls: StateFlow<Boolean> = _showRotationControls.asStateFlow()

    private val _showCompass: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showCompass)
    val showCompass: StateFlow<Boolean> = _showCompass.asStateFlow()

    private val _showZoomLevel: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showZoomLevel)
    val showZoomLevel: StateFlow<Boolean> = _showZoomLevel.asStateFlow()

    private val _showMapCenter: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showMapCenter)
    val showMapCenter: StateFlow<Boolean> = _showMapCenter.asStateFlow()

    private val _showSatelliteInfo: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showSatelliteInfo)
    val showSatelliteInfo: StateFlow<Boolean> = _showSatelliteInfo.asStateFlow()

    private val _showViewAngle: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showViewAngle)
    val showViewAngle: StateFlow<Boolean> = _showViewAngle.asStateFlow()

    private val _showViewLine: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showViewLine)
    val showViewLine: StateFlow<Boolean> = _showViewLine.asStateFlow()

    private val _showMoveDirection: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showMoveDirection)
    val showMoveDirection: StateFlow<Boolean> = _showMoveDirection.asStateFlow()

    private val _showMoveLine: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showMoveLine)
    val showMoveLine: StateFlow<Boolean> = _showMoveLine.asStateFlow()

    private val _showLabelsGlobal: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showLabelsGlobal)
    val showLabelsGlobal: StateFlow<Boolean> = _showLabelsGlobal.asStateFlow()

    private val _showImagesGlobal: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showImagesGlobal)
    val showImagesGlobal: StateFlow<Boolean> = _showImagesGlobal.asStateFlow()

    private val _showPointsGlobal: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showPointsGlobal)
    val showPointsGlobal: StateFlow<Boolean> = _showPointsGlobal.asStateFlow()

    private val _showTracklogsGlobal: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showTracklogsGlobal)
    val showTracklogsGlobal: StateFlow<Boolean> = _showTracklogsGlobal.asStateFlow()

    private val _showLinesGlobal: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showLinesGlobal)
    val showLinesGlobal: StateFlow<Boolean> = _showLinesGlobal.asStateFlow()

    private val _showPolygonsGlobal: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showPolygonsGlobal)
    val showPolygonsGlobal: StateFlow<Boolean> = _showPolygonsGlobal.asStateFlow()

    private val _showIncidentsGlobal: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showIncidentsGlobal)
    val showIncidentsGlobal: StateFlow<Boolean> = _showIncidentsGlobal.asStateFlow()

    private val _showDailyJournalsGlobal: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showDailyJournalsGlobal)
    val showDailyJournalsGlobal: StateFlow<Boolean> = _showDailyJournalsGlobal.asStateFlow()

    private val _showFloraFaunaGlobal: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showFloraFaunaGlobal)
    val showFloraFaunaGlobal: StateFlow<Boolean> = _showFloraFaunaGlobal.asStateFlow()

    private val _showNaturalImpactGlobal: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showNaturalImpactGlobal)
    val showNaturalImpactGlobal: StateFlow<Boolean> = _showNaturalImpactGlobal.asStateFlow()

    // Category Settings
    private val _imageIconType: MutableStateFlow<String> = MutableStateFlow(repository.prefs.imageIconType)
    val imageIconType: StateFlow<String> = _imageIconType.asStateFlow()

    private val _imageIconSize: MutableStateFlow<Int> = MutableStateFlow(repository.prefs.imageIconSize)
    val imageIconSize: StateFlow<Int> = _imageIconSize.asStateFlow()

    private val _imageColor: MutableStateFlow<String> = MutableStateFlow(repository.prefs.imageColor)
    val imageColor: StateFlow<String> = _imageColor.asStateFlow()

    private val _showImageLabels: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showImageLabels)
    val showImageLabels: StateFlow<Boolean> = _showImageLabels.asStateFlow()

    private val _imageLabelSize: MutableStateFlow<Int> = MutableStateFlow(repository.prefs.imageLabelSize)
    val imageLabelSize: StateFlow<Int> = _imageLabelSize.asStateFlow()

    private val _imageQuality: MutableStateFlow<Int> = MutableStateFlow(repository.prefs.imageQuality)
    val imageQuality: StateFlow<Int> = _imageQuality.asStateFlow()

    private val _imageResize: MutableStateFlow<Int> = MutableStateFlow(repository.prefs.imageResize)
    val imageResize: StateFlow<Int> = _imageResize.asStateFlow()

    private val _pointIconType: MutableStateFlow<String> = MutableStateFlow(repository.prefs.pointIconType)
    val pointIconType: StateFlow<String> = _pointIconType.asStateFlow()

    private val _pointIconSize: MutableStateFlow<Int> = MutableStateFlow(repository.prefs.pointIconSize)
    val pointIconSize: StateFlow<Int> = _pointIconSize.asStateFlow()

    private val _pointColor: MutableStateFlow<String> = MutableStateFlow(repository.prefs.pointColor)
    val pointColor: StateFlow<String> = _pointColor.asStateFlow()

    private val _showPointLabels: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showPointLabels)
    val showPointLabels: StateFlow<Boolean> = _showPointLabels.asStateFlow()

    private val _pointLabelSize: MutableStateFlow<Int> = MutableStateFlow(repository.prefs.pointLabelSize)
    val pointLabelSize: StateFlow<Int> = _pointLabelSize.asStateFlow()

    private val _tracklogColor: MutableStateFlow<String> = MutableStateFlow(repository.prefs.tracklogColor)
    val tracklogColor: StateFlow<String> = _tracklogColor.asStateFlow()

    private val _tracklogWidth: MutableStateFlow<Float> = MutableStateFlow(repository.prefs.tracklogWidth)
    val tracklogWidth: StateFlow<Float> = _tracklogWidth.asStateFlow()

    private val _tracklogStyle: MutableStateFlow<String> = MutableStateFlow(repository.prefs.tracklogStyle)
    val tracklogStyle: StateFlow<String> = _tracklogStyle.asStateFlow()

    private val _showTracklogLabels: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showTracklogLabels)
    val showTracklogLabels: StateFlow<Boolean> = _showTracklogLabels.asStateFlow()

    private val _showTracklogValue: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showTracklogValue)
    val showTracklogValue: StateFlow<Boolean> = _showTracklogValue.asStateFlow()

    private val _tracklogFontSize: MutableStateFlow<Int> = MutableStateFlow(repository.prefs.tracklogFontSize)
    val tracklogFontSize: StateFlow<Int> = _tracklogFontSize.asStateFlow()

    private val _lineColor: MutableStateFlow<String> = MutableStateFlow(repository.prefs.lineColor)
    val lineColor: StateFlow<String> = _lineColor.asStateFlow()

    private val _lineWidth: MutableStateFlow<Float> = MutableStateFlow(repository.prefs.lineWidth)
    val lineWidth: StateFlow<Float> = _lineWidth.asStateFlow()

    private val _lineStyle: MutableStateFlow<String> = MutableStateFlow(repository.prefs.lineStyle)
    val lineStyle: StateFlow<String> = _lineStyle.asStateFlow()

    private val _showLineLabels: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showLineLabels)
    val showLineLabels: StateFlow<Boolean> = _showLineLabels.asStateFlow()

    private val _showLineValue: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showLineValue)
    val showLineValue: StateFlow<Boolean> = _showLineValue.asStateFlow()

    private val _lineFontSize: MutableStateFlow<Int> = MutableStateFlow(repository.prefs.lineFontSize)
    val lineFontSize: StateFlow<Int> = _lineFontSize.asStateFlow()

    private val _polygonBoundaryColor: MutableStateFlow<String> = MutableStateFlow(repository.prefs.polygonBoundaryColor)
    val polygonBoundaryColor: StateFlow<String> = _polygonBoundaryColor.asStateFlow()

    private val _polygonFillColor: MutableStateFlow<String> = MutableStateFlow(repository.prefs.polygonFillColor)
    val polygonFillColor: StateFlow<String> = _polygonFillColor.asStateFlow()

    private val _polygonWidth: MutableStateFlow<Float> = MutableStateFlow(repository.prefs.polygonWidth)
    val polygonWidth: StateFlow<Float> = _polygonWidth.asStateFlow()

    private val _polygonStyle: MutableStateFlow<String> = MutableStateFlow(repository.prefs.polygonStyle)
    val polygonStyle: StateFlow<String> = _polygonStyle.asStateFlow()

    private val _showPolygonLabels: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showPolygonLabels)
    val showPolygonLabels: StateFlow<Boolean> = _showPolygonLabels.asStateFlow()

    private val _showPolygonValue: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showPolygonValue)
    val showPolygonValue: StateFlow<Boolean> = _showPolygonValue.asStateFlow()

    private val _polygonFontSize: MutableStateFlow<Int> = MutableStateFlow(repository.prefs.polygonFontSize)
    val polygonFontSize: StateFlow<Int> = _polygonFontSize.asStateFlow()

    private val _incidentColor: MutableStateFlow<String> = MutableStateFlow(repository.prefs.incidentColor)
    val incidentColor: StateFlow<String> = _incidentColor.asStateFlow()

    private val _incidentIconType: MutableStateFlow<String> = MutableStateFlow(repository.prefs.incidentIconType)
    val incidentIconType: StateFlow<String> = _incidentIconType.asStateFlow()

    private val _incidentIconSize: MutableStateFlow<Int> = MutableStateFlow(repository.prefs.incidentIconSize)
    val incidentIconSize: StateFlow<Int> = _incidentIconSize.asStateFlow()

    private val _showIncidentLabels: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showIncidentLabels)
    val showIncidentLabels: StateFlow<Boolean> = _showIncidentLabels.asStateFlow()

    private val _incidentFontSize: MutableStateFlow<Int> = MutableStateFlow(repository.prefs.incidentFontSize)
    val incidentFontSize: StateFlow<Int> = _incidentFontSize.asStateFlow()

    private val _floraFaunaColor: MutableStateFlow<String> = MutableStateFlow(repository.prefs.floraFaunaColor)
    val floraFaunaColor: StateFlow<String> = _floraFaunaColor.asStateFlow()

    private val _floraFaunaIconType: MutableStateFlow<String> = MutableStateFlow(repository.prefs.floraFaunaIconType)
    val floraFaunaIconType: StateFlow<String> = _floraFaunaIconType.asStateFlow()

    private val _floraFaunaIconSize: MutableStateFlow<Int> = MutableStateFlow(repository.prefs.floraFaunaIconSize)
    val floraFaunaIconSize: StateFlow<Int> = _floraFaunaIconSize.asStateFlow()

    private val _showFloraFaunaLabels: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showFloraFaunaLabels)
    val showFloraFaunaLabels: StateFlow<Boolean> = _showFloraFaunaLabels.asStateFlow()

    private val _floraFaunaFontSize: MutableStateFlow<Int> = MutableStateFlow(repository.prefs.floraFaunaFontSize)
    val floraFaunaFontSize: StateFlow<Int> = _floraFaunaFontSize.asStateFlow()

    private val _naturalImpactColor: MutableStateFlow<String> = MutableStateFlow(repository.prefs.naturalImpactColor)
    val naturalImpactColor: StateFlow<String> = _naturalImpactColor.asStateFlow()

    private val _naturalImpactIconType: MutableStateFlow<String> = MutableStateFlow(repository.prefs.naturalImpactIconType)
    val naturalImpactIconType: StateFlow<String> = _naturalImpactIconType.asStateFlow()

    private val _naturalImpactIconSize: MutableStateFlow<Int> = MutableStateFlow(repository.prefs.naturalImpactIconSize)
    val naturalImpactIconSize: StateFlow<Int> = _naturalImpactIconSize.asStateFlow()

    private val _showNaturalImpactLabels: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showNaturalImpactLabels)
    val showNaturalImpactLabels: StateFlow<Boolean> = _showNaturalImpactLabels.asStateFlow()

    private val _naturalImpactFontSize: MutableStateFlow<Int> = MutableStateFlow(repository.prefs.naturalImpactFontSize)
    val naturalImpactFontSize: StateFlow<Int> = _naturalImpactFontSize.asStateFlow()

    private val _landmarkColor: MutableStateFlow<String> = MutableStateFlow(repository.prefs.landmarkColor)
    val landmarkColor: StateFlow<String> = _landmarkColor.asStateFlow()

    private val _landmarkIconType: MutableStateFlow<String> = MutableStateFlow(repository.prefs.landmarkIconType)
    val landmarkIconType: StateFlow<String> = _landmarkIconType.asStateFlow()

    private val _landmarkIconSize: MutableStateFlow<Int> = MutableStateFlow(repository.prefs.landmarkIconSize)
    val landmarkIconSize: StateFlow<Int> = _landmarkIconSize.asStateFlow()

    private val _showLandmarkLabels: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showLandmarkLabels)
    val showLandmarkLabels: StateFlow<Boolean> = _showLandmarkLabels.asStateFlow()

    private val _showLandmarkCode: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.showLandmarkCode)
    val showLandmarkCode: StateFlow<Boolean> = _showLandmarkCode.asStateFlow()

    private val _landmarkLabelSize: MutableStateFlow<Int> = MutableStateFlow(repository.prefs.landmarkLabelSize)
    val landmarkLabelSize: StateFlow<Int> = _landmarkLabelSize.asStateFlow()

    private val _distanceUnit: MutableStateFlow<String> = MutableStateFlow(repository.prefs.distanceUnit)
    val distanceUnit: StateFlow<String> = _distanceUnit.asStateFlow()

    private val _areaUnit: MutableStateFlow<String> = MutableStateFlow(repository.prefs.areaUnit)
    val areaUnit: StateFlow<String> = _areaUnit.asStateFlow()

    private val _gpsFilterDistance: MutableStateFlow<Float> = MutableStateFlow(repository.prefs.gpsFilterDistance)
    val gpsFilterDistance: StateFlow<Float> = _gpsFilterDistance.asStateFlow()

    private val _trackingIntervalSeconds: MutableStateFlow<Int> = MutableStateFlow(repository.prefs.trackingIntervalSeconds)
    val trackingIntervalSeconds: StateFlow<Int> = _trackingIntervalSeconds.asStateFlow()

    private val _antennaHeight: MutableStateFlow<Float> = MutableStateFlow(repository.prefs.antennaHeight)
    val antennaHeight: StateFlow<Float> = _antennaHeight.asStateFlow()

    private val _useAGps: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.useAGps)
    val useAGps: StateFlow<Boolean> = _useAGps.asStateFlow()

    private val _shakeToMoveMap: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.shakeToMoveMap)
    val shakeToMoveMap: StateFlow<Boolean> = _shakeToMoveMap.asStateFlow()

    private val _keepScreenOn: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.keepScreenOn)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _fixMbTilesDisplay: MutableStateFlow<Boolean> = MutableStateFlow(repository.prefs.fixMbTilesDisplay)
    val fixMbTilesDisplay: StateFlow<Boolean> = _fixMbTilesDisplay.asStateFlow()

    private val _defaultIncidentLeader: MutableStateFlow<String> = MutableStateFlow(repository.prefs.defaultIncidentLeader)
    val defaultIncidentLeader: StateFlow<String> = _defaultIncidentLeader.asStateFlow()

    private val _defaultIncidentField: MutableStateFlow<String> = MutableStateFlow(repository.prefs.defaultIncidentField)
    val defaultIncidentField: StateFlow<String> = _defaultIncidentField.asStateFlow()

    private val _syncStatus: MutableStateFlow<SyncStatus> = MutableStateFlow(SyncStatus.SYNCED)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    var trackingService: GpxTrackingService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as GpxTrackingService.LocalBinder
            trackingService = binder.getService()
            isBound = true
            viewModelScope.launch { trackingService?.currentLocation?.collect { _currentLocation.value = it } }
            viewModelScope.launch { trackingService?.isTracking?.collect { _isTrackingGpx.value = it } }
            viewModelScope.launch { trackingService?.trackedPoints?.collect { _trackedPoints.value = it } }
            viewModelScope.launch { trackingService?.satellitesCount?.collect { _satellitesCount.value = it } }
            viewModelScope.launch { trackingService?.satellitesVisible?.collect { _satellitesVisible.value = it } }
            viewModelScope.launch { trackingService?.satelliteDetails?.collect { _satelliteDetails.value = it } }
        }
        override fun onServiceDisconnected(name: ComponentName?) { trackingService = null; isBound = false }
    }

    fun startLocationUpdates() { trackingService?.startLocationListening() }

    init {
        com.baoverung.app.gis.CoordinateSystemConverter.initialize(application)
        sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        val serviceIntent = Intent(application, GpxTrackingService::class.java)
        application.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        viewModelScope.launch {
            gisLayers.collect { layers ->
                val currentReaderMap = _mbtilesReaders.value.toMutableMap()
                for (layer in layers) {
                    if (layer.isVisible) {
                        if ((layer.fileType == "MBTILES" || layer.fileType == "SQLITE") && !currentReaderMap.containsKey(layer.id)) {
                            currentReaderMap[layer.id] = com.baoverung.app.gis.MBTilesReader(File(layer.filePath))
                        }
                    }
                }
                _mbtilesReaders.value = currentReaderMap
            }
        }

        if (_currentLocation.value == null) {
            _currentLocation.value = GpsPoint(latitude = repository.prefs.lastMapLat, longitude = repository.prefs.lastMapLon, altitude = 1480.0, speed = 0f, accuracy = 3.5f, satellitesCount = 14)
        }
        viewModelScope.launch { _mapFocusRequest.emit(Triple(repository.prefs.lastMapLat, repository.prefs.lastMapLon, repository.prefs.lastMapZoom)) }

        viewModelScope.launch {
            if (!repository.prefs.isDefaultLayerImported) {
                repository.importAssetLayer("default_map_Kk2025.mbtiles", "Bản đồ Hiện trạng rừng 2025")
                repository.prefs.isDefaultLayerImported = true
            }
            if (!repository.prefs.isDefaultSddLayerImported) {
                repository.importAssetLayer("default_map_SDD.mbtiles", "Bản đồ Quy hoạch SDD")
                repository.prefs.isDefaultSddLayerImported = true
            }
        }

        // --- Optimized Maintenance Loop (Grouped for performance) ---
        viewModelScope.launch {
            var tick = 0L
            while (true) {
                // 1. Measurement updates (every 2s)
                if (_measurementMode.value == MeasurementMode.GPX_DISTANCE || _measurementMode.value == MeasurementMode.GPX_AREA) {
                    _currentLocation.value?.let { loc ->
                        val lastPoint = _measurementPoints.value.lastOrNull()
                        if (lastPoint == null || com.baoverung.app.gis.GisAreaCalculator.calculateDistance(lastPoint.latitude, lastPoint.longitude, loc.latitude, loc.longitude) > 2.0) {
                            _measurementPoints.value = _measurementPoints.value + loc
                        }
                    }
                }

                // 2. Sync Status (every 10s)
                if (tick % 5 == 0L) updateSyncStatus()

                // 3. Auto Report (every 30s)
                if (tick % 15 == 0L) checkAndRunDailyAutoReportExport(getApplication())

                // 4. Cloud Sync (every 60s)
                if (tick % 30 == 0L) runCloudSync()

                // 5. Key Validity (every 300s)
                if (tick % 150 == 0L) checkKeyValidityInBackground()

                // 6. Auto-GPX Idle Restart Check (every 60s)
                if (tick % 30 == 0L) checkAutoGpxIdleRestart()

                tick++
                kotlinx.coroutines.delay(2000L)
            }
        }


        viewModelScope.launch {
            userSession.collect { session ->
                if (session.isLoggedIn && !session.isOfflineMode && session.registrationKey.isNotEmpty()) {
                    startKeyMonitoring(session.registrationKey)
                } else {
                    stopKeyMonitoring()
                }
            }
        }
    }

    private suspend fun updateSyncStatus() {
        if (_syncStatus.value == SyncStatus.SYNCING) return
        val hasUnsynced = repository.getUnsyncedWaypoints().isNotEmpty() ||
                repository.getUnsyncedTracks().isNotEmpty() ||
                repository.getUnsyncedPatrols().isNotEmpty() ||
                repository.getUnsyncedJournals().isNotEmpty() ||
                repository.getUnsyncedFloraFauna().isNotEmpty() ||
                repository.getUnsyncedNaturalImpacts().isNotEmpty()
        
        _syncStatus.value = if (hasUnsynced) SyncStatus.PENDING else SyncStatus.SYNCED
    }


    private suspend fun runCloudSync() = withContext(Dispatchers.IO) {
        val session = _userSession.value
        if (!session.isLoggedIn || session.isOfflineMode) return@withContext
        
        // Requirement IV: Always sync personnel/officer info (BVR Force Data)
        cloudRepository.updatePersonnelInfo(session)

        // Only sync heavy field data if allowed by the activation key configuration on web admin
        if (!session.canSync) {
            Log.d("MainViewModel", "Field data sync is disabled from Web Admin for this key.")
            updateSyncStatus()
            return@withContext
        }

        val cm = _vn2000CentralMeridian.value
        val prov = _vn2000ProvinceName.value
        val zone = _vn2000ZoneDegrees.value
        _syncStatus.value = SyncStatus.SYNCING
        try {
            repository.getUnsyncedWaypoints().forEach { if (cloudRepository.syncWaypoint(session, it, cm, prov, zone)) repository.markWaypointSynced(it.id) }
            repository.getUnsyncedTracks().forEach { if (cloudRepository.syncTrack(session, it, cm, prov, zone)) repository.markTrackSynced(it.id) }
            repository.getUnsyncedPolygons().forEach { if (cloudRepository.syncPolygon(session, it, cm, prov, zone)) repository.markPolygonSynced(it.id) }
            repository.getUnsyncedPatrols().forEach { if (cloudRepository.syncPatrol(session, it, cm, prov, zone)) repository.markPatrolSynced(it.id) }
            repository.getUnsyncedJournals().forEach { if (cloudRepository.syncDailyJournal(session, it, cm, prov, zone)) repository.markJournalSynced(it.id) }
            repository.getUnsyncedFloraFauna().forEach { if (cloudRepository.syncFloraFauna(session, it, cm, prov, zone)) repository.markFloraFaunaSynced(it.id) }
            repository.getUnsyncedNaturalImpacts().forEach { if (cloudRepository.syncNaturalImpact(session, it, cm, prov, zone)) repository.markNaturalImpactSynced(it.id) }
        } catch (e: Exception) { 
            e.printStackTrace()
        }
        updateSyncStatus()
    }

    private suspend fun checkKeyValidityInBackground() {
        val session = _userSession.value
        if (!session.isLoggedIn || session.isOfflineMode) return
        
        val lastKey = getApplication<Application>().getSharedPreferences("vtool_prefs", Context.MODE_PRIVATE).getString("last_key", "") ?: ""
        if (lastKey.isNotEmpty()) {
            val androidId = android.provider.Settings.Secure.getString(getApplication<Application>().contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown"
            val result = cloudRepository.verifyActivationKey(lastKey, androidId)
            if (!result.isValid) {
                withContext(Dispatchers.Main) { logout(); android.widget.Toast.makeText(getApplication(), "Key bị thay đổi hoặc không hợp lệ. Vui lòng đăng nhập lại!", android.widget.Toast.LENGTH_LONG).show() }
            }
        }
    }

    fun login(email: String, displayName: String, phoneNumber: String, unit: String, department: String, registrationKey: String, expiry: String, perms: String, autoGpx: Boolean, canSync: Boolean) {
        val session = UserSession(
            userId = "usr_${System.currentTimeMillis()}", 
            displayName = displayName, 
            email = email, 
            phoneNumber = phoneNumber, 
            unit = unit, 
            department = department, 
            registrationKey = registrationKey,
            expiryDate = expiry, 
            permissions = perms, 
            autoGpx = autoGpx,
            canSync = canSync,
            loginTimestamp = System.currentTimeMillis(), 
            photoUrl = "", 
            isLoggedIn = true, 
            isOfflineMode = false
        )
        repository.saveUserSession(session); _userSession.value = session
    }

    fun continueAsOfflineGuest() {
        val session = UserSession(userId = "usr_guest", displayName = "Khách", email = "khach@daithanhforest.vn", photoUrl = "", isLoggedIn = true, isOfflineMode = true)
        _userSession.value = session
    }

    fun logout() { 
        stopKeyMonitoring()
        repository.prefs.clearSession()
        _userSession.value = UserSession(isLoggedIn = false) 
    }

    fun startGpxTracking(context: Context? = null) {
        if (isReadOnlyUser()) { notifyReadOnlyRestriction(context); return }
        val intent = Intent(getApplication(), GpxTrackingService::class.java).apply { 
            action = GpxTrackingService.ACTION_START_TRACKING 
        }
        androidx.core.content.ContextCompat.startForegroundService(getApplication(), intent)
        trackingService?.startTracking()
    }

    fun stopAndSaveGpxTracking(title: String, context: Context? = null) {
        if (isReadOnlyUser()) { notifyReadOnlyRestriction(context); return }
        val points = trackingService?.trackedPoints?.value ?: emptyList()
        if (com.baoverung.app.gis.GisAreaCalculator.calculatePathLength(points) < 5.0) {
            context?.let { android.widget.Toast.makeText(it, "Đường quá ngắn!", android.widget.Toast.LENGTH_SHORT).show() }
            trackingService?.stopTracking(); return
        }
        viewModelScope.launch {
            val dist = com.baoverung.app.gis.GisAreaCalculator.calculatePathLength(points)
            val area = if (points.size >= 3) com.baoverung.app.gis.GisAreaCalculator.calculatePolygonArea(points) else 0.0
            val stats = if (area > 500) "${com.baoverung.app.gis.GisAreaCalculator.formatDistance(dist, repository.prefs.distanceUnit)} / ${com.baoverung.app.gis.GisAreaCalculator.formatArea(area, repository.prefs.areaUnit)}" else com.baoverung.app.gis.GisAreaCalculator.formatDistance(dist, repository.prefs.distanceUnit)
            
            val finalTitle = if (title.isBlank() || title.startsWith("Tracklog tuần tra") || title == "Tracklog tự động") {
                val count = repository.getTodayTrackCount() + 1
                "Tracklog tuần tra ${String.format("%02d", count)}"
            } else {
                title
            }
            
            repository.saveTrackLog(title = "$finalTitle ($stats)", startTimeUtc = if (points.isNotEmpty()) points.first().timestampUtc else System.currentTimeMillis(), endTimeUtc = System.currentTimeMillis(), points = points)
            runCloudSync()
        }
        trackingService?.stopTracking()
        getApplication<Application>().startService(Intent(getApplication(), GpxTrackingService::class.java).apply { action = GpxTrackingService.ACTION_STOP_TRACKING })
    }

    fun saveWaypoint(title: String, desc: String, lat: Double, lon: Double, shouldReport: Boolean, photoPath: String?, context: Context? = null, watermarkSettings: com.baoverung.app.util.WatermarkHelper.WatermarkSettings = com.baoverung.app.util.WatermarkHelper.WatermarkSettings()) {
        if (isReadOnlyUser()) { notifyReadOnlyRestriction(context); return }
        viewModelScope.launch {
            val finalPhotoPath = if (photoPath != null && context != null) {
                val photoIdx = repository.getTodayPhotoCount() + 1
                val photoName = if (title.isBlank() || title.startsWith("Ảnh")) "Ảnh ${String.format("%02d", photoIdx)}" else title
                processPhotoWithWatermark(context, photoPath, currentLocation.value ?: GpsPoint(lat, lon), photoName, watermarkSettings)
            } else photoPath
            val count = repository.getTodayWaypointCount() + 1
            val finalTitle = if (title.isBlank()) { if (photoPath != null) "Ảnh ${String.format("%02d", repository.getTodayPhotoCount() + 1)}" else "Điểm ${String.format("%02d", count)}" } else title
            repository.saveWaypoint(title = finalTitle, description = desc, latitude = lat, longitude = lon, altitude = currentLocation.value?.altitude ?: 0.0, accuracy = currentLocation.value?.accuracy ?: 0f, satellitesCount = currentLocation.value?.satellitesCount ?: 0, photoPath = finalPhotoPath)
            runCloudSync()
            if (shouldReport && context != null) {
                val (vx, vy) = CoordinateSystemConverter.wgs84ToVn2000(lat, lon, _vn2000CentralMeridian.value, _vn2000ZoneDegrees.value)
                val cmStr = formatKtt(_vn2000CentralMeridian.value)
                val details = "Tiêu đề: $finalTitle\n" +
                        "Thời gian: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n" +
                        "Tọa độ WGS84: $lat, $lon\n" +
                        "Tọa độ VN2000 (Múi ${_vn2000ZoneDegrees.value}°, KTT $cmStr): X=${String.format("%.2f", vx)}, Y=${String.format("%.2f", vy)}\n" +
                        "Mô tả: $desc"
                
                val validPhotoPath = if (!finalPhotoPath.isNullOrEmpty()) {
                    if (finalPhotoPath.startsWith("content://")) {
                        finalPhotoPath
                    } else {
                        val file = File(finalPhotoPath)
                        if (file.exists()) file.absolutePath else null
                    }
                } else null
                
                EmailSenderHelper.sendEmail(context, repository.prefs.defaultRecipientEmail, "Báo cáo Điểm: $finalTitle", generateReportBody("BÁO CÁO ĐIỂM KHẢO SÁT", details), validPhotoPath)
            }
        }
    }

    fun savePatrolLog(context: Context, incidentType: String, leaderName: String, violationTime: String, violationLocation: String, violatorName: String, violatorIdCard: String, violatorAddress: String, violatorPhone: String, confiscatedTools: String, relatedPersons: String, onSiteAction: String, onSiteRecordings: String, notes: String, photoPaths: List<String>, violationField: String, shouldReport: Boolean, logId: Long? = null, watermarkSettings: com.baoverung.app.util.WatermarkHelper.WatermarkSettings = com.baoverung.app.util.WatermarkHelper.WatermarkSettings()) {
        if (isReadOnlyUser()) { notifyReadOnlyRestriction(context); return }
        viewModelScope.launch {
            val basePhotoIdx = repository.getTodayPhotoCount()
            val watermarkedPaths = photoPaths.mapIndexed { index, path ->
                val photoName = "Ảnh ${String.format("%02d", basePhotoIdx + index + 1)}"
                processPhotoWithWatermark(context, path, currentLocation.value ?: GpsPoint(0.0,0.0), photoName, watermarkSettings)
            }
            val watermarkedPathString = if (watermarkedPaths.isNotEmpty()) watermarkedPaths.joinToString("|") else null
            
            val pCount = patrolLogs.value.count { 
                java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(it.discoveryTimeUtc)) == 
                java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date()) 
            }
            val standardizedIncidentType = "${String.format("%02d", pCount + 1)} $incidentType"

            val finalLogId = repository.savePatrolLog(standardizedIncidentType, currentLocation.value?.latitude ?: 0.0, currentLocation.value?.longitude ?: 0.0, currentLocation.value?.altitude ?: 0.0, currentLocation.value?.accuracy ?: 0f, currentLocation.value?.satellitesCount ?: 0, leaderName, violationTime, violationLocation, violatorName, violatorIdCard, violatorAddress, violatorPhone, confiscatedTools, relatedPersons, onSiteAction, onSiteRecordings, notes, watermarkedPathString, violationField, logId)
            runCloudSync()
            if (shouldReport) {
                val lat = currentLocation.value?.latitude ?: 0.0
                val lon = currentLocation.value?.longitude ?: 0.0
                val (vx, vy) = CoordinateSystemConverter.wgs84ToVn2000(lat, lon, _vn2000CentralMeridian.value, _vn2000ZoneDegrees.value)
                val cmStr = formatKtt(_vn2000CentralMeridian.value)

                val details = "Sự vụ: $standardizedIncidentType\n" +
                        "Thời gian vi phạm: $violationTime\n" +
                        "Địa điểm: $violationLocation\n" +
                        "Tọa độ WGS84: $lat, $lon\n" +
                        "Tọa độ VN2000 (Múi ${_vn2000ZoneDegrees.value}°, KTT $cmStr): X=${String.format("%.2f", vx)}, Y=${String.format("%.2f", vy)}\n" +
                        "Đối tượng: $violatorName ($violatorPhone)\n" +
                        "Biện pháp: $onSiteAction"
                
                val reportFile = File(repository.getExportDirectory(), "BaoCao_SuVu_$finalLogId.docx")
                
                val validPhotoPaths = if (!watermarkedPathString.isNullOrEmpty()) {
                    watermarkedPathString.split("|").mapNotNull { path ->
                        if (path.startsWith("content://")) {
                            path
                        } else {
                            val file = File(path)
                            if (file.exists()) file.absolutePath else null
                        }
                    }.joinToString("|")
                } else ""
                
                val attachmentPaths = if (validPhotoPaths.isNotEmpty()) "${reportFile.absolutePath}|$validPhotoPaths" else reportFile.absolutePath
                
                EmailSenderHelper.sendEmail(context, repository.prefs.defaultRecipientEmail, "Nhật ký Tuần tra: $standardizedIncidentType ($violationField)", generateReportBody("BÁO CÁO NHẬT KÝ TUẦN TRA RỪNG", details), attachmentPaths)
            }
        }
    }

    fun saveFloraFaunaLog(context: Context, appearance: String, features: String, count: String, habitat: String, temp: String, humidity: String, canopy: String, surroundPlants: String, specimens: String, photoPaths: List<String>, logId: Long? = null, shouldReport: Boolean = false) {
        if (isReadOnlyUser()) { notifyReadOnlyRestriction(context); return }
        viewModelScope.launch {
            val watermarkedPaths = photoPaths.mapIndexed { index, path ->
                val photoName = "FloraFauna_${System.currentTimeMillis()}_$index"
                processPhotoWithWatermark(context, path, currentLocation.value ?: GpsPoint(0.0,0.0), photoName)
            }
            val watermarkedPathString = if (watermarkedPaths.isNotEmpty()) watermarkedPaths.joinToString("|") else null
            
            // Tự động đánh số thứ tự trong ngày
            val startOfDay = repository.getStartOfDayTimestamp()
            val todayLogs = repository.db.floraFaunaLogDao().getAllLogsList().filter { it.timestampUtc >= startOfDay }
            val stt = String.format("%02d", todayLogs.size + 1)
            val standardizedAppearance = "$stt $appearance"

            val finalLogId = repository.saveFloraFaunaLog(
                currentLocation.value?.latitude ?: 0.0, currentLocation.value?.longitude ?: 0.0,
                currentLocation.value?.altitude ?: 0.0, currentLocation.value?.accuracy ?: 0f,
                currentLocation.value?.satellitesCount ?: 0, _userSession.value.displayName,
                standardizedAppearance, features, count, habitat, temp, humidity, canopy, surroundPlants, specimens, watermarkedPathString, logId
            )
            runCloudSync()
            if (shouldReport) {
                sendFloraFaunaReport(context, repository.db.floraFaunaLogDao().getById(finalLogId) ?: return@launch)
            }
            android.widget.Toast.makeText(context, "Đã lưu nhật ký động thực vật!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun saveNaturalImpactLog(context: Context, cause: String, otherCause: String, area: String, before: String, after: String, damage: String, time: String, photoPaths: List<String>, logId: Long? = null, shouldReport: Boolean = false) {
        if (isReadOnlyUser()) { notifyReadOnlyRestriction(context); return }
        viewModelScope.launch {
            val watermarkedPaths = photoPaths.mapIndexed { index, path ->
                val photoName = "NaturalImpact_${System.currentTimeMillis()}_$index"
                processPhotoWithWatermark(context, path, currentLocation.value ?: GpsPoint(0.0,0.0), photoName)
            }
            val watermarkedPathString = if (watermarkedPaths.isNotEmpty()) watermarkedPaths.joinToString("|") else null
            
            // Tự động đánh số thứ tự trong ngày
            val startOfDay = repository.getStartOfDayTimestamp()
            val todayLogs = repository.db.naturalImpactLogDao().getAllLogsList().filter { it.timestampUtc >= startOfDay }
            val stt = String.format("%02d", todayLogs.size + 1)
            val finalCause = if (cause == "Khác") otherCause else cause
            val standardizedCause = "$stt $finalCause"

            val finalLogId = repository.saveNaturalImpactLog(
                currentLocation.value?.latitude ?: 0.0, currentLocation.value?.longitude ?: 0.0,
                currentLocation.value?.altitude ?: 0.0, currentLocation.value?.accuracy ?: 0f,
                currentLocation.value?.satellitesCount ?: 0, _userSession.value.displayName,
                standardizedCause, "", area, before, after, damage, time, watermarkedPathString, logId
            )
            runCloudSync()
            if (shouldReport) {
                sendNaturalImpactReport(context, repository.db.naturalImpactLogDao().getById(finalLogId) ?: return@launch)
            }
            android.widget.Toast.makeText(context, "Đã lưu nhật ký tác động tự nhiên!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun getAddressFromLocation(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = android.location.Geocoder(getApplication(), java.util.Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val subLocality = addr.subLocality ?: ""
                val locality = addr.locality ?: ""
                val subAdminArea = addr.subAdminArea ?: ""
                val adminArea = addr.adminArea ?: ""
                
                listOf(subLocality, locality, subAdminArea, adminArea)
                    .filter { it.isNotEmpty() }
                    .joinToString(", ")
            } else "Vị trí không xác định"
        } catch (e: Exception) {
            "Ngoại tuyến - Không có địa chỉ"
        }
    }

    private suspend fun processPhotoWithWatermark(context: Context, photoPath: String, gps: GpsPoint, fileNameHint: String? = null, watermarkSettings: com.baoverung.app.util.WatermarkHelper.WatermarkSettings = com.baoverung.app.util.WatermarkHelper.WatermarkSettings()): String = withContext(Dispatchers.IO) {
        try {
            val uri = android.net.Uri.parse(photoPath)
            val inputStream = context.contentResolver.openInputStream(uri)
            val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream); inputStream?.close()
            if (originalBitmap == null) return@withContext photoPath
            val (vx, vy) = com.baoverung.app.gis.CoordinateSystemConverter.wgs84ToVn2000(gps.latitude, gps.longitude, _vn2000CentralMeridian.value, _vn2000ZoneDegrees.value)
            
            val logo = android.graphics.BitmapFactory.decodeResource(context.resources, com.baoverung.app.R.drawable.app_icon_forestry)
            val address = getAddressFromLocation(gps.latitude, gps.longitude)
            
            val watermarkedBitmap = com.baoverung.app.util.WatermarkHelper.drawWatermark(
                source = originalBitmap, 
                wgs84 = "${String.format("%.6f°", gps.latitude)}, ${String.format("%.6f°", gps.longitude)}", 
                vn2000 = "X ${String.format("%.0f", vx)} m · Y ${String.format("%.0f", vy)} m", 
                altitude = gps.altitude, 
                time = java.text.SimpleDateFormat("HH:mm · dd/MM/yyyy · EEEE", java.util.Locale.getDefault()).format(java.util.Date()), 
                direction = _compassAzimuth.value, 
                userName = _userSession.value.displayName.ifEmpty { "Cán bộ" }, 
                unitName = _userSession.value.unit, 
                centralMeridian = _vn2000CentralMeridian.value, 
                accuracy = gps.accuracy,
                logo = logo,
                address = address,
                settings = watermarkSettings
            )
            
            val fileName = if (fileNameHint != null) "$fileNameHint.jpg" else "BVR_${System.currentTimeMillis()}.jpg"

            val internalPath = com.baoverung.app.util.StorageUtils.saveImageToInternalStorage(context, watermarkedBitmap, fileName)
            com.baoverung.app.util.StorageUtils.saveImageToPublicStorage(context, watermarkedBitmap, fileName)
            
            try {
                if (photoPath.contains("cache")) {
                    val sourceFile = File(android.net.Uri.parse(photoPath).path ?: "")
                    if (sourceFile.exists()) sourceFile.delete()
                }
            } catch (e: Exception) {}
            
            return@withContext internalPath ?: photoPath
        } catch (e: Exception) { return@withContext photoPath }
    }

    fun updateMapState(lat: Double, lon: Double, zoom: Float) {
        repository.prefs.lastMapLat = lat; repository.prefs.lastMapLon = lon; repository.prefs.lastMapZoom = zoom
    }

    fun updateMapBounds(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) {
        _mapBounds.value = CoordinateSystemConverter.RectD(minLon, minLat, maxLon, maxLat)
    }

    fun forceSyncNow(context: Context) {
        viewModelScope.launch { if (_userSession.value.isOfflineMode) return@launch; try { runCloudSync(); android.widget.Toast.makeText(context, "Đã đồng bộ!", android.widget.Toast.LENGTH_SHORT).show() } catch (e: Exception) { e.printStackTrace() } }
    }

    fun forceResetSyncStatus(context: Context) {
        viewModelScope.launch { repository.resetWaypointsSyncStatus(); repository.resetTracksSyncStatus(); repository.resetPatrolsSyncStatus(); repository.resetJournalsSyncStatus(); runCloudSync() }
    }

    fun checkAndRunDailyAutoReportExport(context: Context) {
        if (!repository.prefs.isAutoEmailReportEnabled) return
        val session = _userSession.value
        if (!session.isLoggedIn || session.isOfflineMode) return
        
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        if (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) >= 17 && repository.prefs.lastAutoReportExportDate != todayStr) {
            viewModelScope.launch {
                try {
                    val pkg = repository.exportFullDailyReportPackage(); val recipient = repository.prefs.defaultRecipientEmail
                    val details = "Bao cao tong hop du lieu tu dong 17h ngay $todayStr\n" +
                            "- Diem: ${repository.getTodayWaypointCount()}\n" +
                            "- Track: ${repository.getTodayTrackCount()}\n" +
                            "- Vung: ${repository.getTodayPolygonCount()}\n" +
                            "- Su vu: ${patrolLogs.value.filter { java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(it.discoveryTimeUtc)) == todayStr }.size}\n" +
                            "- Dong vat: ${floraFaunaLogs.value.filter { java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(it.timestampUtc)) == todayStr }.size}\n" +
                            "- Tac dong TN: ${naturalImpactLogs.value.filter { java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(it.timestampUtc)) == todayStr }.size}"
                    
                    val zipPath = pkg.zipFile?.absolutePath
                    EmailSenderHelper.showReportNotification(context, recipient, "[BVR DAI THANH] Bao cao tu dong 17h - $todayStr", generateReportBody("BAO CAO DINH KY TU DONG", details), zipPath)
                    repository.prefs.lastAutoReportExportDate = todayStr
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun setMeasurementMode(m: MeasurementMode) { _measurementMode.value = m; if (m == MeasurementMode.NONE) { _measurementPoints.value = emptyList(); _targetNavPoint.value = null } }
    fun addMeasurementPoint(p: GpsPoint) { _measurementPoints.value = _measurementPoints.value + p }
    fun undoMeasurementPoint() {
        val current = _measurementPoints.value
        if (current.isNotEmpty()) {
            _measurementPoints.value = current.dropLast(1)
        }
    }
    fun deleteMeasurementPoint(index: Int) {
        val current = _measurementPoints.value
        if (index in current.indices) {
            _measurementPoints.value = current.filterIndexed { i, _ -> i != index }
        }
    }
    fun updateMeasurementPoint(index: Int, p: GpsPoint) {
        val current = _measurementPoints.value.toMutableList()
        if (index in current.indices) {
            current[index] = p
            _measurementPoints.value = current
        }
    }
    fun clearMeasurementPoints() { _measurementPoints.value = emptyList() }
    fun setTargetNavPoint(p: GpsPoint?) { _targetNavPoint.value = p; if (p != null) { _measurementMode.value = MeasurementMode.NAVIGATION; viewModelScope.launch { _mapFocusRequest.emit(Triple(p.latitude, p.longitude, 18f)) } } }
    fun setPreviewTrackLog(t: TrackLogEntity?) { if (t == null) _previewTrackPoints.value = emptyList() else { _previewTrackPoints.value = repository.parseTrackPoints(t.pointsJson); if (_previewTrackPoints.value.isNotEmpty()) viewModelScope.launch { _mapFocusRequest.emit(Triple(_previewTrackPoints.value[0].latitude, _previewTrackPoints.value[0].longitude, 15f)) } } }
    fun setPreviewTrackLogUi(trk: TrackLogUiModel?) {
        if (trk == null) {
            _previewTrackPoints.value = emptyList()
        } else {
            _previewTrackPoints.value = trk.fullPoints
            if (_previewTrackPoints.value.isNotEmpty()) {
                viewModelScope.launch {
                    _mapFocusRequest.emit(Triple(_previewTrackPoints.value[0].latitude, _previewTrackPoints.value[0].longitude, 15f))
                }
            }
        }
    }
    fun clearPreviewTrackLog() { _previewTrackPoints.value = emptyList() }
    fun isReadOnlyUser(): Boolean = isGuestUser() || _userSession.value.permissions == "VIEW_ONLY"
    private fun notifyReadOnlyRestriction(c: Context?) { android.widget.Toast.makeText(c ?: getApplication(), "Tài khoản không có quyền lưu dữ liệu (Chế độ Xem/Dùng thử).", android.widget.Toast.LENGTH_LONG).show() }
    fun isGuestUser(): Boolean = _userSession.value.displayName == "Khách" || _userSession.value.isOfflineMode

    private val _hiddenWaypointIds = MutableStateFlow<Set<Long>>(emptySet()); val hiddenWaypointIds = _hiddenWaypointIds.asStateFlow()
    private val _hiddenTrackLogIds = MutableStateFlow<Set<Long>>(emptySet()); val hiddenTrackLogIds = _hiddenTrackLogIds.asStateFlow()
    private val _hiddenPatrolLogIds = MutableStateFlow<Set<Long>>(emptySet()); val hiddenPatrolLogIds = _hiddenPatrolLogIds.asStateFlow()
    private val _hiddenFloraFaunaIds = MutableStateFlow<Set<Long>>(emptySet()); val hiddenFloraFaunaIds = _hiddenFloraFaunaIds.asStateFlow()
    private val _hiddenNaturalImpactIds = MutableStateFlow<Set<Long>>(emptySet()); val hiddenNaturalImpactIds = _hiddenNaturalImpactIds.asStateFlow()
    private val _hiddenPolygonIds = MutableStateFlow<Set<Long>>(emptySet()); val hiddenPolygonIds = _hiddenPolygonIds.asStateFlow()
    
    fun toggleWaypointVisibility(id: Long) { _hiddenWaypointIds.value = if (_hiddenWaypointIds.value.contains(id)) _hiddenWaypointIds.value - id else _hiddenWaypointIds.value + id }
    fun toggleTrackLogVisibility(id: Long) { _hiddenTrackLogIds.value = if (_hiddenTrackLogIds.value.contains(id)) _hiddenTrackLogIds.value - id else _hiddenTrackLogIds.value + id }
    fun togglePatrolLogVisibility(id: Long) { _hiddenPatrolLogIds.value = if (_hiddenPatrolLogIds.value.contains(id)) _hiddenPatrolLogIds.value - id else _hiddenPatrolLogIds.value + id }
    fun toggleFloraFaunaVisibility(id: Long) { _hiddenFloraFaunaIds.value = if (_hiddenFloraFaunaIds.value.contains(id)) _hiddenFloraFaunaIds.value - id else _hiddenFloraFaunaIds.value + id }
    fun toggleNaturalImpactVisibility(id: Long) { _hiddenNaturalImpactIds.value = if (_hiddenNaturalImpactIds.value.contains(id)) _hiddenNaturalImpactIds.value - id else _hiddenNaturalImpactIds.value + id }
    fun togglePolygonVisibility(id: Long) { _hiddenPolygonIds.value = if (_hiddenPolygonIds.value.contains(id)) _hiddenPolygonIds.value - id else _hiddenPolygonIds.value + id }
    
    fun setAllWaypointsVisible(v: Boolean) { _hiddenWaypointIds.value = if (v) emptySet() else waypoints.value.map { it.id }.toSet() }
    fun setAllTrackLogsVisible(v: Boolean) { _hiddenTrackLogIds.value = if (v) emptySet() else trackLogs.value.map { it.id }.toSet() }
    fun setAllPatrolLogsVisible(v: Boolean) { _hiddenPatrolLogIds.value = if (v) emptySet() else patrolLogs.value.map { it.id }.toSet() }
    fun setAllFloraFaunaVisible(v: Boolean) { _hiddenFloraFaunaIds.value = if (v) emptySet() else floraFaunaLogs.value.map { it.id }.toSet() }
    fun setAllNaturalImpactVisible(v: Boolean) { _hiddenNaturalImpactIds.value = if (v) emptySet() else naturalImpactLogs.value.map { it.id }.toSet() }
    fun setAllPolygonsVisible(v: Boolean) { _hiddenPolygonIds.value = if (v) emptySet() else polygons.value.map { it.id }.toSet() }
    fun setAllGisLayersVisible(v: Boolean) { 
        viewModelScope.launch { 
            gisLayers.value.forEach { layer -> 
                if (layer.isVisible != v) repository.toggleGisLayerVisibility(layer) 
            } 
        } 
    }
    fun updateWaypointColor(id: Long, c: String) { viewModelScope.launch { repository.updateWaypointColor(id, c) } }
    fun updateWaypointTitle(id: Long, t: String) { viewModelScope.launch { repository.updateWaypointTitle(id, t) } }
    fun updateTrackLogColor(id: Long, c: String) { viewModelScope.launch { repository.updateTrackLogColor(id, c) } }
    fun updateTrackLogTitle(id: Long, t: String) { viewModelScope.launch { repository.updateTrackLogTitle(id, t) } }
    fun updatePatrolLogColor(id: Long, c: String) { viewModelScope.launch { repository.updatePatrolLogColor(id, c) } }
    fun updatePatrolLogTitle(id: Long, t: String) { viewModelScope.launch { repository.updatePatrolLogTitle(id, t) } }
    fun updateFloraFaunaColor(id: Long, c: String) { viewModelScope.launch { repository.updateFloraFaunaColor(id, c) } }
    fun updateNaturalImpactColor(id: Long, c: String) { viewModelScope.launch { repository.updateNaturalImpactColor(id, c) } }
    fun updateDailyJournalColor(id: Long, c: String) { viewModelScope.launch { repository.updateDailyJournalColor(id, c) } }
    fun deleteWaypoint(id: Long) { viewModelScope.launch { repository.deleteWaypoint(id) } }
    fun deleteTrackLog(id: Long) { viewModelScope.launch { repository.deleteTrackLog(id) } }
    fun deletePatrolLog(id: Long) { viewModelScope.launch { repository.deletePatrolLog(id) } }
    fun deleteFloraFaunaLog(id: Long) { viewModelScope.launch { repository.deleteFloraFaunaLog(id) } }
    fun deleteNaturalImpactLog(id: Long) { viewModelScope.launch { repository.deleteNaturalImpactLog(id) } }
    fun deletePolygon(id: Long) { viewModelScope.launch { repository.deletePolygon(id) } }
    fun clearAllCloudData(context: Context) {
        viewModelScope.launch {
            val success = cloudRepository.clearAllCloudData()
            withContext(Dispatchers.Main) {
                if (success) android.widget.Toast.makeText(context, "Đã xóa toàn bộ dữ liệu thực địa trên Web!", android.widget.Toast.LENGTH_LONG).show()
                else android.widget.Toast.makeText(context, "Lỗi khi xóa dữ liệu Web!", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    fun updatePolygonColor(id: Long, c: String) { viewModelScope.launch { repository.updatePolygonColor(id, c) } }
    fun updatePolygonTitle(id: Long, t: String) { viewModelScope.launch { repository.updatePolygonTitle(id, t) } }
    fun deleteDailyJournal(id: Long) { viewModelScope.launch { repository.deleteDailyJournal(id) } }

    fun saveDailyJournal(dateStr: String, content: String, notes: String, weather: String = "", patrolTeam: String = "", patrolCompartment: String = "", color: String = "#FF1976D2", context: Context? = null) {
        if (isReadOnlyUser()) { notifyReadOnlyRestriction(context); return }
        viewModelScope.launch {
            repository.saveDailyJournal(dateStr, content, notes, weather, patrolTeam, patrolCompartment, color)
            runCloudSync()
            context?.let { android.widget.Toast.makeText(it, "Đã lưu nhật ký hằng ngày!", android.widget.Toast.LENGTH_SHORT).show() }
        }
    }

    suspend fun getDailyJournalById(id: Long): com.baoverung.app.data.local.entity.DailyJournalEntity? {
        return repository.getDailyJournalById(id)
    }

    fun getAutoWeather(): String {
        return com.baoverung.app.util.WeatherHelper.getWeatherAutoInfo()
    }

    fun sendDailyJournalViaGmail(context: Context, journal: com.baoverung.app.data.local.entity.DailyJournalEntity) {
        viewModelScope.launch {
            val user = _userSession.value
            val file = com.baoverung.app.util.WordExportHelper.exportDailyJournalToWord(context, journal, _vn2000CentralMeridian.value, user.displayName, user.unit, user.email, user.phoneNumber)
            if (file != null) {
                val recipient = repository.prefs.defaultRecipientEmail
                val subject = "[BÁO CÁO NHẬT KÝ] Ngày ${journal.dateStr} - ${_userSession.value.displayName}"
                val details = """
                    - Ngày báo cáo: ${journal.dateStr}
                    - Thời tiết: ${journal.weather}
                    - Đoàn tuần tra: ${journal.patrolTeam}
                    - Khu vực tuần tra: ${journal.patrolCompartment}
                    NỘI DUNG NHẬT KÝ:
                    ${journal.content}
                    GHI CHÚ:
                    ${journal.notes}
                    DỮ LIỆU THỰC ĐỊA LIÊN QUAN TRONG NGÀY:
                    ${journal.linkedDataJson}
                """.trimIndent()
                val body = generateReportBody("BÁO CÁO NHẬT KÝ TUẦN TRA HẰNG NGÀY", details)
                EmailSenderHelper.sendEmail(context, recipient, subject, body, file.absolutePath)
            } else {
                android.widget.Toast.makeText(context, "Lỗi khi xuất file báo cáo!", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    suspend fun getLinkedDataSummaryForDate(date: String): String {
        return repository.getLinkedDataSummaryForDate(date)
    }
    fun sendWaypointReport(c: Context, wp: WaypointEntity) { 
        viewModelScope.launch { 
            val (vx, vy) = CoordinateSystemConverter.wgs84ToVn2000(wp.latitude, wp.longitude, _vn2000CentralMeridian.value, _vn2000ZoneDegrees.value)
            val cmStr = formatKtt(_vn2000CentralMeridian.value)
            val details = "Điểm: ${wp.title}\nMô tả: ${wp.description}\nThời gian: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(wp.timestampUtc))}\nTọa độ WGS84: ${wp.latitude}, ${wp.longitude}\nTọa độ VN2000 (Múi ${_vn2000ZoneDegrees.value}°, KTT $cmStr): X=${String.format("%.2f", vx)}, Y=${String.format("%.2f", vy)}"
            val validPhotoPath = if (!wp.photoPath.isNullOrEmpty()) { if (wp.photoPath!!.startsWith("content://")) wp.photoPath else { val file = File(wp.photoPath!!); if (file.exists()) file.absolutePath else null } } else null
            EmailSenderHelper.sendEmail(c, repository.prefs.defaultRecipientEmail, "Báo cáo Điểm: ${wp.title}", generateReportBody("BÁO CÁO ĐIỂM KHẢO SÁT", details), validPhotoPath) 
        } 
    }
    fun sendPolygonReport(c: Context, p: PolygonEntity) {
        viewModelScope.launch {
            val (vx, vy) = CoordinateSystemConverter.wgs84ToVn2000(p.centroidLat, p.centroidLon, _vn2000CentralMeridian.value, _vn2000ZoneDegrees.value)
            val cmStr = formatKtt(_vn2000CentralMeridian.value)
            val gpxFile = File(repository.getExportDirectory(), "Vung_${p.id}.gpx")
            GpxExporter.exportPolygonToGpx(p, gpxFile)
            val details = "Vùng: ${p.title}\nDiện tích: ${GisAreaCalculator.formatArea(p.areaSquareMeters, repository.prefs.areaUnit)}\nChu vi: ${GisAreaCalculator.formatDistance(p.perimeterMeters, repository.prefs.distanceUnit)}\nTọa độ trung tâm (WGS84): ${p.centroidLat}, ${p.centroidLon}\nTọa độ trung tâm (VN2000 Múi ${_vn2000ZoneDegrees.value}°, KTT $cmStr): X=${String.format("%.2f", vx)}, Y=${String.format("%.2f", vy)}"
            EmailSenderHelper.sendEmail(c, repository.prefs.defaultRecipientEmail, "Báo cáo Vùng: ${p.title}", generateReportBody("BÁO CÁO VÙNG DIỆN TÍCH", details), gpxFile.absolutePath)
        }
    }
    fun sendTrackLogReport(c: Context, t: TrackLogEntity) { 
        viewModelScope.launch { 
            val details = "Tracklog: ${t.title}\nChiều dài: ${GisAreaCalculator.formatDistance(t.totalDistanceMeters, repository.prefs.distanceUnit)}\nBắt đầu: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(t.startTimeUtc))}"
            EmailSenderHelper.sendEmail(c, repository.prefs.defaultRecipientEmail, "Báo cáo Tracklog: ${t.title}", generateReportBody("BÁO CÁO TRACKLOG", details), t.gpxFilePath) 
        } 
    }
    fun sendDailyReportByDate(c: Context, d: String) { 
        viewModelScope.launch { 
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
            val dateWaypoints = waypoints.value.filter { sdf.format(java.util.Date(it.timestampUtc)) == d }
            val dateTracks = trackLogs.value.filter { sdf.format(java.util.Date(it.startTimeUtc)) == d }
            val datePatrols = patrolLogs.value.filter { sdf.format(java.util.Date(it.discoveryTimeUtc)) == d }
            val dateFF = floraFaunaLogs.value.filter { sdf.format(java.util.Date(it.timestampUtc)) == d }
            val dateNI = naturalImpactLogs.value.filter { sdf.format(java.util.Date(it.timestampUtc)) == d }
            val datePolygons = polygons.value.filter { sdf.format(java.util.Date(it.timestampUtc)) == d }
            
            val pkg = repository.exportFilteredDailyReportPackage(dateWaypoints, dateTracks, datePatrols, datePolygons, d)
            val details = "Bao cao tong hop du lieu thuc dia ngay: $d\n" +
                    "- So luong diem khao sat: ${dateWaypoints.size}\n" +
                    "- So luong Tracklog: ${dateTracks.size}\n" +
                    "- So luong vu viec tuan tra: ${datePatrols.size}\n" +
                    "- So luong ghi nhan dong thuc vat: ${dateFF.size}\n" +
                    "- So luong tac dong tu nhien: ${dateNI.size}\n" +
                    "- So luong vung dien tich: ${datePolygons.size}"
            
            val attachmentPaths = pkg.allAttachmentFiles.joinToString("|") { it.absolutePath }
            EmailSenderHelper.sendEmail(c, repository.prefs.defaultRecipientEmail, "[BVR DAI THANH] Bao cao thuc dia ngay $d", generateReportBody("BAO CAO TONG HOP DU LIEU HANG NGAY", details), attachmentPaths) 
        } 
    }
    fun sendPendingEmails(c: Context) { viewModelScope.launch { val list = repository.emailQueueFlow.first(); if (list.isEmpty()) return@launch; EmailSenderHelper.sendEmailQueueBatch(c, list); list.forEach { repository.markEmailSent(it.id) } } }
    fun sendPatrolLogEmail(c: Context, p: PatrolLogEntity) { 
        viewModelScope.launch { 
            val user = _userSession.value
            val reportFile = File(repository.getExportDirectory(), "BaoCao_SuVu_${p.id}.docx")
            WordExportHelper.exportPatrolLogToWord(c, p, _vn2000CentralMeridian.value, user.unit, user.email, user.phoneNumber, reportFile)
            val details = "Su vu: ${p.incidentType}\nDia diem: ${p.violationLocation}\nToa do VN2000: X=${String.format("%.2f", p.vn2000X)}, Y=${String.format("%.2f", p.vn2000Y)}"
            val validPhotoPaths = if (!p.photoPath.isNullOrEmpty()) { p.photoPath!!.split("|").mapNotNull { path -> if (path.startsWith("content://")) path else { val file = File(path); if (file.exists()) file.absolutePath else null } }.joinToString("|") } else ""
            val attachments = if (validPhotoPaths.isNotEmpty()) "${reportFile.absolutePath}|$validPhotoPaths" else reportFile.absolutePath
            EmailSenderHelper.sendEmail(c, repository.prefs.defaultRecipientEmail, "Nhật ký Tuần tra: ${p.incidentType} (${p.violationField})", generateReportBody("BÁO CÁO NHẬT KÝ TUẦN TRA RỪNG", details), attachments) 
        } 
    }

    fun sendFloraFaunaReport(c: Context, log: com.baoverung.app.data.local.entity.FloraFaunaLogEntity) {
        viewModelScope.launch {
            val user = _userSession.value
            val reportFile = WordExportHelper.exportFloraFaunaLogToWord(c, log, _vn2000CentralMeridian.value, user.unit, user.email, user.phoneNumber)
            val details = "Dong thuc vat: ${log.appearanceDescription}\nSo luong: ${log.count}\nSinh canh: ${log.habitatType}\nToa do VN2000: X=${String.format("%.1f", log.vn2000X)}, Y=${String.format("%.1f", log.vn2000Y)}"
            val validPhotoPaths = if (!log.photoPath.isNullOrEmpty()) { log.photoPath!!.split("|").mapNotNull { path -> if (path.startsWith("content://")) path else { val file = File(path); if (file.exists()) file.absolutePath else null } }.joinToString("|") } else ""
            val attachments = if (reportFile != null) (if (validPhotoPaths.isNotEmpty()) "${reportFile.absolutePath}|$validPhotoPaths" else reportFile.absolutePath) else validPhotoPaths
            EmailSenderHelper.sendEmail(c, repository.prefs.defaultRecipientEmail, "Bao cao Dong thuc vat: ${log.appearanceDescription}", generateReportBody("BAO CAO THEO DOI DONG THUC VAT", details), attachments)
        }
    }

    fun sendNaturalImpactReport(c: Context, log: com.baoverung.app.data.local.entity.NaturalImpactLogEntity) {
        viewModelScope.launch {
            val user = _userSession.value
            val reportFile = WordExportHelper.exportNaturalImpactLogToWord(c, log, _vn2000CentralMeridian.value, user.unit, user.email, user.phoneNumber)
            val cause = if (log.cause == "Khác") log.otherCause else log.cause
            val details = "Tac dong TN: $cause\nDien tich: ${log.affectedArea}\nThiet hai: ${log.resourceDamage}\nToa do VN2000: X=${String.format("%.1f", log.vn2000X)}, Y=${String.format("%.1f", log.vn2000Y)}"
            val validPhotoPaths = if (!log.photoPath.isNullOrEmpty()) { log.photoPath!!.split("|").mapNotNull { path -> if (path.startsWith("content://")) path else { val file = File(path); if (file.exists()) file.absolutePath else null } }.joinToString("|") } else ""
            val attachments = if (reportFile != null) (if (validPhotoPaths.isNotEmpty()) "${reportFile.absolutePath}|$validPhotoPaths" else reportFile.absolutePath) else validPhotoPaths
            EmailSenderHelper.sendEmail(c, repository.prefs.defaultRecipientEmail, "Bao cao Tac dong TN: $cause", generateReportBody("BAO CAO TAC DONG TU NHIEN DEN RUNG", details), attachments)
        }
    }
    fun updateGisLayerOpacity(l: GisLayerEntity, o: Float) { 
        viewModelScope.launch { 
            repository.updateGisLayerOpacity(l, o) 
            _gisRefreshTrigger.value++
        } 
    }
    fun toggleGisLayer(l: GisLayerEntity) { 
        viewModelScope.launch { 
            repository.toggleGisLayerVisibility(l) 
            _gisRefreshTrigger.value++
        } 
    }
    fun updateGisLayerName(id: Long, name: String) { viewModelScope.launch { repository.updateGisLayerName(id, name) } }
    fun updateGisLayerLabelColumn(id: Long, column: String?) { 
        viewModelScope.launch { 
            repository.updateGisLayerLabelColumn(id, column)
            _gisRefreshTrigger.value++
        } 
    }
    fun moveGisLayerUp(l: GisLayerEntity) {
        viewModelScope.launch {
            val list = gisLayers.value; val idx = list.indexOf(l)
            if (idx > 0) { val prev = list[idx - 1]; repository.updateGisLayerPriority(l.id, prev.priority); repository.updateGisLayerPriority(prev.id, l.priority) }
        }
    }
    fun moveGisLayerDown(l: GisLayerEntity) {
        viewModelScope.launch {
            val list = gisLayers.value; val idx = list.indexOf(l)
            if (idx < list.size - 1) { val next = list[idx + 1]; repository.updateGisLayerPriority(l.id, next.priority); repository.updateGisLayerPriority(next.id, l.priority) }
        }
    }
    fun updateGisLayerCoordSys(id: Long, cm: Double, zd: Int) {
        viewModelScope.launch {
            repository.updateGisLayerCoordSys(id, cm, zd)
            // Critical: Re-cache features because WGS84 coordinates change with new CM/Zone
            triggerLayerCaching(id)
            _gisRefreshTrigger.value++
        }
    }
    fun setActiveCoordinateSystem(sys: String) { repository.prefs.activeCoordinateSystem = sys; _activeCoordinateSystem.value = sys }
    fun addGisLayer(n: String, t: String, p: String, cm: Double = 107.75, zd: Int = 3, c: Context? = null, label: String? = null) { 
        if (isReadOnlyUser()) { notifyReadOnlyRestriction(c); return }
        viewModelScope.launch { val maxPriority = gisLayers.value.maxOfOrNull { it.priority } ?: 0; repository.addGisLayer(n, t, p, cm, zd, maxPriority + 1, label) } 
    }

    data class ImportState(val fileName: String = "", val currentProgress: Int = 0, val totalCount: Int = 0, val isMetadataScan: Boolean = false, val isFullLoading: Boolean = false, val fields: List<String> = emptyList(), val filePath: String = "", val fileType: String = "", val errorMessage: String? = null, val isSuccess: Boolean = false, val layerId: Long = 0)
    private val _importState = MutableStateFlow(ImportState()); val importState: StateFlow<ImportState> = _importState.asStateFlow()

    fun importGisLayerUnified(context: Context, source: Any) {
        if (isReadOnlyUser()) { notifyReadOnlyRestriction(context); return }
        viewModelScope.launch(Dispatchers.IO) {
            val fileName: String
            val sourceUri: Uri?
            val sourceFile: File?

            when (source) {
                is Uri -> {
                    sourceUri = source
                    sourceFile = null
                    fileName = source.path?.substringAfterLast("/") ?: "file_gis"
                }
                is File -> {
                    sourceUri = null
                    sourceFile = source
                    fileName = source.name
                }
                else -> return@launch
            }

            val extension = fileName.substringAfterLast(".").lowercase()
            val name = fileName.substringBeforeLast(".")
            
            withContext(Dispatchers.Main) {
                _importState.value = ImportState(fileName = fileName, isFullLoading = true, fileType = extension.uppercase())
            }
            
            try {
                if (extension == "tab") {
                    // Handle MapInfo with staging (4-file set)
                    val result = if (sourceUri != null) {
                        com.baoverung.app.gis.MapInfoSafHelper.stageFileSet(context, sourceUri)
                    } else {
                        com.baoverung.app.gis.MapInfoSafHelper.stageFileSetFromFile(context, sourceFile!!)
                    }
                    
                    val stagedPath = result.tabPath
                    
                    if (stagedPath == null) {
                        val errorMsg = if (result.missingExtensions.isNotEmpty()) {
                            "Thiếu bộ tệp MapInfo (.TAB, .MAP, .ID, .DAT) trong cùng một thư mục.\n\nThiếu: ${result.missingExtensions.joinToString(", ")}"
                        } else {
                            result.error ?: "Không thể nạp tệp MapInfo."
                        }
                        _importState.update { it.copy(isFullLoading = false, errorMessage = errorMsg) }
                        return@launch
                    }

                    val maxPriority = gisLayers.value.maxOfOrNull { it.priority } ?: 0
                    val id = repository.addGisLayer(name, "TAB", stagedPath, 107.75, 3, maxPriority + 1, null)
                    triggerLayerCaching(id)
                } else if (extension == "shp") {
                    // Requirement I.1: Handle Shapefile with staging (shp, dbf, shx, prj)
                    val result = if (sourceUri != null) {
                        com.baoverung.app.gis.ShapefileSafHelper.stageFileSet(context, sourceUri)
                    } else {
                        com.baoverung.app.gis.ShapefileSafHelper.stageFileSetFromFile(context, sourceFile!!)
                    }

                    val stagedPath = result.shpPath

                    if (stagedPath == null) {
                        val errorMsg = if (result.missingExtensions.isNotEmpty()) {
                            "Thiếu bộ tệp QGIS (.SHP, .DBF, .SHX) trong cùng một thư mục.\n\nThiếu: ${result.missingExtensions.joinToString(", ")}"
                        } else {
                            result.error ?: "Không thể nạp tệp Shapefile."
                        }
                        _importState.update { it.copy(isFullLoading = false, errorMessage = errorMsg) }
                        return@launch
                    }

                    val maxPriority = gisLayers.value.maxOfOrNull { it.priority } ?: 0
                    val id = repository.addGisLayer(name, "SHP", stagedPath, 107.75, 3, maxPriority + 1, null)
                    triggerLayerCaching(id)
                } else {
                    // Handle other single-file formats
                    val destDir = File(context.filesDir, "imported_gis/singles")
                    destDir.mkdirs()
                    val destFile = File(destDir, "gis_${System.currentTimeMillis()}_$fileName")
                    
                    if (sourceUri != null) {
                        context.contentResolver.openInputStream(sourceUri)?.use { input ->
                            destFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    } else {
                        sourceFile!!.inputStream().use { input ->
                            destFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }

                    val type = when(extension) {
                        "mbtiles", "sqlite", "sqlitedb" -> "MBTILES"
                        "kml" -> "KML"
                        "kmz" -> "KMZ"
                        "shp" -> "SHP"
                        "geojson", "json" -> "GEOJSON"
                        else -> "RASTER"
                    }

                    val maxPriority = gisLayers.value.maxOfOrNull { it.priority } ?: 0
                    val id = repository.addGisLayer(name, type, destFile.absolutePath, 107.75, 3, maxPriority + 1, null)
                    
                    if (type in listOf("SHP", "KML", "KMZ", "GEOJSON")) {
                        triggerLayerCaching(id)
                    } else {
                        _importState.update { it.copy(isFullLoading = false, isSuccess = true, layerId = id) }
                    }
                }
            } catch (e: Exception) {
                _importState.update { it.copy(isFullLoading = false, errorMessage = "Lỗi nạp: ${e.message}") }
            }
        }
    }

    private suspend fun triggerLayerCaching(layerId: Long) {
        val layer = repository.db.gisLayerDao().getAllGisLayersList().find { it.id == layerId }
        if (layer != null) {
            var lastUpdate = 0L
            repository.cacheLayerFeatures(layer) { cur, tot ->
                val now = System.currentTimeMillis()
                if (now - lastUpdate > 200 || cur == tot) {
                    _importState.update { it.copy(currentProgress = cur, totalCount = tot) }
                    lastUpdate = now
                }
            }
            
            val fields = repository.getLayerFieldNames(layer)
            withContext(Dispatchers.Main) {
                _importState.update { it.copy(
                    isFullLoading = false,
                    isSuccess = true,
                    layerId = layerId,
                    fields = fields,
                    totalCount = it.currentProgress
                ) }
                _gisRefreshTrigger.value++ 
                
                // Show label selector hint or toast
                android.widget.Toast.makeText(getApplication(), "Nạp thành công! Vui lòng chọn nhãn hiển thị.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun cancelImport() {
        val state = _importState.value
        if (state.layerId != 0L && !state.isSuccess) {
            viewModelScope.launch(Dispatchers.IO) { repository.deleteGisLayer(state.layerId) }
        }
        _importState.value = ImportState()
    }

    fun finalizeGisImport(cm: Double, zd: Int, label: String?) {
        val state = _importState.value
        if (state.layerId == 0L) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val layer = repository.db.gisLayerDao().getAllGisLayersList().find { it.id == state.layerId }
                if (layer != null) {
                    if (layer.centralMeridian != cm || layer.zoneDegrees != zd) {
                        repository.updateGisLayerCoordSys(state.layerId, cm, zd)
                    }
                    repository.updateGisLayerLabelColumn(state.layerId, label)
                }
                
                withContext(Dispatchers.Main) {
                    _importState.value = ImportState()
                    _gisRefreshTrigger.value++
                    
                    val layer = repository.db.gisLayerDao().getAllGisLayersList().find { it.id == state.layerId }
                    if (layer != null) {
                        zoomToLayer(layer)
                    }
                    
                    android.widget.Toast.makeText(getApplication(), "Đã nạp và cấu hình thành công!", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    withContext(Dispatchers.Main) {
                        _importState.value = state.copy(errorMessage = "Lỗi cấu hình: ${e.message}")
                    }
                }
            }
        }
    }

    fun cancelDownloadMap() {
        downloadJob?.cancel()
        downloadJob = null
        _importState.value = ImportState()
    }

    private var downloadJob: kotlinx.coroutines.Job? = null

    fun downloadMapArea(points: List<com.baoverung.app.data.model.GpsPoint>, minZoom: Int, maxZoom: Int, mapSource: String) {
        if (points.isEmpty()) return
        
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            val urlTemplate = when {
                mapSource.contains("Satellite") -> "https://mt1.google.com/vt/lyrs=s&x={x}&y={y}&z={z}"
                mapSource.contains("Street") || mapSource.contains("Đường") -> "https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}"
                mapSource.contains("Terrain") -> "https://mt1.google.com/vt/lyrs=p&x={x}&y={y}&z={z}"
                mapSource.contains("Hybrid") -> "https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}"
                mapSource.contains("Esri") -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
                mapSource.contains("OpenStreetMap") -> "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
                else -> "https://mt1.google.com/vt/lyrs=s&x={x}&y={y}&z={z}"
            }

            val fileName = "Map_Offline_${System.currentTimeMillis()}.mbtiles"
            val outputFile = File(repository.getExportDirectory(), fileName)

            _importState.value = ImportState(fileName = fileName, isFullLoading = true)
            
            try {
                com.baoverung.app.gis.TileDownloader.downloadArea(
                    outputFile = outputFile,
                    minLat = minLat,
                    maxLat = maxLat,
                    minLon = minLon,
                    maxLon = maxLon,
                    minZoom = minZoom,
                    maxZoom = maxZoom,
                    urlTemplate = urlTemplate
                ) { current, total ->
                    _importState.value = _importState.value.copy(currentProgress = current, totalCount = total)
                }

                addGisLayer(fileName.substringBeforeLast("."), "MBTILES", outputFile.absolutePath)
                
                withContext(Dispatchers.Main) {
                    _importState.value = ImportState()
                    android.widget.Toast.makeText(getApplication(), "Tải bản đồ thành công!", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _importState.value = ImportState(errorMessage = "Lỗi khi tải: ${e.message}")
                }
            }
        }
    }

    fun deleteGisLayer(id: Long) { viewModelScope.launch { repository.deleteGisLayer(id); _gisRefreshTrigger.value++ } }
    fun updateGisLayerColors(id: Long, stroke: String, fill: String) { 
        viewModelScope.launch { 
            repository.updateGisLayerColors(id, stroke, fill)
            _gisRefreshTrigger.value++
        } 
    }
    suspend fun getLayerFieldNames(layer: GisLayerEntity): List<String> = repository.getLayerFieldNames(layer)
    fun zoomToLayer(l: GisLayerEntity) {
        viewModelScope.launch {
            if (l.fileType == "MBTILES") {
                _mbtilesReaders.value[l.id]?.getBounds()?.let { b ->
                    _mapFocusRequest.emit(Triple((b[0] + b[2]) / 2.0, (b[1] + b[3]) / 2.0, 15f))
                }
            } else {
                val extent = repository.getLayerExtent(l.id)
                if (extent != null && extent.minLat != 0.0) {
                    val centerLat = (extent.minLat + extent.maxLat) / 2.0
                    val centerLon = (extent.minLon + extent.maxLon) / 2.0
                    _mapFocusRequest.emit(Triple(centerLat, centerLon, 16f))
                } else {
                    repository.loadFeaturesFromLayer(l).firstOrNull()?.points?.firstOrNull()?.let { p ->
                        _mapFocusRequest.emit(Triple(p.latitude, p.longitude, 16f))
                    }
                }
            }
        }
    }
    fun saveManualTrack(title: String, context: Context? = null) {
        if (isReadOnlyUser()) { notifyReadOnlyRestriction(context); return }
        val points = _measurementPoints.value; if (points.size < 2) return
        viewModelScope.launch {
            val distMeters = com.baoverung.app.gis.GisAreaCalculator.calculatePathLength(points); val area = if (points.size >= 3) com.baoverung.app.gis.GisAreaCalculator.calculatePolygonArea(points) else 0.0
            if (_measurementMode.value == MeasurementMode.AREA || _measurementMode.value == MeasurementMode.GPX_AREA) { val count = repository.getTodayPolygonCount() + 1; val finalTitle = if (title.isEmpty()) "Vùng ${String.format("%02d", count)}" else title; repository.savePolygon(title = finalTitle, description = "Đo diện tích: ${GisAreaCalculator.formatArea(area, repository.prefs.areaUnit)}", points = points) } else { val stats = if (area > 500) "${GisAreaCalculator.formatDistance(distMeters, repository.prefs.distanceUnit)} / ${GisAreaCalculator.formatArea(area, repository.prefs.areaUnit)}" else GisAreaCalculator.formatDistance(distMeters, repository.prefs.distanceUnit); val count = repository.getTodayTrackCount() + 1; val finalTitle = if (title.isEmpty()) "Vệt ${String.format("%02d", count)}" else title; repository.saveTrackLog(title = "$finalTitle ($stats)", startTimeUtc = System.currentTimeMillis(), endTimeUtc = System.currentTimeMillis(), points = points); val lastTrack = repository.trackLogsFlow.first().maxByOrNull { it.id }; if (lastTrack != null) { repository.updateTrackCategory(lastTrack.id, "LINE", repository.prefs.lineColor) } }
            _measurementPoints.value = emptyList(); _measurementMode.value = MeasurementMode.NONE; runCloudSync()
        }
    }
    fun updateVn2000Settings(p: String, c: Double, z: Int) { repository.prefs.vn2000ProvinceName = p; repository.prefs.vn2000CentralMeridian = c; repository.prefs.vn2000ZoneDegrees = z; _vn2000ProvinceName.value = p; _vn2000CentralMeridian.value = c; _vn2000ZoneDegrees.value = z }

    fun updateFontEncoding(e: String) { repository.prefs.fontEncoding = e }
    fun updateShowViewAngle(v: Boolean) { repository.prefs.showViewAngle = v; _showViewAngle.value = v }
    fun updateShowViewLine(v: Boolean) { repository.prefs.showViewLine = v; _showViewLine.value = v }
    fun updateShowMoveDirection(v: Boolean) { repository.prefs.showMoveDirection = v; _showMoveDirection.value = v }
    fun updateShowMoveLine(v: Boolean) { repository.prefs.showMoveLine = v; _showMoveLine.value = v }
    fun updateShowCompass(v: Boolean) { repository.prefs.showCompass = v; _showCompass.value = v }
    fun updateShowSatelliteInfo(v: Boolean) { repository.prefs.showSatelliteInfo = v; _showSatelliteInfo.value = v }
    fun updateShowZoomControls(v: Boolean) { repository.prefs.showZoomControls = v; _showZoomControls.value = v }
    fun updateShowRotationControls(v: Boolean) { repository.prefs.showRotationControls = v; _showRotationControls.value = v }
    fun updateShowZoomLevel(v: Boolean) { repository.prefs.showZoomLevel = v; _showZoomLevel.value = v }
    fun updateShowMapCenter(v: Boolean) { repository.prefs.showMapCenter = v; _showMapCenter.value = v }

    fun updateShowLabelsGlobal(v: Boolean) { repository.prefs.showLabelsGlobal = v; _showLabelsGlobal.value = v }
    fun updateShowImagesGlobal(v: Boolean) { repository.prefs.showImagesGlobal = v; _showImagesGlobal.value = v }
    fun updateShowPointsGlobal(v: Boolean) { repository.prefs.showPointsGlobal = v; _showPointsGlobal.value = v }
    fun updateShowTracklogsGlobal(v: Boolean) { repository.prefs.showTracklogsGlobal = v; _showTracklogsGlobal.value = v }
    fun updateShowLinesGlobal(v: Boolean) { repository.prefs.showLinesGlobal = v; _showLinesGlobal.value = v }
    fun updateShowPolygonsGlobal(v: Boolean) { repository.prefs.showPolygonsGlobal = v; _showPolygonsGlobal.value = v }
    fun updateShowIncidentsGlobal(v: Boolean) { repository.prefs.showIncidentsGlobal = v; _showIncidentsGlobal.value = v }
    fun updateShowDailyJournalsGlobal(v: Boolean) { repository.prefs.showDailyJournalsGlobal = v; _showDailyJournalsGlobal.value = v }
    fun updateShowFloraFaunaGlobal(v: Boolean) { repository.prefs.showFloraFaunaGlobal = v; _showFloraFaunaGlobal.value = v }
    fun updateShowNaturalImpactGlobal(v: Boolean) { repository.prefs.showNaturalImpactGlobal = v; _showNaturalImpactGlobal.value = v }

    fun updateImageIconType(t: String) { repository.prefs.imageIconType = t; _imageIconType.value = t }
    fun updateImageIconSize(s: Int) { repository.prefs.imageIconSize = s; _imageIconSize.value = s }
    fun updateImageColor(c: String) { repository.prefs.imageColor = c; _imageColor.value = c; viewModelScope.launch { repository.updateAllImagesColor(c) } }
    fun updateShowImageLabels(v: Boolean) { repository.prefs.showImageLabels = v; _showImageLabels.value = v }
    fun updateImageLabelSize(s: Int) { repository.prefs.imageLabelSize = s; _imageLabelSize.value = s }
    fun updateImageQuality(q: Int) { repository.prefs.imageQuality = q; _imageQuality.value = q }
    fun updateImageResize(r: Int) { repository.prefs.imageResize = r; _imageResize.value = r }

    fun updatePointIconType(t: String) { repository.prefs.pointIconType = t; _pointIconType.value = t }
    fun updatePointIconSize(s: Int) { repository.prefs.pointIconSize = s; _pointIconSize.value = s }
    fun updatePointColor(c: String) { repository.prefs.pointColor = c; _pointColor.value = c; viewModelScope.launch { repository.updateAllWaypointsColor(c) } }
    fun updateShowPointLabels(v: Boolean) { repository.prefs.showPointLabels = v; _showPointLabels.value = v }
    fun updatePointLabelSize(s: Int) { repository.prefs.pointLabelSize = s; _pointLabelSize.value = s }

    fun updateTracklogColor(c: String) { repository.prefs.tracklogColor = c; _tracklogColor.value = c; viewModelScope.launch { repository.updateAllTracksColor(c) } }
    fun updateTracklogWidth(w: Float) { repository.prefs.tracklogWidth = w; _tracklogWidth.value = w }
    fun updateTracklogStyle(s: String) { repository.prefs.tracklogStyle = s; _tracklogStyle.value = s }
    fun updateShowTracklogLabels(v: Boolean) { repository.prefs.showTracklogLabels = v; _showTracklogLabels.value = v }
    fun updateShowTracklogValue(v: Boolean) { repository.prefs.showTracklogValue = v; _showTracklogValue.value = v }
    fun updateTracklogFontSize(s: Int) { repository.prefs.tracklogFontSize = s; _tracklogFontSize.value = s }

    fun updateLineColor(c: String) { repository.prefs.lineColor = c; _lineColor.value = c; viewModelScope.launch { repository.updateAllLinesColor(c) } }
    fun updateLineWidth(w: Float) { repository.prefs.lineWidth = w; _lineWidth.value = w }
    fun updateLineStyle(s: String) { repository.prefs.lineStyle = s; _lineStyle.value = s }
    fun updateShowLineLabels(v: Boolean) { repository.prefs.showLineLabels = v; _showLineLabels.value = v }
    fun updateShowLineValue(v: Boolean) { repository.prefs.showLineValue = v; _showLineValue.value = v }
    fun updateLineFontSize(s: Int) { repository.prefs.lineFontSize = s; _lineFontSize.value = s }

    fun updatePolygonBoundaryColor(c: String) { repository.prefs.polygonBoundaryColor = c; _polygonBoundaryColor.value = c; viewModelScope.launch { repository.updateAllPolygonsColor(c) } }
    fun updatePolygonFillColor(c: String) { repository.prefs.polygonFillColor = c; _polygonFillColor.value = c }
    fun updatePolygonWidth(w: Float) { repository.prefs.polygonWidth = w; _polygonWidth.value = w }
    fun updatePolygonStyle(s: String) { repository.prefs.polygonStyle = s; _polygonStyle.value = s }
    fun updateShowPolygonLabels(v: Boolean) { repository.prefs.showPolygonLabels = v; _showPolygonLabels.value = v }
    fun updateShowPolygonValue(v: Boolean) { repository.prefs.showPolygonValue = v; _showPolygonValue.value = v }
    fun updatePolygonFontSize(s: Int) { repository.prefs.polygonFontSize = s; _polygonFontSize.value = s }

    fun updateIncidentColor(c: String) { repository.prefs.incidentColor = c; _incidentColor.value = c; viewModelScope.launch { repository.updateAllIncidentsColor(c) } }
    fun updateIncidentIconType(t: String) { repository.prefs.incidentIconType = t; _incidentIconType.value = t }
    fun updateIncidentIconSize(s: Int) { repository.prefs.incidentIconSize = s; _incidentIconSize.value = s }
    fun updateShowIncidentLabels(v: Boolean) { repository.prefs.showIncidentLabels = v; _showIncidentLabels.value = v }
    fun updateIncidentFontSize(s: Int) { repository.prefs.incidentFontSize = s; _incidentFontSize.value = s }

    fun updateFloraFaunaColor(c: String) { repository.prefs.floraFaunaColor = c; _floraFaunaColor.value = c; viewModelScope.launch { repository.updateAllFloraFaunaColor(c) } }
    fun updateFloraFaunaIconType(t: String) { repository.prefs.floraFaunaIconType = t; _floraFaunaIconType.value = t }
    fun updateFloraFaunaIconSize(s: Int) { repository.prefs.floraFaunaIconSize = s; _floraFaunaIconSize.value = s }
    fun updateShowFloraFaunaLabels(v: Boolean) { repository.prefs.showFloraFaunaLabels = v; _showFloraFaunaLabels.value = v }
    fun updateFloraFaunaFontSize(s: Int) { repository.prefs.floraFaunaFontSize = s; _floraFaunaFontSize.value = s }

    fun updateNaturalImpactColor(c: String) { repository.prefs.naturalImpactColor = c; _naturalImpactColor.value = c; viewModelScope.launch { repository.updateAllNaturalImpactColor(c) } }
    fun updateNaturalImpactIconType(t: String) { repository.prefs.naturalImpactIconType = t; _naturalImpactIconType.value = t }
    fun updateNaturalImpactIconSize(s: Int) { repository.prefs.naturalImpactIconSize = s; _naturalImpactIconSize.value = s }
    fun updateShowNaturalImpactLabels(v: Boolean) { repository.prefs.showNaturalImpactLabels = v; _showNaturalImpactLabels.value = v }
    fun updateNaturalImpactFontSize(s: Int) { repository.prefs.naturalImpactFontSize = s; _naturalImpactFontSize.value = s }

    fun updateLandmarkColor(c: String) { repository.prefs.landmarkColor = c; _landmarkColor.value = c }
    fun updateLandmarkIconType(t: String) { repository.prefs.landmarkIconType = t; _landmarkIconType.value = t }
    fun updateLandmarkIconSize(s: Int) { repository.prefs.landmarkIconSize = s; _landmarkIconSize.value = s }
    fun updateShowLandmarkLabels(v: Boolean) { repository.prefs.showLandmarkLabels = v; _showLandmarkLabels.value = v }
    fun updateShowLandmarkCode(v: Boolean) { repository.prefs.showLandmarkCode = v; _showLandmarkCode.value = v }
    fun updateLandmarkLabelSize(s: Int) { repository.prefs.landmarkLabelSize = s; _landmarkLabelSize.value = s }

    fun updateClusterLandmarks(v: Boolean) { repository.prefs.clusterLandmarks = v }
    fun updateQuickCreateLandmark(v: Boolean) { repository.prefs.quickCreateLandmark = v }

    fun updateDistanceUnit(u: String) { repository.prefs.distanceUnit = u; _distanceUnit.value = u }
    fun updateAreaUnit(u: String) { repository.prefs.areaUnit = u; _areaUnit.value = u }
    fun updateGpsFilterDistance(d: Float) { repository.prefs.gpsFilterDistance = d; _gpsFilterDistance.value = d }
    fun updateTrackingIntervalSeconds(s: Int) { repository.prefs.trackingIntervalSeconds = s; _trackingIntervalSeconds.value = s }
    fun updateAntennaHeight(h: Float) { repository.prefs.antennaHeight = h; _antennaHeight.value = h }
    fun updateUseAGps(v: Boolean) { repository.prefs.useAGps = v; _useAGps.value = v }
    fun updateLongPressOnMapEnabled(v: Boolean) { repository.prefs.longPressOnMapEnabled = v }
    fun updateGetAddressOnPress(v: Boolean) { repository.prefs.getAddressOnPress = v }
    fun updateShakeToMoveMap(v: Boolean) { repository.prefs.shakeToMoveMap = v; _shakeToMoveMap.value = v }
    fun updateKeepScreenOn(v: Boolean) { repository.prefs.keepScreenOn = v; _keepScreenOn.value = v }
    fun updateFixMbTilesDisplay(v: Boolean) { repository.prefs.fixMbTilesDisplay = v; _fixMbTilesDisplay.value = v }

    fun updateDefaultIncidentLeader(s: String) { repository.prefs.defaultIncidentLeader = s; _defaultIncidentLeader.value = s }
    fun updateDefaultIncidentField(s: String) { repository.prefs.defaultIncidentField = s; _defaultIncidentField.value = s }

    // Watermark
    private val _watermarkSettings = MutableStateFlow(repository.prefs.getWatermarkSettings())
    val watermarkSettings: StateFlow<com.baoverung.app.util.WatermarkHelper.WatermarkSettings> = _watermarkSettings.asStateFlow()

    fun updateWatermarkSettings(s: com.baoverung.app.util.WatermarkHelper.WatermarkSettings) {
        repository.prefs.saveWatermarkSettings(s)
        _watermarkSettings.value = s
    }

    private fun formatKtt(valcm: Double): String { val d = valcm.toInt(); val m = Math.round((valcm - d) * 60).toInt(); return "${d}°${if (m < 10) "0$m" else m}'" }
    private fun generateReportBody(h: String, d: String): String { val user = _userSession.value; val timeNow = java.text.SimpleDateFormat("HH:mm:ss dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date()); val cmStr = formatKtt(_vn2000CentralMeridian.value); return "$h\n----------------------------------------------------\nCấu hình hệ tọa độ VN2000: Múi ${_vn2000ZoneDegrees.value}° | Kinh tuyến trục (KTT): $cmStr\nTHỜI GIAN BÁO CÁO: $timeNow\n\nCHI TIẾT DỮ LIỆU:\n$d\n\nTHÔNG TIN CÁN BỘ BÁO CÁO:\n- Họ và tên: ${user.displayName}\n- Đơn vị: ${user.unit}\n- Bộ phận: ${user.department}\n- Email: ${user.email}\n- Số điện thoại: ${user.phoneNumber}\n\n----------------------------------------------------\n(Báo cáo chuyên nghiệp được trích xuất từ Ứng dụng Bảo vệ rừng - Đại Thành)" }

    override fun onSensorChanged(e: SensorEvent?) { if (e == null) return; when (e.sensor.type) { Sensor.TYPE_ORIENTATION -> _compassAzimuth.value = e.values[0]; Sensor.TYPE_ACCELEROMETER -> _accelerometerValues.value = e.values.clone(); Sensor.TYPE_GRAVITY -> _gravityValues.value = e.values.clone() } }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    private fun checkAutoGpxIdleRestart() {
        val session = _userSession.value
        if (!session.isLoggedIn || session.isOfflineMode || !session.autoGpx) return
        
        if (!_isTrackingGpx.value) {
            val lastActivity = repository.prefs.lastGpxActivityTime
            val now = System.currentTimeMillis()
            // If idle for more than 1 hour (3600000 ms), restart
            if (now - lastActivity > 3600000L) {
                viewModelScope.launch(Dispatchers.Main) {
                    startGpxTracking()
                    android.widget.Toast.makeText(getApplication(), "Tự động kích hoạt lại Tracklog (sau 1 giờ tạm dừng).", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startKeyMonitoring(key: String) {
        if (currentMonitoredKey == key) return; stopKeyMonitoring(); currentMonitoredKey = key; val ref = FirebaseDatabase.getInstance().reference.child("ActivationKeys").child(key)
        activeKeyMonitoringListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return; val isActive = snapshot.child("isActive").value as? Boolean ?: false; val boundId = snapshot.child("deviceId").value as? String ?: ""
                val remotePerms = snapshot.child("permissions").value as? String ?: "FULL"
                val expiryTs = (snapshot.child("expiryTimestamp").value as? Long) ?: 0L
                val remoteAutoGpx = snapshot.child("autoGpx").value as? Boolean ?: false
                val remoteCanSync = snapshot.child("canSync").value as? Boolean ?: true
                
                val currentSession = _userSession.value
                if (currentSession.isLoggedIn && !currentSession.isOfflineMode) {
                    val formattedExpiry = if (expiryTs > 0) java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(expiryTs)) else currentSession.expiryDate
                    
                    if (remotePerms != currentSession.permissions || formattedExpiry != currentSession.expiryDate || remoteAutoGpx != currentSession.autoGpx || remoteCanSync != currentSession.canSync) {
                        viewModelScope.launch(Dispatchers.Main) { 
                            repository.prefs.updatePermissions(remotePerms)
                            repository.prefs.updateExpiryDate(formattedExpiry)
                            
                            // Correctly update UserSession and Prefs for autoGpx and canSync
                            val prefs = getApplication<Application>().getSharedPreferences("vtool_prefs", Context.MODE_PRIVATE)
                            prefs.edit()
                                .putBoolean("user_auto_gpx", remoteAutoGpx)
                                .putBoolean("user_can_sync", remoteCanSync)
                                .apply()

                            _userSession.value = currentSession.copy(
                                permissions = remotePerms, 
                                expiryDate = formattedExpiry,
                                autoGpx = remoteAutoGpx,
                                canSync = remoteCanSync
                            )
                            android.widget.Toast.makeText(getApplication(), "Cập nhật cấu hình từ máy chủ.", android.widget.Toast.LENGTH_SHORT).show()
                            
                            if (remoteAutoGpx && !currentSession.autoGpx && !_isTrackingGpx.value) {
                                startGpxTracking()
                            }
                        }
                    }
                }
                if (!isActive) { viewModelScope.launch(Dispatchers.Main) { logout(); android.widget.Toast.makeText(getApplication(), "Tài khoản đã bị khóa.", android.widget.Toast.LENGTH_LONG).show() }; return }
                if (boundId.isEmpty()) { viewModelScope.launch(Dispatchers.Main) { logout(); android.widget.Toast.makeText(getApplication(), "Đăng xuất từ xa.", android.widget.Toast.LENGTH_LONG).show() }; return }
                if (expiryTs > 0 && System.currentTimeMillis() > expiryTs) viewModelScope.launch(Dispatchers.Main) { logout(); android.widget.Toast.makeText(getApplication(), "Hết hạn sử dụng.", android.widget.Toast.LENGTH_LONG).show() }
            }
            override fun onCancelled(error: DatabaseError) { Log.e("MainViewModel", "Key monitoring cancelled: ${error.message}") }
        }
        ref.addValueEventListener(activeKeyMonitoringListener!!)
    }

    private fun stopKeyMonitoring() { currentMonitoredKey?.let { key -> activeKeyMonitoringListener?.let { listener -> FirebaseDatabase.getInstance().reference.child("ActivationKeys").child(key).removeEventListener(listener) } }; activeKeyMonitoringListener = null; currentMonitoredKey = null }
    override fun onCleared() { super.onCleared(); sensorManager.unregisterListener(this); stopKeyMonitoring(); try { if (isBound) { getApplication<Application>().unbindService(serviceConnection); isBound = false } } catch (e: Exception) {} }
}
