package com.baoverung.app.ui.patrol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
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
fun FloraFaunaFormScreen(
    currentLocation: GpsPoint?,
    userName: String,
    onSubmit: (
        appearance: String,
        features: String,
        count: String,
        habitat: String,
        temp: String,
        humidity: String,
        canopy: String,
        plants: String,
        specimens: String,
        photoPaths: List<String>,
        shouldReport: Boolean
    ) -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    var appearance by remember { mutableStateOf("") }
    var features by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("1") }
    var habitat by remember { mutableStateOf("Rừng tự nhiên") }
    var temp by remember { mutableStateOf("") }
    var humidity by remember { mutableStateOf("") }
    var canopy by remember { mutableStateOf("") }
    var plants by remember { mutableStateOf("") }
    var specimens by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GHI NHẬN ĐỘNG THỰC VẬT", fontWeight = FontWeight.Black, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    Button(onClick = { 
                        onSubmit(appearance, features, count, habitat, temp, humidity, canopy, plants, specimens, emptyList(), true)
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
            
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("VỊ TRÍ PHÁT HIỆN", fontWeight = FontWeight.Black, fontSize = 12.sp)
                    currentLocation?.let {
                        Text("${it.latitude.format(6)}, ${it.longitude.format(6)}", fontWeight = FontWeight.Bold)
                    } ?: Text("ĐANG TÌM GPS...", color = MaterialTheme.colorScheme.error)
                }
            }

            OutlinedTextField(value = appearance, onValueChange = { appearance = it }, label = { Text("Tên loài / Mô tả nhận dạng") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = features, onValueChange = { features = it }, label = { Text("Đặc điểm nổi bật") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = count, onValueChange = { count = it }, label = { Text("Số lượng cá thể") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = habitat, onValueChange = { habitat = it }, label = { Text("Sinh cảnh") }, modifier = Modifier.fillMaxWidth())
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = temp, onValueChange = { temp = it }, label = { Text("Nhiệt độ") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = humidity, onValueChange = { humidity = it }, label = { Text("Độ ẩm") }, modifier = Modifier.weight(1f))
            }
            
            OutlinedTextField(value = canopy, onValueChange = { canopy = it }, label = { Text("Độ tàn che (%)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = plants, onValueChange = { plants = it }, label = { Text("Thực vật đi kèm") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = specimens, onValueChange = { specimens = it }, label = { Text("Mẫu vật thu thập (nếu có)") }, modifier = Modifier.fillMaxWidth())
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
