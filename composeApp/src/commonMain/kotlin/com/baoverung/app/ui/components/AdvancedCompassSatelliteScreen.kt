package com.baoverung.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoverung.app.data.model.SatelliteInfo
import kotlin.math.*

@Composable
fun AdvancedCompassSatelliteDialog(
    azimuth: Float,
    satellites: List<SatelliteInfo>,
    accelValues: FloatArray,
    gravityValues: FloatArray,
    initialShowSatellite: Boolean = false,
    onDismiss: () -> Unit
) {
    var showSatellite by remember { mutableStateOf(initialShowSatellite) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF283593)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                Row {
                    TextButton(onClick = { showSatellite = false }) {
                        Text("LA BÀN", color = if (!showSatellite) Color.White else Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { showSatellite = true }) {
                        Text("VỆ TINH", color = if (showSatellite) Color.White else Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = Color.White) }
            }

            if (!showSatellite) {
                CompassView(azimuth, accelValues, gravityValues)
            } else {
                SatelliteRadarView(satellites)
            }
        }
    }
}

@Composable
fun CompassView(azimuth: Float, accel: FloatArray, gravity: FloatArray) {
    val directionName = when {
        azimuth >= 337.5 || azimuth < 22.5 -> "Bắc"
        azimuth >= 22.5 && azimuth < 67.5 -> "Đông Bắc"
        azimuth >= 67.5 && azimuth < 112.5 -> "Đông"
        azimuth >= 112.5 && azimuth < 157.5 -> "Đông Nam"
        azimuth >= 157.5 && azimuth < 202.5 -> "Nam"
        azimuth >= 202.5 && azimuth < 247.5 -> "Tây Nam"
        azimuth >= 247.5 && azimuth < 292.5 -> "Tây"
        else -> "Tây Bắc"
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = directionName, fontSize = 32.sp, color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))
        CompassPlate(azimuth)
        Spacer(modifier = Modifier.height(48.dp))
        Text(text = "${azimuth.toInt()}°", fontSize = 48.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CompassPlate(azimuth: Float) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = Modifier.size(300.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.width / 2f
        drawCircle(Color(0xFF3F51B5), radius, center)

        rotate(-azimuth, center) {
            for (i in 0 until 360 step 10) {
                val angle = (i.toDouble() - 90) * PI / 180.0
                val start = Offset(center.x + (radius - 10).toFloat() * cos(angle).toFloat(), center.y + (radius - 10).toFloat() * sin(angle).toFloat())
                val end = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
                drawLine(Color.White, start, end, 2f)
            }
            
            val labels = listOf("B" to 0, "Đ" to 90, "N" to 180, "T" to 270)
            labels.forEach { (text, angle) ->
                val rad = (angle.toDouble() - 90) * PI / 180.0
                val pos = Offset(center.x + (radius - 40).toFloat() * cos(rad).toFloat(), center.y + (radius - 40).toFloat() * sin(rad).toFloat())
                drawText(textMeasurer, text, pos - Offset(10f, 15f), style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold))
            }
        }
        
        // Needle
        val needlePath = Path().apply {
            moveTo(center.x, center.y - radius - 5f)
            lineTo(center.x - 10f, center.y - radius + 20f)
            lineTo(center.x + 10f, center.y - radius + 20f)
            close()
        }
        drawPath(needlePath, Color.Red)
    }
}

@Composable
fun SatelliteRadarView(satellites: List<SatelliteInfo>) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(300.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.width / 2f
            drawCircle(Color.White.copy(alpha = 0.2f), radius, center, style = Stroke(2f))
            drawCircle(Color.White.copy(alpha = 0.2f), radius * 0.66f, center, style = Stroke(1f))
            drawCircle(Color.White.copy(alpha = 0.2f), radius * 0.33f, center, style = Stroke(1f))

            satellites.forEach { sat ->
                val angle = (sat.azimuth.toDouble() - 90) * PI / 180.0
                val dist = radius * (1.0 - sat.elevation / 90.0)
                val pos = Offset(center.x + dist.toFloat() * cos(angle).toFloat(), center.y + dist.toFloat() * sin(angle).toFloat())
                drawCircle(if (sat.usedInFix) Color.Green else Color.Gray, radius = 6f, center = pos)
            }
        }
    }
}
