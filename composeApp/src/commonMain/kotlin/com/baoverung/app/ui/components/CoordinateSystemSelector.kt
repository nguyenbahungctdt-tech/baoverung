package com.baoverung.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoverung.app.gis.CoordinateSystem
import com.baoverung.app.gis.CoordinateSystemConverter

@Composable
fun SystemSelector(selected: CoordinateSystem, onSelect: (CoordinateSystem) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { showDialog = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(selected.name, maxLines = 1, modifier = Modifier.weight(1f), textAlign = TextAlign.Left, fontSize = 14.sp)
            Icon(Icons.Default.ArrowDropDown, null)
        }
    }

    if (showDialog) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredSystems = remember(searchQuery) {
            CoordinateSystemConverter.SYSTEMS.filter { 
                it.name.contains(searchQuery, ignoreCase = true) || it.id.contains(searchQuery, ignoreCase = true)
            }
        }

        val groupedSystems = remember(filteredSystems) {
            filteredSystems.groupBy { 
                when {
                    it.projection == "WGS84" -> "Hệ tọa độ Quốc tế (WGS 84)"
                    it.projection == "VN2000" && it.zoneDegrees == 3 -> "VN2000 Múi 3° (Theo Tỉnh)"
                    it.projection == "VN2000" && it.zoneDegrees == 6 -> "VN2000 Múi 6° (Toàn quốc)"
                    it.projection == "UTM" -> "Hệ lưới chiếu UTM WGS 84"
                    it.projection == "HN72" -> "Hệ tọa độ HN-72 (Krasovsky)"
                    else -> "Hệ tọa độ khác"
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("CHỌN HỆ TỌA ĐỘ", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Tìm kiếm (Tên hoặc EPSG)...") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 500.dp)) {
                        groupedSystems.forEach { (header, systems) ->
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = header,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            items(systems, key = { it.id }) { sys ->
                                ListItem(
                                    headlineContent = { 
                                        Text(
                                            sys.name, 
                                            fontWeight = if (sys.id == selected.id) FontWeight.Bold else FontWeight.Normal,
                                            color = if (sys.id == selected.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 13.sp
                                        ) 
                                    },
                                    supportingContent = {
                                        Text("ID: ${sys.id} | CM: ${sys.centralMeridian}°", fontSize = 11.sp)
                                    },
                                    leadingContent = { 
                                        Icon(
                                            if (sys.projection == "WGS84") Icons.Default.Public else Icons.Default.Map, 
                                            null,
                                            tint = if (sys.id == selected.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        ) 
                                    },
                                    modifier = Modifier.clickable { 
                                        onSelect(sys)
                                        showDialog = false 
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("ĐÓNG") }
            }
        )
    }
}
