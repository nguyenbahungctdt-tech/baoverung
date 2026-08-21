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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import com.baoverung.app.data.model.*
import com.baoverung.app.gis.GisAreaCalculator
import com.baoverung.app.util.GeometryUtils
import com.baoverung.app.util.format
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

@Composable
fun OnlineMapTileLayer(
    selectedMapSource: String,
    centerLat: Double,
    centerLon: Double,
    zoomLevel: Float,
    widthPx: Float,
    heightPx: Float
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.LightGray)) {
        Text("Map Tiles Placeholder", modifier = Modifier.align(Alignment.Center))
    }
}

data class MapUiSettings(
    val showMapCenter: Boolean = true,
    val showViewAngle: Boolean = true,
    val showMoveDirection: Boolean = true
)

enum class MeasurementMode {
    NONE, DISTANCE, AREA, NAVIGATION
}

@Composable
fun MapScreen(
    centerLat: Double,
    centerLon: Double,
    zoomLevel: Float,
    onMapChange: (Double, Double, Float) -> Unit,
    currentLocation: GpsPoint?,
    compassAzimuth: Float,
    measurementMode: MeasurementMode,
    measurementPoints: List<GpsPoint>,
    targetNavPoint: GpsPoint?,
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
    uiSettings: MapUiSettings = MapUiSettings(),
    modifier: Modifier = Modifier
) {
    var mapRotation by remember { mutableFloatStateOf(0f) }
    val currLoc = currentLocation ?: GpsPoint(11.9404, 108.4378)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = maxWidth.value 
        val heightPx = maxHeight.value

        val viewportBounds = remember(centerLat, centerLon, zoomLevel, widthPx, heightPx) {
            val cw = latLonToWorld(centerLat, centerLon, zoomLevel)
            object {
                val cwX = cw.x
                val cwY = cw.y
            }
        }

        fun latLonToOffset(lat: Double, lon: Double, width: Float, height: Float): Offset {
            val w = latLonToWorld(lat, lon, zoomLevel)
            return Offset((w.x - viewportBounds.cwX + width / 2.0).toFloat(), (w.y - viewportBounds.cwY + height / 2.0).toFloat())
        }

        Box(modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = mapRotation }) {
            OnlineMapTileLayer(selectedMapSource, centerLat, centerLon, zoomLevel, widthPx, heightPx)
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val canvasCenter = Offset(width / 2f, height / 2f)

            rotate(mapRotation, canvasCenter) {
                if (trackedPoints.size >= 2) {
                    val path = Path()
                    val p0 = latLonToOffset(trackedPoints[0].latitude, trackedPoints[0].longitude, width, height)
                    path.moveTo(p0.x, p0.y)
                    for (i in 1 until trackedPoints.size) {
                        val pi = latLonToOffset(trackedPoints[i].latitude, trackedPoints[i].longitude, width, height)
                        path.lineTo(pi.x, pi.y)
                    }
                    drawPath(path, Color.Red, style = Stroke(width = 4f))
                }

                val myOffset = latLonToOffset(currLoc.latitude, currLoc.longitude, width, height)
                drawCircle(Color.Blue, radius = 10f, center = myOffset)
            }

            if (uiSettings.showMapCenter) {
                val center = Offset(width / 2f, height / 2f)
                drawLine(Color.Black, Offset(center.x - 20, center.y), Offset(center.x + 20, center.y), strokeWidth = 2f)
                drawLine(Color.Black, Offset(center.x, center.y - 20), Offset(center.x, center.y + 20), strokeWidth = 2f)
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(onClick = { onOpenPatrolForm() }, containerColor = MaterialTheme.colorScheme.error) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
            FloatingActionButton(onClick = { onToggleGpxTracking() }) {
                Icon(if (isTrackingGpx) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
            }
        }
        
        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.8f)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("Vị trí: ${currLoc.latitude.format(6)}, ${currLoc.longitude.format(6)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
