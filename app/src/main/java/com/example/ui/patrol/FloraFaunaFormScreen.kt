package com.baoverung.app.ui.patrol

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.baoverung.app.data.model.GpsPoint
import com.baoverung.app.gis.CoordinateSystemConverter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloraFaunaFormScreen(
    navController: NavController,
    currentLocation: GpsPoint?,
    userName: String,
    activeCoordSystemId: String = "VN2000_3",
    provinceName: String = "",
    editLogId: Long? = null,
    onSubmit: (
        appearance: String,
        features: String,
        count: String,
        habitat: String,
        temp: String,
        humidity: String,
        canopy: String,
        surroundPlants: String,
        specimens: String,
        photoPaths: List<String>,
        logId: Long?,
        shouldReport: Boolean
    ) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { com.baoverung.app.data.local.AppDatabase.getDatabase(context) }
    val scrollState = rememberScrollState()

    // Form states
    var appearanceDescription by remember { mutableStateOf("") }
    var features by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("") }
    var habitatType by remember { mutableStateOf("") }
    var temperature by remember { mutableStateOf("") }
    var humidity by remember { mutableStateOf("") }
    var canopyCover by remember { mutableStateOf("") }
    var surroundingPlants by remember { mutableStateOf("") }
    var specimens by remember { mutableStateOf("") }
    var selectedPhotoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    LaunchedEffect(editLogId) {
        if (editLogId != null) {
            val log = db.floraFaunaLogDao().getById(editLogId)
            if (log != null) {
                appearanceDescription = log.appearanceDescription
                features = log.features
                count = log.count
                habitatType = log.habitatType
                temperature = log.temperature
                humidity = log.humidity
                canopyCover = log.canopyCover
                surroundingPlants = log.surroundingPlants
                specimens = log.specimens
                selectedPhotoUris = log.photoPath?.split("|")?.filter { it.isNotEmpty() }?.map { Uri.parse(it) } ?: emptyList()
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        selectedPhotoUris = selectedPhotoUris + uris
    }

    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    LaunchedEffect(savedStateHandle) {
        savedStateHandle?.getLiveData<String>("captured_photo_uri")?.observeForever { uriString ->
            if (uriString != null) {
                selectedPhotoUris = selectedPhotoUris + Uri.parse(uriString)
                savedStateHandle.remove<String>("captured_photo_uri")
            }
        }
    }

    val currLoc = currentLocation
    val timeStr = if (currLoc != null) SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(currLoc.timestampUtc)) else "Chưa có tọa độ"
    val currentSystem = CoordinateSystemConverter.SYSTEMS.find { it.id == activeCoordSystemId } ?: CoordinateSystemConverter.SYSTEMS[2]
    val (dX, dY) = if (currLoc != null) CoordinateSystemConverter.fromWgs84(currLoc.latitude, currLoc.longitude, currentSystem) else Pair(0.0, 0.0)
    val coordDisplay = if (currLoc != null) CoordinateSystemConverter.formatCoordinateDisplay(dX, dY, currentSystem, provinceName) else "Đang tìm GPS..."

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NHẬT KÝ ĐỘNG THỰC VẬT", fontWeight = FontWeight.Black, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (currLoc == null) {
                                android.widget.Toast.makeText(context, "Chưa có tọa độ thực địa!", android.widget.Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            onSubmit(appearanceDescription, features, count, habitatType, temperature, humidity, canopyCover, surroundingPlants, specimens, selectedPhotoUris.map { it.toString() }, editLogId, false)
                            onBack()
                        },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text("LƯU MÁY", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            if (currLoc == null) {
                                android.widget.Toast.makeText(context, "Chưa có tọa độ thực địa!", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            onSubmit(appearanceDescription, features, count, habitatType, temperature, humidity, canopyCover, surroundingPlants, specimens, selectedPhotoUris.map { it.toString() }, editLogId, true)
                            onBack()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Send, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("BÁO CÁO", fontWeight = FontWeight.Black)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues).padding(horizontal = 16.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Section 1: Thời gian địa điểm
            Text("1. THỜI GIAN ĐỊA ĐIỂM", fontWeight = FontWeight.Black, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (currLoc != null) 
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Cán bộ: $userName", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Thời gian: $timeStr", fontSize = 12.sp)
                    Text(coordDisplay, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    if (currLoc != null) {
                        Text("Độ cao: ${String.format("%.1f m", currLoc.altitude)}", fontSize = 12.sp)
                    }
                }
            }

            // Section 2: Đặc điểm sinh thái và hình thái
            Text("2. ĐẶC ĐIỂM SINH THÁI VÀ HÌNH THÁI", fontWeight = FontWeight.Black, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(value = appearanceDescription, onValueChange = { appearanceDescription = it }, label = { Text("Mô tả ngoại hình") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = features, onValueChange = { features = it }, label = { Text("Bộ phận đặc trưng") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = count, onValueChange = { count = it }, label = { Text("Số lượng cá thể") }, modifier = Modifier.fillMaxWidth())

            // Section 3: Sinh cảnh và môi trường sống
            Text("3. SINH CẢNH VÀ MÔI TRƯỜNG SỐNG", fontWeight = FontWeight.Black, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(value = habitatType, onValueChange = { habitatType = it }, label = { Text("Loại sinh cảnh") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = temperature, onValueChange = { temperature = it }, label = { Text("Nhiệt độ (°C)") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = humidity, onValueChange = { humidity = it }, label = { Text("Độ ẩm (%)") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = canopyCover, onValueChange = { canopyCover = it }, label = { Text("Độ tàn che") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = surroundingPlants, onValueChange = { surroundingPlants = it }, label = { Text("Các loài cây sống quanh") }, modifier = Modifier.fillMaxWidth())

            // Section 4: Mẫu vật
            Text("4. MẪU VẬT", fontWeight = FontWeight.Black, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(value = specimens, onValueChange = { specimens = it }, label = { Text("Đã thu thập được gì") }, modifier = Modifier.fillMaxWidth())

            // Photo Attachment
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (selectedPhotoUris.isNotEmpty()) {
                        androidx.compose.foundation.lazy.LazyRow(modifier = Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(selectedPhotoUris.size) { index ->
                                Image(painter = rememberAsyncImagePainter(selectedPhotoUris[index]), contentDescription = null, modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { navController.navigate("camera_capture") }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.CameraAlt, null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CHỤP ẢNH")
                        }
                        OutlinedButton(onClick = { photoPickerLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PhotoLibrary, null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CHỌN ẢNH")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
