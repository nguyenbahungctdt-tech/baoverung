package com.baoverung.app.ui.converter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoverung.app.gis.CoordinateSystemConverter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordinateConverterScreen(
    centralMeridian: Double,
    zoneDegrees: Int,
    onBack: () -> Unit
) {
    var inputX by remember { mutableStateOf("") }
    var inputY by remember { mutableStateOf("") }
    var outputResult by remember { mutableStateOf("Kết quả sẽ hiển thị ở đây") }
    var isVnToWgs by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CHUYỂN ĐỔI TỌA ĐỘ", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (isVnToWgs) "VN2000 -> WGS84" else "WGS84 -> VN2000",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = inputX,
                onValueChange = { inputX = it },
                label = { Text(if (isVnToWgs) "Kinh độ (X) / Easting" else "Kinh độ (Lon)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = inputY,
                onValueChange = { inputY = it },
                label = { Text(if (isVnToWgs) "Vĩ độ (Y) / Northing" else "Vĩ độ (Lat)") },
                modifier = Modifier.fillMaxWidth()
            )

            IconButton(
                onClick = { isVnToWgs = !isVnToWgs },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Default.SwapVert, "Chuyển đổi")
            }

            Button(
                onClick = {
                    val x = inputX.toDoubleOrNull() ?: 0.0
                    val y = inputY.toDoubleOrNull() ?: 0.0
                    outputResult = if (isVnToWgs) {
                        val (lat, lon) = CoordinateSystemConverter.vn2000ToWgs84(x, y, centralMeridian, zoneDegrees)
                        "WGS84: Lat=$lat, Lon=$lon"
                    } else {
                        val (vx, vy) = CoordinateSystemConverter.wgs84ToVn2000(y, x, centralMeridian, zoneDegrees)
                        "VN2000: X=$vx, Y=$vy"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("CHUYỂN ĐỔI")
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Text(
                    outputResult,
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
