package com.baoverung.app.ui.converter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoverung.app.gis.CoordinateSystem
import com.baoverung.app.gis.CoordinateSystemConverter
import com.baoverung.app.ui.components.SystemSelector
import androidx.compose.ui.draw.alpha

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordinateConverterScreen(
    onBack: () -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Nhập tay", "Danh sách")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CHUYỂN ĐỔI TỌA ĐỘ", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontSize = 12.sp) }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> ManualConverter()
                1 -> ListConverter()
            }
        }
    }
}

@Composable
fun ManualConverter() {
    var inputX by remember { mutableStateOf("") }
    var inputY by remember { mutableStateOf("") }
    var sourceSystem by remember { mutableStateOf(CoordinateSystemConverter.SYSTEMS[1]) }
    var targetSystem by remember { mutableStateOf(CoordinateSystemConverter.SYSTEMS[0]) }
    
    var resultTextX by remember { mutableStateOf("") }
    var resultTextY by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("HỆ TỌA ĐỘ NGUỒN", fontWeight = FontWeight.Bold)
                SystemSelector(selected = sourceSystem, onSelect = { sourceSystem = it })
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = inputX, onValueChange = { inputX = it }, label = { Text("X / Lon") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = inputY, onValueChange = { inputY = it }, label = { Text("Y / Lat") }, modifier = Modifier.weight(1f))
                }
            }
        }

        Icon(Icons.Default.SyncAlt, null, modifier = Modifier.align(Alignment.CenterHorizontally))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("HỆ TỌA ĐỘ ĐÍCH", fontWeight = FontWeight.Bold)
                SystemSelector(selected = targetSystem, onSelect = { targetSystem = it })
            }
        }

        Button(
            onClick = {
                val x = inputX.toDoubleOrNull() ?: 0.0
                val y = inputY.toDoubleOrNull() ?: 0.0
                val (lat, lon) = if (sourceSystem.projection == "WGS84") Pair(y, x) 
                                 else CoordinateSystemConverter.toWgs84(x, y, sourceSystem)
                val (tx, ty) = CoordinateSystemConverter.fromWgs84(lat, lon, targetSystem)
                resultTextX = tx.toString()
                resultTextY = ty.toString()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("CHUYỂN ĐỔI")
        }

        if (resultTextX.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("KẾT QUẢ:", fontWeight = FontWeight.Bold)
                    Text("X: $resultTextX")
                    Text("Y: $resultTextY")
                }
            }
        }
    }
}

@Composable
fun ListConverter() {
    var inputText by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<String>>(emptyList()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            modifier = Modifier.fillMaxWidth().height(150.dp),
            label = { Text("Danh sách tọa độ (X Y)") }
        )
        Button(onClick = { /* Logic tương tự Manual */ }, modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth()) {
            Text("CHUYỂN ĐỔI HÀNG LOẠT")
        }
    }
}
