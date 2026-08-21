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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.baoverung.app.data.model.GpsPoint
import com.baoverung.app.gis.CoordinateSystemConverter
import androidx.navigation.NavController
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatrolLogFormScreen(
    navController: NavController,
    currentLocation: GpsPoint?,
    userEmail: String,
    userName: String,
    centralMeridian: Double,
    zoneDegrees: Int,
    activeCoordSystemId: String = "VN2000_3",
    provinceName: String = "",
    editLogId: Long? = null,
    defaultLeader: String = "",
    defaultField: String = "Lâm nghiệp",
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
        shouldReport: Boolean,
        logId: Long?,
        watermarkSettings: com.baoverung.app.util.WatermarkHelper.WatermarkSettings
    ) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { com.baoverung.app.data.local.AppDatabase.getDatabase(context) }
    val scrollState = rememberScrollState()

    // Form states
    var violationField by remember { mutableStateOf(defaultField.ifEmpty { "Lâm nghiệp" }) }
    var incidentType by remember { mutableStateOf("") }
    var otherIncidentType by remember { mutableStateOf("") }
    var leaderName by remember { mutableStateOf(defaultLeader.ifEmpty { userName }) }
    var violationTime by remember { mutableStateOf("") }
    var violationLocation by remember { mutableStateOf("") }
    var violatorName by remember { mutableStateOf("") }
    var violatorIdCard by remember { mutableStateOf("") }
    var violatorAddress by remember { mutableStateOf("") }
    var violatorPhone by remember { mutableStateOf("") }
    var confiscatedTools by remember { mutableStateOf("") }
    var relatedPersons by remember { mutableStateOf("") }
    var onSiteAction by remember { mutableStateOf("Lập biên bản tại chỗ và thu giữ tang vật") }
    var onSiteRecordings by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedPhotoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var watermarkSettingsState by remember { mutableStateOf(com.baoverung.app.util.WatermarkHelper.WatermarkSettings()) }

    // Load existing data if editing
    LaunchedEffect(editLogId) {
        if (editLogId != null) {
            val log = db.patrolLogDao().getById(editLogId)
            if (log != null) {
                violationField = log.violationField
                incidentType = log.incidentType
                leaderName = log.leaderName
                violationTime = log.violationTime
                violationLocation = log.violationLocation
                violatorName = log.violatorName
                violatorIdCard = log.violatorIdCard
                violatorAddress = log.violatorAddress
                violatorPhone = log.violatorPhone
                confiscatedTools = log.confiscatedTools
                relatedPersons = log.relatedPersons
                onSiteAction = log.onSiteAction
                onSiteRecordings = log.onSiteRecordings
                notes = log.notes
                selectedPhotoUris = log.photoPath?.split("|")?.filter { it.isNotEmpty() }?.map { Uri.parse(it) } ?: emptyList()
            }
        } else {
            incidentType = if (violationField == "Lâm nghiệp") "Phá rừng trái pháp luật (Điều 23)" else "Lấn đất hoặc chiếm đất (Điều 13)"
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        selectedPhotoUris = selectedPhotoUris + uris
    }

    // Observe result from camera capture
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    LaunchedEffect(savedStateHandle) {
        savedStateHandle?.getLiveData<String>("captured_photo_uri")?.observeForever { uriString ->
            if (uriString != null) {
                selectedPhotoUris = selectedPhotoUris + Uri.parse(uriString)
                savedStateHandle.remove<String>("captured_photo_uri")
            }
        }
        savedStateHandle?.getLiveData<com.baoverung.app.util.WatermarkHelper.WatermarkSettings>("watermark_settings")?.observeForever { value ->
            if (value != null) {
                watermarkSettingsState = value
                savedStateHandle.remove<com.baoverung.app.util.WatermarkHelper.WatermarkSettings>("watermark_settings")
            }
        }
    }

    var showAccuracyWarningDialog by remember { mutableStateOf(false) }

    val accuracy = currentLocation?.accuracy ?: 1000f
    val satellites = currentLocation?.satellitesCount ?: 0

    val currentSystem = CoordinateSystemConverter.SYSTEMS.find { it.id == activeCoordSystemId }
        ?: CoordinateSystemConverter.SYSTEMS[2]

    val (dX, dY) = if (currentLocation != null) {
        CoordinateSystemConverter.fromWgs84(currentLocation.latitude, currentLocation.longitude, currentSystem)
    } else Pair(0.0, 0.0)

    val timeStr = if (currentLocation != null) {
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss 'UTC'", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(currentLocation.timestampUtc))
    } else "Chưa có tọa độ"

    fun handleSave(shouldReport: Boolean) {
        if (currentLocation == null) {
            android.widget.Toast.makeText(context, "Chưa xác định được tọa độ hiện tại. Vui lòng đợi GPS!", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        if (accuracy > 20.0f) {
            showAccuracyWarningDialog = true
            return
        }
        val finalIncidentType = if (incidentType == "Khác") otherIncidentType else incidentType
        onSubmitPatrolLog(
            finalIncidentType,
            leaderName,
            violationTime,
            violationLocation,
            violatorName,
            violatorIdCard,
            violatorAddress,
            violatorPhone,
            confiscatedTools,
            relatedPersons,
            onSiteAction,
            onSiteRecordings,
            notes,
            selectedPhotoUris.map { it.toString() },
            violationField,
            shouldReport,
            editLogId,
            watermarkSettingsState
        )
        onBack()
    }

    val forestryIncidents = listOf(
        "Phá rừng trái pháp luật (Điều 23)",
        "Khai thác rừng tự nhiên trái pháp luật (Điều 16)",
        "Vi phạm quy định về bảo vệ động vật rừng (Điều 24)",
        "Vận chuyển lâm sản trái pháp luật (Điều 25)",
        "Tàng trữ, mua bán, chế biến lâm sản trái pháp luật (Điều 26)",
        "Vi phạm quy định về phòng cháy và chữa cháy rừng (Điều 20)",
        "Vi phạm các quy định chung của Nhà nước về bảo vệ rừng (Điều 19)",
        "Phá hủy công trình bảo vệ và phát triển rừng (Điều 22)",
        "Khác"
    )
    val landIncidents = listOf(
        "Lấn đất hoặc chiếm đất (Điều 13)",
        "Hủy hoại đất (Điều 14)",
        "Cản trở, gây khó khăn cho việc sử dụng đất của người khác (Điều 15)",
        "Vi phạm quy định về quản lý mốc địa giới đơn vị hành chính (Điều 26)",
        "Khác"
    )
    val violationFields = listOf("Lâm nghiệp", "Đất đai")
    var showFieldDropdown by remember { mutableStateOf(false) }
    var showTypeDropdown by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val calendar = remember { Calendar.getInstance() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NHẬT KÝ SỰ VỤ VI PHẠM", fontWeight = FontWeight.Black, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { handleSave(false) },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text("LƯU MÁY", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { handleSave(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(end = 8.dp).testTag("save_patrol_log_btn")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Gửi", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("BÁO CÁO", fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // Header Location & Satellite Card - More Professional
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (currentLocation != null) 
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GpsFixed, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("VỊ TRÍ HIỆN TRƯỜNG", fontWeight = FontWeight.Black, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        if (currentLocation != null) {
                            Surface(
                                color = if (accuracy <= 10f) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "±${String.format(java.util.Locale.US, "%.1f", accuracy)}m", 
                                    color = Color.White, 
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text("ĐANG TÌM GPS...", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                    Text("Thời gian GPS: $timeStr", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    
                    if (currentLocation != null) {
                        val coordDisplay = CoordinateSystemConverter.formatCoordinateDisplay(dX, dY, currentSystem, provinceName)
                        Text(coordDisplay, fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("H=${String.format(java.util.Locale.US, "%.1f m", currentLocation.altitude)}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Vệ tinh: $satellites SV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("Vui lòng đợi thiết bị cập nhật tọa độ chính xác trước khi lưu dữ liệu thực địa.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // Grouping Inputs into "Chung" section
            Text("THÔNG TIN CHUNG", fontWeight = FontWeight.Black, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
            
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Violation Field Dropdown
                    ExposedDropdownMenuBox(
                        expanded = showFieldDropdown,
                        onExpandedChange = { showFieldDropdown = !showFieldDropdown }
                    ) {
                        OutlinedTextField(
                            value = violationField,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Lĩnh vực vi phạm") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showFieldDropdown) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = showFieldDropdown,
                            onDismissRequest = { showFieldDropdown = false }
                        ) {
                            violationFields.forEach { field ->
                                DropdownMenuItem(
                                    text = { Text(field) },
                                    onClick = {
                                        violationField = field
                                        showFieldDropdown = false
                                        incidentType = if (field == "Lâm nghiệp") forestryIncidents[0] else landIncidents[0]
                                    }
                                )
                            }
                        }
                    }

                    // Incident Type Dropdown
                    ExposedDropdownMenuBox(
                        expanded = showTypeDropdown,
                        onExpandedChange = { showTypeDropdown = !showTypeDropdown }
                    ) {
                        OutlinedTextField(
                            value = incidentType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Sự vụ phát hiện") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showTypeDropdown) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .testTag("incident_type_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = showTypeDropdown,
                            onDismissRequest = { showTypeDropdown = false }
                        ) {
                            val currentList = if (violationField == "Lâm nghiệp") forestryIncidents else landIncidents
                            currentList.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        incidentType = type
                                        showTypeDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    if (incidentType == "Khác") {
                        OutlinedTextField(
                            value = otherIncidentType,
                            onValueChange = { otherIncidentType = it },
                            label = { Text("Nhập thông tin sự vụ cụ thể") },
                            modifier = Modifier.fillMaxWidth().testTag("other_incident_type_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    OutlinedTextField(
                        value = leaderName,
                        onValueChange = { leaderName = it },
                        label = { Text("Cán bộ tổ trưởng") },
                        modifier = Modifier.fillMaxWidth().testTag("leader_name_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            OutlinedTextField(
                value = violationTime,
                onValueChange = { violationTime = it },
                label = { Text("Thời gian xảy ra vi phạm") },
                leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().testTag("violation_time_input"),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarToday, contentDescription = "Chọn ngày")
                    }
                }
            )

            if (showDatePicker) {
                val datePickerState = rememberDatePickerState()
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let {
                                calendar.timeInMillis = it
                                showDatePicker = false
                                showTimePicker = true
                            }
                        }) { Text("TIẾP TỤC") }
                    },
                    dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("HỦY") } }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            if (showTimePicker) {
                val timePickerState = rememberTimePickerState(
                    initialHour = calendar.get(Calendar.HOUR_OF_DAY),
                    initialMinute = calendar.get(Calendar.MINUTE)
                )
                AlertDialog(
                    onDismissRequest = { showTimePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            calendar.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            calendar.set(Calendar.MINUTE, timePickerState.minute)
                            val sdf = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault())
                            violationTime = sdf.format(calendar.time)
                            showTimePicker = false
                        }) { Text("CHỌN XONG") }
                    },
                    dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("HỦY") } },
                    text = {
                        TimePicker(state = timePickerState)
                    }
                )
            }

            OutlinedTextField(
                value = violationLocation,
                onValueChange = { violationLocation = it },
                label = { Text("Địa điểm vi phạm (Chi tiết lô/khoảnh)") },
                leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().testTag("violation_location_input")
            )

            HorizontalDivider()
            Text("THÔNG TIN ĐỐI TƯỢNG VI PHẠM (NẾU CÓ)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(
                value = violatorName,
                onValueChange = { violatorName = it },
                label = { Text("Họ và tên đối tượng vi phạm") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().testTag("violator_name_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = violatorIdCard,
                    onValueChange = { violatorIdCard = it },
                    label = { Text("Số CCCD/CMND") },
                    modifier = Modifier.weight(1f).testTag("id_card_input")
                )
                OutlinedTextField(
                    value = violatorPhone,
                    onValueChange = { violatorPhone = it },
                    label = { Text("Số điện thoại") },
                    modifier = Modifier.weight(1f).testTag("phone_input")
                )
            }

            OutlinedTextField(
                value = violatorAddress,
                onValueChange = { violatorAddress = it },
                label = { Text("Địa chỉ thường trú / tạm trú") },
                modifier = Modifier.fillMaxWidth().testTag("address_input")
            )

            HorizontalDivider()
            Text("TANG VẬT & BIỆN PHÁP XỬ LÝ", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(
                value = confiscatedTools,
                onValueChange = { confiscatedTools = it },
                label = { Text("Tang vật, phương tiện tạm giữ (Cưa xăng, xe máy, gỗ...)") },
                leadingIcon = { Icon(Icons.Default.Construction, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().testTag("tools_input")
            )

            OutlinedTextField(
                value = relatedPersons,
                onValueChange = { relatedPersons = it },
                label = { Text("Cá nhân liên quan / Người đi cùng") },
                modifier = Modifier.fillMaxWidth().testTag("related_persons_input")
            )

            OutlinedTextField(
                value = onSiteAction,
                onValueChange = { onSiteAction = it },
                label = { Text("Biện pháp xử lý tại chỗ") },
                modifier = Modifier.fillMaxWidth().testTag("onsite_action_input")
            )

            OutlinedTextField(
                value = onSiteRecordings,
                onValueChange = { onSiteRecordings = it },
                label = { Text("Một số ghi nhận tại hiện trường") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().testTag("onsite_recordings_input")
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Ghi chú bổ sung") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().testTag("notes_input")
            )

            // Geotagged Photo Attachment
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (selectedPhotoUris.isNotEmpty()) {
                        androidx.compose.foundation.lazy.LazyRow(
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(selectedPhotoUris.size) { index ->
                                val uri = selectedPhotoUris[index]
                                Box {
                                    Image(
                                        painter = rememberAsyncImagePainter(uri),
                                        contentDescription = "Ảnh Geotag",
                                        modifier = Modifier.size(150.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    IconButton(
                                        onClick = { selectedPhotoUris = selectedPhotoUris.filterIndexed { i, _ -> i != index } },
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Xóa", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { navController.navigate("camera_capture") },
                            modifier = Modifier.weight(1f).testTag("capture_photo_btn")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CHỤP ẢNH")
                        }
                        OutlinedButton(
                            onClick = { photoPickerLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f).testTag("pick_photo_btn")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CHỌN ẢNH")
                        }
                    }
                    if (selectedPhotoUris.isNotEmpty()) {
                        TextButton(
                            onClick = { selectedPhotoUris = emptyList() },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("XÓA TẤT CẢ ẢNH")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Accuracy Warning Dialog (>20m)
    if (showAccuracyWarningDialog) {
        AlertDialog(
            onDismissRequest = { showAccuracyWarningDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("CẢNH BÁO ĐỘ CHÍNH XÁC THẤP", fontWeight = FontWeight.Bold) },
            text = { Text("Độ sai số vị trí hiện tại quá lớn (±${String.format("%.1f", accuracy)}m > 20m). Để đảm bảo tính chính xác của dữ liệu báo cáo, vui lòng di chuyển ra khoảng trống để bắt tín hiệu vệ tinh tốt hơn!") },
            confirmButton = {
                TextButton(onClick = { showAccuracyWarningDialog = false }) {
                    Text("ĐỢI GPS TỐT HƠN")
                }
            }
        )
    }
}
