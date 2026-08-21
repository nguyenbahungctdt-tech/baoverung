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
import com.baoverung.app.data.model.GpsPoint
import com.baoverung.app.util.format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NaturalImpactFormScreen(
    currentLocation: GpsPoint?,
    userName: String,
    onSubmit: (
        cause: String,
        otherCause: String,
        area: String,
        before: String,
        after: String,
        damage: String,
        time: String,
        photoPaths: List<String>,
        shouldReport: Boolean
    ) -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    var cause by remember { mutableStateOf("Sạt lở đất") }
    var otherCause by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var before by remember { mutableStateOf("") }
    var after by remember { mutableStateOf("") }
    var damage by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TÁC ĐỘNG TỰ NHIÊN", fontWeight = FontWeight.Black, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    Button(onClick = { 
                        onSubmit(cause, otherCause, area, before, after, damage, time, emptyList(), true)
                    }) {
                        Text("BÁO CÁO", fontWeight = FontWeight.Black)
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
            
            OutlinedTextField(value = cause, onValueChange = { cause = it }, label = { Text("Nguyên nhân tác động") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = area, onValueChange = { area = it }, label = { Text("Diện tích bị ảnh hưởng") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = damage, onValueChange = { damage = it }, label = { Text("Mức độ thiệt hại rừng") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("Thời điểm xảy ra") }, modifier = Modifier.fillMaxWidth())
            
            OutlinedTextField(value = before, onValueChange = { before = it }, label = { Text("Trạng thái trước tác động") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            OutlinedTextField(value = after, onValueChange = { after = it }, label = { Text("Trạng thái hiện tại") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
