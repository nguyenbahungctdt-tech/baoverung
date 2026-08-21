package com.baoverung.app.ui.patrol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoverung.app.data.local.entity.DailyJournalEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyJournalFormScreen(
    userName: String,
    onSave: (dateStr: String, content: String, notes: String) -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    var content by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LẬP NHẬT KÝ TUẦN TRA", fontWeight = FontWeight.Black, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    Button(
                        onClick = {
                            onSave("2025-01-01", content, notes)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("LƯU", fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Nội dung nhật ký") }, modifier = Modifier.fillMaxWidth(), minLines = 5)
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Ghi chú") }, modifier = Modifier.fillMaxWidth())
        }
    }
}
