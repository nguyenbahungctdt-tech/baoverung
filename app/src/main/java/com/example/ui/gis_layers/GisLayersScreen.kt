package com.baoverung.app.ui.gis_layers

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoverung.app.ui.MainViewModel
import com.baoverung.app.data.local.entity.GisLayerEntity
import com.baoverung.app.gis.CoordinateSystem
import com.baoverung.app.gis.CoordinateSystemConverter
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class GisImportType(val displayName: String, val extensions: List<String>, val mimeTypes: List<String>) {
    MBTILES("Nền (MBTiles)", listOf("mbtiles"), listOf("application/octet-stream")),
    KML_KMZ("Google Earth (KML)", listOf("kml", "kmz"), listOf("application/vnd.google-earth.kml+xml", "application/vnd.google-earth.kmz", "application/octet-stream")),
    SHP("QGis (SHP)", listOf("shp"), listOf("application/x-qgis-shp", "application/x-shp", "application/octet-stream")),
    TAB("Mapinfo (TAB/MIF)", listOf("tab", "mif"), listOf("application/x-mapinfo", "text/plain", "application/octet-stream")),
    GEOJSON_JSON("GeoJSON", listOf("geojson", "json"), listOf("application/geo+json", "application/json")),
    RASTER("Ảnh (TIFF, JPG)", listOf("tif", "tiff", "jpg", "png"), listOf("image/tiff", "image/jpeg", "image/png"))
}

private fun scanDeviceForGisFiles(type: GisImportType): List<File> {
    val results = mutableListOf<File>()
    val root = Environment.getExternalStorageDirectory()
    
    fun recursiveScan(dir: File) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (file.name.startsWith(".") || file.name == "Android") continue
                recursiveScan(file)
            } else {
                if (type.extensions.any { file.extension.lowercase() == it }) {
                    results.add(file)
                }
            }
        }
    }
    
    recursiveScan(root)
    return results.sortedByDescending { it.lastModified() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GisLayersScreen(
    gisLayers: List<GisLayerEntity>,
    onToggleVisibility: (GisLayerEntity) -> Unit,
    onUpdateOpacity: (GisLayerEntity, Float) -> Unit,
    onMoveLayerUp: (GisLayerEntity) -> Unit,
    onMoveLayerDown: (GisLayerEntity) -> Unit,
    onEditLayerCoordSys: (Long, Double, Int) -> Unit,
    onZoomToLayer: (GisLayerEntity) -> Unit,
    onUpdateName: (Long, String) -> Unit,
    onUpdateLabelColumn: (Long, String?) -> Unit,
    getLayerFieldNames: suspend (GisLayerEntity) -> List<String>,
    onAddGisLayer: (name: String, fileType: String, filePath: String, cm: Double, zd: Int, labelColumn: String?) -> Unit,
    onDeleteGisLayer: (Long) -> Unit,
    onImportGisLayer: (source: Any) -> Unit,
    onFinalizeImport: (Double, Int, String?) -> Unit = { _, _, _ -> },
    onCancelImport: () -> Unit = {},
    importState: MainViewModel.ImportState = MainViewModel.ImportState(),
    onRefreshLayer: (Long) -> Unit = {},
    onUpdateColors: (Long, String, String) -> Unit = { _, _, _ -> },
    onBack: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<GisImportType?>(null) }
    var scannedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var layerToRename by remember { mutableStateOf<GisLayerEntity?>(null) }
    var layerToLabel by remember { mutableStateOf<GisLayerEntity?>(null) }
    var layerToShowInfo by remember { mutableStateOf<GisLayerEntity?>(null) }

    val hasManagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportGisLayer(uri)
        }
    }

    fun launchPickerForType(type: GisImportType?) {
        val mimes = when (type) {
            GisImportType.MBTILES -> arrayOf("application/octet-stream", "application/x-sqlite3")
            GisImportType.KML_KMZ -> arrayOf("application/vnd.google-earth.kml+xml", "application/vnd.google-earth.kmz", "application/octet-stream")
            GisImportType.SHP -> arrayOf("application/x-qgis-shp", "application/x-shp", "application/octet-stream")
            GisImportType.TAB -> arrayOf("application/x-mapinfo", "text/plain", "application/octet-stream")
            GisImportType.GEOJSON_JSON -> arrayOf("application/geo+json", "application/json")
            GisImportType.RASTER -> arrayOf("image/tiff", "image/jpeg", "image/png")
            else -> arrayOf("*/*")
        }
        filePickerLauncher.launch(mimes)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QUẢN LÝ LỚP BẢN ĐỒ (GIS)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { paddingValues: PaddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // 0. Permission Warning
            if (!hasManagePermission) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Cần quyền Truy cập tất cả các tệp", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Để quét và nạp file GIS từ bộ nhớ máy, vui lòng cấp quyền trong cài đặt.", fontSize = 12.sp)
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                    intent.data = Uri.parse("package:${context.packageName}")
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        ) { Text("ĐI ĐẾN CÀI ĐẶT", fontSize = 12.sp) }
                    }
                }
            }

            // 1. Horizontal Category Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GisImportType.values().forEach { type ->
                    FilterChip(
                        selected = selectedCategory == type,
                        onClick = {
                            selectedCategory = type
                            isScanning = true
                            scope.launch(Dispatchers.IO) {
                                val files = scanDeviceForGisFiles(type)
                                withContext(Dispatchers.Main) {
                                    scannedFiles = files
                                    isScanning = false
                                }
                            }
                        },
                        label = { Text(type.displayName, fontSize = 12.sp) },
                        leadingIcon = if (selectedCategory == type) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            // 2. File Scanner Results (If category selected)
            if (selectedCategory != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("TỆP ĐÃ TÌM THẤY (${scannedFiles.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            if (isScanning) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            IconButton(onClick = { selectedCategory = null }) { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) }
                        }
                        
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            // SAF Manual Entry
                            item {
                                ListItem(
                                    headlineContent = { Text("Chọn tệp thủ công từ bộ nhớ...", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                                    supportingContent = { Text("Sử dụng trình duyệt file hệ thống (SAF)", fontSize = 10.sp) },
                                    leadingContent = { Icon(Icons.Default.FileUpload, null, tint = MaterialTheme.colorScheme.primary) },
                                    modifier = Modifier.clickable { 
                                        launchPickerForType(selectedCategory)
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                            }

                            if (scannedFiles.isEmpty() && !isScanning) {
                                item {
                                    Text("Không tìm thấy tệp nào phù hợp trong máy.", modifier = Modifier.padding(16.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            } else {
                                items(scannedFiles) { file ->
                                    ListItem(
                                        headlineContent = { Text(file.name, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                                        supportingContent = { Text(file.absolutePath, fontSize = 10.sp, maxLines = 1) },
                                        modifier = Modifier.clickable {
                                            val fileType = when (selectedCategory!!) {
                                                GisImportType.MBTILES -> "MBTILES"
                                                GisImportType.KML_KMZ -> if (file.extension.lowercase() == "kmz") "KMZ" else "KML"
                                                GisImportType.GEOJSON_JSON -> "GEOJSON"
                                                GisImportType.RASTER -> "RASTER"
                                                GisImportType.TAB -> "TAB"
                                                GisImportType.SHP -> "SHP"
                                            }
                                            if (fileType in listOf("TAB", "SHP", "KML", "KMZ", "GEOJSON")) {
                                                onImportGisLayer(file)
                                            } else {
                                                onAddGisLayer(file.nameWithoutExtension, fileType, file.absolutePath, 107.75, 3, null)
                                            }
                                            selectedCategory = null
                                        },
                                        trailingContent = { Icon(Icons.Default.AddCircle, null, tint = MaterialTheme.colorScheme.primary) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Current Layer List
            Text(
                "DANH SÁCH LỚP ĐANG MỞ",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )

            if (gisLayers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LayersClear, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                        Text("Chưa có lớp dữ liệu nào.", color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = gisLayers,
                    key = { it.id }
                ) { layer ->
                    GisLayerItem(
                        layer = layer,
                        onToggleVisibility = { onToggleVisibility(layer) },
                        onUpdateOpacity = { onUpdateOpacity(layer, it) },
                        onMoveLayerUp = { onMoveLayerUp(layer) },
                        onMoveLayerDown = { onMoveLayerDown(layer) },
                        onZoomToLayer = { onZoomToLayer(layer) },
                        onRename = { layerToRename = layer },
                        onSetLabel = { layerToLabel = layer },
                        onShowInfo = { layerToShowInfo = layer },
                        onEditCoordSys = { onEditLayerCoordSys(layer.id, layer.centralMeridian, layer.zoneDegrees) },
                        onDelete = { onDeleteGisLayer(layer.id) },
                        onRefresh = { onRefreshLayer(layer.id) },
                        onUpdateColors = { stroke, fill -> onUpdateColors(layer.id, stroke, fill) }
                    )
                }
            }
        }

        // Dialogs
        if (importState.isFullLoading || importState.isMetadataScan) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text(if (importState.isMetadataScan) "Đang phân tích tệp..." else "Đang nạp dữ liệu...") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(importState.fileName)
                        if (importState.totalCount > 0) {
                            Text("${importState.currentProgress} / ${importState.totalCount}")
                            LinearProgressIndicator(
                                progress = { importState.currentProgress.toFloat() / importState.totalCount.toFloat() },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                        } else {
                            Text("Đã nạp: ${importState.currentProgress} đối tượng")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onCancelImport) { Text("HỦY") }
                }
            )
        }

        if (importState.isSuccess) {
            AlertDialog(
                onDismissRequest = onCancelImport,
                title = { Text("Nạp thành công") },
                text = { 
                    Column {
                        Text("Đã nạp ${importState.totalCount} đối tượng từ ${importState.fileName}")
                        if (importState.fields.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Text("Chọn nhãn hiển thị:", fontWeight = FontWeight.Bold)
                            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                items(importState.fields) { field ->
                                    ListItem(
                                        headlineContent = { Text(field) },
                                        modifier = Modifier.clickable { 
                                            onFinalizeImport(107.75, 3, field)
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        onFinalizeImport(107.75, 3, null)
                    }) { Text(if (importState.fields.isEmpty()) "XONG" else "BỎ QUA") }
                }
            )
        }

        if (importState.errorMessage != null) {
            AlertDialog(
                onDismissRequest = onCancelImport,
                title = { Text("LỖI NẠP DỮ LIỆU", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
                text = { Text(importState.errorMessage ?: "") },
                confirmButton = {
                    Button(onClick = onCancelImport, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("ĐÓNG") }
                }
            )
        }

        layerToRename?.let { layer ->
            var newName by remember { mutableStateOf(layer.name) }
            AlertDialog(
                onDismissRequest = { layerToRename = null },
                title = { Text("Đổi tên lớp") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Tên mới") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onUpdateName(layer.id, newName)
                        layerToRename = null
                    }) { Text("CẬP NHẬT") }
                },
                dismissButton = {
                    TextButton(onClick = { layerToRename = null }) { Text("HỦY") }
                }
            )
        }

        layerToLabel?.let { layer ->
            var fieldNames by remember { mutableStateOf<List<String>>(emptyList()) }
            LaunchedEffect(layer) {
                fieldNames = getLayerFieldNames(layer)
            }
            
            AlertDialog(
                onDismissRequest = { layerToLabel = null },
                title = { Text("Chọn nhãn hiển thị") },
                text = {
                    Column {
                        Text("Chọn cột dữ liệu để làm nhãn hiển thị trên bản đồ.")
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            item {
                                ListItem(
                                    headlineContent = { Text("Tắt nhãn") },
                                    modifier = Modifier.clickable {
                                        onUpdateLabelColumn(layer.id, null)
                                        layerToLabel = null
                                    },
                                    trailingContent = { if (layer.labelColumn == null) Icon(Icons.Default.Check, null) }
                                )
                            }
                            items(fieldNames) { field ->
                                ListItem(
                                    headlineContent = { Text(field) },
                                    modifier = Modifier.clickable {
                                        onUpdateLabelColumn(layer.id, field)
                                        layerToLabel = null
                                    },
                                    trailingContent = { if (layer.labelColumn == field) Icon(Icons.Default.Check, null) }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { layerToLabel = null }) { Text("ĐÓNG") }
                }
            )
        }

        layerToShowInfo?.let { layer ->
            AlertDialog(
                onDismissRequest = { layerToShowInfo = null },
                title = { Text("Thông tin lớp") },
                text = {
                    Column {
                        Text("Tên: ${layer.name}", fontWeight = FontWeight.Bold)
                        Text("Loại: ${layer.fileType}")
                        Text("Đường dẫn: ${layer.filePath}")
                        Text("Hệ tọa độ: ${layer.coordinateSystem}")
                        Text("Kinh tuyến trục: ${layer.centralMeridian}")
                        Text("Múi chiếu: ${layer.zoneDegrees} độ")
                        Text("Nhãn: ${layer.labelColumn ?: "Tắt"}")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { layerToShowInfo = null }) { Text("ĐÓNG") }
                }
            )
        }
    }
}

@Composable
fun GisLayerItem(
    layer: GisLayerEntity,
    onToggleVisibility: () -> Unit,
    onUpdateOpacity: (Float) -> Unit,
    onMoveLayerUp: () -> Unit,
    onMoveLayerDown: () -> Unit,
    onZoomToLayer: () -> Unit,
    onRename: () -> Unit,
    onSetLabel: () -> Unit,
    onShowInfo: () -> Unit,
    onEditCoordSys: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit = {},
    onUpdateColors: (String, String) -> Unit = { _, _ -> }
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (layer.isVisible) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = layer.isVisible, onCheckedChange = { onToggleVisibility() })
                Column(modifier = Modifier.weight(1f).clickable { isExpanded = !isExpanded }) {
                    Text(layer.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (layer.isVisible) Color.Unspecified else Color.Gray)
                    Text("${layer.fileType} | Nhãn: ${layer.labelColumn ?: "Tắt"}", fontSize = 12.sp, color = Color.Gray)
                }
                
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = onZoomToLayer) { Icon(Icons.Default.MyLocation, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = onMoveLayerUp) { Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = onMoveLayerDown) { Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(20.dp)) }
            }

            if (isExpanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
                
                // Opacity Slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Opacity, null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Độ mờ: ${(layer.opacity * 100).toInt()}%", fontSize = 12.sp)
                    Slider(
                        value = layer.opacity,
                        onValueChange = onUpdateOpacity,
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                    )
                }

                // Action Buttons Row 1
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(onClick = onShowInfo, label = { Text("Chi tiết", fontSize = 10.sp) }, leadingIcon = { Icon(Icons.Default.Info, null, modifier = Modifier.size(14.dp)) })
                    AssistChip(onClick = onRename, label = { Text("Tên", fontSize = 10.sp) }, leadingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp)) })
                    AssistChip(onClick = onRefresh, label = { Text("Mở", fontSize = 10.sp) }, leadingIcon = { Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp)) })
                    AssistChip(onClick = { showColorPicker = true }, label = { Text("Màu", fontSize = 10.sp) }, leadingIcon = { Icon(Icons.Default.Palette, null, modifier = Modifier.size(14.dp)) })
                }
                
                // Action Buttons Row 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(onClick = onSetLabel, label = { Text("Nhãn", fontSize = 10.sp) }, leadingIcon = { Icon(Icons.Default.Label, null, modifier = Modifier.size(14.dp)) })
                    AssistChip(onClick = onEditCoordSys, label = { Text("Hệ tọa độ", fontSize = 10.sp) }, leadingIcon = { Icon(Icons.Default.Settings, null, modifier = Modifier.size(14.dp)) })
                }
            }
        }
    }

    if (showColorPicker) {
        val colors = listOf("#FF2E7D32", "#FF1976D2", "#FFD84315", "#FF7B1FA2", "#FFC2185B", "#FF000000", "#FFFFFFFF")
        AlertDialog(
            onDismissRequest = { showColorPicker = false },
            title = { Text("Chọn màu hiển thị") },
            text = {
                Column {
                    Text("Màu viền:")
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                        colors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(color)))
                                    .border(if (layer.strokeColorHex == color) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                                    .clickable { onUpdateColors(color, layer.fillColorHex); showColorPicker = false }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showColorPicker = false }) { Text("ĐÓNG") } }
        )
    }
}
