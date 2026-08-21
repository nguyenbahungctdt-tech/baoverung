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
import com.baoverung.app.util.toDateTimeString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatrolLogFormScreen(
    currentLocation: GpsPoint?,
    userEmail: String,
    userName: String,
    centralMeridian: Double,
    zoneDegrees: Int,
    onSubmitPatrolLog: (
        incidentType: String,
        leaderName: String,
        violationTime: String,
        violationLocation: String,
        violatorName: String,
        violatorIdCard: String,
        violatorAddress: String,
        violatorPhone: String,
        confiscatedTools: String,
        relatedPersons: String,
        onSiteAction: String,
        onSiteRecordings: String,
        notes: String,
        photoPaths: List<String>,
        violationField: String,
        shouldReport: Boolean
    ) -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Form states
    var violationField by remember { mutableStateOf("Lâm nghiệp") }
    var incidentType by remember { mutableStateOf("") }
    var leaderName by remember { mutableStateOf(userName) }
    var violationLocation by remember { mutableStateOf("") }
    var violatorName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val timeStr = currentLocation?.timestampUtc?.toDateTimeString() ?: "Đang tìm GPS..."

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NHẬT KÝ SỰ VỤ", fontWeight = FontWeight.Black, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            onSubmitPatrolLog(
                                incidentType, leaderName, timeStr, violationLocation,
                                violatorName, "", "", "", "", "",
                                "", "", notes, emptyList(), violationField, true
                            )
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("BÁO CÁO", fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("VỊ TRÍ HIỆN TRƯỜNG", fontWeight = FontWeight.Black, fontSize = 12.sp)
                    Text("Thời gian: $timeStr", fontSize = 11.sp)
                    if (currentLocation != null) {
                        Text("${currentLocation.latitude}, ${currentLocation.longitude}", fontWeight = FontWeight.Bold)
                    }
                }
            }

            OutlinedTextField(
                value = incidentType,
                onValueChange = { incidentType = it },
                label = { Text("Sự vụ phát hiện") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = leaderName,
                onValueChange = { leaderName = it },
                label = { Text("Cán bộ tổ trưởng") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = violationLocation,
                onValueChange = { violationLocation = it },
                label = { Text("Địa điểm (Lô/Khoảnh)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = violatorName,
                onValueChange = { violatorName = it },
                label = { Text("Đối tượng vi phạm") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Ghi chú") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
