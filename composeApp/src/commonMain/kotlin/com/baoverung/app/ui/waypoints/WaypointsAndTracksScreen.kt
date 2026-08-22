package com.baoverung.app.ui.waypoints

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.baoverung.app.data.local.entity.*
import com.baoverung.app.data.model.GpsPoint
import com.baoverung.app.gis.GisAreaCalculator
import com.baoverung.app.util.toDateTimeString
import com.baoverung.app.util.toDateString
import com.baoverung.app.util.parseHexColor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun CardStatusIcons(isSynced: Boolean, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (isSynced) Icons.Default.CloudDone else Icons.Default.CloudQueue, null, tint = if (isSynced) Color(0xFF2E7D32) else Color.Gray, modifier = Modifier.size(16.dp))
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun CardActionButtons(onDetail: () -> Unit, onNavigate: (() -> Unit)? = null) {
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onDetail) { Text("CHI TIẾT", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        onNavigate?.let {
            Button(onClick = it, modifier = Modifier.height(32.dp), shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Default.Navigation, null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("DẪN ĐƯỜNG", fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun WaypointCard(wp: WaypointEntity, onDelete: (Long) -> Unit, onNavigate: (GpsPoint) -> Unit, onDetail: (WaypointEntity) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(70.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (!wp.photoPath.isNullOrEmpty()) AsyncImage(model = wp.photoPath, contentDescription = null, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Default.Place, null, modifier = Modifier.align(Alignment.Center), tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(wp.title.ifEmpty { "Điểm không tên" }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    CardStatusIcons(wp.isSynced, { onDelete(wp.id) })
                }
                Text(wp.timestampUtc.toDateTimeString(), fontSize = 11.sp, color = Color.Gray)
                CardActionButtons({ onDetail(wp) }, { onNavigate(GpsPoint(wp.latitude, wp.longitude)) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaypointsAndTracksScreen(
    waypoints: List<WaypointEntity>,
    trackLogs: List<TrackLogEntity>,
    patrolLogs: List<PatrolLogEntity>,
    floraFaunaLogs: List<FloraFaunaLogEntity>,
    naturalImpactLogs: List<NaturalImpactLogEntity>,
    polygons: List<PolygonEntity>,
    dailyJournals: List<DailyJournalEntity>,
    onDeleteWaypoint: (Long) -> Unit,
    onDeleteTrackLog: (Long) -> Unit,
    onDeletePatrolLog: (Long) -> Unit,
    onNavigateToPoint: (GpsPoint) -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("ĐIỂM", "TRACKLOG", "SỰ VỤ", "NHẬT KÝ")

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("QUẢN LÝ DỮ LIỆU", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } })
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { idx, title -> Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(title, fontSize = 11.sp) }) }
            }
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (selectedTab) {
                    0 -> items(waypoints) { wp -> WaypointCard(wp, onDeleteWaypoint, onNavigateToPoint, {}) }
                    1 -> items(trackLogs) { trk -> 
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(trk.title, fontWeight = FontWeight.Bold)
                                    CardStatusIcons(trk.isSynced, { onDeleteTrackLog(trk.id) })
                                }
                                Text("${(trk.totalDistanceMeters/1000).toInt()} km - ${trk.startTimeUtc.toDateString()}", fontSize = 12.sp)
                            }
                        }
                    }
                    2 -> items(patrolLogs) { pt ->
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(pt.incidentType, fontWeight = FontWeight.Bold, color = Color.Red)
                                    CardStatusIcons(pt.isSynced, { onDeletePatrolLog(pt.id) })
                                }
                                Text("Cán bộ: ${pt.leaderName}", fontSize = 12.sp)
                            }
                        }
                    }
                    3 -> items(dailyJournals) { j ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(j.dateStr, fontWeight = FontWeight.Bold)
                                Text(j.content, maxLines = 2, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
