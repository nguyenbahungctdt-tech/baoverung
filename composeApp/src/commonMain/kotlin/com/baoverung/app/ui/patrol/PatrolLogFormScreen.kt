package com.baoverung.app.ui.patrol

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.baoverung.app.data.model.GpsPoint
import com.baoverung.app.gis.CoordinateSystemConverter
import com.baoverung.app.util.format
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatrolLogFormScreen(
    currentLocation: GpsPoint?,
    userEmail: String,
    userName: String,
    centralMeridian: Double,
    zoneDegrees: Int,
    activeCoordSystemId: String = "VN2000_3",
    onSubmitPatrolLog: (
        incidentType: String,
        leaderName: String,
        violationTime: String,
        violationLocation: String,
        violatorName: String,
        onSiteAction: String,
        notes: String,
        photoPaths: List<String>,
        shouldReport: Boolean
    ) -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Form states
    var violationField by remember { mutableStateOf("Lâm nghiệp") }
    var incidentType by remember { mutableStateOf("Phá rừng trái pháp luật (Điều 23)") }
    var leaderName by remember { mutableStateOf(userName) }
    var violationTime by remember { 
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        mutableStateOf("${now.hour}:${now.minute} ${now.dayOfMonth}/${now.monthNumber}/${now.year}")
    }
    var violationLocation by remember { mutableStateOf("") }
    var violatorName by remember { mutableStateOf("") }
    var onSiteAction by remember { mutableStateOf("Lập biên bản tại chỗ") }
    var notes by remember { mutableStateOf("") }
    var selectedPhotoPaths by remember { mutableStateOf<List<String>>(emptyList()) }

    val accuracy = currentLocation?.accuracy ?: 1000f
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NHẬT KÝ SỰ VỤ", fontWeight = FontWeight.Black, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { 
                            onSubmitPatrolLog(incidentType, leaderName, violationTime, violationLocation, violatorName, onSiteAction, notes, selectedPhotoPaths, true)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("GỬI BÁO CÁO", fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // Location Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("VỊ TRÍ HIỆN TRƯỜNG", fontWeight = FontWeight.Black, fontSize = 12.sp)
                    if (currentLocation != null) {
                        Text("Lat: ${currentLocation.latitude.format(6)}, Lon: ${currentLocation.longitude.format(6)}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Sai số: ±${accuracy.toDouble().format(1)}m", fontSize = 11.sp)
                    } else {
                        Text("ĐANG TÌM GPS...", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text("THÔNG TIN CHI TIẾT", fontWeight = FontWeight.Black, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            
            OutlinedTextField(value = incidentType, onValueChange = { incidentType = it }, label = { Text("Loại sự vụ") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = leaderName, onValueChange = { leaderName = it }, label = { Text("Tổ trưởng") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = violationTime, onValueChange = { violationTime = it }, label = { Text("Thời gian") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = violationLocation, onValueChange = { violationLocation = it }, label = { Text("Địa điểm (Lô/Khoảnh)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = violatorName, onValueChange = { violatorName = it }, label = { Text("Đối tượng vi phạm") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = onSiteAction, onValueChange = { onSiteAction = it }, label = { Text("Xử lý tại chỗ") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Ghi chú") }, modifier = Modifier.fillMaxWidth(), minLines = 3)

            // Photo Section Placeholder
            Surface(modifier = Modifier.fillMaxWidth().height(100.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Phần đính kèm ảnh (Sắp có trên iOS)")
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
