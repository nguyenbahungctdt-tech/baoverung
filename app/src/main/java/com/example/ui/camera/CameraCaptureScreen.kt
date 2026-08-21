package com.baoverung.app.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.baoverung.app.data.model.GpsPoint
import com.baoverung.app.gis.CoordinateSystemConverter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

import com.baoverung.app.util.WatermarkHelper.WatermarkSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraCaptureScreen(
    currentLocation: GpsPoint?,
    compassAzimuth: Float,
    userName: String,
    centralMeridian: Double,
    zoneDegrees: Int,
    activeCoordSystemId: String = "VN2000_3",
    provinceName: String = "",
    initialWatermarkSettings: WatermarkSettings = WatermarkSettings(),
    onSettingsChanged: (WatermarkSettings) -> Unit = {},
    onPhotoCaptured: (String, Boolean, WatermarkSettings) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .build() 
    }
    
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var showGrid by remember { mutableStateOf(false) }
    var showCompassOverlay by remember { mutableStateOf(true) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // Watermark settings
    var watermarkSettings by remember { mutableStateOf(initialWatermarkSettings) }
    
    var capturedUri by remember { mutableStateOf<String?>(null) }
    
    // Cleanup temp photo when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            capturedUri?.let { uriStr ->
                try {
                    val file = File(Uri.parse(uriStr).path!!)
                    if (file.exists() && file.absolutePath.contains("cache")) {
                        file.delete()
                    }
                } catch (e: Exception) {}
            }
        }
    }
    
    val currLoc = currentLocation ?: GpsPoint(11.9404, 108.4378)
    
    val currentSystem = CoordinateSystemConverter.SYSTEMS.find { it.id == activeCoordSystemId } 
        ?: CoordinateSystemConverter.SYSTEMS[2] // Default to VN2000_3
    
    // Calculate current coordinates based on active system
    val (cX, cY) = CoordinateSystemConverter.fromWgs84(currLoc.latitude, currLoc.longitude, currentSystem)
    
    val timeStr = SimpleDateFormat("HH:mm · dd/MM/yyyy · EEEE", Locale.getDefault()).format(Date())

    LaunchedEffect(Unit) {
        val cameraProvider = context.getCameraProvider()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        } catch (e: Exception) {
            Log.e("CameraCapture", "Use case binding failed", e)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera Preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Grid Overlay
        if (showGrid) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawLine(Color.White.copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(w / 3, 0f), androidx.compose.ui.geometry.Offset(w / 3, h), strokeWidth = 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(2 * w / 3, 0f), androidx.compose.ui.geometry.Offset(2 * w / 3, h), strokeWidth = 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(0f, h / 3), androidx.compose.ui.geometry.Offset(w, h / 3), strokeWidth = 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(0f, 2 * h / 3), androidx.compose.ui.geometry.Offset(w, 2 * h / 3), strokeWidth = 1.dp.toPx())
            }
        }

        // Top Tools Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color.White)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Flash Toggle
                IconButton(
                    onClick = {
                        flashMode = when (flashMode) {
                            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                            else -> ImageCapture.FLASH_MODE_OFF
                        }
                        imageCapture.flashMode = flashMode
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = when (flashMode) {
                            ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                            ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                            else -> Icons.Default.FlashOff
                        },
                        contentDescription = "Flash",
                        tint = if (flashMode != ImageCapture.FLASH_MODE_OFF) Color.Yellow else Color.White
                    )
                }

                // Grid Toggle
                IconButton(
                    onClick = { showGrid = !showGrid },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(Icons.Default.Grid4x4, contentDescription = "Lưới", tint = if (showGrid) Color.Yellow else Color.White)
                }

                // Compass Overlay Toggle
                IconButton(
                    onClick = { showCompassOverlay = !showCompassOverlay },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(Icons.Default.Explore, contentDescription = "Hướng", tint = if (showCompassOverlay) Color.Yellow else Color.White)
                }

                // Camera Settings
                IconButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Cài đặt", tint = Color.White)
                }
            }
        }

        // Watermark Overlay on Screen
        if (watermarkSettings.showInfo) {
            Surface(
                modifier = Modifier
                    .align(when(watermarkSettings.position) {
                        "TOP_LEFT" -> Alignment.TopStart
                        "TOP_RIGHT" -> Alignment.TopEnd
                        "BOTTOM_RIGHT" -> Alignment.BottomEnd
                        else -> Alignment.BottomStart
                    })
                    .padding(if (watermarkSettings.position.startsWith("TOP")) 80.dp else 16.dp),
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (watermarkSettings.showOfficer) {
                        Text(userName.uppercase(), fontWeight = FontWeight.Bold, fontSize = watermarkSettings.labelSize.sp, color = Color(0xFF4ADE80))
                    }
                    
                    if (watermarkSettings.showTime) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Text(timeStr, color = Color.White, fontSize = watermarkSettings.labelSize.sp)
                        }
                    }
                    
                    if (watermarkSettings.showAddress || watermarkSettings.showWgs84 || watermarkSettings.showVn2000) {
                        HorizontalDivider(color = Color(0xFF4ADE80).copy(alpha = 0.5f), thickness = 1.dp, modifier = Modifier.padding(vertical = 2.dp))
                    }

                    if (watermarkSettings.showAddress) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(12.dp))
                            Text(text = "Địa điểm đang tải...", color = Color.White, fontSize = (watermarkSettings.labelSize + 1).sp)
                        }
                    }

                    if (watermarkSettings.showWgs84) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Public, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(12.dp))
                            Text(
                                text = "${String.format(Locale.US, "%.6f, %.6f", currLoc.latitude, currLoc.longitude)}",
                                color = Color.White,
                                fontSize = (watermarkSettings.labelSize + 1).sp
                            )
                        }
                    }

                    if (watermarkSettings.showVn2000) {
                        val coordDisplay = CoordinateSystemConverter.formatCoordinateDisplay(cX, cY, currentSystem, provinceName)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Tag, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(12.dp))
                            Text(
                                text = "$coordDisplay KTT $centralMeridian",
                                color = Color(0xFFFFD700),
                                fontSize = (watermarkSettings.labelSize + 1).sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (watermarkSettings.showAccuracy) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(12.dp))
                                Text("±${String.format(Locale.US, "%.1f m", currLoc.accuracy)}", color = Color(0xFFFFD700), fontSize = watermarkSettings.labelSize.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (watermarkSettings.showAltitude) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Terrain, contentDescription = null, tint = Color(0xFF4ADE80), modifier = Modifier.size(12.dp))
                                Text("${String.format(Locale.US, "%.1f m", currLoc.altitude)}", color = Color(0xFFFFD700), fontSize = watermarkSettings.labelSize.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Default.Forest, contentDescription = null, tint = Color(0xFF4ADE80), modifier = Modifier.size(16.dp))
                        Text("Ứng dụng Bảo vệ rừng - Đại Thành", color = Color.White, fontSize = (watermarkSettings.labelSize - 1).sp)
                    }
                }
            }
        }

        if (capturedUri == null) {
            // GPS Status Indicator
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 80.dp, end = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = if ((currentLocation?.accuracy ?: 100f) < 20f) Color(0xFF2E7D32).copy(alpha = 0.7f) else Color(0xFFC62828).copy(alpha = 0.7f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if ((currentLocation?.accuracy ?: 100f) < 20f) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (currentLocation != null) "±${String.format("%.1f", currentLocation.accuracy)}m" else "CHỜ GPS...",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Capture Button Area
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LargeFloatingActionButton(
                    onClick = {
                        val tempFile = File(context.cacheDir, "TEMP_IMG_${System.currentTimeMillis()}.jpg")
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

                        imageCapture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    capturedUri = Uri.fromFile(tempFile).toString()
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    Log.e("CameraCapture", "Capture failed", exception)
                                }
                            }
                        )
                    },
                    shape = CircleShape,
                    containerColor = Color.White,
                    contentColor = Color.Black
                ) {
                    Icon(Icons.Default.Camera, contentDescription = "Chụp", modifier = Modifier.size(36.dp))
                }
            }
        } else {
            // Preview Image
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                Image(
                    painter = rememberAsyncImagePainter(capturedUri),
                    contentDescription = "Preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            // Action Buttons
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { 
                            try { File(Uri.parse(capturedUri!!).path!!).delete() } catch(e: Exception) {}
                            capturedUri = null 
                        },
                        modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Chụp lại", tint = Color.White)
                    }
                    
                    Button(
                        onClick = { onPhotoCaptured(capturedUri!!, false, watermarkSettings) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("LƯU", fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = { onPhotoCaptured(capturedUri!!, true, watermarkSettings) },
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4ADE80), contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("BÁO CÁO", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("CÀI ĐẶT MÁY ẢNH & ĐÓNG DẤU") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Vị trí đóng dấu:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf("TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT").forEach { pos ->
                                FilterChip(
                                    selected = watermarkSettings.position == pos,
                                    onClick = { watermarkSettings = watermarkSettings.copy(position = pos) },
                                    label = { Text(pos.replace("_", " "), fontSize = 10.sp) }
                                )
                            }
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        
                        Text("Nội dung đóng dấu:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        
                        WatermarkToggleRow("Hiện thông tin đóng dấu", watermarkSettings.showInfo) { watermarkSettings = watermarkSettings.copy(showInfo = it) }
                        WatermarkToggleRow("Tên cán bộ thực hiện", watermarkSettings.showOfficer) { watermarkSettings = watermarkSettings.copy(showOfficer = it) }
                        WatermarkToggleRow("Thời gian chụp ảnh", watermarkSettings.showTime) { watermarkSettings = watermarkSettings.copy(showTime = it) }
                        WatermarkToggleRow("Địa chỉ địa lý (GPS)", watermarkSettings.showAddress) { watermarkSettings = watermarkSettings.copy(showAddress = it) }
                        WatermarkToggleRow("Tọa độ WGS84", watermarkSettings.showWgs84) { watermarkSettings = watermarkSettings.copy(showWgs84 = it) }
                        WatermarkToggleRow("Tọa độ VN-2000", watermarkSettings.showVn2000) { watermarkSettings = watermarkSettings.copy(showVn2000 = it) }
                        WatermarkToggleRow("Độ cao (m)", watermarkSettings.showAltitude) { watermarkSettings = watermarkSettings.copy(showAltitude = it) }
                        WatermarkToggleRow("Sai số vị trí", watermarkSettings.showAccuracy) { watermarkSettings = watermarkSettings.copy(showAccuracy = it) }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        
                        Text("Màu sắc & Kích thước:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Màu nhãn:", modifier = Modifier.weight(1f))
                            listOf("#FFD700", "#4ADE80", "#3B82F6", "#FFFFFF").forEach { colorHex ->
                                val colorInt = android.graphics.Color.parseColor(colorHex)
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color(colorInt), CircleShape)
                                        .clickable { watermarkSettings = watermarkSettings.copy(labelColor = colorInt) }
                                        .then(if (watermarkSettings.labelColor == colorInt) Modifier.background(Color.White, CircleShape).padding(2.dp).background(Color(colorInt), CircleShape) else Modifier)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Kích thước nhãn: ", modifier = Modifier.weight(1f))
                            Text("${watermarkSettings.labelSize.toInt()} dp", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = watermarkSettings.labelSize,
                            onValueChange = { watermarkSettings = watermarkSettings.copy(labelSize = it) },
                            valueRange = 8f..20f,
                            steps = 12
                        )
                    }
                },
                confirmButton = { Button(onClick = { 
                    onSettingsChanged(watermarkSettings)
                    showSettingsDialog = false 
                }) { Text("XÁC NHẬN") } }
            )
        }
    }
}

@Composable
fun WatermarkToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.scale(0.8f))
    }
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    ProcessCameraProvider.getInstance(this).also { cameraProviderFuture ->
        cameraProviderFuture.addListener({
            continuation.resume(cameraProviderFuture.get())
        }, ContextCompat.getMainExecutor(this))
    }
}
