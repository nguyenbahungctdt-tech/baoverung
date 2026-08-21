package com.baoverung.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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
        color = Color(0xFF283593) // Dark blue background as in image
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showSatellite = false }) {
                        Text("LA BÀN", color = if (!showSatellite) Color.White else Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { showSatellite = true }) {
                        Text("VỆ TINH", color = if (showSatellite) Color.White else Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color.White)
                }
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
        Text(
            text = directionName,
            fontSize = 32.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        Box(contentAlignment = Alignment.Center) {
            CompassPlate(azimuth)
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "${azimuth.toInt()}°",
            fontSize = 48.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            InfoCircle("Gia tốc", accel[0], accel[1])
            InfoCircle("Cân bằng", gravity[0], gravity[1])
        }
    }
}

@Composable
fun CompassPlate(azimuth: Float) {
    val density = LocalDensity.current
    Canvas(modifier = Modifier.size(320.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.width / 2f
        
        // Background Circle
        drawCircle(
            color = Color(0xFF3F51B5),
            radius = radius,
            center = center
        )

        rotate(-azimuth, center) {
            // Outer Ring Degrees
            for (i in 0 until 360 step 2) {
                val angle = Math.toRadians(i.toDouble() - 90)
                val start = Offset(
                    center.x + (radius - 15).toFloat() * cos(angle).toFloat(),
                    center.y + (radius - 15).toFloat() * sin(angle).toFloat()
                )
                val end = Offset(
                    center.x + radius * cos(angle).toFloat(),
                    center.y + radius * sin(angle).toFloat()
                )
                val length = if (i % 10 == 0) 15f else 8f
                drawLine(
                    color = Color.White.copy(alpha = 0.7f),
                    start = Offset(
                        center.x + (radius - length).toFloat() * cos(angle).toFloat(),
                        center.y + (radius - length).toFloat() * sin(angle).toFloat()
                    ),
                    end = end,
                    strokeWidth = if (i % 10 == 0) 2f else 1f
                )

                if (i % 30 == 0) {
                    drawContext.canvas.nativeCanvas.drawText(
                        i.toString(),
                        center.x + (radius - 40).toFloat() * cos(angle).toFloat(),
                        center.y + (radius - 40).toFloat() * sin(angle).toFloat() + 5f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = with(density) { 10.sp.toPx() }
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                }
            }

            // Direction Letters (N, E, S, W in Vietnamese: B, Đ, N, T)
            val directions = listOf("N" to 0, "Đ" to 90, "B" to 180, "T" to 270) // Note: Image shows N at top (180 deg mark?)
            // Actually standard: N at 0, E at 90, S at 180, W at 270.
            // Screenshot shows N (B) at bottom (near 0) and N (North?) at top (near 180).
            // Wait, the screenshot has 'N' at top but the degree mark is '180'. So S (South) is at top.
            // Vietnamese: Bắc (N), Nam (S), Đông (E), Tây (W).
            // Image has 'N' at top, but degree 180. So 'N' means 'Nam' (South).
            // Image has 'B' at bottom, degree 0. So 'B' means 'Bắc' (North).
            // East is 'Đ' (Đông) at 90. West is 'T' (Tây) at 270.
            
            val labels = listOf("B" to 0, "Đ" to 90, "N" to 180, "T" to 270)
            labels.forEach { (text, angle) ->
                val rad = Math.toRadians(angle.toDouble() - 90)
                val pos = Offset(
                    center.x + (radius - 75).toFloat() * cos(rad).toFloat(),
                    center.y + (radius - 75).toFloat() * sin(rad).toFloat()
                )
                drawContext.canvas.nativeCanvas.drawText(
                    text,
                    pos.x,
                    pos.y + 15f,
                    android.graphics.Paint().apply {
                        color = if (text == "N") android.graphics.Color.WHITE else if (text == "B") 0xFFFF3D00.toInt() else android.graphics.Color.WHITE
                        textSize = with(density) { 24.sp.toPx() }
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }

            // Bát Quái Trigrams
            val trigrams = listOf(
                "☰", // Càn (S) - 180
                "☱", // Đoài
                "☲", // Ly (E) - 90
                "☳", // Chấn
                "☷", // Khôn (N) - 0
                "☶", // Cấn
                "☵", // Khảm (W) - 270
                "☴"  // Tốn
            )
            // Tiên Thiên Bát Quái: Càn (S), Khôn (N), Ly (E), Khảm (W), Đoài (SE), Chấn (NE), Tốn (SW), Cấn (NW)
            // Order clockwise from North (0): Khôn, Chấn, Ly, Đoài, Càn, Tốn, Khảm, Cấn
            val trigramLabels = listOf(
                "☷" to 0, "☳" to 45, "☲" to 90, "☱" to 135,
                "☰" to 180, "☴" to 225, "☵" to 270, "☶" to 315
            )
            
            trigramLabels.forEach { (sym, angle) ->
                val rad = Math.toRadians(angle.toDouble() - 90)
                val pos = Offset(
                    center.x + (radius - 120).toFloat() * cos(rad).toFloat(),
                    center.y + (radius - 120).toFloat() * sin(rad).toFloat()
                )
                
                // Trigrams are better drawn manually for precision
                drawTrigram(sym, pos, 30f)
            }
        }

        // Center Yin-Yang
        drawYinYang(center, 50f)
        
        // North Needle (Fixed at top)
        val needlePath = Path().apply {
            moveTo(center.x, center.y - radius - 5f)
            lineTo(center.x - 10f, center.y - radius + 15f)
            lineTo(center.x + 10f, center.y - radius + 15f)
            close()
        }
        drawPath(needlePath, Color(0xFFFF3D00))
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTrigram(sym: String, center: Offset, size: Float) {
    // ☰, ☱, ☲, ☳, ☴, ☵, ☶, ☷
    val strokeWidth = 4f
    val spacing = 8f
    val width = size * 1.5f
    
    val lines = when(sym) {
        "☰" -> listOf(false, false, false)
        "☱" -> listOf(true, false, false)
        "☲" -> listOf(false, true, false)
        "☳" -> listOf(true, true, false)
        "☴" -> listOf(false, false, true)
        "☵" -> listOf(true, false, true)
        "☶" -> listOf(false, true, true)
        "☷" -> listOf(true, true, true)
        else -> listOf(false, false, false)
    }

    lines.forEachIndexed { i, isBroken ->
        val y = center.y - size/2 + i * spacing
        if (!isBroken) {
            drawLine(Color(0xFFFFD54F), Offset(center.x - width/2, y), Offset(center.x + width/2, y), strokeWidth)
        } else {
            drawLine(Color(0xFFFFD54F), Offset(center.x - width/2, y), Offset(center.x - width/6, y), strokeWidth)
            drawLine(Color(0xFFFFD54F), Offset(center.x + width/6, y), Offset(center.x + width/2, y), strokeWidth)
        }
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawYinYang(center: Offset, radius: Float) {
    // White half (right)
    drawArc(
        color = Color.White,
        startAngle = -90f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2)
    )
    // Black half (left)
    drawArc(
        color = Color.Black,
        startAngle = 90f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2)
    )
    
    // Top circle
    drawCircle(Color.White, radius = radius / 2, center = Offset(center.x, center.y - radius / 2))
    drawCircle(Color.Black, radius = radius / 6, center = Offset(center.x, center.y - radius / 2))
    
    // Bottom circle
    drawCircle(Color.Black, radius = radius / 2, center = Offset(center.x, center.y + radius / 2))
    drawCircle(Color.White, radius = radius / 6, center = Offset(center.x, center.y + radius / 2))
    
    // Border
    drawCircle(Color(0xFFFFD54F), radius = radius, center = center, style = Stroke(width = 2f))
}

@Composable
fun InfoCircle(label: String, x: Float, y: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Color.White.copy(alpha = 0.1f), CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(Color.White.copy(alpha = 0.3f), radius = size.width / 2f, center = center, style = Stroke(1f))
                drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, center.y), Offset(size.width, center.y))
                drawLine(Color.White.copy(alpha = 0.3f), Offset(center.x, 0f), Offset(center.x, size.height))
                
                // Indicator dot
                val dx = (x / 10f).coerceIn(-1f, 1f) * (size.width / 2f)
                val dy = (y / 10f).coerceIn(-1f, 1f) * (size.height / 2f)
                drawCircle(Color(0xFFFF9800), radius = 6f, center = Offset(center.x + dx, center.y + dy))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
fun SatelliteRadarView(satellites: List<SatelliteInfo>) {
    val gpsList = satellites.filter { it.constellationType == 1 }
    val glonassList = satellites.filter { it.constellationType == 3 }
    val qzssList = satellites.filter { it.constellationType == 4 }
    val galileoList = satellites.filter { it.constellationType == 6 }
    val beidouList = satellites.filter { it.constellationType == 5 }
    val irnssList = satellites.filter { it.constellationType == 7 }
    val sbasList = satellites.filter { it.constellationType == 2 }
    val unknownList = satellites.filter { it.constellationType == 0 }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            RadarCanvas(satellites)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Legend & Statistics
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    LegendItem("GPS", gpsList.size, Color(0xFF4CAF50))
                    LegendItem("GLONASS", glonassList.size, Color(0xFF2196F3))
                    LegendItem("QZSS", qzssList.size, Color(0xFFFF9800))
                    LegendItem("GALILEO", galileoList.size, Color(0xFFF44336))
                }
                Column(modifier = Modifier.weight(1f)) {
                    LegendItem("BEIDOU", beidouList.size, Color(0xFF9C27B0))
                    LegendItem("IRNSS", irnssList.size, Color(0xFF00BCD4))
                    LegendItem("SBAS", sbasList.size, Color(0xFFFFEB3B))
                    LegendItem("UNKNOWN", unknownList.size, Color.White)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Đã sử dụng (${satellites.count { it.usedInFix }})", color = Color.White, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(Color(0xFF2196F3), CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Chưa sử dụng (${satellites.count { !it.usedInFix }})", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun LegendItem(name: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(modifier = Modifier.size(14.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(modifier = Modifier.width(8.dp))
        Text("$name ($count)", color = Color.White, fontSize = 13.sp)
    }
}

@Composable
fun RadarCanvas(satellites: List<SatelliteInfo>) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    Canvas(modifier = Modifier.size(300.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.width / 2f
        
        // Circular grid
        drawCircle(Color.White.copy(alpha = 0.2f), radius = radius, center = center, style = Stroke(2f))
        drawCircle(Color.White.copy(alpha = 0.2f), radius = radius * 0.66f, center = center, style = Stroke(1f))
        drawCircle(Color.White.copy(alpha = 0.2f), radius = radius * 0.33f, center = center, style = Stroke(1f))
        
        drawLine(Color.White.copy(alpha = 0.2f), Offset(0f, center.y), Offset(size.width, center.y))
        drawLine(Color.White.copy(alpha = 0.2f), Offset(center.x, 0f), Offset(center.x, size.height))

        // Scanning sweep
        drawArc(
            color = Color(0x334CAF50),
            startAngle = sweepAngle - 45f,
            sweepAngle = 45f,
            useCenter = true,
            topLeft = Offset(0f, 0f),
            size = size
        )

        // Satellites
        satellites.forEach { sat ->
            val angle = Math.toRadians(sat.azimuth.toDouble() - 90)
            // Elevation: 90 is center, 0 is horizon
            val dist = radius * (1.0 - sat.elevation / 90.0)
            val pos = Offset(
                center.x + dist.toFloat() * cos(angle).toFloat(),
                center.y + dist.toFloat() * sin(angle).toFloat()
            )
            
            val color = when(sat.constellationType) {
                1 -> Color(0xFF4CAF50) // GPS
                3 -> Color(0xFF2196F3) // GLONASS
                4 -> Color(0xFFFF9800) // QZSS
                6 -> Color(0xFFF44336) // GALILEO
                5 -> Color(0xFF9C27B0) // BEIDOU
                7 -> Color(0xFF00BCD4) // IRNSS
                2 -> Color(0xFFFFEB3B) // SBAS
                else -> Color.White
            }
            
            drawCircle(color, radius = 8f, center = pos)
            if (sat.usedInFix) {
                drawCircle(Color.White, radius = 10f, center = pos, style = Stroke(2f))
            }
        }
    }
}
