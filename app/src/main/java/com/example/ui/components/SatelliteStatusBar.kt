package com.baoverung.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoverung.app.data.model.GpsPoint
import com.baoverung.app.gis.CoordinateSystemConverter
import com.baoverung.app.ui.SyncStatus

@Composable
fun SatelliteStatusBar(
    currentLocation: GpsPoint?,
    satVisible: Int = 0,
    provinceName: String = "Lâm Đồng",
    centralMeridian: Double = 107.75,
    zoneDegrees: Int = 3,
    activeCoordinateSystem: String = "VN2000",
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    modifier: Modifier = Modifier
) {
    val accuracy = currentLocation?.accuracy ?: 15.0f
    val satCount = currentLocation?.satellitesCount ?: 0

    // Signal Status Color: Green (<5m), Yellow (5m-10m), Red (>10m)
    val (statusColor, statusText) = when {
        accuracy <= 5.0f -> Color(0xFF2E7D32) to "Tín hiệu Tốt"
        accuracy <= 10.0f -> Color(0xFFF57F17) to "Trung bình"
        else -> Color(0xFFC62828) to "Tín hiệu Yếu"
    }

    Surface(
        modifier = modifier.testTag("satellite_status_bar"),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Satellites Count
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SatelliteAlt,
                        contentDescription = "Vệ tinh",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Vệ tinh: $satCount/$satVisible",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Accuracy Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (accuracy > 15f) Icons.Default.Warning else Icons.Default.GpsFixed,
                        contentDescription = "Độ chính xác",
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Sai số: ±${String.format("%.1f", accuracy)}m",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

                // Cloud Sync Status
                val infiniteTransition = rememberInfiniteTransition(label = "sync")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "syncAlpha"
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (syncIcon, syncColor, _) = when (syncStatus) {
                        SyncStatus.SYNCED -> Triple(Icons.Default.CloudDone, Color(0xFF2E7D32), "Đã đồng bộ")
                        SyncStatus.PENDING -> Triple(Icons.Default.Cloud, Color(0xFFF57F17), "Chờ đồng bộ")
                        SyncStatus.SYNCING -> Triple(Icons.Default.CloudSync, MaterialTheme.colorScheme.primary, "")
                    }

                    Icon(
                        imageVector = syncIcon,
                        contentDescription = null,
                        tint = syncColor,
                        modifier = Modifier
                            .size(18.dp)
                            .then(if (syncStatus == SyncStatus.SYNCING) Modifier.graphicsLayer(alpha = alpha) else Modifier)
                    )
                }
            }
        }
    }
}
