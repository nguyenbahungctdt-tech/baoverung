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
import com.baoverung.app.data.model.GpsPoint
import com.baoverung.app.util.toDateTimeString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NaturalImpactFormScreen(
    currentLocation: GpsPoint?,
    userName: String,
    onSubmit: (
        cause: String,
        area: String,
        damage: String,
        time: String,
        photoPaths: List<String>,
        shouldReport: Boolean
    ) -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    var cause by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    val timeStr = currentLocation?.timestampUtc?.toDateTimeString() ?: "Đang tìm GPS..."

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TÁC ĐỘNG TỰ NHIÊN", fontWeight = FontWeight.Black, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    Button(
                        onClick = {
                            onSubmit(cause, area, "", timeStr, emptyList(), true)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("BÁO CÁO", fontSize = 12.sp, fontWeight = FontWeight.Black)
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
            OutlinedTextField(value = cause, onValueChange = { cause = it }, label = { Text("Nguyên nhân") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = area, onValueChange = { area = it }, label = { Text("Diện tích ảnh hưởng") }, modifier = Modifier.fillMaxWidth())
        }
    }
}
