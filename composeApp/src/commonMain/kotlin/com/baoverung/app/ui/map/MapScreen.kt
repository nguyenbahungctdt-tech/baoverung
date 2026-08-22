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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import com.baoverung.app.data.local.entity.*
import com.baoverung.app.data.model.*
import com.baoverung.app.gis.GisAreaCalculator
import com.baoverung.app.gis.MBTilesReader
import com.baoverung.app.util.GeometryUtils
import com.baoverung.app.ui.components.SatelliteStatusBar
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

data class DOffset(val x: Double, val y: Double)

private fun latLonToWorld(lat: Double, lon: Double, zoom: Float): DOffset {
    val scale = 256.0 * 2.0.pow(zoom.toDouble())
    val x = (lon + 180.0) / 360.0 * scale
    val latRad = lat.coerceIn(-85.05112878, 85.05112878) * PI / 180.0
    val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * scale
    return DOffset(x, y)
}

private fun worldToLatLon(worldX: Double, worldY: Double, zoom: Float): Pair<Double, Double> {
    val scale = 256.0 * 2.0.pow(zoom.toDouble())
    val lon = (worldX / scale) * 360.0 - 180.0
    val n = PI - 2.0 * PI * (worldY / scale)
    val latRad = atan(sinh(n))
    val lat = latRad * 180.0 / PI
    return Pair(lat, lon)
}

private fun Offset.rotateAround(center: Offset, degrees: Float): Offset {
    val angleRad = degrees * PI / 180.0
    val cosA = cos(angleRad).toFloat()
    val sinA = sin(angleRad).toFloat()
    val dx = x - center.x
    val dy = y - center.y
    return Offset(
        x = center.x + (dx * cosA - dy * sinA),
        y = center.y + (dx * sinA + dy * cosA)
    )
}

enum class MeasurementMode {
    NONE, DISTANCE, AREA, NAVIGATION, MAP_DOWNLOAD, GPX_DISTANCE, GPX_AREA
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
    val tileZoom = zoomLevel.toInt().coerceIn(1, 21)
    val centerWorld = latLonToWorld(centerLat, centerLon, zoomLevel)
    val leftWorld = centerWorld.x - widthPx / 2.0
    val topWorld = centerWorld.y - heightPx / 2.0
    val scale = 2.0.pow((zoomLevel - tileZoom).toDouble())
    val tileSize = 256.0 * scale

    val minX = floor(leftWorld / tileSize).toInt()
    val maxX = floor((leftWorld + widthPx) / tileSize).toInt()
    val minY = floor(topWorld / tileSize).toInt()
    val maxY = floor((topWorld + heightPx) / tileSize).toInt()

    val density = androidx.compose.ui.platform.LocalDensity.current

    Box(modifier = Modifier.fillMaxSize()) {
        for (tx in minX..maxX) {
            for (ty in minY..maxY) {
                val screenX = (tx * tileSize - leftWorld).toFloat()
                val screenY = (ty * tileSize - topWorld).toFloat()
                val url = "https://mt1.google.com/vt/lyrs=s&x=$tx&y=$ty&z=$tileZoom"
                
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier
                        .offset(
                            x = with(density) { screenX.toDp() },
                            y = with(density) { screenY.toDp() }
                        )
                        .size(with(density) { tileSize.toFloat().toDp() }),
                    contentScale = ContentScale.FillBounds
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
    val centerWorld = latLonToWorld(centerLat, centerLon, zoomLevel)
    val leftWorld = centerWorld.x - widthPx / 2.0
    val topWorld = centerWorld.y - heightPx / 2.0

    Canvas(modifier = Modifier.fillMaxSize()) {
        for (layer in gisLayers) {
            if (!layer.isVisible) continue
            val reader = mbtilesReaders[layer.id] ?: continue
            val tileZoom = zoomLevel.toInt().coerceIn(1, reader.getMaxZoom())
            val scale = 2.0.pow((zoomLevel - tileZoom).toDouble())
            val tileSize = 256.0 * scale

            val minX = floor(leftWorld / tileSize).toInt()
            val maxX = floor((leftWorld + widthPx) / tileSize).toInt()
            val minY = floor(topWorld / tileSize).toInt()
            val maxY = floor((topWorld + heightPx) / tileSize).toInt()

            for (tx in minX..maxX) {
                for (ty in minY..maxY) {
                    val bitmap = reader.getTileBitmap(tileZoom, tx, ty) ?: continue
                    val screenX = (tx * tileSize - leftWorld).toFloat()
                    val screenY = (ty * tileSize - topWorld).toFloat()
                    drawImage(
                        image = bitmap,
                        dstOffset = androidx.compose.ui.unit.IntOffset(screenX.toInt(), screenY.toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize(tileSize.toInt(), tileSize.toInt()),
                        alpha = layer.opacity
                    )
                }
            }
        }
    }
}

data class MapUiSettings(
    val showMapCenter: Boolean = true,
    val showViewAngle: Boolean = true,
    val showMoveDirection: Boolean = true,
    val showCompass: Boolean = true,
    val showSatelliteInfo: Boolean = true,
    val showZoomLevel: Boolean = true,
    val showLabelsGlobal: Boolean = true,
    val showPointsGlobal: Boolean = true,
    val showTracklogsGlobal: Boolean = true,
    val showPolygonsGlobal: Boolean = true,
    val pointFontSize: Int = 12
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    centerLat: Double,
    centerLon: Double,
    zoomLevel: Float,
    onMapChange: (Double, Double, Float) -> Unit,
    currentLocation: GpsPoint?,
    compassAzimuth: Float,
    satellitesVisible: Int = 0,
    measurementMode: MeasurementMode,
    measurementPoints: List<GpsPoint>,
    targetNavPoint: GpsPoint?,
    gisLayers: List<GisLayerEntity>,
    mbtilesReaders: Map<Long, MBTilesReader> = emptyMap(),
    waypoints: List<WaypointEntity> = emptyList(),
    trackLogs: List<TrackLogEntity> = emptyList(),
    polygons: List<PolygonEntity> = emptyList(),
    patrolLogs: List<PatrolLogEntity> = emptyList(),
    isTrackingGpx: Boolean,
    trackedPoints: List<GpsPoint>,
    selectedMapSource: String,
    onSelectMapSource: (String) -> Unit,
    onSetMeasurementMode: (MeasurementMode) -> Unit,
    onAddMeasurementPoint: (GpsPoint) -> Unit,
    onClearMeasurement: () -> Unit,
    onToggleGpxTracking: () -> Unit,
    onOpenAddWaypoint: () -> Unit,
    onOpenPatrolForm: () -> Unit,
    onOpenGisLayers: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDataManagement: () -> Unit,
    uiSettings: MapUiSettings = MapUiSettings(),
    modifier: Modifier = Modifier
) {
    var mapRotation by remember { mutableFloatStateOf(0f) }
    val textMeasurer = rememberTextMeasurer()
    val currLoc = currentLocation ?: GpsPoint(11.9404, 108.4378)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = maxWidth.value
        val heightPx = maxHeight.value

        val viewportBounds = remember(centerLat, centerLon, zoomLevel, widthPx, heightPx) {
            val cw = latLonToWorld(centerLat, centerLon, zoomLevel)
            object { val cwX = cw.x; val cwY = cw.y }
        }

        fun latLonToOffset(lat: Double, lon: Double): Offset {
            val w = latLonToWorld(lat, lon, zoomLevel)
            return Offset((w.x - viewportBounds.cwX + widthPx / 2.0).toFloat(), (w.y - viewportBounds.cwY + heightPx / 2.0).toFloat())
        }

        // Map Layers
        Box(modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = mapRotation }) {
            OnlineMapTileLayer(selectedMapSource, centerLat, centerLon, zoomLevel, widthPx, heightPx)
            OfflineMapTileLayer(mbtilesReaders, gisLayers, centerLat, centerLon, zoomLevel, widthPx, heightPx)
        }

        // Vector Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasCenter = Offset(size.width / 2f, size.height / 2f)
            rotate(mapRotation, canvasCenter) {
                // TrackLogs
                if (uiSettings.showTracklogsGlobal) {
                    trackLogs.forEach { trk ->
                        // Simplified path drawing
                        val path = Path()
                        // ... Logic to draw path from trk.pointsJson ...
                    }
                }

                // Current GPS Position
                val myOffset = latLonToOffset(currLoc.latitude, currLoc.longitude)
                drawCircle(Color.Blue, radius = 10.dp.toPx(), center = myOffset)
                
                if (uiSettings.showViewAngle) {
                    val sweepAngle = 45f
                    val startAngle = compassAzimuth - 90f - (sweepAngle / 2f)
                    drawArc(Color.Blue.copy(alpha = 0.2f), startAngle, sweepAngle, true, Offset(myOffset.x - 100f, myOffset.y - 100f), androidx.compose.ui.geometry.Size(200f, 200f))
                }
            }

            // Crosshair
            if (uiSettings.showMapCenter) {
                drawLine(Color.Black, Offset(size.width / 2 - 20, size.height / 2), Offset(size.width / 2 + 20, size.height / 2), 2f)
                drawLine(Color.Black, Offset(size.width / 2, size.height / 2 - 20), Offset(size.width / 2, size.height / 2 + 20), 2f)
            }
        }

        // UI Overlays
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(onClick = onOpenSettings, containerColor = MaterialTheme.colorScheme.secondary) { Icon(Icons.Default.Settings, null) }
            FloatingActionButton(onClick = onOpenGisLayers, containerColor = MaterialTheme.colorScheme.tertiary) { Icon(Icons.Default.Layers, null) }
            FloatingActionButton(onClick = onOpenPatrolForm, containerColor = MaterialTheme.colorScheme.error) { Icon(Icons.Default.Add, null) }
            FloatingActionButton(onClick = onToggleGpxTracking) {
                Icon(if (isTrackingGpx) Icons.Default.Stop else Icons.Default.PlayArrow, null)
            }
        }

        SatelliteStatusBar(
            currentLocation = currentLocation,
            satVisible = satellitesVisible,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
        )
    }
}
