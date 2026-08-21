package com.baoverung.app.ui.patrol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyJournalFormScreen(
    navController: NavController,
    userEmail: String,
    userName: String,
    editJournalId: Long? = null,
    onSaveJournal: (dateStr: String, content: String, notes: String, weather: String, team: String, compartment: String, color: String) -> Unit,
    onGetLinkedData: suspend (String) -> String,
    onGetJournalById: suspend (Long) -> com.baoverung.app.data.local.entity.DailyJournalEntity?,
    onGetAutoWeather: () -> String,
    onExportWord: (com.baoverung.app.data.local.entity.DailyJournalEntity) -> Unit,
    onSendGmail: (com.baoverung.app.data.local.entity.DailyJournalEntity) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var dateStr by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    val displayDate = remember(dateStr) { 
        try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date!!)
        } catch (e: Exception) { dateStr }
    }

    var content by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var weather by remember { mutableStateOf("") }
    var patrolTeam by remember { mutableStateOf("") }
    var patrolCompartment by remember { mutableStateOf("") }
    var displayColorHex by remember { mutableStateOf("#FF1976D2") }
    var linkedDataSummary by remember { mutableStateOf("Đang tải dữ liệu liên quan...") }
    var isLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(editJournalId) {
        if (editJournalId != null && !isLoaded) {
            val journal = onGetJournalById(editJournalId)
            if (journal != null) {
                dateStr = journal.dateStr
                content = journal.content
                notes = journal.notes
                weather = journal.weather
                patrolTeam = journal.patrolTeam
                patrolCompartment = journal.patrolCompartment
                displayColorHex = journal.displayColorHex
            }
            isLoaded = true
        } else if (editJournalId == null) {
            if (weather.isEmpty()) weather = onGetAutoWeather()
            isLoaded = true
        }
    }

    LaunchedEffect(dateStr) {
        linkedDataSummary = onGetLinkedData(dateStr)
    }

    val currentJournalEntity = com.baoverung.app.data.local.entity.DailyJournalEntity(
        id = editJournalId ?: 0L,
        dateStr = dateStr,
        content = content,
        notes = notes,
        userEmail = userEmail,
        linkedDataJson = linkedDataSummary,
        weather = weather,
        patrolTeam = patrolTeam,
        patrolCompartment = patrolCompartment,
        displayColorHex = displayColorHex
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editJournalId != null) "CHỈNH SỬA NHẬT KÝ" else "LẬP NHẬT KÝ TUẦN TRA", fontWeight = FontWeight.Black, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            onSaveJournal(dateStr, content, notes, weather, patrolTeam, patrolCompartment, displayColorHex)
                            onBack()
                        },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text("LƯU MÁY", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            onSaveJournal(dateStr, content, notes, weather, patrolTeam, patrolCompartment, displayColorHex)
                            onSendGmail(currentJournalEntity)
                            onBack()
                        },
                        modifier = Modifier.padding(end = 8.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // Header Info - Modern Professional Look
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(18.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("PHIẾU NHẬT KÝ CÔNG TÁC", fontWeight = FontWeight.Black, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        Text(displayDate, fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Người lập: $userName", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.EditCalendar, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }

            // Professional Forestry Fields
            OutlinedTextField(
                value = weather,
                onValueChange = { weather = it },
                label = { Text("Tình hình thời tiết (Hệ thống)") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(onClick = { weather = onGetAutoWeather() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Cập nhật")
                    }
                }
            )

            OutlinedTextField(
                value = patrolTeam,
                onValueChange = { patrolTeam = it },
                label = { Text("Thành phần đoàn tuần tra") },
                placeholder = { Text("Ví dụ: Hạt kiểm lâm, Trạm BVR số 1...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Group, contentDescription = null) },
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = patrolCompartment,
                onValueChange = { patrolCompartment = it },
                label = { Text("Tiểu khu / Khoảnh tuần tra") },
                placeholder = { Text("Ví dụ: TK 120, Khoảnh 2, 3...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Map, contentDescription = null) },
                shape = RoundedCornerShape(12.dp)
            )

            // Linked Data Auto-Summary
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("DỮ LIỆU TỰ ĐỘNG KÈM THEO TRONG NGÀY", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (linkedDataSummary.isEmpty() || linkedDataSummary.startsWith("Đang tải")) {
                        Text("Không có dữ liệu thực địa nào được ghi nhận trong hôm nay.", fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    } else {
                        Text(linkedDataSummary, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }

            // Main Content Field
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Nội dung nhật ký công việc *") },
                placeholder = { Text("Nhập chi tiết các hoạt động tuần tra, kiểm tra rừng đã thực hiện...") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                minLines = 6,
                shape = RoundedCornerShape(12.dp)
            )

            // Notes Field
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Ghi chú bổ sung") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Note, contentDescription = null) },
                shape = RoundedCornerShape(12.dp)
            )

            // Color Picker for Journal
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Màu sắc định dạng nhật ký", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val colors = listOf("#FF1976D2", "#FFD32F2F", "#FF388E3C", "#FFFBC02D", "#FF7B1FA2", "#FFFF3D00")
                        colors.forEach { colorStr ->
                            Surface(
                                onClick = { displayColorHex = colorStr },
                                shape = CircleShape,
                                color = Color(android.graphics.Color.parseColor(colorStr)),
                                modifier = Modifier.size(36.dp),
                                border = if (displayColorHex == colorStr) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
                            ) {}
                        }
                    }
                }
            }

            // Quick Actions: Export and Share
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onExportWord(currentJournalEntity) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("XUẤT WORD", fontSize = 12.sp)
                }

                Button(
                    onClick = { onSendGmail(currentJournalEntity) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)), // Gmail Red
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("BÁO CÁO GMAIL", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                "Lưu ý: Bạn có thể xuất file Word và gửi trực tiếp qua Gmail cho lãnh đạo theo cấu hình đã mặc định sẵn nội dung và các file kèm theo.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}
