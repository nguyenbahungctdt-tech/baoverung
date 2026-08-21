package com.baoverung.app.ui.converter

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoverung.app.gis.CoordinateSystem
import com.baoverung.app.gis.CoordinateSystemConverter
import com.baoverung.app.ui.components.SystemSelector
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordinateConverterScreen(
    onBack: () -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Nhập tay", "Danh sách", "Tệp (CSV/Excel)")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CHUYỂN ĐỔI HỆ TỌA ĐỘ", fontWeight = FontWeight.Bold) },
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
                2 -> FileConverter()
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
    
    var resultLat by remember { mutableStateOf("") }
    var resultLon by remember { mutableStateOf("") }
    var resultX by remember { mutableStateOf("") }
    var resultY by remember { mutableStateOf("") }
    
    // Store raw WGS84 results for DMS display
    var rawWgs84Lat by remember { mutableDoubleStateOf(0.0) }
    var rawWgs84Lon by remember { mutableDoubleStateOf(0.0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("HỆ TỌA ĐỘ NGUỒN", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                SystemSelector(selected = sourceSystem, onSelect = { sourceSystem = it })
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = inputX,
                        onValueChange = { inputX = it },
                        label = { Text(if (sourceSystem.projection == "WGS84") "Longitude" else "Kinh độ X / Easting") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = inputY,
                        onValueChange = { inputY = it },
                        label = { Text(if (sourceSystem.projection == "WGS84") "Latitude" else "Vĩ độ Y / Northing") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Icon(Icons.Default.SyncAlt, contentDescription = null, modifier = Modifier.align(Alignment.CenterHorizontally).size(32.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("HỆ TỌA ĐỘ ĐÍCH", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                SystemSelector(selected = targetSystem, onSelect = { targetSystem = it })
            }
        }

        Button(
            onClick = {
                val x = inputX.toDoubleOrNull() ?: 0.0
                val y = inputY.toDoubleOrNull() ?: 0.0
                
                // Convert source to WGS84 first
                val (lat, lon) = if (sourceSystem.projection == "WGS84") Pair(y, x) 
                                 else CoordinateSystemConverter.toWgs84(x, y, sourceSystem)
                
                // Convert WGS84 to target
                val (tx, ty) = CoordinateSystemConverter.fromWgs84(lat, lon, targetSystem)
                
                if (targetSystem.projection == "WGS84") {
                    rawWgs84Lat = ty
                    rawWgs84Lon = tx
                    if (targetSystem.id == "WGS84_DMS") {
                        resultLon = CoordinateSystemConverter.formatDecimalToDms(tx)
                        resultLat = CoordinateSystemConverter.formatDecimalToDms(ty)
                    } else {
                        resultLon = String.format(Locale.getDefault(), "%.6f", tx)
                        resultLat = String.format(Locale.getDefault(), "%.6f", ty)
                    }
                    resultX = ""; resultY = ""
                } else {
                    resultX = String.format(Locale.getDefault(), "%.2f", tx)
                    resultY = String.format(Locale.getDefault(), "%.2f", ty)
                    resultLon = ""; resultLat = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("CHUYỂN ĐỔI")
        }

        if (resultLat.isNotEmpty() || resultX.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("KẾT QUẢ CHUYỂN ĐỔI:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    
                    if (resultLat.isNotEmpty()) {
                        val decimalLon = String.format(Locale.getDefault(), "%.6f", rawWgs84Lon)
                        val decimalLat = String.format(Locale.getDefault(), "%.6f", rawWgs84Lat)
                        val dmsLonStr = CoordinateSystemConverter.formatDecimalToDms(rawWgs84Lon)
                        val dmsLatStr = CoordinateSystemConverter.formatDecimalToDms(rawWgs84Lat)
                        
                        Column {
                            Text("Tọa độ Thập phân (Decimal):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Kinh độ (Lon): $decimalLon", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text("Vĩ độ (Lat): $decimalLat", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp).alpha(0.3f))

                        Column {
                            Text("Tọa độ Độ Phút Giây (DMS):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Lon: $dmsLonStr", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text("Lat: $dmsLatStr", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    } else {
                        Column {
                            Text("Tọa độ VN2000 / UTM / HN72:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("X (Easting): $resultX", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Y (Northing): $resultY", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ListConverter() {
    var inputText by remember { mutableStateOf("") }
    var sourceSystem by remember { mutableStateOf(CoordinateSystemConverter.SYSTEMS[1]) }
    var targetSystem by remember { mutableStateOf(CoordinateSystemConverter.SYSTEMS[0]) }
    var results by remember { mutableStateOf<List<String>>(emptyList()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Nhập danh sách tọa độ (mỗi dòng một cặp X, Y hoặc X Y):", fontSize = 13.sp)
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            modifier = Modifier.fillMaxWidth().height(150.dp),
            placeholder = { Text("Ví dụ:\n543210 1234567\n543220 1234570") }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Từ:", fontSize = 11.sp)
                SystemSelector(sourceSystem) { sourceSystem = it }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Sang:", fontSize = 11.sp)
                SystemSelector(targetSystem) { targetSystem = it }
            }
        }

        Button(
            onClick = {
                val lines = inputText.split("\n")
                val res = mutableListOf<String>()
                lines.forEach { line ->
                    val parts = line.trim().split(Regex("[,\\s\\t]+"))
                    if (parts.size >= 2) {
                        val x = parts[0].toDoubleOrNull() ?: 0.0
                        val y = parts[1].toDoubleOrNull() ?: 0.0
                        val (lat, lon) = if (sourceSystem.projection == "WGS84") Pair(y, x) 
                                         else CoordinateSystemConverter.toWgs84(x, y, sourceSystem)
                        val (tx, ty) = CoordinateSystemConverter.fromWgs84(lat, lon, targetSystem)
                        val formatted = if (targetSystem.projection == "WGS84") {
                            if (targetSystem.id == "WGS84_DMS") {
                                "${CoordinateSystemConverter.formatDecimalToDms(tx)}, ${CoordinateSystemConverter.formatDecimalToDms(ty)}"
                            } else {
                                "${String.format(Locale.getDefault(), "%.6f", tx)}, ${String.format(Locale.getDefault(), "%.6f", ty)}"
                            }
                        } else {
                            "${String.format("%.2f", tx)}, ${String.format("%.2f", ty)}"
                        }
                        res.add(formatted)
                    }
                }
                results = res
            },
            modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth()
        ) {
            Text("CHUYỂN ĐỔI HÀNG LOẠT")
        }

        if (results.isNotEmpty()) {
            Text("Kết quả (${results.size}):", fontWeight = FontWeight.Bold)
            LazyColumn(modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(8.dp)) {
                items(results) { item ->
                    Text(item, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
fun FileConverter() {
    val context = LocalContext.current
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var sourceSystem by remember { mutableStateOf(CoordinateSystemConverter.SYSTEMS[1]) }
    var targetSystem by remember { mutableStateOf(CoordinateSystemConverter.SYSTEMS[0]) }
    var processedCount by remember { mutableStateOf(0) }
    var exportPath by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedFileUri = it
            fileName = getFileName(context, it)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Chọn tệp CSV hoặc Excel (dạng CSV) có chứa cột tọa độ để chuyển đổi và xuất tệp mới.", fontSize = 14.sp)
        
        OutlinedButton(onClick = { filePicker.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.UploadFile, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (fileName.isEmpty()) "CHỌN TỆP TIN" else fileName)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Nguồn:", fontSize = 11.sp)
                SystemSelector(sourceSystem) { sourceSystem = it }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Đích:", fontSize = 11.sp)
                SystemSelector(targetSystem) { targetSystem = it }
            }
        }

        Button(
            onClick = {
                selectedFileUri?.let { uri ->
                    val result = processFile(context, uri, sourceSystem, targetSystem)
                    processedCount = result.first
                    exportPath = result.second
                }
            },
            enabled = selectedFileUri != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("CHUYỂN ĐỔI VÀ XUẤT FILE")
        }

        if (processedCount > 0) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("THÀNH CÔNG!", fontWeight = FontWeight.Bold)
                    Text("Đã xử lý $processedCount điểm tọa độ.")
                    Text("Tệp kết quả: ${exportPath.substringAfterLast("/")}", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tệp đã được lưu trong bộ nhớ máy (Download/BaoVeRung_Converted)", fontSize = 11.sp)
                }
            }
        }
    }
}

private fun getFileName(context: Context, uri: Uri): String {
    var name = ""
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst()) name = cursor.getString(nameIndex)
    }
    return name
}

private fun processFile(context: Context, uri: Uri, source: CoordinateSystem, target: CoordinateSystem): Pair<Int, String> {
    var count = 0
    val outputDir = File(context.getExternalFilesDir(null), "Converted")
    outputDir.mkdirs()
    val outputFile = File(outputDir, "Converted_${System.currentTimeMillis()}.csv")
    
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val writer = FileOutputStream(outputFile).bufferedWriter()
        
        val header = reader.readLine()
        if (header != null) {
            writer.write("$header,Converted_X,Converted_Y\n")
        }
        
        reader.forEachLine { line ->
            val parts = line.split(Regex("[,\\t]"))
            if (parts.size >= 2) {
                // Heuristic to find X/Y: look for numbers that look like coords
                val x = parts.find { it.toDoubleOrNull() != null }?.toDouble() ?: 0.0
                val y = parts.findLast { it.toDoubleOrNull() != null }?.toDouble() ?: 0.0
                
                val (lat, lon) = if (source.projection == "WGS84") Pair(y, x) 
                                 else CoordinateSystemConverter.toWgs84(x, y, source)
                val (tx, ty) = CoordinateSystemConverter.fromWgs84(lat, lon, target)
                
                val formattedX = if (target.id == "WGS84_DMS") CoordinateSystemConverter.formatDecimalToDms(tx) 
                                 else String.format(Locale.getDefault(), "%.6f", tx)
                val formattedY = if (target.id == "WGS84_DMS") CoordinateSystemConverter.formatDecimalToDms(ty) 
                                 else String.format(Locale.getDefault(), "%.6f", ty)
                                 
                writer.write("$line,$formattedX,$formattedY\n")
                count++
            }
        }
        writer.close()
        reader.close()
    } catch (e: Exception) { e.printStackTrace() }
    
    return Pair(count, outputFile.absolutePath)
}
