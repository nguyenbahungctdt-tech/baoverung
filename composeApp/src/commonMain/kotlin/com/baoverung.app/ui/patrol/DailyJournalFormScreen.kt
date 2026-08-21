package com.baoverung.app.ui.patrol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyJournalFormScreen(
    userName: String,
    onSave: (date: String, content: String, notes: String, weather: String, team: String, compartment: String) -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    var dateStr by remember { mutableStateOf("${now.dayOfMonth}/${now.monthNumber}/${now.year}") }
    var content by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var weather by remember { mutableStateOf("Nắng ráo") }
    var team by remember { mutableStateOf(userName) }
    var compartment by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NHẬT KÝ TUẦN TRA NGÀY", fontWeight = FontWeight.Black, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    Button(onClick = { 
                        onSave(dateStr, content, notes, weather, team, compartment)
                        onBack()
                    }) {
                        Text("LƯU", fontWeight = FontWeight.Black)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(value = dateStr, onValueChange = { dateStr = it }, label = { Text("Ngày thực hiện") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = team, onValueChange = { team = it }, label = { Text("Thành phần đoàn") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = compartment, onValueChange = { compartment = it }, label = { Text("Tiểu khu / Khoảnh tuần tra") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = weather, onValueChange = { weather = it }, label = { Text("Tình hình thời tiết") }, modifier = Modifier.fillMaxWidth())
            
            OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Nội dung tuần tra chính") }, modifier = Modifier.fillMaxWidth(), minLines = 5)
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Ghi chú bổ sung") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
