package com.baoverung.app.ui.gis_layers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baoverung.app.data.local.entity.GisLayerEntity
import com.baoverung.app.platform.FilePicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GisLayersScreen(
    layers: List<GisLayerEntity>,
    onToggleVisibility: (GisLayerEntity) -> Unit,
    onDeleteLayer: (GisLayerEntity) -> Unit,
    onImportFile: (String) -> Unit,
    onBack: () -> Unit
) {
    val filePicker = remember { FilePicker() }
    
    filePicker.registerPicker { path ->
        path?.let { onImportFile(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lớp bản đồ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { filePicker.launchPicker("*/*") }) {
                        Icon(Icons.Default.Add, contentDescription = "Thêm lớp")
                    }
                }
            )
        }
    ) { padding ->
        if (layers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Chưa có lớp bản đồ nào. Nhấn + để thêm.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(layers) { layer ->
                    ListItem(
                        headlineContent = { Text(layer.name) },
                        supportingContent = { Text("${layer.fileType} - ${layer.filePath}") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { onToggleVisibility(layer) }) {
                                    Icon(
                                        if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                                IconButton(onClick = { onDeleteLayer(layer) }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
