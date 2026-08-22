package com.baoverung.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoverung.app.data.model.GpsPoint
import com.baoverung.app.ui.SyncStatus

@Composable
fun SatelliteStatusBar(
    currentLocation: GpsPoint?,
    satVisible: Int = 0,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    modifier: Modifier = Modifier
) {
    val accuracy = currentLocation?.accuracy ?: 15.0f
    val satCount = currentLocation?.satellitesCount ?: 0

    val (statusColor, statusText) = when {
        accuracy <= 5.0f -> Color(0xFF2E7D32) to "Tốt"
        accuracy <= 10.0f -> Color(0xFFF57F17) to "Trung bình"
        else -> Color(0xFFC62828) to "Yếu"
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SatelliteAlt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Vệ tinh: $satCount/$satVisible", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (accuracy > 15f) Icons.Default.Warning else Icons.Default.GpsFixed, null, tint = statusColor, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Sai số: ±${accuracy.toInt()}m", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusColor)
            }
        }
    }
}
