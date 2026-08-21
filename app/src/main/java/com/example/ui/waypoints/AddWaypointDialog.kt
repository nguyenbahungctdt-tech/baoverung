package com.baoverung.app.ui.waypoints

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoverung.app.data.model.GpsPoint
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.baoverung.app.gis.CoordinateSystem
import com.baoverung.app.gis.CoordinateSystemConverter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWaypointDialog(
    currentLocation: GpsPoint?,
    mapCenterLocation: GpsPoint,
    centralMeridian: Double,
    zoneDegrees: Int,
    activeCoordSystemId: String = "VN2000_3",
    provinceName: String = "",
    onSaveWaypoint: (title: String, description: String, lat: Double, lon: Double, shouldReport: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableIntStateOf(0) } // 0: GPS, 1: Map Center, 2: Manual
    
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    
    // Manual inputs
    var manualX by remember { mutableStateOf("") }
    var manualY by remember { mutableStateOf("") }
    var isVn2000Input by remember { mutableStateOf(true) }
    var manualLat by remember { mutableStateOf("") }
    var manualLon by remember { mutableStateOf("") }

    var overrideCm by remember { mutableDoubleStateOf(centralMeridian) }
    var overrideZd by remember { mutableIntStateOf(zoneDegrees) }
    var selectedProvName by remember { mutableStateOf(provinceName) }
    var showProvDropdown by remember { mutableStateOf(false) }

    val currLoc = currentLocation ?: GpsPoint(11.9404, 108.4378, 1480.0, 0f, 3.5f, 14)
    
    val activeLat: Double
    val activeLon: Double
    
    when (mode) {
        0 -> {
            activeLat = currLoc.latitude
            activeLon = currLoc.longitude
        }
        1 -> {
            activeLat = mapCenterLocation.latitude
            activeLon = mapCenterLocation.longitude
        }
        else -> {
            if (isVn2000Input) {
                val x = manualX.toDoubleOrNull() ?: 0.0
                val y = manualY.toDoubleOrNull() ?: 0.0
                val (lat, lon) = CoordinateSystemConverter.vn2000ToWgs84(x, y, overrideCm, overrideZd)
                activeLat = lat
                activeLon = lon
            } else {
                activeLat = manualLat.toDoubleOrNull() ?: 0.0
                activeLon = manualLon.toDoubleOrNull() ?: 0.0
            }
        }
    }

    val currentSystem = CoordinateSystemConverter.SYSTEMS.find { it.id == activeCoordSystemId }
        ?: CoordinateSystemConverter.SYSTEMS[2]

    // Calculate current coordinates based on active system (or overridden system if manual)
    val displaySystem = if (mode == 2 && isVn2000Input) {
        CoordinateSystem("TEMP", "Manual VN2000", "VN2000", overrideCm, overrideZd)
    } else {
        currentSystem
    }

    val (dX, dY) = CoordinateSystemConverter.fromWgs84(activeLat, activeLon, displaySystem)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ĐÁNH DẤU ĐIỂM THỰC ĐỊA", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Mode Selector
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val modes = listOf("GPS", "TÂM BẢN ĐỒ", "THỦ CÔNG")
                    modes.forEachIndexed { index, label ->
                        Button(
                            onClick = { mode = index },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (mode == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (mode == index) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (mode == 2) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = isVn2000Input, onClick = { isVn2000Input = true })
                                Text("Nhập VN2000 (X, Y)", fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                RadioButton(selected = !isVn2000Input, onClick = { isVn2000Input = false })
                                Text("Nhập WGS84", fontSize = 12.sp)
                            }
                            
                            if (isVn2000Input) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = manualX, onValueChange = { manualX = it }, label = { Text("X (Đông)") }, modifier = Modifier.weight(1f), singleLine = true)
                                    OutlinedTextField(value = manualY, onValueChange = { manualY = it }, label = { Text("Y (Bắc)") }, modifier = Modifier.weight(1f), singleLine = true)
                                }
                                
                                // Province/CM Selector for override
                                val systems = CoordinateSystemConverter.SYSTEMS.filter { it.projection == "VN2000" }
                                var searchQuery by remember { mutableStateOf("") }
                                val filteredSystems = remember(searchQuery) {
                                    systems.filter { it.name.contains(searchQuery, ignoreCase = true) || it.id.contains(searchQuery, ignoreCase = true) }
                                }

                                Box {
                                    OutlinedButton(
                                        onClick = { showProvDropdown = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        val cmStr = CoordinateSystemConverter.formatDegreeToDm(overrideCm)
                                        Text(if (selectedProvName.isEmpty()) "Chọn Tỉnh/Hệ tọa độ VN2000" else "VN2000 Múi $overrideZd° KT $cmStr ($selectedProvName)", fontSize = 11.sp)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                }

                                if (showProvDropdown) {
                                    AlertDialog(
                                        onDismissRequest = { showProvDropdown = false },
                                        title = { Text("CHỌN HỆ TỌA ĐỘ VN2000", fontWeight = FontWeight.Bold) },
                                        text = {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedTextField(
                                                    value = searchQuery,
                                                    onValueChange = { searchQuery = it },
                                                    placeholder = { Text("Tìm tên tỉnh...") },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    leadingIcon = { Icon(Icons.Default.Search, null) },
                                                    singleLine = true
                                                )
                                                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                                    items(filteredSystems) { sys ->
                                                        ListItem(
                                                            headlineContent = { Text(sys.name, fontSize = 12.sp) },
                                                            modifier = Modifier.clickable {
                                                                selectedProvName = sys.name.substringAfterLast("+ ").trim()
                                                                overrideCm = sys.centralMeridian
                                                                overrideZd = sys.zoneDegrees
                                                                showProvDropdown = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(onClick = { showProvDropdown = false }) { Text("ĐÓNG") }
                                        }
                                    )
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = manualLat, onValueChange = { manualLat = it }, label = { Text("Latitude") }, modifier = Modifier.weight(1f), singleLine = true)
                                    OutlinedTextField(value = manualLon, onValueChange = { manualLon = it }, label = { Text("Longitude") }, modifier = Modifier.weight(1f), singleLine = true)
                                }
                            }
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Tọa độ ghi nhận:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        
                        val coordDisplay = CoordinateSystemConverter.formatCoordinateDisplay(dX, dY, displaySystem, if (mode == 2) selectedProvName else provinceName)
                        Text(
                            text = coordDisplay,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        
                        Text(
                            text = "WGS84: ${String.format(java.util.Locale.US, "%.6f, %.6f", activeLat, activeLon)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tên điểm khảo sát *") },
                    modifier = Modifier.fillMaxWidth().testTag("waypoint_title_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Ghi chú thực địa") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("waypoint_desc_input")
                )
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onSaveWaypoint(title, description, activeLat, activeLon, true)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("save_report_waypoint_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LƯU & BÁO CÁO (EMAIL)")
                }
                OutlinedButton(
                    onClick = {
                        onSaveWaypoint(title, description, activeLat, activeLon, false)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("save_local_waypoint_btn")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CHỈ LƯU MÁY (LOCAL)")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 80.dp)) {
                Text("HỦY BỎ")
            }
        }
    )
}
