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
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.baoverung.app.data.local.entity.*
import com.baoverung.app.data.model.GpsPoint
import com.baoverung.app.gis.GisAreaCalculator
import com.baoverung.app.util.toDateTimeString

@Composable
fun CardStatusIcons(
    isSynced: Boolean,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onDelete: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isSynced) {
            Icon(Icons.Default.CloudDone, "Synced", tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
        } else {
            Icon(Icons.Default.CloudQueue, "Pending", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onToggleVisibility, modifier = Modifier.size(32.dp)) {
            Icon(imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = if (isVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun CardActionButtons(
    onEdit: () -> Unit = {},
    onDetail: () -> Unit,
    onReport: () -> Unit,
    onNavigate: (() -> Unit)? = null,
    onOpenMap: (() -> Unit)? = null
) {
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        TextButton(onClick = onDetail, modifier = Modifier.height(32.dp)) { 
            Text("CHI TIẾT", fontSize = 10.sp, fontWeight = FontWeight.Black) 
        }
        Spacer(modifier = Modifier.width(4.dp))
        
        IconButton(onClick = onReport, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Email, null, tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
        }
        
        onNavigate?.let {
            Spacer(modifier = Modifier.width(4.dp))
            Button(onClick = it, modifier = Modifier.height(32.dp), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Default.Navigation, null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("DẪN ĐƯỜNG", fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }

        onOpenMap?.let {
            Spacer(modifier = Modifier.width(4.dp))
            Button(onClick = it, modifier = Modifier.height(32.dp), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Default.Map, null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("MỞ ĐƯỜNG", fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun WaypointCard(
    wp: WaypointEntity,
    isVisible: Boolean,
    onToggleVisibility: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onNavigate: (GpsPoint) -> Unit,
    onDetail: (WaypointEntity) -> Unit,
    onBack: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.size(85.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (!wp.photoPath.isNullOrEmpty()) {
                    AsyncImage(model = wp.photoPath, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                } else {
                    Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), modifier = Modifier.align(Alignment.Center).size(36.dp))
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Text(wp.title.ifEmpty { "Điểm không tên" }, fontWeight = FontWeight.Black, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                    CardStatusIcons(wp.isSynced, isVisible, { onToggleVisibility(wp.id) }, { onDelete(wp.id) })
                }
                Text(wp.timestampUtc.toDateTimeString(), fontSize = 11.sp, color = Color.Gray)
                CardActionButtons(
                    onDetail = { onDetail(wp) },
                    onReport = { /* TODO */ },
                    onNavigate = { onNavigate(GpsPoint(wp.latitude, wp.longitude)); onBack() }
                )
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
    val tabs = listOf("ĐIỂM", "TRACKLOG", "VÙNG", "SỰ VỤ")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QUẢN LÝ DỮ LIỆU", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title, fontSize = 12.sp) })
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (selectedTab) {
                    0 -> items(waypoints) { wp -> WaypointCard(wp, true, {}, onDeleteWaypoint, onNavigateToPoint, {}, onBack) }
                    // Các tab khác sẽ được bổ sung tương tự
                }
            }
        }
    }
}
