package com.baoverung.app.ui.waypoints

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.baoverung.app.data.local.entity.WaypointEntity
import com.baoverung.app.data.model.GpsPoint
import com.baoverung.app.ui.MainViewModel
import com.baoverung.app.util.toDateTimeString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaypointsAndTracksScreen(
    viewModel: MainViewModel,
    onNavigateToPoint: (GpsPoint) -> Unit,
    onBack: () -> Unit
) {
    val waypoints by viewModel.waypoints.collectAsState()
    val trackLogs by viewModel.trackLogs.collectAsState()
    
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

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        items(waypoints) { wp ->
                            WaypointCard(wp, onNavigate = onNavigateToPoint)
                        }
                    }
                    1 -> {
                        // Tracklogs items
                    }
                }
            }
        }
    }
}

@Composable
fun WaypointCard(
    wp: WaypointEntity,
    onNavigate: (GpsPoint) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(wp.title.ifEmpty { "Điểm không tên" }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(wp.timestampUtc.toDateTimeString(), fontSize = 11.sp, color = Color.Gray)
            Text("${wp.latitude}, ${wp.longitude}", fontSize = 12.sp)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onNavigate(GpsPoint(wp.latitude, wp.longitude)) }) {
                    Text("DẪN ĐƯỜNG")
                }
            }
        }
    }
}
