package com.baoverung.app.ui.map

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.baoverung.app.util.GeometryUtils
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.baoverung.app.data.local.entity.GisLayerEntity
import com.baoverung.app.data.model.GisFeature
import com.baoverung.app.data.model.GisShapeType
import com.baoverung.app.data.model.GpsPoint
import com.baoverung.app.data.model.TrackLogUiModel
import com.baoverung.app.data.model.PolygonUiModel
import com.baoverung.app.gis.GisAreaCalculator
import com.baoverung.app.ui.MeasurementMode
import com.baoverung.app.ui.components.SatelliteStatusBar
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import com.baoverung.app.gis.CoordinateSystemConverter
import com.baoverung.app.gis.MBTilesReader
import com.baoverung.app.ui.SyncStatus
import java.io.File
import kotlin.math.*

data class DOffset(val x: Double, val y: Double)

private fun latLonToWorld(lat: Double, lon: Double, zoom: Float): DOffset {
    val scale = 256.0 * 2.0.pow(zoom.toDouble())
    val x = (lon + 180.0) / 360.0 * scale
    val latRad = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
    val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * scale
    return DOffset(x, y)
}

private fun worldToLatLon(worldX: Double, worldY: Double, zoom: Float): Pair<Double, Double> {
    val scale = 256.0 * 2.0.pow(zoom.toDouble())
    val lon = (worldX / scale) * 360.0 - 180.0
    val n = PI - 2.0 * PI * (worldY / scale)
    val latRad = atan(sinh(n))
    val lat = Math.toDegrees(latRad)
    return Pair(lat, lon)
}

private fun Offset.rotateAround(center: Offset, degrees: Float): Offset {
    val angleRad = Math.toRadians(degrees.toDouble())
    val cosA = cos(angleRad).toFloat()
    val sinA = sin(angleRad).toFloat()
    val dx = x - center.x
    val dy = y - center.y
    return Offset(
        x = center.x + (dx * cosA - dy * sinA),
        y = center.y + (dx * sinA + dy * cosA)
    )
}

private fun getPathEffect(style: String): PathEffect? {
    return when (style) {
        "dashed" -> PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        "dotted" -> PathEffect.dashPathEffect(floatArrayOf(2f, 5f), 0f)
        "dash_dot" -> PathEffect.dashPathEffect(floatArrayOf(10f, 5f, 2f, 5f), 0f)
        "long_dash" -> PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
        else -> null
    }
}

@Composable
fun OnlineMapTileLayer(
    selectedMapSource: String,
    centerLat: Double,
    centerLon: Double,
    zoomLevel: Float,
    widthPx: Float,
    heightPx: Float
) {
    if (widthPx <= 0 || heightPx <= 0) return

    val maxRequestZoom = when {
        selectedMapSource.contains("Satellite") -> 21
        selectedMapSource.contains("Esri") -> 18
        selectedMapSource.contains("OpenStreetMap") -> 19
        else -> 21
    }
    val tileZoom = zoomLevel.toInt().coerceIn(1, maxRequestZoom)
    val numTiles = 1 shl tileZoom

    val centerWorld = latLonToWorld(centerLat, centerLon, zoomLevel)

    val leftWorld = centerWorld.x - widthPx / 2.0
    val topWorld = centerWorld.y - heightPx / 2.0

    val scaleFromTileZoomToCurrent = 2.0.pow((zoomLevel - tileZoom).toDouble())
    val tileSizeOnScreen = 256.0 * scaleFromTileZoomToCurrent

    // High-precision range calculation
    val minX = floor(leftWorld / tileSizeOnScreen).toInt()
    val maxX = floor((leftWorld + widthPx) / tileSizeOnScreen).toInt()
    val minY = floor(topWorld / tileSizeOnScreen).toInt()
    val maxY = floor((topWorld + heightPx) / tileSizeOnScreen).toInt()

    val density = LocalDensity.current
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        for (tx_raw in minX..maxX) {
            // Handle Globe Wrap-Around
            val tx = (tx_raw % numTiles + numTiles) % numTiles
            
            for (ty in minY..maxY) {
                if (ty < 0 || ty >= numTiles) continue

                val tileWorldX = tx_raw * tileSizeOnScreen
                val tileWorldY = ty * tileSizeOnScreen

                val screenX = (tileWorldX - leftWorld).toFloat()
                val screenY = (tileWorldY - topWorld).toFloat()

                val tileUrl = when {
                    selectedMapSource.contains("Satellite") -> "https://mt1.google.com/vt/lyrs=s&x=$tx&y=$ty&z=$tileZoom"
                    selectedMapSource.contains("Street") || selectedMapSource.contains("Đường") -> "https://mt1.google.com/vt/lyrs=m&x=$tx&y=$ty&z=$tileZoom"
                    selectedMapSource.contains("Terrain") -> "https://mt1.google.com/vt/lyrs=p&x=$tx&y=$ty&z=$tileZoom"
                    selectedMapSource.contains("Hybrid") -> "https://mt1.google.com/vt/lyrs=y&x=$tx&y=$ty&z=$tileZoom"
                    selectedMapSource.contains("Esri") -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$tileZoom/$ty/$tx"
                    selectedMapSource.contains("OpenStreetMap") -> "https://tile.openstreetmap.org/$tileZoom/$tx/$ty.png"
                    else -> "https://mt1.google.com/vt/lyrs=s&x=$tx&y=$ty&z=$tileZoom"
                }

                val imageRequest = remember(tileUrl) {
                    ImageRequest.Builder(context)
                        .data(tileUrl)
                        .addHeader("User-Agent", "Mozilla/5.0 (Android; vToolSurveyGIS/1.0)")
                        .crossfade(true)
                        .build()
                }

                // Optimization: Overlap tiles by 8.0px to hide white grid lines completely
                // Increased overlap ensures seamless imagery at extreme zoom (23)
                val sizeDp = with(density) { (tileSizeOnScreen + 8.0).toFloat().toDp() }
                
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .offset { 
                            androidx.compose.ui.unit.IntOffset(
                                floor(screenX).toInt(), 
                                floor(screenY).toInt()
                            ) 
                        }
                        .size(sizeDp)
                )
            }
        }
    }
}

@Composable
fun OfflineMapTileLayer(
    mbtilesReaders: Map<Long, MBTilesReader>,
    gisLayers: List<GisLayerEntity>,
    centerLat: Double,
    centerLon: Double,
    zoomLevel: Float,
    widthPx: Float,
    heightPx: Float
) {
    if (widthPx <= 0 || heightPx <= 0) return

    val tileZoomRaw = zoomLevel.toInt()
    val centerWorld = latLonToWorld(centerLat, centerLon, zoomLevel)
    val leftWorld = centerWorld.x - widthPx / 2.0
    val topWorld = centerWorld.y - heightPx / 2.0

    Box(modifier = Modifier.fillMaxSize()) {
        for (layer in gisLayers) {
            if (!layer.isVisible || (layer.fileType != "MBTILES" && layer.fileType != "SQLITE")) continue
            val reader = mbtilesReaders[layer.id] ?: continue
            
            val maxZ = reader.getMaxZoom()
            val tileZoom = tileZoomRaw.coerceIn(1, maxZ)
            val numTiles = 1 shl tileZoom
            val scaleFromTileZoomToCurrent = 2.0.pow((zoomLevel - tileZoom).toDouble())
            val tileSizeOnScreen = 256.0 * scaleFromTileZoomToCurrent

            val minX = floor(leftWorld / tileSizeOnScreen).toInt()
            val maxX = floor((leftWorld + widthPx) / tileSizeOnScreen).toInt()
            val minY = floor(topWorld / tileSizeOnScreen).toInt()
            val maxY = floor((topWorld + heightPx) / tileSizeOnScreen).toInt()

            Canvas(modifier = Modifier.fillMaxSize()) {
                val alpha = layer.opacity
                for (tx_raw in minX..maxX) {
                    val tx = (tx_raw % numTiles + numTiles) % numTiles
                    for (ty in minY..maxY) {
                        if (ty < 0 || ty >= numTiles) continue
                        
                        var bitmap = reader.getTileBitmap(tileZoom, tx, ty)
                        if (bitmap == null) continue

                        val tileWorldX = tx_raw * tileSizeOnScreen
                        val tileWorldY = ty * tileSizeOnScreen
                        val screenX = (tileWorldX - leftWorld).toFloat()
                        val screenY = (tileWorldY - topWorld).toFloat()
                        
                        val imageBitmap = bitmap.asImageBitmap()
                        
                        // Use 8.0px overlap for high-precision vector tile rendering
                        val sizePx = (tileSizeOnScreen + 8.0).toInt()
                        
                        drawImage(
                            image = imageBitmap,
                            dstOffset = androidx.compose.ui.unit.IntOffset(floor(screenX).toInt(), floor(screenY).toInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(sizePx, sizePx), 
                            alpha = alpha,
                            filterQuality = androidx.compose.ui.graphics.FilterQuality.High
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VisibilityToggleIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isVisible: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp).background(if (isVisible) Color.White.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isVisible) Color.White else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
    }
}

data class MapUiSettings(
    val showZoomControls: Boolean = true,
    val showRotationControls: Boolean = true,
    val showCompass: Boolean = true,
    val showSatelliteInfo: Boolean = true,
    val showZoomLevel: Boolean = true,
    val showMapCenter: Boolean = true,
    val showViewAngle: Boolean = true,
    val showViewLine: Boolean = false,
    val showMoveDirection: Boolean = true,
    val showMoveLine: Boolean = false,
    val showLabelsGlobal: Boolean = true,
    val showImagesGlobal: Boolean = true,
    val showPointsGlobal: Boolean = true,
    val showTracklogsGlobal: Boolean = true,
    val showLinesGlobal: Boolean = true,
    val showPolygonsGlobal: Boolean = true,
    val showIncidentsGlobal: Boolean = true,
    val showFloraFaunaGlobal: Boolean = true,
    val showNaturalImpactGlobal: Boolean = true,

    // Image Settings
    val imageIconType: String = "camera",
    val imageIconSize: Int = 40,
    val imageColor: String = "#FFD32F2F",
    val showImageLabels: Boolean = true,
    val imageFontSize: Int = 14,

    // Point Settings
    val pointIconType: String = "tree",
    val pointIconSize: Int = 40,
    val pointColor: String = "#FF1976D2",
    val showPointLabels: Boolean = true,
    val pointFontSize: Int = 14,

    // Tracklog Settings
    val tracklogColor: String = "#FFFF3D00",
    val tracklogWidth: Float = 3f,
    val tracklogStyle: String = "solid",
    val showTracklogLabels: Boolean = true,
    val showTracklogValue: Boolean = true,
    val tracklogFontSize: Int = 14,

    // Line Settings
    val lineColor: String = "#FF9C27B0",
    val lineWidth: Float = 2f,
    val lineStyle: String = "solid",
    val showLineLabels: Boolean = true,
    val showLineValue: Boolean = true,
    val lineFontSize: Int = 14,

    // Polygon Settings
    val polygonBoundaryColor: String = "#FF1976D2",
    val polygonFillColor: String = "#334CAF50",
    val polygonWidth: Float = 2f,
    val polygonStyle: String = "solid",
    val showPolygonLabels: Boolean = true,
    val showPolygonValue: Boolean = true,
    val polygonFontSize: Int = 14,

    // Incident Settings
    val incidentColor: String = "#FFD32F2F",
    val incidentIconType: String = "notebook",
    val incidentIconSize: Int = 40,
    val showIncidentLabels: Boolean = true,
    val incidentFontSize: Int = 14,

    // Flora/Fauna Settings
    val floraFaunaColor: String = "#FF2E7D32",
    val floraFaunaIconType: String = "tree",
    val floraFaunaIconSize: Int = 40,
    val showFloraFaunaLabels: Boolean = true,
    val floraFaunaFontSize: Int = 14,

    // Natural Impact Settings
    val naturalImpactColor: String = "#FFFBC02D",
    val naturalImpactIconType: String = "alert",
    val naturalImpactIconSize: Int = 40,
    val showNaturalImpactLabels: Boolean = true,
    val naturalImpactFontSize: Int = 14,

    // Landmark Settings
    val landmarkColor: String = "#FFD84315",
    val landmarkIconType: String = "flag",
    val landmarkIconSize: Int = 30,
    val showLandmarkLabels: Boolean = true,
    val landmarkLabelSize: Int = 14,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    centerLat: Double,
    centerLon: Double,
    zoomLevel: Float,
    onMapChange: (Double, Double, Float) -> Unit,
    onUpdateMapBounds: (Double, Double, Double, Double) -> Unit = { _, _, _, _ -> },
    currentLocation: GpsPoint?,
    compassAzimuth: Float,
    satellitesCount: Int,
    satellitesVisible: Int = 0,
    satelliteDetails: List<com.baoverung.app.data.model.SatelliteInfo> = emptyList(),
    accelerometerValues: FloatArray = floatArrayOf(0f, 0f, 0f),
    gravityValues: FloatArray = floatArrayOf(0f, 0f, 0f),
    measurementMode: MeasurementMode,
    measurementPoints: List<GpsPoint>,
    targetNavPoint: GpsPoint?,
    gisLayers: List<GisLayerEntity>,
    gisFeaturesMap: Map<Long, List<GisFeature>>,
    mbtilesReaders: Map<Long, MBTilesReader> = emptyMap(),
    // Data for rendering on map
    waypoints: List<com.baoverung.app.data.local.entity.WaypointEntity> = emptyList(),
    trackLogs: List<TrackLogUiModel> = emptyList(),
    patrolLogs: List<com.baoverung.app.data.local.entity.PatrolLogEntity> = emptyList(),
    floraFaunaLogs: List<com.baoverung.app.data.local.entity.FloraFaunaLogEntity> = emptyList(),
    naturalImpactLogs: List<com.baoverung.app.data.local.entity.NaturalImpactLogEntity> = emptyList(),
    polygons: List<PolygonUiModel> = emptyList(),
    hiddenWaypointIds: Set<Long> = emptySet(),
    hiddenTrackLogIds: Set<Long> = emptySet(),
    hiddenPatrolLogIds: Set<Long> = emptySet(),
    hiddenFloraFaunaIds: Set<Long> = emptySet(),
    hiddenNaturalImpactIds: Set<Long> = emptySet(),
    hiddenPolygonIds: Set<Long> = emptySet(),
    isTrackingGpx: Boolean,
    trackedPoints: List<GpsPoint>,
    previewTrackPoints: List<GpsPoint> = emptyList(),
    onClearPreviewTrack: () -> Unit = {},
    selectedMapSource: String,
    onSelectMapSource: (String) -> Unit,
    onSetMeasurementMode: (MeasurementMode) -> Unit,
    onAddMeasurementPoint: (GpsPoint) -> Unit,
    onUndoMeasurementPoint: () -> Unit = {},
    onDeleteMeasurementPoint: (Int) -> Unit = {},
    onUpdateMeasurementPoint: (Int, GpsPoint) -> Unit = { _, _ -> },
    onNavigateToPoint: (GpsPoint) -> Unit = {},
    onClearMeasurement: () -> Unit,
    onToggleGpxTracking: () -> Unit,
    onOpenAddWaypoint: () -> Unit,
    onOpenPatrolForm: () -> Unit,
    onOpenFloraFaunaForm: () -> Unit = {},
    onOpenNaturalImpactForm: () -> Unit = {},
    onOpenDailyJournalForm: () -> Unit = {},
    onOpenGisLayers: () -> Unit,
    onCapturePhoto: () -> Unit = {},
    onSaveManualTrack: (String) -> Unit = {},
    onPreviewTrackLog: (TrackLogUiModel) -> Unit = {},
    onParseTrackPoints: (String) -> List<GpsPoint> = { emptyList() },
    onSetAllWaypointsVisible: (Boolean) -> Unit = {},
    onSetAllTrackLogsVisible: (Boolean) -> Unit = {},
    onSetAllPolygonsVisible: (Boolean) -> Unit = {},
    onSetAllGisLayersVisible: (Boolean) -> Unit = {},
    onDownloadArea: (points: List<GpsPoint>, minZoom: Int, maxZoom: Int, source: String) -> Unit = { _, _, _, _ -> },
    onCancelDownload: () -> Unit = {},
    onOpenCoordConverter: () -> Unit = {},
    importState: com.baoverung.app.ui.MainViewModel.ImportState = com.baoverung.app.ui.MainViewModel.ImportState(),
    userSession: com.baoverung.app.data.model.UserSession,
    provinceName: String = "Lâm Đồng",
    centralMeridian: Double = 107.75,
    zoneDegrees: Int = 3,
    activeCoordinateSystem: String = "VN2000",
    focusRequest: Triple<Double, Double, Float>? = null,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    uiSettings: MapUiSettings = MapUiSettings(),
    modifier: Modifier = Modifier
) {
    val currentOnMapChange by rememberUpdatedState(onMapChange)
    val currentCenterLat by rememberUpdatedState(centerLat)
    val currentCenterLon by rememberUpdatedState(centerLon)
    val currentZoomLevel by rememberUpdatedState(zoomLevel)

    var mapRotation by remember { mutableFloatStateOf(0f) }
    var isSurveyMenuExpanded by remember { mutableStateOf(false) }
    var isJournalMenuExpandedInternal by remember { mutableStateOf(true) }
    var showCompassDialog by remember { mutableStateOf(false) }
    var showSatelliteTab by remember { mutableStateOf(false) }
    
    var isLeftToolbarExpanded by remember { mutableStateOf(true) }
    var isRightToolbarExpanded by remember { mutableStateOf(true) }
    var isCoordInfoExpanded by remember { mutableStateOf(true) }

    // Radar & Scanning Animation
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val radarRadius by infiniteTransition.animateFloat(
        initialValue = 40f,
        targetValue = 120f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radius"
    )
    val radarAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )
    val sweepAngleAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    // Handle incoming focus request
    LaunchedEffect(focusRequest) {
        focusRequest?.let { (lat, lon, zoom) ->
            onMapChange(lat, lon, zoom)
        }
    }

    var selectedFeature by remember { mutableStateOf<GisFeature?>(null) }
    var showMapSourceMenu by remember { mutableStateOf(false) }
    var hasCenteredOnFirstLocation by remember { mutableStateOf(false) }

    val currentGisFeaturesMap by rememberUpdatedState(gisFeaturesMap)

    var selectedWaypointForDetail by remember { mutableStateOf<com.baoverung.app.data.local.entity.WaypointEntity?>(null) }
    var selectedPatrolForDetail by remember { mutableStateOf<com.baoverung.app.data.local.entity.PatrolLogEntity?>(null) }
    var selectedTrackForDetail by remember { mutableStateOf<TrackLogUiModel?>(null) }
    var selectedPolygonForDetail by remember { mutableStateOf<PolygonUiModel?>(null) }
    
    var draggedPointIndex by remember { mutableStateOf(-1) }
    var selectedMeasurementPointIndex by remember { mutableStateOf(-1) }

    // Download Configuration State
    var showDownloadConfig by remember { mutableStateOf(false) }
    var downloadMinZoom by remember { mutableFloatStateOf(10f) }
    var downloadMaxZoom by remember { mutableFloatStateOf(15f) }
    var downloadSource by remember { mutableStateOf(selectedMapSource) }

    // Optimization: Double-Buffered Cache for GIS features
    // ReadyPaths: What we draw NOW. PendingPaths: What we are calculating.
    val readyPaths = remember { mutableStateMapOf<Long, List<Pair<GisFeature, Path>>>() }
    var lastCachedZoom by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(currentGisFeaturesMap, zoomLevel) {
        withContext(Dispatchers.Default) {
            // Requirement 2: Zero Shape Change (epsilon = 0)
            val epsilon = 0f 
            
            // If zoom changed significantly, we'll keep drawing scaled 'readyPaths' 
            // while this background job prepares the new pixel-perfect paths.
            
            val newPathsMap = mutableMapOf<Long, List<Pair<GisFeature, Path>>>()
            
            // Parallel calculation for multiple layers
            currentGisFeaturesMap.entries.chunked(2).forEach { layerBatch ->
                // Process layers in small parallel groups if needed, but here we process all
                for ((layerId, features) in layerBatch) {
                    val layerPaths = mutableListOf<Pair<GisFeature, Path>>()
                    
                    // Progressive batching for UI smoothness
                    features.chunked(300).forEach { chunk ->
                        for (feature in chunk) {
                            if (feature.points.size < 2) continue
                            val path = Path()
                            val offsets = feature.points.map { latLonToWorld(it.latitude, it.longitude, zoomLevel) }
                            
                            path.moveTo(offsets[0].x.toFloat(), offsets[0].y.toFloat())
                            for (i in 1 until offsets.size) {
                                path.lineTo(offsets[i].x.toFloat(), offsets[i].y.toFloat())
                            }
                            if (feature.shapeType == GisShapeType.POLYGON) {
                                path.close()
                            }
                            layerPaths.add(feature to path)
                        }
                        
                        // Show progress for each layer
                        val currentLayerSnapshot = layerPaths.toList()
                        withContext(Dispatchers.Main) {
                            readyPaths[layerId] = currentLayerSnapshot
                        }
                    }
                }
            }
            
            lastCachedZoom = zoomLevel
            
            // Cleanup layers no longer present
            val currentLayerIds = currentGisFeaturesMap.keys
            withContext(Dispatchers.Main) {
                readyPaths.keys.retainAll(currentLayerIds)
            }
        }
    }

    // Auto center map on initial location update
    LaunchedEffect(currentLocation) {
        if (currentLocation != null && !hasCenteredOnFirstLocation) {
            onMapChange(currentLocation.latitude, currentLocation.longitude, 16f)
            hasCenteredOnFirstLocation = true
        }
    }


    // Synchronize map center with GPS if user taps my location
    val currLoc = currentLocation ?: GpsPoint(11.9404, 108.4378)
    val isReadOnly = userSession.permissions == "VIEW_ONLY" || userSession.isOfflineMode
    
    // Optimized pre-fetching for forestry use (Reduced for lighter performance)
    val context = LocalContext.current
    LaunchedEffect(centerLat, centerLon, zoomLevel, selectedMapSource) {
        kotlinx.coroutines.delay(500) // Longer delay to avoid pre-fetching while panning
        
        val z = zoomLevel.toInt().coerceIn(1, 23)
        val centerWorld = latLonToWorld(centerLat, centerLon, z.toFloat())
        val tx = floor(centerWorld.x / 256.0).toInt()
        val ty = floor(centerWorld.y / 256.0).toInt()
        
        val range = -2..2 // 5x5 grid is enough for smoothness without overloading RAM
        
        for (dx in range) {
            for (dy in range) {
                val fetchTx = tx + dx
                val fetchTy = ty + dy
                if (fetchTx < 0 || fetchTy < 0) continue
                
                val tileUrl = when {
                    selectedMapSource.contains("Satellite") -> "https://mt1.google.com/vt/lyrs=s&x=$fetchTx&y=$fetchTy&z=$z"
                    selectedMapSource.contains("Street") || selectedMapSource.contains("Đường") -> "https://mt1.google.com/vt/lyrs=m&x=$fetchTx&y=$fetchTy&z=$z"
                    selectedMapSource.contains("Terrain") -> "https://mt1.google.com/vt/lyrs=p&x=$fetchTx&y=$fetchTy&z=$z"
                    selectedMapSource.contains("Hybrid") -> "https://mt1.google.com/vt/lyrs=y&x=$fetchTx&y=$fetchTy&z=$z"
                    selectedMapSource.contains("Esri") -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$fetchTy/$fetchTx"
                    selectedMapSource.contains("OpenStreetMap") -> "https://tile.openstreetmap.org/$z/$fetchTx/$fetchTy.png"
                    else -> null
                } ?: continue

                val request = ImageRequest.Builder(context)
                    .data(tileUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Android; vToolSurveyGIS/1.0)")
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .precision(coil.size.Precision.INEXACT)
                    .build()
                coil.Coil.imageLoader(context).enqueue(request)
            }
        }
    }
    
    val reusablePath = remember { Path() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("map_screen_box")
    ) {
        val widthPx = with(LocalDensity.current) { this@BoxWithConstraints.maxWidth.toPx() }
        val heightPx = with(LocalDensity.current) { this@BoxWithConstraints.maxHeight.toPx() }

        // 1. Map Container (Always flat 2D/3D, no Globe clip)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {

        // Optimized Viewport bounds for culling (Calculated once per map state change)
        val viewportBounds = remember(centerLat, centerLon, zoomLevel, widthPx, heightPx) {
            val cw = latLonToWorld(centerLat, centerLon, zoomLevel)
            val tl = worldToLatLon(cw.x - widthPx / 2.0, cw.y - heightPx / 2.0, zoomLevel)
            val br = worldToLatLon(cw.x + widthPx / 2.0, cw.y + heightPx / 2.0, zoomLevel)
            
            object {
                val vMinLat = min(tl.first, br.first) - abs(tl.first - br.first) * 0.1
                val vMaxLat = max(tl.first, br.first) + abs(tl.first - br.first) * 0.1
                val vMinLon = min(tl.second, br.second) - abs(br.second - tl.second) * 0.1
                val vMaxLon = max(tl.second, br.second) + abs(br.second - tl.second) * 0.1
                val cwX = cw.x
                val cwY = cw.y
                val minLat = min(tl.first, br.first)
                val maxLat = max(tl.first, br.first)
                val minLon = min(tl.second, br.second)
                val maxLon = max(tl.second, br.second)
            }
        }

        LaunchedEffect(viewportBounds) {
            onUpdateMapBounds(viewportBounds.minLat, viewportBounds.maxLat, viewportBounds.minLon, viewportBounds.maxLon)
        }

        fun intersectsViewport(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): Boolean {
            return !(maxLat < viewportBounds.vMinLat || minLat > viewportBounds.vMaxLat || maxLon < viewportBounds.vMinLon || minLon > viewportBounds.vMaxLon)
        }

        fun latLonToOffset(lat: Double, lon: Double, width: Float, height: Float): Offset {
            val w = latLonToWorld(lat, lon, zoomLevel)
            return Offset((w.x - viewportBounds.cwX + width / 2.0).toFloat(), (w.y - viewportBounds.cwY + height / 2.0).toFloat())
        }

        fun offsetToLatLon(offset: Offset, width: Float, height: Float): GpsPoint {
            val wx = offset.x.toDouble() - width / 2.0 + viewportBounds.cwX
            val wy = offset.y.toDouble() - height / 2.0 + viewportBounds.cwY
            val (lat, lon) = worldToLatLon(wx, wy, zoomLevel)
            return GpsPoint(latitude = lat, longitude = lon)
        }

        // 1. Online Map Tile Background Layer (Google Maps / OpenStreetMap / Satellite)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = mapRotation
                    cameraDistance = 8 * density
                }
        ) {
            OnlineMapTileLayer(
                selectedMapSource = selectedMapSource,
                centerLat = centerLat,
                centerLon = centerLon,
                zoomLevel = zoomLevel,
                widthPx = widthPx,
                heightPx = heightPx
            )

            // 1b. Offline MBTiles Layer
            OfflineMapTileLayer(
                mbtilesReaders = mbtilesReaders,
                gisLayers = gisLayers,
                centerLat = centerLat,
                centerLon = centerLon,
                zoomLevel = zoomLevel,
                widthPx = widthPx,
                heightPx = heightPx
            )
        }

        // 2. Interactive Canvas Overlay for Vectors & Tracking
        val labelPaint = remember {
            android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 28f
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
        }
        val labelOutlinePaint = remember {
            android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 28f
                textAlign = android.graphics.Paint.Align.CENTER
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 4f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
        }
        val rasterBitmaps = remember { mutableMapOf<Long, android.graphics.Bitmap>() }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(measurementMode, measurementPoints, mapRotation) {
                    detectDragGestures(
                        onDragStart = { offset: Offset ->
                            if (measurementMode != MeasurementMode.NONE) {
                                val center = Offset(widthPx / 2f, heightPx / 2f)
                                val rotatedOffset = offset.rotateAround(center, -mapRotation)
                                val idx = measurementPoints.indexOfFirst { pt ->
                                    val pOffset = latLonToOffset(pt.latitude, pt.longitude, widthPx, heightPx)
                                    (pOffset - rotatedOffset).getDistance() < 50f
                                }
                                draggedPointIndex = idx
                            }
                        },
                        onDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Offset ->
                            if (draggedPointIndex != -1) {
                                val center = Offset(widthPx / 2f, heightPx / 2f)
                                val rotatedPos = change.position.rotateAround(center, -mapRotation)
                                val nextPt = offsetToLatLon(rotatedPos, widthPx, heightPx)
                                onUpdateMeasurementPoint(draggedPointIndex, nextPt)
                                change.consume()
                            }
                        },
                        onDragEnd = { draggedPointIndex = -1 },
                        onDragCancel = { draggedPointIndex = -1 }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, rotation ->
                        mapRotation = (mapRotation + rotation) % 360f
                        val nextZoom = (currentZoomLevel * zoom).coerceIn(1f, 23f)
                        
                        // Ultra-precise world coordinate shift
                        val curCenterWorld = latLonToWorld(currentCenterLat, currentCenterLon, nextZoom.coerceIn(1f, 23f))
                        
                        // Transform pan by rotation
                        val angleRad = Math.toRadians(-mapRotation.toDouble())
                        val cosA = cos(angleRad)
                        val sinA = sin(angleRad)
                        val rx = pan.x.toDouble() * cosA - pan.y.toDouble() * sinA
                        val ry = pan.x.toDouble() * sinA + pan.y.toDouble() * cosA
                        
                        val newCenterWorldX = curCenterWorld.x - rx
                        val newCenterWorldY = curCenterWorld.y - ry
                        
                        val (newLat, newLon) = worldToLatLon(newCenterWorldX, newCenterWorldY, nextZoom)
                        currentOnMapChange(newLat, newLon, nextZoom)
                    }
                }
                .pointerInput(measurementMode, currentGisFeaturesMap, measurementPoints, mapRotation) {
                    detectTapGestures(
                        onTap = { offset ->
                            val center = Offset(widthPx / 2f, heightPx / 2f)
                            val rotatedOffset = offset.rotateAround(center, -mapRotation)
                            val clickedPt = offsetToLatLon(rotatedOffset, widthPx, heightPx)
                            if (measurementMode != MeasurementMode.NONE) {
                                // First check if tapping near an existing point to select it
                                val nearIdx = measurementPoints.indexOfFirst { pt ->
                                    val pOffset = latLonToOffset(pt.latitude, pt.longitude, widthPx, heightPx)
                                    (pOffset - rotatedOffset).getDistance() < 40f
                                }
                                
                                if (nearIdx != -1) {
                                    selectedMeasurementPointIndex = if (selectedMeasurementPointIndex == nearIdx) -1 else nearIdx
                                } else {
                                    onAddMeasurementPoint(clickedPt)
                                    selectedMeasurementPointIndex = -1
                                }
                            } else {
                                var found: GisFeature? = null
                                // Search in GIS features
                                for ((_, features) in currentGisFeaturesMap) {
                                    for (feat in features) {
                                        // Optimization: Fast Bounding Box Check
                                        if (clickedPt.latitude < feat.minLat || clickedPt.latitude > feat.maxLat ||
                                            clickedPt.longitude < feat.minLon || clickedPt.longitude > feat.maxLon) continue

                                        when (feat.shapeType) {
                                            GisShapeType.POINT -> {
                                                if (feat.points.isNotEmpty()) {
                                                    val pOffset = latLonToOffset(feat.points[0].latitude, feat.points[0].longitude, widthPx, heightPx)
                                                    if ((pOffset - rotatedOffset).getDistance() < 40f) {
                                                        found = feat
                                                        break
                                                    }
                                                }
                                            }
                                            GisShapeType.LINE -> {
                                                val pts = feat.points
                                                for (i in 0 until pts.size - 1) {
                                                    val p1 = latLonToOffset(pts[i].latitude, pts[i].longitude, widthPx, heightPx)
                                                    val p2 = latLonToOffset(pts[i+1].latitude, pts[i+1].longitude, widthPx, heightPx)
                                                    if (com.baoverung.app.util.GeometryUtils.distToSegment(rotatedOffset, p1, p2) < 25f) {
                                                        found = feat
                                                        break
                                                    }
                                                }
                                            }
                                            GisShapeType.POLYGON -> {
                                                val screenPoints = feat.points.map { latLonToOffset(it.latitude, it.longitude, widthPx, heightPx) }
                                                if (com.baoverung.app.util.GeometryUtils.isPointInPolygon(rotatedOffset, screenPoints)) {
                                                    found = feat
                                                    break
                                                }
                                                // Also check boundary for thin polygons
                                                for (i in 0 until screenPoints.size) {
                                                    val p1 = screenPoints[i]
                                                    val p2 = screenPoints[(i + 1) % screenPoints.size]
                                                    if (com.baoverung.app.util.GeometryUtils.distToSegment(rotatedOffset, p1, p2) < 20f) {
                                                        found = feat
                                                        break
                                                    }
                                                }
                                            }
                                        }
                                        if (found != null) break
                                    }
                                    if (found != null) break
                                }
                                selectedFeature = found
                                
                                if (found == null) {
                                    // Search for nearby Map Items
                                    var foundWp: com.baoverung.app.data.local.entity.WaypointEntity? = null
                                    for (wp in waypoints) {
                                        if (hiddenWaypointIds.contains(wp.id)) continue
                                        val pOffset = latLonToOffset(wp.latitude, wp.longitude, widthPx, heightPx)
                                        if ((pOffset - rotatedOffset).getDistance() < 30f) {
                                            foundWp = wp
                                            break
                                        }
                                    }
                                    if (foundWp != null) {
                                        selectedWaypointForDetail = foundWp
                                    } else {
                                        var foundPl: com.baoverung.app.data.local.entity.PatrolLogEntity? = null
                                        for (pl in patrolLogs) {
                                            if (hiddenPatrolLogIds.contains(pl.id)) continue
                                            val pOffset = latLonToOffset(pl.latitude, pl.longitude, widthPx, heightPx)
                                            if ((pOffset - rotatedOffset).getDistance() < 30f) {
                                                foundPl = pl
                                                break
                                            }
                                        }
                                        if (foundPl != null) {
                                            selectedPatrolForDetail = foundPl
                                        } else {
                                            // Search for nearby Track segments
                                            var foundTrk: TrackLogUiModel? = null
                                            for (trk in trackLogs) {
                                                if (hiddenTrackLogIds.contains(trk.id)) continue
                                                val pts = trk.fullPoints
                                                if (pts.size < 2) continue
                                                
                                                for (i in 0 until pts.size - 1) {
                                                    val p1 = latLonToOffset(pts[i].latitude, pts[i].longitude, widthPx, heightPx)
                                                    val p2 = latLonToOffset(pts[i+1].latitude, pts[i+1].longitude, widthPx, heightPx)
                                                    
                                                    // Distance from point to line segment
                                                    val dist = com.baoverung.app.util.GeometryUtils.distToSegment(rotatedOffset, p1, p2)
                                                    if (dist < 20f) {
                                                        foundTrk = trk
                                                        break
                                                    }
                                                }
                                                if (foundTrk != null) break
                                            }
                                            if (foundTrk != null) {
                                                selectedTrackForDetail = foundTrk
                                            } else {
                                                // Search for nearby Polygons
                                                var foundPoly: PolygonUiModel? = null
                                                for (poly in polygons) {
                                                    if (hiddenPolygonIds.contains(poly.id)) continue
                                                    if (poly.points.size < 3) continue
                                                    
                                                    // Point in polygon test
                                                    if (com.baoverung.app.util.GeometryUtils.isPointInPolygon(rotatedOffset, poly.points.map { latLonToOffset(it.latitude, it.longitude, widthPx, heightPx) })) {
                                                        foundPoly = poly
                                                        break
                                                    }
                                                }
                                                if (foundPoly != null) {
                                                    selectedPolygonForDetail = foundPoly
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
        ) {
            val width = size.width
            val height = size.height
            val canvasCenter = Offset(width / 2f, height / 2f)

            rotate(mapRotation, canvasCenter) {
                // 1. Render Raster Layers (.tif, .jpg, .png)
                for (layer in gisLayers) {
                    if (!layer.isVisible || layer.fileType !in listOf("TIF", "JPG", "PNG", "JPEG")) continue
                    
                    val bitmap = rasterBitmaps.getOrPut(layer.id) {
                        try {
                            android.graphics.BitmapFactory.decodeFile(layer.filePath)
                        } catch (e: Exception) { null } ?: return@getOrPut null!! 
                    } ?: continue
                    
                    val info = com.baoverung.app.gis.RasterParser.getRasterInfo(File(layer.filePath)) ?: continue
                    
                    if (!intersectsViewport(info.bottomRight.latitude, info.topLeft.latitude, info.topLeft.longitude, info.bottomRight.longitude)) continue

                    val topLeft = latLonToOffset(info.topLeft.latitude, info.topLeft.longitude, width, height)
                    val bottomRight = latLonToOffset(info.bottomRight.latitude, info.bottomRight.longitude, width, height)
                    
                    drawContext.canvas.nativeCanvas.drawBitmap(
                        bitmap,
                        null,
                        android.graphics.RectF(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y),
                        android.graphics.Paint().apply { alpha = (layer.opacity * 255).toInt() }
                    )
                }

                // 2a. Render GIS Vector Layers (.shp, .kml, .tab, .geojson) - DOUBLE BUFFERED
                for (layer in gisLayers) {
                    if (!layer.isVisible) continue
                    val layerData = readyPaths[layer.id] ?: emptyList()
                    val strokeColor = try {
                        Color(android.graphics.Color.parseColor(layer.strokeColorHex))
                    } catch (e: Exception) {
                        Color(0xFF2E7D32)
                    }
                    val fillColor = strokeColor.copy(alpha = layer.opacity * 0.3f)
                    val strokeStyle = Stroke(width = 3f, pathEffect = getPathEffect(uiSettings.lineStyle))

                    // Requirement: High Smoothness during Zoom
                    // If zoom is changing, we draw the last valid cache with a scale factor
                    val zoomRatio = if (abs(zoomLevel - lastCachedZoom) > 0.001f) {
                        2.0.pow((zoomLevel - lastCachedZoom).toDouble()).toFloat()
                    } else 1.0f

                    for (entry in layerData) {
                        val (feature, path) = entry
                        // Fast Viewport Culling
                        if (!intersectsViewport(feature.minLat, feature.maxLat, feature.minLon, feature.maxLon)) continue

                        withTransform({
                            // Transform from World Coords to Screen Coords
                            translate(
                                left = -viewportBounds.cwX.toFloat() + width / 2f,
                                top = -viewportBounds.cwY.toFloat() + height / 2f
                            )
                            // If calculating new paths, scale the existing ones for visual continuity
                            if (zoomRatio != 1.0f) {
                                scale(zoomRatio, zoomRatio, pivot = Offset(viewportBounds.cwX.toFloat(), viewportBounds.cwY.toFloat()))
                            }
                        }) {
                            if (feature.shapeType == GisShapeType.POLYGON) {
                                drawPath(path, fillColor)
                            }
                            drawPath(path, strokeColor.copy(alpha = layer.opacity), style = strokeStyle)
                        }

                        // Requirement: Labels visible at all zoom, no area filtering
                        if (uiSettings.showLabelsGlobal && !layer.labelColumn.isNullOrEmpty() && zoomLevel >= 10.0f) {
                            val label = feature.attributes[layer.labelColumn]
                            if (!label.isNullOrEmpty()) {
                                val offset = latLonToOffset(feature.centroidLat, feature.centroidLon, width, height)
                                
                                // Scale label size slightly with zoom for professional look
                                val currentLabelSize = (uiSettings.pointFontSize * (if (zoomLevel < 13f) 0.6f else 1.0f)).coerceAtLeast(6f)
                                labelPaint.textSize = currentLabelSize * 2f
                                labelOutlinePaint.textSize = currentLabelSize * 2f
                                
                                drawContext.canvas.nativeCanvas.drawText(label, offset.x, offset.y, labelOutlinePaint)
                                drawContext.canvas.nativeCanvas.drawText(label, offset.x, offset.y, labelPaint)
                            }
                        }
                    }
                }

                // 2b. Render Saved TrackLogs & Lines - OPTIMIZED
                for (trk in trackLogs) {
                    if (hiddenTrackLogIds.contains(trk.id)) continue
                    if (!intersectsViewport(trk.minLat, trk.maxLat, trk.minLon, trk.maxLon)) continue

                    val isGpx = trk.category == "GPX"
                    if (isGpx && !uiSettings.showTracklogsGlobal) continue
                    if (!isGpx && !uiSettings.showLinesGlobal) continue

                    val points = if (zoomLevel > 16f) trk.fullPoints else trk.sampledPoints
                    if (points.size >= 2) {
                        reusablePath.reset()
                        val p0 = latLonToOffset(points[0].latitude, points[0].longitude, width, height)
                        reusablePath.moveTo(p0.x, p0.y)
                        for (i in 1 until points.size) {
                            val pi = latLonToOffset(points[i].latitude, points[i].longitude, width, height)
                            reusablePath.lineTo(pi.x, pi.y)
                        }
                        
                        val color = try { Color(android.graphics.Color.parseColor(trk.displayColorHex)) } catch (e: Exception) { 
                            Color(android.graphics.Color.parseColor(if (isGpx) uiSettings.tracklogColor else uiSettings.lineColor)) 
                        }
                        
                        val strokeWidth = if (isGpx) uiSettings.tracklogWidth else uiSettings.lineWidth
                        val style = if (isGpx) uiSettings.tracklogStyle else uiSettings.lineStyle
                        val pathEffect = getPathEffect(style)
                        
                        drawPath(reusablePath, color.copy(alpha = 0.8f), style = Stroke(width = strokeWidth * 2f, pathEffect = pathEffect))
                        
                        val showLabel = uiSettings.showLabelsGlobal && (if (isGpx) uiSettings.showTracklogLabels else uiSettings.showLineLabels)
                        if (showLabel) {
                             val midPt = points[points.size / 2]
                             val offset = latLonToOffset(midPt.latitude, midPt.longitude, width, height)
                             val showVal = if (isGpx) uiSettings.showTracklogValue else uiSettings.showLineValue
                             val label = if (showVal) "${trk.title} (${String.format("%.1f km", trk.totalDistanceMeters/1000)})" else trk.title
                             val fontSize = if (isGpx) uiSettings.tracklogFontSize else uiSettings.lineFontSize
                             labelPaint.textSize = fontSize.toFloat() * 2f
                             labelOutlinePaint.textSize = fontSize.toFloat() * 2f
                             drawContext.canvas.nativeCanvas.drawText(label, offset.x, offset.y, labelOutlinePaint)
                             drawContext.canvas.nativeCanvas.drawText(label, offset.x, offset.y, labelPaint)
                        }
                    }
                }

                // 2c. Render Saved Waypoints (Images, Points & Landmarks) - OPTIMIZED
                for (wp in waypoints) {
                    if (hiddenWaypointIds.contains(wp.id)) continue
                    if (wp.latitude < viewportBounds.vMinLat || wp.latitude > viewportBounds.vMaxLat || wp.longitude < viewportBounds.vMinLon || wp.longitude > viewportBounds.vMaxLon) continue

                    val isImage = !wp.photoPath.isNullOrEmpty()
                    val isLandmark = wp.category == "Mốc tọa độ" || wp.category == "Landmark"
                    
                    if (isImage && !uiSettings.showImagesGlobal) continue
                    if (!isImage && !uiSettings.showPointsGlobal) continue
                    // For now Landmarks share showPointsGlobal or you can add showLandmarksGlobal

                    val p = latLonToOffset(wp.latitude, wp.longitude, width, height)
                    
                    val iconColor = try { 
                        Color(android.graphics.Color.parseColor(wp.displayColorHex))
                    } catch (e: Exception) { 
                        when {
                            isLandmark -> Color(android.graphics.Color.parseColor(uiSettings.landmarkColor))
                            isImage -> Color(android.graphics.Color.parseColor(uiSettings.imageColor))
                            else -> Color(0xFF1976D2)
                        }
                    }
                    
                    val iconSize = when {
                        isLandmark -> uiSettings.landmarkIconSize.toFloat()
                        isImage -> uiSettings.imageIconSize.toFloat()
                        else -> uiSettings.pointIconSize.toFloat()
                    }
                    
                    val iconType = when {
                        isLandmark -> uiSettings.landmarkIconType
                        isImage -> uiSettings.imageIconType
                        else -> uiSettings.pointIconType
                    }
                    
                    rotate(-mapRotation, p) {
                        when (iconType) {
                            "camera" -> {
                                val camRect = androidx.compose.ui.geometry.Rect(p.x - iconSize * 0.5f, p.y - iconSize * 0.3f, p.x + iconSize * 0.5f, p.y + iconSize * 0.3f)
                                drawRoundRect(iconColor, camRect.topLeft, camRect.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
                                drawCircle(Color.White, radius = iconSize * 0.2f, center = p)
                                drawCircle(iconColor, radius = iconSize * 0.1f, center = p)
                            }
                            "picture", "gallery", "photo" -> {
                                val rect = androidx.compose.ui.geometry.Rect(p.x - iconSize * 0.4f, p.y - iconSize * 0.4f, p.x + iconSize * 0.4f, p.y + iconSize * 0.4f)
                                drawRect(Color.White, rect.topLeft, rect.size)
                                drawRect(iconColor, rect.topLeft, rect.size, style = Stroke(width = 2f))
                                drawCircle(iconColor, radius = iconSize * 0.12f, center = Offset(p.x - iconSize * 0.15f, p.y - iconSize * 0.15f))
                                val triPath = Path()
                                triPath.moveTo(rect.left + 6f, rect.bottom - 6f)
                                triPath.lineTo(p.x, p.y + 4f)
                                triPath.lineTo(rect.right - 6f, rect.bottom - 6f)
                                triPath.close()
                                drawPath(triPath, iconColor)
                            }
                            "lens" -> {
                                drawCircle(iconColor, radius = iconSize * 0.45f, center = p, style = Stroke(width = 5f))
                                drawCircle(iconColor, radius = iconSize * 0.25f, center = p)
                            }
                            "tree" -> {
                                // Forestry Standard Conifer Tree (3 Tiers)
                                val trunkW = iconSize * 0.12f
                                val trunkH = iconSize * 0.25f
                                drawRect(
                                    color = Color(0xFF5D4037),
                                    topLeft = Offset(p.x - trunkW/2, p.y + iconSize * 0.15f),
                                    size = androidx.compose.ui.geometry.Size(trunkW, trunkH)
                                )
                                
                                val treePath = Path()
                                // Top Tier
                                treePath.moveTo(p.x, p.y - iconSize * 0.5f)
                                treePath.lineTo(p.x - iconSize * 0.25f, p.y - iconSize * 0.2f)
                                treePath.lineTo(p.x + iconSize * 0.25f, p.y - iconSize * 0.2f)
                                treePath.close()
                                
                                // Middle Tier
                                treePath.moveTo(p.x, p.y - iconSize * 0.3f)
                                treePath.lineTo(p.x - iconSize * 0.35f, p.y)
                                treePath.lineTo(p.x + iconSize * 0.35f, p.y)
                                treePath.close()
                                
                                // Bottom Tier
                                treePath.moveTo(p.x, p.y - iconSize * 0.1f)
                                treePath.lineTo(p.x - iconSize * 0.45f, p.y + iconSize * 0.2f)
                                treePath.lineTo(p.x + iconSize * 0.45f, p.y + iconSize * 0.2f)
                                treePath.close()
                                
                                drawPath(treePath, iconColor)
                                drawPath(treePath, Color.White.copy(alpha = 0.4f), style = Stroke(width = 1.5f))
                            }
                            "star" -> {
                                // Vibrant 5-Pointed Star
                                val starPath = Path()
                                val outR = iconSize * 0.55f
                                val innR = iconSize * 0.22f
                                for (i in 0 until 10) {
                                    val r = if (i % 2 == 0) outR else innR
                                    val ang = Math.toRadians((i * 36 - 90).toDouble())
                                    val sx = p.x + (r * cos(ang)).toFloat()
                                    val sy = p.y + (r * sin(ang)).toFloat()
                                    if (i == 0) starPath.moveTo(sx, sy) else starPath.lineTo(sx, sy)
                                }
                                starPath.close()
                                
                                drawPath(starPath, Color.White, style = Stroke(width = 5f))
                                drawPath(starPath, iconColor)
                                drawPath(starPath, Color.Black.copy(alpha = 0.2f), style = Stroke(width = 1f))
                            }
                            "flag" -> {
                                // Forestry Landmark Flag
                                drawLine(
                                    color = Color.Black,
                                    start = Offset(p.x - iconSize * 0.15f, p.y + iconSize * 0.45f),
                                    end = Offset(p.x - iconSize * 0.15f, p.y - iconSize * 0.45f),
                                    strokeWidth = 4f
                                )
                                
                                val fPath = Path()
                                fPath.moveTo(p.x - iconSize * 0.15f, p.y - iconSize * 0.45f)
                                fPath.lineTo(p.x + iconSize * 0.45f, p.y - iconSize * 0.25f)
                                fPath.lineTo(p.x - iconSize * 0.15f, p.y - iconSize * 0.05f)
                                fPath.close()
                                
                                drawPath(fPath, iconColor)
                                drawPath(fPath, Color.White, style = Stroke(width = 2f))
                            }
                            "square" -> {
                                val rect = androidx.compose.ui.geometry.Rect(p.x - iconSize * 0.3f, p.y - iconSize * 0.3f, p.x + iconSize * 0.3f, p.y + iconSize * 0.3f)
                                drawRect(Color.White, rect.topLeft, rect.size)
                                drawRect(iconColor, rect.topLeft, rect.size, style = Stroke(width = 4f))
                                drawRect(iconColor.copy(alpha = 0.5f), rect.topLeft, rect.size)
                            }
                            else -> {
                                drawCircle(Color.White, radius = iconSize * 0.35f, center = p)
                                drawCircle(iconColor, radius = iconSize * 0.25f, center = p)
                            }
                        }
                    }
                    
                    val showLabel = uiSettings.showLabelsGlobal && when {
                        isLandmark -> uiSettings.showLandmarkLabels
                        isImage -> uiSettings.showImageLabels
                        else -> uiSettings.showPointLabels
                    }
                    
                    if (showLabel) {
                        val fontSize = when {
                            isLandmark -> uiSettings.landmarkLabelSize
                            isImage -> uiSettings.imageFontSize
                            else -> uiSettings.pointFontSize
                        }
                        labelPaint.textSize = fontSize.toFloat() * 2f
                        labelOutlinePaint.textSize = fontSize.toFloat() * 2f
                        drawContext.canvas.nativeCanvas.drawText(wp.title, p.x, p.y + iconSize * 0.8f, labelOutlinePaint)
                        drawContext.canvas.nativeCanvas.drawText(wp.title, p.x, p.y + iconSize * 0.8f, labelPaint)
                    }
                }

                // 2d. Render Saved Polygons - OPTIMIZED
                if (uiSettings.showPolygonsGlobal) {
                    for (poly in polygons) {
                        if (hiddenPolygonIds.contains(poly.id)) continue
                        if (!intersectsViewport(poly.minLat, poly.maxLat, poly.minLon, poly.maxLon)) continue

                        val points = poly.points
                        if (points.size >= 3) {
                            reusablePath.reset()
                            val p0 = latLonToOffset(points[0].latitude, points[0].longitude, width, height)
                            reusablePath.moveTo(p0.x, p0.y)
                            for (i in 1 until points.size) {
                                val pi = latLonToOffset(points[i].latitude, points[i].longitude, width, height)
                                reusablePath.lineTo(pi.x, pi.y)
                            }
                            reusablePath.close()
                            
                            val strokeColor = try { Color(android.graphics.Color.parseColor(poly.displayColorHex)) } catch (e: Exception) { Color(android.graphics.Color.parseColor(uiSettings.polygonBoundaryColor)) }
                            val fillColor = Color(android.graphics.Color.parseColor(uiSettings.polygonFillColor)).copy(alpha = 0.3f)
                            
                            val pathEffect = getPathEffect(uiSettings.polygonStyle)
                            
                            drawPath(reusablePath, fillColor)
                            drawPath(reusablePath, strokeColor, style = Stroke(width = uiSettings.polygonWidth * 2f, pathEffect = pathEffect))

                            if (uiSettings.showLabelsGlobal && uiSettings.showPolygonLabels) {
                                 val offset = latLonToOffset(poly.centroidLat, poly.centroidLon, width, height)
                                 val label = if (uiSettings.showPolygonValue) "${poly.title} (${com.baoverung.app.gis.GisAreaCalculator.formatArea(poly.areaSquareMeters)})" else poly.title
                                 labelPaint.textSize = uiSettings.polygonFontSize.toFloat() * 2f
                                 labelOutlinePaint.textSize = uiSettings.polygonFontSize.toFloat() * 2f
                                 drawContext.canvas.nativeCanvas.drawText(label, offset.x, offset.y, labelOutlinePaint)
                                 drawContext.canvas.nativeCanvas.drawText(label, offset.x, offset.y, labelPaint)
                            }
                        }
                    }
                }

                // 2e. Render Patrol Logs - OPTIMIZED
                if (uiSettings.showIncidentsGlobal) {
                    for (pl in patrolLogs) {
                        if (hiddenPatrolLogIds.contains(pl.id)) continue
                        if (pl.latitude < viewportBounds.vMinLat || pl.latitude > viewportBounds.vMaxLat || pl.longitude < viewportBounds.vMinLon || pl.longitude > viewportBounds.vMaxLon) continue

                        val incidentColor = try { 
                            Color(android.graphics.Color.parseColor(pl.displayColorHex))
                        } catch (e: Exception) { 
                            Color(0xFFD32F2F)
                        }
                        val incidentIconSize = uiSettings.incidentIconSize.toFloat()

                        val p = latLonToOffset(pl.latitude, pl.longitude, width, height)
                        
                        rotate(-mapRotation, p) {
                            when (uiSettings.incidentIconType) {
                                "a4", "report", "contract" -> {
                                    val rect = androidx.compose.ui.geometry.Rect(p.x - incidentIconSize * 0.35f, p.y - incidentIconSize * 0.45f, p.x + incidentIconSize * 0.35f, p.y + incidentIconSize * 0.45f)
                                    drawRect(Color.White, rect.topLeft, rect.size)
                                    drawRect(incidentColor, rect.topLeft, rect.size, style = Stroke(width = 3f))
                                    for (i in 0..3) {
                                        val lineY = rect.top + incidentIconSize * 0.2f + i * incidentIconSize * 0.18f
                                        drawLine(incidentColor, Offset(rect.left + 8f, lineY), Offset(rect.right - 8f, lineY), strokeWidth = 2f)
                                    }
                                }
                                "note", "ledger" -> {
                                    val rect = androidx.compose.ui.geometry.Rect(p.x - incidentIconSize * 0.4f, p.y - incidentIconSize * 0.4f, p.x + incidentIconSize * 0.4f, p.y + incidentIconSize * 0.4f)
                                    drawRoundRect(incidentColor, rect.topLeft, rect.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f))
                                    drawLine(Color.White, Offset(rect.left + 8f, p.y), Offset(rect.right - 8f, p.y), strokeWidth = 4f)
                                    drawLine(Color.White, Offset(p.x, rect.top + 8f), Offset(p.x, rect.bottom - 8f), strokeWidth = 4f)
                                }
                                "notebook" -> {
                                    val bookWidth = incidentIconSize * 0.75f
                                    val bookHeight = incidentIconSize
                                    val rect = androidx.compose.ui.geometry.Rect(p.x - bookWidth/2, p.y - bookHeight/2, p.x + bookWidth/2, p.y + bookHeight/2)
                                    drawRoundRect(Color.White, rect.topLeft, rect.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
                                    drawRoundRect(incidentColor, rect.topLeft, rect.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f), style = Stroke(width = 2f))
                                    for (i in 0..3) {
                                        drawCircle(incidentColor, radius = 2f, center = Offset(rect.left + 4f, rect.top + 6f + i * 7f))
                                    }
                                }
                                "alert" -> {
                                    val trianglePath = Path()
                                    trianglePath.moveTo(p.x, p.y - incidentIconSize * 0.5f)
                                    trianglePath.lineTo(p.x - incidentIconSize * 0.5f, p.y + incidentIconSize * 0.4f)
                                    trianglePath.lineTo(p.x + incidentIconSize * 0.5f, p.y + incidentIconSize * 0.4f)
                                    trianglePath.close()
                                    drawPath(trianglePath, incidentColor)
                                    drawCircle(Color.White, radius = 3f, center = Offset(p.x, p.y + incidentIconSize * 0.2f))
                                    drawLine(Color.White, Offset(p.x, p.y - incidentIconSize * 0.2f), Offset(p.x, p.y + incidentIconSize * 0.05f), strokeWidth = 4f)
                                }
                                "camera" -> {
                                    val camRect = androidx.compose.ui.geometry.Rect(p.x - incidentIconSize * 0.5f, p.y - incidentIconSize * 0.3f, p.x + incidentIconSize * 0.5f, p.y + incidentIconSize * 0.3f)
                                    drawRoundRect(incidentColor, camRect.topLeft, camRect.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
                                    drawCircle(Color.White, radius = incidentIconSize * 0.2f, center = p)
                                    drawCircle(incidentColor, radius = incidentIconSize * 0.1f, center = p)
                                }
                                "pin" -> {
                                    val pinPath = Path()
                                    pinPath.moveTo(p.x, p.y)
                                    pinPath.cubicTo(p.x - incidentIconSize * 0.4f, p.y - incidentIconSize * 0.8f, p.x + incidentIconSize * 0.4f, p.y - incidentIconSize * 0.8f, p.x, p.y)
                                    drawPath(pinPath, incidentColor)
                                    drawCircle(Color.White, radius = incidentIconSize * 0.15f, center = Offset(p.x, p.y - incidentIconSize * 0.45f))
                                }
                                else -> {
                                    drawCircle(incidentColor, radius = incidentIconSize * 0.4f, center = p)
                                    drawCircle(Color.White, radius = incidentIconSize * 0.2f, center = p)
                                }
                            }
                        }
                        
                        if (uiSettings.showLabelsGlobal && uiSettings.showIncidentLabels) {
                            labelPaint.textSize = uiSettings.incidentFontSize.toFloat() * 2f
                            labelOutlinePaint.textSize = uiSettings.incidentFontSize.toFloat() * 2f
                            drawContext.canvas.nativeCanvas.drawText(pl.incidentType, p.x, p.y + incidentIconSize * 0.8f, labelOutlinePaint)
                            drawContext.canvas.nativeCanvas.drawText(pl.incidentType, p.x, p.y + incidentIconSize * 0.8f, labelPaint)
                        }
                    }
                }

                // 2f. Render Flora/Fauna Logs
                if (uiSettings.showFloraFaunaGlobal) {
                    for (ff in floraFaunaLogs) {
                        if (hiddenFloraFaunaIds.contains(ff.id)) continue
                        if (ff.latitude < viewportBounds.vMinLat || ff.latitude > viewportBounds.vMaxLat || ff.longitude < viewportBounds.vMinLon || ff.longitude > viewportBounds.vMaxLon) continue
                        val ffColor = try { 
                            Color(android.graphics.Color.parseColor(ff.displayColorHex)) 
                        } catch (e: Exception) { 
                            Color(android.graphics.Color.parseColor(uiSettings.floraFaunaColor)) 
                        }
                        val ffIconSize = uiSettings.floraFaunaIconSize.toFloat()
                        val p = latLonToOffset(ff.latitude, ff.longitude, width, height)
                        
                        rotate(-mapRotation, p) {
                            when (uiSettings.floraFaunaIconType) {
                                "tree", "forest" -> {
                                    val treePath = Path()
                                    treePath.moveTo(p.x, p.y - ffIconSize * 0.5f)
                                    treePath.lineTo(p.x - ffIconSize * 0.4f, p.y + ffIconSize * 0.4f)
                                    treePath.lineTo(p.x + ffIconSize * 0.4f, p.y + ffIconSize * 0.4f)
                                    treePath.close()
                                    drawPath(treePath, ffColor)
                                }
                                "eco", "nature", "grass" -> {
                                    val rect = androidx.compose.ui.geometry.Rect(p.x - ffIconSize * 0.4f, p.y - ffIconSize * 0.4f, p.x + ffIconSize * 0.4f, p.y + ffIconSize * 0.4f)
                                    drawRoundRect(ffColor, rect.topLeft, rect.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f))
                                    // Draw a leaf-like line inside
                                    drawLine(Color.White, Offset(p.x - ffIconSize * 0.2f, p.y + ffIconSize * 0.2f), Offset(p.x + ffIconSize * 0.2f, p.y - ffIconSize * 0.2f), strokeWidth = 3f)
                                }
                                else -> {
                                    drawCircle(ffColor, radius = ffIconSize * 0.3f, center = p)
                                }
                            }
                        }
                        if (uiSettings.showLabelsGlobal && uiSettings.showFloraFaunaLabels) {
                            labelPaint.textSize = uiSettings.floraFaunaFontSize.toFloat() * 2f
                            labelOutlinePaint.textSize = uiSettings.floraFaunaFontSize.toFloat() * 2f
                            drawContext.canvas.nativeCanvas.drawText(ff.appearanceDescription, p.x, p.y + ffIconSize * 0.8f, labelOutlinePaint)
                            drawContext.canvas.nativeCanvas.drawText(ff.appearanceDescription, p.x, p.y + ffIconSize * 0.8f, labelPaint)
                        }
                    }
                }

                // 2g. Render Natural Impact Logs
                if (uiSettings.showNaturalImpactGlobal) {
                    for (ni in naturalImpactLogs) {
                        if (hiddenNaturalImpactIds.contains(ni.id)) continue
                        if (ni.latitude < viewportBounds.vMinLat || ni.latitude > viewportBounds.vMaxLat || ni.longitude < viewportBounds.vMinLon || ni.longitude > viewportBounds.vMaxLon) continue
                        val niColor = try { 
                            Color(android.graphics.Color.parseColor(ni.displayColorHex)) 
                        } catch (e: Exception) { 
                            Color(android.graphics.Color.parseColor(uiSettings.naturalImpactColor)) 
                        }
                        val niIconSize = uiSettings.naturalImpactIconSize.toFloat()
                        val p = latLonToOffset(ni.latitude, ni.longitude, width, height)
                        
                        rotate(-mapRotation, p) {
                            when (uiSettings.naturalImpactIconType) {
                                "warning", "alert", "fire", "storm" -> {
                                    val triPath = Path()
                                    triPath.moveTo(p.x, p.y - niIconSize * 0.5f)
                                    triPath.lineTo(p.x - niIconSize * 0.45f, p.y + niIconSize * 0.4f)
                                    triPath.lineTo(p.x + niIconSize * 0.45f, p.y + niIconSize * 0.4f)
                                    triPath.close()
                                    drawPath(triPath, niColor)
                                    // exclamation mark
                                    drawCircle(Color.White, radius = 3f, center = Offset(p.x, p.y + niIconSize * 0.2f))
                                    drawLine(Color.White, Offset(p.x, p.y - niIconSize * 0.2f), Offset(p.x, p.y + niIconSize * 0.05f), strokeWidth = 4f)
                                }
                                else -> {
                                    drawRect(niColor, Offset(p.x - niIconSize * 0.35f, p.y - niIconSize * 0.35f), androidx.compose.ui.geometry.Size(niIconSize * 0.7f, niIconSize * 0.7f))
                                }
                            }
                        }
                        if (uiSettings.showLabelsGlobal && uiSettings.showNaturalImpactLabels) {
                            val label = if(ni.cause=="Khác") ni.otherCause else ni.cause
                            labelPaint.textSize = uiSettings.naturalImpactFontSize.toFloat() * 2f
                            labelOutlinePaint.textSize = uiSettings.naturalImpactFontSize.toFloat() * 2f
                            drawContext.canvas.nativeCanvas.drawText(label, p.x, p.y + niIconSize * 0.8f, labelOutlinePaint)
                            drawContext.canvas.nativeCanvas.drawText(label, p.x, p.y + niIconSize * 0.8f, labelPaint)
                        }
                    }
                }

                // 3. Render Active GPX Trackline
                if (trackedPoints.size >= 2 && uiSettings.showTracklogsGlobal) {
                    reusablePath.reset()
                    val p0 = latLonToOffset(trackedPoints[0].latitude, trackedPoints[0].longitude, width, height)
                    reusablePath.moveTo(p0.x, p0.y)

                    for (i in 1 until trackedPoints.size) {
                        val pi = latLonToOffset(trackedPoints[i].latitude, trackedPoints[i].longitude, width, height)
                        reusablePath.lineTo(pi.x, pi.y)
                    }
                    drawPath(reusablePath, Color(0xFFFF3D00), style = Stroke(width = 10f))
                }

                // Render Preview Track Log (Imported or Opened Route)
                if (previewTrackPoints.size >= 2) {
                    reusablePath.reset()
                    val p0 = latLonToOffset(previewTrackPoints[0].latitude, previewTrackPoints[0].longitude, width, height)
                    reusablePath.moveTo(p0.x, p0.y)

                    for (i in 1 until previewTrackPoints.size) {
                        val pi = latLonToOffset(previewTrackPoints[i].latitude, previewTrackPoints[i].longitude, width, height)
                        reusablePath.lineTo(pi.x, pi.y)
                    }
                    drawPath(reusablePath, Color(0xFF00E5FF), style = Stroke(width = 12f))
                    val pEnd = latLonToOffset(previewTrackPoints.last().latitude, previewTrackPoints.last().longitude, width, height)
                    drawCircle(Color(0xFF2E7D32), radius = 10f, center = p0)
                    drawCircle(Color(0xFFD84315), radius = 10f, center = pEnd)
                }

                // 4. Render Measurement Overlay
                if (measurementPoints.isNotEmpty()) {
                    val isDownload = measurementMode == MeasurementMode.MAP_DOWNLOAD
                    val color = if (isDownload) Color.Blue else Color(0xFF1976D2)
                    
                    reusablePath.reset()
                    val p0 = latLonToOffset(measurementPoints[0].latitude, measurementPoints[0].longitude, width, height)
                    reusablePath.moveTo(p0.x, p0.y)

                    for (i in 1 until measurementPoints.size) {
                        val pi = latLonToOffset(measurementPoints[i].latitude, measurementPoints[i].longitude, width, height)
                        reusablePath.lineTo(pi.x, pi.y)
                        val isSelected = i == selectedMeasurementPointIndex
                        drawCircle(if (isSelected) Color.Red else color, radius = if (isSelected) 12f else 8f, center = pi)
                    }
                    val isP0Selected = selectedMeasurementPointIndex == 0
                    drawCircle(if (isP0Selected) Color.Red else color, radius = if (isP0Selected) 12f else 8f, center = p0)

                    if ((measurementMode == MeasurementMode.AREA || isDownload) && measurementPoints.size >= 3) {
                        reusablePath.close()
                        drawPath(reusablePath, color.copy(alpha = 0.2f))
                    }
                    
                    val measurePathEffect = getPathEffect(uiSettings.lineStyle)
                    drawPath(reusablePath, color, style = Stroke(width = 4f, pathEffect = measurePathEffect))
                }

                // Direct Navigation Vector to Target Point
                if (targetNavPoint != null) {
                    val pUser = latLonToOffset(currLoc.latitude, currLoc.longitude, width, height)
                    val pTarget = latLonToOffset(targetNavPoint.latitude, targetNavPoint.longitude, width, height)
                    drawLine(Color(0xFFD84315), pUser, pTarget, strokeWidth = 12f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 15f)))
                    drawCircle(Color(0xFFD84315), radius = 16f, center = pTarget)
                }

                // 5. Render Current GPS Position Marker
                val myOffset = latLonToOffset(currLoc.latitude, currLoc.longitude, width, height)
                
                if (uiSettings.showMoveDirection) {
                    drawCircle(
                        color = Color(0xFF1B4D3E).copy(alpha = radarAlpha),
                        radius = radarRadius,
                        center = myOffset,
                        style = Stroke(width = 4f)
                    )
                }

                if (uiSettings.showViewAngle) {
                    val sweepAngle = 45f
                    val startAngle = compassAzimuth - 90f - (sweepAngle / 2f)
                    
                    drawArc(
                        color = Color(0x661B4D3E),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = true,
                        topLeft = Offset(myOffset.x - 200f, myOffset.y - 200f),
                        size = androidx.compose.ui.geometry.Size(400f, 400f)
                    )

                    val sweepRad = Math.toRadians((startAngle + (sweepAngleAnim % sweepAngle)).toDouble())
                    drawLine(
                        color = Color(0xCC1B4D3E),
                        start = myOffset,
                        end = Offset(myOffset.x + 200f * cos(sweepRad).toFloat(), myOffset.y + 200f * sin(sweepRad).toFloat()),
                        strokeWidth = 3f
                    )
                }
                
                if (uiSettings.showViewLine) {
                    val rad = Math.toRadians((compassAzimuth.toDouble() - 90))
                    drawLine(Color(0xFF1B4D3E).copy(alpha = 0.5f), myOffset, Offset(myOffset.x + 1000f * cos(rad).toFloat(), myOffset.y + 1000f * sin(rad).toFloat()), strokeWidth = 2f)
                }
                
                if (uiSettings.showCompass) {
                    val rad = Math.toRadians((compassAzimuth.toDouble() - 90))
                    val nx = myOffset.x + (28f * cos(rad)).toFloat()
                    val ny = myOffset.y + (28f * sin(rad)).toFloat()
                    drawLine(Color(0xFFD84315), myOffset, Offset(nx, ny), strokeWidth = 5f)
                }

                drawCircle(Color(0x331B4D3E), radius = 32f, center = myOffset)
                drawCircle(Color.White, radius = 14f, center = myOffset)
                drawCircle(Color(0xFF1B4D3E), radius = 10f, center = myOffset)
            }

            // 6. Center Crosshair
            if (uiSettings.showMapCenter) {
                val center = Offset(width / 2f, height / 2f)
                val crossSize = 30f
                drawLine(Color.White, Offset(center.x - crossSize, center.y), Offset(center.x + crossSize, center.y), strokeWidth = 4f)
                drawLine(Color.White, Offset(center.x, center.y - crossSize), Offset(center.x, center.y + crossSize), strokeWidth = 4f)
                drawLine(Color.Black, Offset(center.x - crossSize, center.y), Offset(center.x + crossSize, center.y), strokeWidth = 1.5f)
                drawLine(Color.Black, Offset(center.x, center.y - crossSize), Offset(center.x, center.y + crossSize), strokeWidth = 1.5f)
            }
        }

        // Forestry Info Overlay
        val currentSystem = remember(activeCoordinateSystem, centralMeridian, zoneDegrees, provinceName) {
            val base = CoordinateSystemConverter.SYSTEMS.find { it.id == activeCoordinateSystem }
            if (base != null && base.projection != "VN2000") base
            else {
                val cmStr = centralMeridian.toString().replace(".", "°") + "'"
                com.baoverung.app.gis.CoordinateSystem(
                    id = activeCoordinateSystem,
                    name = if (activeCoordinateSystem.startsWith("VN2000_3")) "VN2000 Múi 3° - $cmStr ($provinceName)" else base?.name ?: activeCoordinateSystem,
                    projection = base?.projection ?: "VN2000",
                    centralMeridian = centralMeridian,
                    zoneDegrees = zoneDegrees
                )
            }
        }
        val (cX, cY) = CoordinateSystemConverter.fromWgs84(currLoc.latitude, currLoc.longitude, currentSystem)
        val (tX, tY) = CoordinateSystemConverter.fromWgs84(centerLat, centerLon, currentSystem)

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(
                        modifier = Modifier.wrapContentWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = currentSystem.name, 
                            color = MaterialTheme.colorScheme.primary, 
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                        IconButton(onClick = { isCoordInfoExpanded = !isCoordInfoExpanded }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                if (isCoordInfoExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Thu gọn/Mở rộng",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    if (isCoordInfoExpanded) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)

                        // User Coordinates
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("X: ${String.format("%.1f", cX)}", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Black)
                            Text("Y: ${String.format("%.1f", cY)}", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Black)
                        }
                        Text("Vị trí WGS84: ${String.format("%.6f", currLoc.latitude)}, ${String.format("%.6f", currLoc.longitude)}", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)

                        // Map Center Coordinates
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Tâm X: ${String.format("%.1f", tX)}", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Y: ${String.format("%.1f", tY)}", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = if (currLoc.accuracy <= 10f) Color(0xFF2E7D32) else Color(0xFFC62828),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("±${String.format("%.1f", currLoc.accuracy)}m", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                            }
                            Text("Cao: ${String.format("%.1f", currLoc.altitude)}m", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            if (uiSettings.showZoomLevel) {
                                Text("Zoom: ${String.format("%.1f", zoomLevel)}", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Top Status Bar
        if (uiSettings.showSatelliteInfo) {
            SatelliteStatusBar(
                currentLocation = currentLocation,
                satVisible = satellitesVisible,
                provinceName = provinceName,
                centralMeridian = centralMeridian,
                zoneDegrees = zoneDegrees,
                activeCoordinateSystem = activeCoordinateSystem,
                syncStatus = syncStatus,
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(0.72f)
            )
        }

        // Status Bar for Track Preview
        if (previewTrackPoints.isNotEmpty()) {
            val dist = GisAreaCalculator.calculatePathLength(previewTrackPoints)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 130.dp, start = 12.dp, end = 12.dp)
                    .fillMaxWidth(0.72f),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("XEM TRƯỚC TRACKLOG", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text("Chiều dài: ${if (dist > 1000) String.format("%.2f km", dist / 1000) else String.format("%.0f m", dist)} | Điểm: ${previewTrackPoints.size}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    IconButton(onClick = onClearPreviewTrack) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng xem trước")
                    }
                }
            }
        }

        // Navigation Vector Banner
        if (targetNavPoint != null && previewTrackPoints.isEmpty()) {
            val dist = GisAreaCalculator.calculateDistance(currLoc.latitude, currLoc.longitude, targetNavPoint.latitude, targetNavPoint.longitude)
            val bearing = GisAreaCalculator.calculateBearing(currLoc.latitude, currLoc.longitude, targetNavPoint.latitude, targetNavPoint.longitude)

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 130.dp, start = 12.dp, end = 12.dp)
                    .fillMaxWidth(0.72f),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("DẪN ĐƯỜNG TỚI MỤC TIÊU", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Khoảng cách: ${if (dist > 1000) String.format("%.2f km", dist / 1000) else String.format("%.0f m", dist)}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Góc hướng (Bearing): ${String.format("%.1f°", bearing)}", fontSize = 12.sp)
                    }
                    IconButton(onClick = { onSetMeasurementMode(MeasurementMode.NONE) }) {
                        Icon(Icons.Default.Close, contentDescription = "Thoát dẫn đường")
                    }
                }
            }
        }

        // Measurement Result Banner
        if (measurementMode != MeasurementMode.NONE && measurementMode != MeasurementMode.NAVIGATION) {
            val dist = GisAreaCalculator.calculatePathLength(measurementPoints)
            val isCurrentArea = measurementMode == MeasurementMode.AREA || measurementMode == MeasurementMode.GPX_AREA
            val areaM2 = if (isCurrentArea) GisAreaCalculator.calculatePolygonArea(measurementPoints) else 0.0

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 10.dp, start = 8.dp, end = 8.dp)
                    .fillMaxWidth(0.75f),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 12.dp,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (!isCurrentArea) "ĐO KHOẢNG CÁCH THỰC ĐỊA" else "ĐO DIỆN TÍCH RỪNG",
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                if (!isCurrentArea) {
                                    Text(GisAreaCalculator.formatDistance(dist), fontWeight = FontWeight.Black, fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface)
                                } else {
                                    Text(GisAreaCalculator.formatArea(areaM2), fontWeight = FontWeight.Black, fontSize = 24.sp, color = Color(0xFF2E7D32))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Chu vi: ${GisAreaCalculator.formatDistance(GisAreaCalculator.calculatePolygonPerimeter(measurementPoints))}", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (measurementPoints.size >= 2) {
                                IconButton(onClick = { onSaveManualTrack("") }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Save, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (selectedMeasurementPointIndex != -1) {
                                IconButton(onClick = { 
                                    onDeleteMeasurementPoint(selectedMeasurementPointIndex)
                                    selectedMeasurementPointIndex = -1
                                }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.RemoveCircle, null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            IconButton(onClick = onUndoMeasurementPoint, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Undo, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = onClearMeasurement, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val isGpx = measurementMode == MeasurementMode.GPX_DISTANCE || measurementMode == MeasurementMode.GPX_AREA
                        Button(
                            onClick = { if (isCurrentArea) onSetMeasurementMode(MeasurementMode.AREA) else onSetMeasurementMode(MeasurementMode.DISTANCE) },
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if(!isGpx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if(!isGpx) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("VẼ TAY", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { if (isCurrentArea) onSetMeasurementMode(MeasurementMode.GPX_AREA) else onSetMeasurementMode(MeasurementMode.GPX_DISTANCE) },
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if(isGpx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if(isGpx) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("TỰ ĐỘNG", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (measurementMode == MeasurementMode.MAP_DOWNLOAD) {
                        HorizontalDivider(modifier = Modifier.width(20.dp).padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = { if (measurementPoints.size >= 3) showDownloadConfig = true },
                                modifier = Modifier.weight(1f).height(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                enabled = measurementPoints.size >= 3
                            ) {
                                Text("TẢI VỀ", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = onUndoMeasurementPoint, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Undo, null, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = {
                                onSetMeasurementMode(MeasurementMode.NONE)
                                onClearMeasurement()
                            }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        }

        // 7. Map Controls & Toolbars
        // Sidebars and FAB are now always accessible for multi-tasking in forestry
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 60.dp, start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Utility Sidebar (Top)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { isLeftToolbarExpanded = !isLeftToolbarExpanded }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (isLeftToolbarExpanded) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
                            contentDescription = "Thu gọn/Mở rộng"
                        )
                    }
                    
                    if (isLeftToolbarExpanded) {
                        HorizontalDivider(modifier = Modifier.width(20.dp).padding(vertical = 4.dp))
                        Box {
                            IconButton(onClick = { showMapSourceMenu = true }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.Layers, "Lớp nền")
                            }
                            DropdownMenu(expanded = showMapSourceMenu, onDismissRequest = { showMapSourceMenu = false }) {
                                listOf("Google Satellite", "Google Street Map", "Google Hybrid", "OpenStreetMap", "Esri World Imagery").forEach { src ->
                                    DropdownMenuItem(text = { Text(src, fontSize = 12.sp) }, onClick = { onSelectMapSource(src); showMapSourceMenu = false })
                                }
                            }
                        }

                        IconButton(onClick = onOpenGisLayers, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Map, "Quản lý lớp")
                        }
                        
                        IconButton(onClick = onOpenCoordConverter, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.SyncAlt, "Chuyển tọa độ", tint = MaterialTheme.colorScheme.secondary)
                        }

                        IconButton(onClick = { 
                            onSetMeasurementMode(MeasurementMode.MAP_DOWNLOAD) 
                            onClearMeasurement()
                        }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Download, "Tải bản đồ", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Journal Sidebar (Bottom - New Position)
            if (!isReadOnly) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { isJournalMenuExpandedInternal = !isJournalMenuExpandedInternal }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = if (isJournalMenuExpandedInternal) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Thu gọn nhật ký",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        if (isJournalMenuExpandedInternal) {
                            HorizontalDivider(modifier = Modifier.width(20.dp).padding(vertical = 4.dp))
                            
                            IconButton(onClick = { onOpenDailyJournalForm() }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.EditCalendar, "Nhật ký ngày", tint = MaterialTheme.colorScheme.tertiary)
                            }
                            
                            IconButton(onClick = { onOpenPatrolForm() }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.Description, "Nhật ký sự vụ", tint = MaterialTheme.colorScheme.error)
                            }

                            IconButton(onClick = { onOpenFloraFaunaForm() }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.Eco, "Nhật ký động thực vật", tint = Color(0xFF2E7D32))
                            }

                            IconButton(onClick = { onOpenNaturalImpactForm() }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.Warning, "Tác động tự nhiên", tint = Color(0xFFFBC02D))
                            }
                        }
                    }
                }
            }
        }

        if (uiSettings.showZoomControls || uiSettings.showRotationControls) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 60.dp, end = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { isRightToolbarExpanded = !isRightToolbarExpanded }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (isRightToolbarExpanded) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                            contentDescription = "Thu gọn/Mở rộng"
                        )
                    }

                    if (isRightToolbarExpanded) {
                        HorizontalDivider(modifier = Modifier.width(20.dp).padding(vertical = 4.dp))
                        
                        if (uiSettings.showZoomControls) {
                            IconButton(onClick = { onMapChange(centerLat, centerLon, (zoomLevel + 0.5f).coerceAtMost(23f)) }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.Add, "Phóng to")
                            }
                            IconButton(onClick = { onMapChange(centerLat, centerLon, (zoomLevel - 0.5f).coerceAtLeast(1f)) }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.Remove, "Thu nhỏ")
                            }
                        }

                        if (uiSettings.showZoomControls && uiSettings.showRotationControls) {
                            HorizontalDivider(modifier = Modifier.width(24.dp).align(Alignment.CenterHorizontally))
                        }

                        if (uiSettings.showRotationControls) {
                            IconButton(onClick = {
                                showCompassDialog = true
                                showSatelliteTab = false
                            }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.Explore, "La bàn", tint = Color(0xFFD84315), modifier = Modifier.rotate(-mapRotation))
                            }

                            IconButton(onClick = {
                                showCompassDialog = true
                                showSatelliteTab = true
                            }, modifier = Modifier.size(40.dp)) {
                                Box {
                                    Icon(Icons.Default.SettingsInputAntenna, "Vệ tinh", tint = MaterialTheme.colorScheme.primary)
                                    if (satellitesCount > 0) {
                                        Surface(
                                            modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp),
                                            color = Color.Red,
                                            shape = CircleShape
                                        ) {
                                            Text(
                                                text = "$satellitesVisible/$satellitesCount",
                                                color = Color.White,
                                                fontSize = 8.sp,
                                                modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(modifier = Modifier.width(24.dp).align(Alignment.CenterHorizontally))

                            IconButton(onClick = { mapRotation = 0f }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.North, "Bắc", tint = Color.Gray)
                            }

                            IconButton(onClick = { onMapChange(currLoc.latitude, currLoc.longitude, 18f) }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.MyLocation, "Định vị", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        // --- Map Import Progress Overlay ---
        if (importState.isFullLoading || importState.isMetadataScan) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Đang cập nhật bản đồ...",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (importState.totalCount > 0) {
                                Text(
                                    "Đã nạp: ${importState.currentProgress}/${importState.totalCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        
                        if (importState.isFullLoading) {
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = onCancelDownload,
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("HỦY", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }

        val isReadOnly = userSession.permissions == "VIEW_ONLY" || userSession.isOfflineMode
        val isAreaMeasure = measurementMode == MeasurementMode.AREA || measurementMode == MeasurementMode.GPX_AREA
        val isDistMeasure = measurementMode == MeasurementMode.DISTANCE || measurementMode == MeasurementMode.GPX_DISTANCE

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isSurveyMenuExpanded) {
                // 1. THANH CÔNG CỤ TIỆN ÍCH
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(4.dp)) {
                        IconButton(onClick = { 
                            onSetMeasurementMode(if (isDistMeasure) MeasurementMode.NONE else MeasurementMode.DISTANCE)
                            isSurveyMenuExpanded = false
                        }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.Straighten, "Đo khoảng cách", tint = if(isDistMeasure) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { 
                            onSetMeasurementMode(if (isAreaMeasure) MeasurementMode.NONE else MeasurementMode.AREA)
                            isSurveyMenuExpanded = false
                        }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.SquareFoot, "Đo diện tích", tint = if(isAreaMeasure) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        
                        if (!isReadOnly) {
                            HorizontalDivider(modifier = Modifier.width(32.dp).align(Alignment.CenterHorizontally).padding(vertical = 4.dp))
                            IconButton(onClick = { 
                                onCapturePhoto()
                                isSurveyMenuExpanded = false
                            }, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Default.CameraAlt, "Chụp ảnh", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { 
                                onToggleGpxTracking()
                                isSurveyMenuExpanded = false
                            }, modifier = Modifier.size(44.dp)) {
                                Icon(
                                    imageVector = if (isTrackingGpx) Icons.Default.Stop else Icons.Default.RadioButtonChecked,
                                    contentDescription = "Ghi Tracklog",
                                    tint = if (isTrackingGpx) Color.Red else MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { 
                                onOpenAddWaypoint()
                                isSurveyMenuExpanded = false
                            }, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Default.PushPin, "Thêm điểm", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { isSurveyMenuExpanded = !isSurveyMenuExpanded },
                containerColor = if (isSurveyMenuExpanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
                contentColor = if (isSurveyMenuExpanded) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (isSurveyMenuExpanded) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = "Menu công cụ"
                )
            }
        }

        // --- Dialogs ---

        if (showDownloadConfig) {
            AlertDialog(
                onDismissRequest = { showDownloadConfig = false },
                title = { Text("CẤU HÌNH TẢI BẢN ĐỒ", fontWeight = FontWeight.Black) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Khoanh vùng: ${measurementPoints.size} điểm", fontSize = 12.sp)
                        
                        Column {
                            Text("Mức Zoom: ${downloadMinZoom.toInt()} đến ${downloadMaxZoom.toInt()}", fontWeight = FontWeight.Bold)
                            RangeSlider(
                                value = downloadMinZoom..downloadMaxZoom,
                                onValueChange = { downloadMinZoom = it.start; downloadMaxZoom = it.endInclusive },
                                valueRange = 1f..20f,
                                steps = 18
                            )
                        }

                        Column {
                            Text("Nguồn bản đồ:", fontWeight = FontWeight.Bold)
                            listOf("Google Satellite", "Google Street Map", "Google Hybrid", "Esri World Imagery", "OpenStreetMap").forEach { src ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = downloadSource == src, onClick = { downloadSource = src })
                                    Text(src, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        onDownloadArea(measurementPoints, downloadMinZoom.toInt(), downloadMaxZoom.toInt(), downloadSource)
                        showDownloadConfig = false
                        onSetMeasurementMode(MeasurementMode.NONE)
                    }) {
                        Text("BẮT ĐẦU TẢI")
                    }
                },
                dismissButton = { TextButton(onClick = { showDownloadConfig = false }) { Text("HỦY") } }
            )
        }

        if (showCompassDialog) {
            com.baoverung.app.ui.components.AdvancedCompassSatelliteDialog(
                azimuth = compassAzimuth,
                satellites = satelliteDetails,
                accelValues = accelerometerValues,
                gravityValues = gravityValues,
                initialShowSatellite = showSatelliteTab,
                onDismiss = { showCompassDialog = false }
            )
        }

        if (selectedFeature != null) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
            ModalBottomSheet(
                onDismissRequest = { selectedFeature = null },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                FeatureInfoContent(
                    feature = selectedFeature!!,
                    onNavigate = { 
                        onNavigateToPoint(GpsPoint(selectedFeature!!.centroidLat, selectedFeature!!.centroidLon))
                        selectedFeature = null
                    },
                    onClose = { selectedFeature = null }
                )
            }
        }

        // --- Item Detail Dialogs ---
        val dateFormat = remember { java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()) }

        if (selectedWaypointForDetail != null) {
            val wp = selectedWaypointForDetail!!
            AlertDialog(
                onDismissRequest = { selectedWaypointForDetail = null },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (!wp.photoPath.isNullOrEmpty()) Icons.Default.CameraAlt else Icons.Default.Place, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CHI TIẾT ĐIỂM KHẢO SÁT", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!wp.photoPath.isNullOrEmpty()) {
                            AsyncImage(
                                model = wp.photoPath,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column {
                                DetailRow("Tiêu đề", wp.title, isBold = true)
                                DetailRow("Mô tả", wp.description.ifEmpty { "Không có mô tả" })
                                DetailRow("Thời gian", dateFormat.format(java.util.Date(wp.timestampUtc)))
                                val (vx, vy) = com.baoverung.app.gis.CoordinateSystemConverter.wgs84ToVn2000(wp.latitude, wp.longitude, centralMeridian, zoneDegrees)
                                DetailRow("VN2000 X", String.format("%.1f", vx))
                                DetailRow("VN2000 Y", String.format("%.1f", vy))
                                DetailRow("Độ cao", String.format("%.1f m", wp.altitude))
                                DetailRow("WGS84", "${String.format("%.6f", wp.latitude)}, ${String.format("%.6f", wp.longitude)}")
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { selectedWaypointForDetail = null }) { Text("ĐÓNG") } }
            )
        }

        if (selectedPatrolForDetail != null) {
            val pt = selectedPatrolForDetail!!
            AlertDialog(
                onDismissRequest = { selectedPatrolForDetail = null },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CHI TIẾT NHẬT KÝ TUẦN TRA", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!pt.photoPath.isNullOrEmpty()) {
                            AsyncImage(
                                model = pt.photoPath?.split("|")?.firstOrNull(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column {
                                DetailRow("Sự vụ", pt.incidentType, isBold = true, color = MaterialTheme.colorScheme.error)
                                DetailRow("Cán bộ", pt.leaderName)
                                DetailRow("Địa điểm", pt.violationLocation)
                                DetailRow("Xử lý", pt.onSiteAction)
                                DetailRow("Thời gian", dateFormat.format(java.util.Date(pt.discoveryTimeUtc)))
                                DetailRow("VN2000 X", String.format("%.1f", pt.vn2000X))
                                DetailRow("VN2000 Y", String.format("%.1f", pt.vn2000Y))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { selectedPatrolForDetail = null }) { Text("ĐÓNG") } }
            )
        }

        if (selectedTrackForDetail != null) {
            val trk = selectedTrackForDetail!!
            AlertDialog(
                onDismissRequest = { selectedTrackForDetail = null },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timeline, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CHI TIẾT TRACKLOG", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column {
                                DetailRow("Tên vệt", trk.title, isBold = true)
                                DetailRow("Bắt đầu", dateFormat.format(java.util.Date(trk.startTimeUtc)))
                                DetailRow("Chiều dài", String.format("%.2f km", trk.totalDistanceMeters / 1000.0), color = MaterialTheme.colorScheme.primary, isBold = true)
                                DetailRow("Số điểm", trk.fullPoints.size.toString())
                            }
                        }
                        
                        if (trk.sampledPoints.isNotEmpty()) {
                            Text("TỌA ĐỘ MẪU (VN2000):", fontWeight = FontWeight.Black, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                            trk.sampledPoints.forEachIndexed { index, pt ->
                                val (vx, vy) = com.baoverung.app.gis.CoordinateSystemConverter.wgs84ToVn2000(pt.latitude, pt.longitude, centralMeridian, zoneDegrees)
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${index + 1}.", fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.width(20.dp))
                                    Text("X: ${String.format("%.1f", vx)}", fontSize = 10.sp, modifier = Modifier.weight(1f))
                                    Text("Y: ${String.format("%.1f", vy)}", fontSize = 10.sp, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        onPreviewTrackLog(trk)
                        selectedTrackForDetail = null
                    }) {
                        Text("MỞ ĐƯỜNG")
                    }
                },
                dismissButton = { TextButton(onClick = { selectedTrackForDetail = null }) { Text("ĐÓNG") } }
            )
        }

        if (selectedPolygonForDetail != null) {
            val poly = selectedPolygonForDetail!!
            AlertDialog(
                onDismissRequest = { selectedPolygonForDetail = null },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SquareFoot, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CHI TIẾT VÙNG DIỆN TÍCH", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column {
                                DetailRow("Tiêu đề", poly.title, isBold = true)
                                DetailRow("Diện tích", com.baoverung.app.gis.GisAreaCalculator.formatArea(poly.areaSquareMeters), color = MaterialTheme.colorScheme.primary, isBold = true)
                                DetailRow("Tâm Lat", String.format("%.6f", poly.centroidLat))
                                DetailRow("Tâm Lon", String.format("%.6f", poly.centroidLon))
                                DetailRow("VN2000 X", String.format("%.1f", poly.centroidVn2000X))
                                DetailRow("VN2000 Y", String.format("%.1f", poly.centroidVn2000Y))
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        onNavigateToPoint(GpsPoint(poly.centroidLat, poly.centroidLon))
                        selectedPolygonForDetail = null
                    }) {
                        Text("DẪN ĐƯỜNG")
                    }
                },
                dismissButton = { TextButton(onClick = { selectedPolygonForDetail = null }) { Text("ĐÓNG") } }
            )
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, isBold: Boolean = false, color: Color = Color.Unspecified) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = label, modifier = Modifier.weight(0.35f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        Text(text = value, modifier = Modifier.weight(0.65f), fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp, color = color, textAlign = TextAlign.End)
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun FeatureInfoContent(
    feature: GisFeature,
    onNavigate: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "THÔNG TIN ĐỐI TƯỢNG",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Dữ liệu bản đồ GIS",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Icon(Icons.Default.Close, null)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Key Spatial Data
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (feature.shapeType == GisShapeType.POINT) Icons.Default.LocationOn else Icons.Default.Hexagon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Column {
                    Text(
                        "Tọa độ tâm (WGS84)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${String.format("%.6f", feature.centroidLat)}, ${String.format("%.6f", feature.centroidLon)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Attributes List
        Text(
            "THUỘC TÍNH CHI TIẾT",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = Color.Gray,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column {
                val attrs = feature.attributes.entries.toList()
                if (attrs.isEmpty()) {
                    Text(
                        "Không có dữ liệu thuộc tính.",
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    attrs.forEachIndexed { index, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (index % 2 == 0) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.key,
                                modifier = Modifier.weight(0.4f),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = entry.value,
                                modifier = Modifier.weight(0.6f),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End
                            )
                        }
                        if (index < attrs.size - 1) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onNavigate,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Directions, null)
                Spacer(Modifier.width(8.dp))
                Text("DẪN ĐƯỜNG", fontWeight = FontWeight.Black)
            }
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.weight(0.5f).height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("ĐÓNG")
            }
        }
    }
}
