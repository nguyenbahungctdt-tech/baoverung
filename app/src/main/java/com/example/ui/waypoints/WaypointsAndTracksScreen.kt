package com.baoverung.app.ui.waypoints

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import android.net.Uri
import com.baoverung.app.data.local.entity.DailyJournalEntity
import com.baoverung.app.data.local.entity.PatrolLogEntity
import com.baoverung.app.data.local.entity.TrackLogEntity
import com.baoverung.app.data.local.entity.WaypointEntity
import com.baoverung.app.data.local.entity.PolygonEntity
import com.baoverung.app.data.model.GpsPoint
import com.baoverung.app.gis.CoordinateSystemConverter
import com.baoverung.app.gis.GisAreaCalculator
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CardStatusIcons(
    isSynced: Boolean,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onDelete: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isSynced) {
            Icon(Icons.Default.CloudDone, "Synced", tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
        } else {
            Icon(Icons.Default.CloudQueue, "Pending", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onToggleVisibility, modifier = Modifier.size(32.dp)) {
            Icon(imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = if (isVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun CardActionButtons(
    onEdit: () -> Unit = {},
    onDetail: () -> Unit,
    onReport: () -> Unit,
    onWord: (() -> Unit)? = null,
    onNavigate: (() -> Unit)? = null,
    onOpenMap: (() -> Unit)? = null
) {
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        TextButton(onClick = onDetail, modifier = Modifier.height(32.dp)) { 
            Text("CHI TIẾT", fontSize = 10.sp, fontWeight = FontWeight.Black) 
        }
        Spacer(modifier = Modifier.width(4.dp))
        
        IconButton(onClick = onReport, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Email, null, tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
        }
        
        onWord?.let {
            IconButton(onClick = it, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }

        onNavigate?.let {
            Spacer(modifier = Modifier.width(4.dp))
            Button(onClick = it, modifier = Modifier.height(32.dp), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Default.Navigation, null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("DẪN ĐƯỜNG", fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }

        onOpenMap?.let {
            Spacer(modifier = Modifier.width(4.dp))
            Button(onClick = it, modifier = Modifier.height(32.dp), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Default.Map, null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("MỞ ĐƯỜNG", fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun DailyJournalCard(
    journal: DailyJournalEntity,
    isVisible: Boolean = true,
    isGloballyVisible: Boolean = true,
    onDelete: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onExportWord: (DailyJournalEntity) -> Unit,
    onDetail: (DailyJournalEntity) -> Unit,
    onSendReport: (DailyJournalEntity) -> Unit,
    onUpdateColor: (Long, String) -> Unit = { _, _ -> }
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
        border = if (!isGloballyVisible) BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)) else null
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("NHẬT KÝ NGÀY: ${journal.dateStr}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    if (!isGloballyVisible) {
                        Text("ĐANG ẨN TOÀN CỤC", color = Color.Red, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
                CardStatusIcons(journal.isSynced, isVisible, {}, { onDelete(journal.id) })
            }
            Text(journal.content, maxLines = 3, fontSize = 12.sp, lineHeight = 18.sp, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            ColorPickerRow(selectedColor = journal.displayColorHex, onColorSelected = { onUpdateColor(journal.id, it) })
            CardActionButtons(
                onEdit = { onEdit(journal.id) },
                onDetail = { onDetail(journal) },
                onReport = { onSendReport(journal) },
                onWord = { onExportWord(journal) }
            )
        }
    }
}

@Composable
fun ColorPickerRow(
    selectedColor: String,
    onColorSelected: (String) -> Unit
) {
    val colors = listOf("#FF1976D2", "#FFD32F2F", "#FF388E3C", "#FFFBC02D", "#FF7B1FA2", "#FFFF3D00")
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Định dạng:", fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterVertically))
        colors.forEach { colorStr ->
            val color = Color(android.graphics.Color.parseColor(colorStr))
            Surface(
                onClick = { onColorSelected(colorStr) },
                shape = CircleShape,
                color = color,
                modifier = Modifier.size(24.dp),
                border = if (selectedColor == colorStr) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null
            ) {}
        }
    }
}

@Composable
fun WaypointCard(
    wp: WaypointEntity,
    isVisible: Boolean,
    isGloballyVisible: Boolean = true,
    onToggleVisibility: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onNavigate: (GpsPoint) -> Unit,
    onDetail: (WaypointEntity) -> Unit,
    onSendWaypointReport: (WaypointEntity) -> Unit,
    onUpdateColor: (Long, String) -> Unit,
    onRename: (Long, String) -> Unit = { _, _ -> },
    onBack: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("waypoint_card_${wp.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (!isGloballyVisible) BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val hasPhoto = !wp.photoPath.isNullOrEmpty() && wp.photoPath != "null"
            Box(modifier = Modifier.size(85.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (hasPhoto) {
                    AsyncImage(model = wp.photoPath, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                } else {
                    Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), modifier = Modifier.align(Alignment.Center).size(36.dp))
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(wp.title.ifEmpty { "Điểm không tên" }, fontWeight = FontWeight.Black, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                        if (!isGloballyVisible) Text("ĐANG ẨN TOÀN CỤC", color = Color.Red, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                    CardStatusIcons(wp.isSynced, isVisible, { onToggleVisibility(wp.id) }, { onDelete(wp.id) })
                }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(6.dp)) {
                    Text("VN2000: X=${String.format("%.0f", wp.vn2000X)}, Y=${String.format("%.0f", wp.vn2000Y)}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                ColorPickerRow(selectedColor = wp.displayColorHex, onColorSelected = { onUpdateColor(wp.id, it) })
                CardActionButtons(
                    onEdit = { onRename(wp.id, wp.title) },
                    onDetail = { onDetail(wp) },
                    onReport = { onSendWaypointReport(wp) },
                    onNavigate = { onNavigate(GpsPoint(wp.latitude, wp.longitude)); onBack() }
                )
            }
        }
    }
}

@Composable
fun TrackLogCard(
    trk: TrackLogEntity,
    isVisible: Boolean,
    isGloballyVisible: Boolean = true,
    onToggleVisibility: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onPreview: (TrackLogEntity) -> Unit,
    onDetail: (TrackLogEntity) -> Unit,
    onSendTrackLogReport: (TrackLogEntity) -> Unit,
    onUpdateColor: (Long, String) -> Unit,
    onRename: (Long, String) -> Unit = { _, _ -> },
    onBack: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    Card(
        modifier = Modifier.fillMaxWidth().testTag("tracklog_card_${trk.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(trk.title.ifEmpty { "Vệt đường thực địa" }, fontWeight = FontWeight.Black, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                    if (!isGloballyVisible) Text("ĐANG ẨN TOÀN CỤC", color = Color.Red, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
                CardStatusIcons(trk.isSynced, isVisible, { onToggleVisibility(trk.id) }, { onDelete(trk.id) })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp)) {
                    Text(String.format("%.2f km", trk.totalDistanceMeters / 1000.0), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontWeight = FontWeight.Black, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
                Text("Bắt đầu: ${dateFormat.format(Date(trk.startTimeUtc))}", fontSize = 11.sp, color = Color.Gray)
            }
            ColorPickerRow(selectedColor = trk.displayColorHex, onColorSelected = { onUpdateColor(trk.id, it) })
            CardActionButtons(
                onEdit = { onRename(trk.id, trk.title) },
                onDetail = { onDetail(trk) },
                onReport = { onSendTrackLogReport(trk) },
                onOpenMap = { onPreview(trk); onBack() }
            )
        }
    }
}

@Composable
fun PatrolLogCard(
    pt: PatrolLogEntity,
    isVisible: Boolean,
    isGloballyVisible: Boolean = true,
    onToggleVisibility: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onSendEmail: (PatrolLogEntity) -> Unit,
    onExportWord: (PatrolLogEntity) -> Unit,
    onDetail: (PatrolLogEntity) -> Unit,
    onUpdateColor: (Long, String) -> Unit = { _, _ -> },
    onRename: (Long, String) -> Unit = { _, _ -> }
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("patrol_card_${pt.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val firstPhoto = pt.photoPath?.split("|")?.firstOrNull { it.isNotEmpty() && it != "null" }
            Box(modifier = Modifier.size(85.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))) {
                if (firstPhoto != null) {
                    AsyncImage(model = firstPhoto, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                } else {
                    Icon(Icons.Default.Book, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center).size(36.dp))
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pt.incidentType, fontWeight = FontWeight.Black, fontSize = 15.sp, color = MaterialTheme.colorScheme.error)
                        if (!isGloballyVisible) Text("ĐANG ẨN TOÀN CỤC", color = Color.Red, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                    CardStatusIcons(pt.isSynced, isVisible, { onToggleVisibility(pt.id) }, { onDelete(pt.id) })
                }
                
                Text("Cán bộ: ${pt.leaderName}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Vị trí: ${pt.violationLocation}", fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)

                ColorPickerRow(selectedColor = pt.displayColorHex, onColorSelected = { onUpdateColor(pt.id, it) })
                
                CardActionButtons(
                    onEdit = { onEdit(pt.id) },
                    onDetail = { onDetail(pt) },
                    onReport = { onSendEmail(pt) },
                    onWord = { onExportWord(pt) }
                )
            }
        }
    }
}

@Composable
fun PolygonCard(
    poly: PolygonEntity,
    isVisible: Boolean,
    isGloballyVisible: Boolean = true,
    onToggleVisibility: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onNavigate: (GpsPoint) -> Unit,
    onDetail: (PolygonEntity) -> Unit,
    onSendReport: (PolygonEntity) -> Unit,
    onUpdateColor: (Long, String) -> Unit,
    onRename: (Long, String) -> Unit = { _, _ -> },
    onBack: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("polygon_card_${poly.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
        border = if (!isGloballyVisible) BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)) else null
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SquareFoot, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(poly.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    if (!isGloballyVisible) Text("ĐANG ẨN TOÀN CỤC", color = Color.Red, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
                CardStatusIcons(poly.isSynced, isVisible, { onToggleVisibility(poly.id) }, { onDelete(poly.id) })
            }
            Text("Diện tích: ${GisAreaCalculator.formatArea(poly.areaSquareMeters)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            ColorPickerRow(selectedColor = poly.displayColorHex, onColorSelected = { onUpdateColor(poly.id, it) })
            CardActionButtons(
                onEdit = { onRename(poly.id, poly.title) },
                onDetail = { onDetail(poly) },
                onReport = { onSendReport(poly) },
                onNavigate = { onNavigate(GpsPoint(poly.centroidLat, poly.centroidLon)); onBack() }
            )
        }
    }
}

@Composable
fun NaturalImpactLogCard(
    log: com.baoverung.app.data.local.entity.NaturalImpactLogEntity,
    isVisible: Boolean,
    isGloballyVisible: Boolean = true,
    onToggleVisibility: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onReport: (com.baoverung.app.data.local.entity.NaturalImpactLogEntity) -> Unit,
    onExportWord: (com.baoverung.app.data.local.entity.NaturalImpactLogEntity) -> Unit,
    onDetail: (com.baoverung.app.data.local.entity.NaturalImpactLogEntity) -> Unit,
    onUpdateColor: (Long, String) -> Unit = { _, _ -> }
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (!isGloballyVisible) BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)) else BorderStroke(1.dp, Color(0xFFFBC02D).copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val firstPhoto = log.photoPath?.split("|")?.firstOrNull { it.isNotEmpty() && it != "null" }
            Box(modifier = Modifier.size(85.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF9C4))) {
                if (firstPhoto != null) {
                    AsyncImage(model = firstPhoto, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                } else {
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFFBC02D), modifier = Modifier.align(Alignment.Center).size(36.dp))
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        val cause = if(log.cause=="Khác") log.otherCause else log.cause
                        Text("Tác động: $cause", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color(0xFFF57F17), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (!isGloballyVisible) Text("ĐANG ẨN TOÀN CỤC", color = Color.Red, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                    CardStatusIcons(log.isSynced, isVisible, { onToggleVisibility(log.id) }, { onDelete(log.id) })
                }
                Text("Diện tích: ${log.affectedArea}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                ColorPickerRow(selectedColor = log.displayColorHex, onColorSelected = { onUpdateColor(log.id, it) })
                CardActionButtons(
                    onEdit = { onEdit(log.id) },
                    onDetail = { onDetail(log) },
                    onReport = { onReport(log) },
                    onWord = { onExportWord(log) }
                )
            }
        }
    }
}

@Composable
fun FloraFaunaLogCard(
    log: com.baoverung.app.data.local.entity.FloraFaunaLogEntity,
    isVisible: Boolean,
    isGloballyVisible: Boolean = true,
    onToggleVisibility: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onReport: (com.baoverung.app.data.local.entity.FloraFaunaLogEntity) -> Unit,
    onExportWord: (com.baoverung.app.data.local.entity.FloraFaunaLogEntity) -> Unit,
    onDetail: (com.baoverung.app.data.local.entity.FloraFaunaLogEntity) -> Unit,
    onUpdateColor: (Long, String) -> Unit = { _, _ -> }
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (!isGloballyVisible) BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)) else BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val firstPhoto = log.photoPath?.split("|")?.firstOrNull { it.isNotEmpty() && it != "null" }
            Box(modifier = Modifier.size(85.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE8F5E9))) {
                if (firstPhoto != null) {
                    AsyncImage(model = firstPhoto, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                } else {
                    Icon(Icons.Default.Eco, null, tint = Color(0xFF2E7D32), modifier = Modifier.align(Alignment.Center).size(36.dp))
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Động thực vật: ${log.appearanceDescription}", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color(0xFF2E7D32), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (!isGloballyVisible) Text("ĐANG ẨN TOÀN CỤC", color = Color.Red, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                    CardStatusIcons(log.isSynced, isVisible, { onToggleVisibility(log.id) }, { onDelete(log.id) })
                }
                Text("Số lượng: ${log.count}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                ColorPickerRow(selectedColor = log.displayColorHex, onColorSelected = { onUpdateColor(log.id, it) })
                CardActionButtons(
                    onEdit = { onEdit(log.id) },
                    onDetail = { onDetail(log) },
                    onReport = { onReport(log) },
                    onWord = { onExportWord(log) }
                )
            }
        }
    }
}

@Composable
fun RenameDialog(
    initialTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Đổi tên dữ liệu", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Tên mới") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text); onDismiss() }) { Text("ĐỒNG Ý") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("HỦY") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaypointsAndTracksScreen(
    waypoints: List<WaypointEntity>,
    trackLogs: List<TrackLogEntity>,
    patrolLogs: List<PatrolLogEntity>,
    floraFaunaLogs: List<com.baoverung.app.data.local.entity.FloraFaunaLogEntity> = emptyList(),
    naturalImpactLogs: List<com.baoverung.app.data.local.entity.NaturalImpactLogEntity> = emptyList(),
    polygons: List<PolygonEntity> = emptyList(),
    dailyJournals: List<DailyJournalEntity> = emptyList(),
    hiddenWaypointIds: Set<Long> = emptySet(),
    hiddenTrackLogIds: Set<Long> = emptySet(),
    hiddenPatrolLogIds: Set<Long> = emptySet(),
    hiddenFloraFaunaIds: Set<Long> = emptySet(),
    hiddenNaturalImpactIds: Set<Long> = emptySet(),
    hiddenPolygonIds: Set<Long> = emptySet(),
    // Global Visibility States
    showImagesGlobal: Boolean = true,
    showPointsGlobal: Boolean = true,
    showTracklogsGlobal: Boolean = true,
    showLinesGlobal: Boolean = true,
    showPolygonsGlobal: Boolean = true,
    showIncidentsGlobal: Boolean = true,
    showDailyJournalsGlobal: Boolean = true,
    showFloraFaunaGlobal: Boolean = true,
    showNaturalImpactGlobal: Boolean = true,
    onToggleWaypointVisibility: (Long) -> Unit = {},
    onToggleTrackLogVisibility: (Long) -> Unit = {},
    onTogglePatrolLogVisibility: (Long) -> Unit = {},
    onToggleFloraFaunaVisibility: (Long) -> Unit = {},
    onToggleNaturalImpactVisibility: (Long) -> Unit = {},
    onTogglePolygonVisibility: (Long) -> Unit = {},
    onSetAllWaypointsVisible: (Boolean) -> Unit = {},
    onSetAllTrackLogsVisible: (Boolean) -> Unit = {},
    onSetAllPatrolLogsVisible: (Boolean) -> Unit = {},
    onSetAllFloraFaunaVisible: (Boolean) -> Unit = {},
    onSetAllNaturalImpactVisible: (Boolean) -> Unit = {},
    onUpdateWaypointColor: (Long, String) -> Unit = { _, _ -> },
    onUpdateTrackLogColor: (Long, String) -> Unit = { _, _ -> },
    onUpdatePolygonColor: (Long, String) -> Unit = { _, _ -> },
    onUpdateWaypointTitle: (Long, String) -> Unit = { _, _ -> },
    onUpdateTrackLogTitle: (Long, String) -> Unit = { _, _ -> },
    onUpdatePolygonTitle: (Long, String) -> Unit = { _, _ -> },
    onUpdatePatrolLogColor: (Long, String) -> Unit = { _, _ -> },
    onUpdatePatrolLogTitle: (Long, String) -> Unit = { _, _ -> },
    onUpdateDailyJournalColor: (Long, String) -> Unit = { _, _ -> },
    onUpdateFloraFaunaColor: (Long, String) -> Unit = { _, _ -> },
    onUpdateNaturalImpactColor: (Long, String) -> Unit = { _, _ -> },
    onDeleteWaypoint: (Long) -> Unit,
    onDeleteTrackLog: (Long) -> Unit,
    onDeletePatrolLog: (Long) -> Unit,
    onDeleteFloraFaunaLog: (Long) -> Unit = {},
    onDeleteNaturalImpactLog: (Long) -> Unit = {},
    onDeletePolygon: (Long) -> Unit = {},
    onDeleteDailyJournal: (Long) -> Unit = {},
    onEditDailyJournal: (Long) -> Unit = {},
    onSendDailyJournalReport: (DailyJournalEntity) -> Unit = {},
    onEditPatrolLog: (Long) -> Unit = {},
    onEditFloraFaunaLog: (Long) -> Unit = {},
    onEditNaturalImpactLog: (Long) -> Unit = {},
    onSendWaypointReport: (WaypointEntity) -> Unit = {},
    onSendTrackLogReport: (TrackLogEntity) -> Unit = {},
    onSendPolygonReport: (PolygonEntity) -> Unit = {},
    onSendFloraFaunaReport: (com.baoverung.app.data.local.entity.FloraFaunaLogEntity) -> Unit = {},
    onSendNaturalImpactReport: (com.baoverung.app.data.local.entity.NaturalImpactLogEntity) -> Unit = {},
    onSendDailyReportByDate: (String) -> Unit = {},
    onNavigateToPoint: (GpsPoint) -> Unit,
    onPreviewTrackLog: (TrackLogEntity) -> Unit = {},
    onSendEmailPatrolLog: (PatrolLogEntity) -> Unit = {},
    onExportDailyJournalWord: (DailyJournalEntity) -> Unit = {},
    onExportPatrolLogWord: (PatrolLogEntity) -> Unit = {},
    onExportFloraFaunaWord: (com.baoverung.app.data.local.entity.FloraFaunaLogEntity) -> Unit = {},
    onExportNaturalImpactWord: (com.baoverung.app.data.local.entity.NaturalImpactLogEntity) -> Unit = {},
    onBack: () -> Unit,
    vn2000CentralMeridian: Double = 107.75,
    vn2000ZoneDegrees: Int = 3
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("THEO NGÀY", "HÌNH ẢNH", "ĐIỂM", "TRACKLOG", "ĐƯỜNG (VỆT)", "VÙNG", "SỰ VỤ", "ĐỘNG THỰC VẬT", "TÁC ĐỘNG TN", "HẰNG NGÀY")

    var selectedTrackForDetail by remember { mutableStateOf<TrackLogEntity?>(null) }
    var selectedWaypointForDetail by remember { mutableStateOf<WaypointEntity?>(null) }
    var selectedPatrolForDetail by remember { mutableStateOf<PatrolLogEntity?>(null) }
    var selectedPolygonForDetail by remember { mutableStateOf<PolygonEntity?>(null) }
    var selectedJournalForDetail by remember { mutableStateOf<DailyJournalEntity?>(null) }
    var selectedFloraFaunaForDetail by remember { mutableStateOf<com.baoverung.app.data.local.entity.FloraFaunaLogEntity?>(null) }
    var selectedNaturalImpactForDetail by remember { mutableStateOf<com.baoverung.app.data.local.entity.NaturalImpactLogEntity?>(null) }

    var renameItem by remember { mutableStateOf<Pair<Long, String>?>(null) } 
    var renameType by remember { mutableStateOf("") } 

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QUẢN LÝ DỮ LIỆU THỰC ĐỊA", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp, divider = {}) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold) })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            when (selectedTab) {
                0 -> { // THEO NGÀY
                    val allItemsByDate = remember(waypoints, trackLogs, patrolLogs, polygons, floraFaunaLogs, naturalImpactLogs) {
                        val map = mutableMapOf<String, MutableList<Any>>()
                        val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        waypoints.forEach { map.getOrPut(dateFmt.format(Date(it.timestampUtc))) { mutableListOf() }.add(it) }
                        trackLogs.forEach { map.getOrPut(dateFmt.format(Date(it.startTimeUtc))) { mutableListOf() }.add(it) }
                        patrolLogs.forEach { map.getOrPut(dateFmt.format(Date(it.discoveryTimeUtc))) { mutableListOf() }.add(it) }
                        floraFaunaLogs.forEach { map.getOrPut(dateFmt.format(Date(it.timestampUtc))) { mutableListOf() }.add(it) }
                        naturalImpactLogs.forEach { map.getOrPut(dateFmt.format(Date(it.timestampUtc))) { mutableListOf() }.add(it) }
                        polygons.forEach { map.getOrPut(dateFmt.format(Date(it.timestampUtc))) { mutableListOf() }.add(it) }
                        map.entries.sortedByDescending { it.key }.toList()
                    }
                    if (allItemsByDate.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Chưa có dữ liệu", fontSize = 13.sp) }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            allItemsByDate.forEach { (date, items) ->
                                item {
                                    Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = "NGÀY $date (${items.size} mục)", modifier = Modifier.padding(8.dp), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                            TextButton(onClick = { onSendDailyReportByDate(date) }) {
                                                Icon(Icons.Default.Send, null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("BÁO CÁO NGÀY", fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                                items(items) { item ->
                                    when (item) {
                                        is WaypointEntity -> WaypointCard(item, !hiddenWaypointIds.contains(item.id), if (!item.photoPath.isNullOrEmpty()) showImagesGlobal else showPointsGlobal, onToggleWaypointVisibility, onDeleteWaypoint, onNavigateToPoint, { selectedWaypointForDetail = it }, onSendWaypointReport, onUpdateWaypointColor, { id, title -> renameItem = id to title; renameType = "WAYPOINT" }, onBack)
                                        is TrackLogEntity -> TrackLogCard(item, !hiddenTrackLogIds.contains(item.id), if (item.category == "GPX") showTracklogsGlobal else showLinesGlobal, onToggleTrackLogVisibility, onDeleteTrackLog, onPreviewTrackLog, { selectedTrackForDetail = it }, onSendTrackLogReport, onUpdateTrackLogColor, { id, title -> renameItem = id to title; renameType = "TRACK" }, onBack)
                                        is PatrolLogEntity -> PatrolLogCard(item, !hiddenPatrolLogIds.contains(item.id), showIncidentsGlobal, onTogglePatrolLogVisibility, onDeletePatrolLog, onEditPatrolLog, onSendEmailPatrolLog, onExportPatrolLogWord, { selectedPatrolForDetail = it }, onUpdatePatrolLogColor, { id, title -> renameItem = id to title; renameType = "PATROL" })
                                        is com.baoverung.app.data.local.entity.FloraFaunaLogEntity -> FloraFaunaLogCard(item, !hiddenFloraFaunaIds.contains(item.id), showFloraFaunaGlobal, onToggleFloraFaunaVisibility, onDeleteFloraFaunaLog, onEditFloraFaunaLog, onSendFloraFaunaReport, onExportFloraFaunaWord, { selectedFloraFaunaForDetail = it }, onUpdateFloraFaunaColor)
                                        is com.baoverung.app.data.local.entity.NaturalImpactLogEntity -> NaturalImpactLogCard(item, !hiddenNaturalImpactIds.contains(item.id), showNaturalImpactGlobal, onToggleNaturalImpactVisibility, onDeleteNaturalImpactLog, onEditNaturalImpactLog, onSendNaturalImpactReport, onExportNaturalImpactWord, { selectedNaturalImpactForDetail = it }, onUpdateNaturalImpactColor)
                                        is PolygonEntity -> PolygonCard(item, !hiddenPolygonIds.contains(item.id), showPolygonsGlobal, onTogglePolygonVisibility, onDeletePolygon, onNavigateToPoint, { selectedPolygonForDetail = it }, onSendPolygonReport, onUpdatePolygonColor, { id, title -> renameItem = id to title; renameType = "POLYGON" }, onBack)
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> { // HÌNH ẢNH
                    val photoWaypoints = waypoints.filter { !it.photoPath.isNullOrEmpty() }.sortedByDescending { it.timestampUtc }
                    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(photoWaypoints) { wp -> WaypointCard(wp, !hiddenWaypointIds.contains(wp.id), showImagesGlobal, onToggleWaypointVisibility, onDeleteWaypoint, onNavigateToPoint, { selectedWaypointForDetail = it }, onSendWaypointReport, onUpdateWaypointColor, { id, title -> renameItem = id to title; renameType = "WAYPOINT" }, onBack) }
                    }
                }
                2 -> { // ĐIỂM
                    val simplePoints = waypoints.filter { it.photoPath.isNullOrEmpty() }
                    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(simplePoints) { wp -> WaypointCard(wp, !hiddenWaypointIds.contains(wp.id), showPointsGlobal, onToggleWaypointVisibility, onDeleteWaypoint, onNavigateToPoint, { selectedWaypointForDetail = it }, onSendWaypointReport, onUpdateWaypointColor, { id, title -> renameItem = id to title; renameType = "WAYPOINT" }, onBack) }
                    }
                }
                3 -> { // TRACKLOG
                    val gpxTracks = trackLogs.filter { it.category == "GPX" }.sortedByDescending { it.startTimeUtc }
                    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(gpxTracks) { trk -> TrackLogCard(trk, !hiddenTrackLogIds.contains(trk.id), showTracklogsGlobal, onToggleTrackLogVisibility, onDeleteTrackLog, onPreviewTrackLog, { selectedTrackForDetail = it }, onSendTrackLogReport, onUpdateTrackLogColor, { id, title -> renameItem = id to title; renameType = "TRACK" }, onBack) }
                    }
                }
                4 -> { // ĐƯỜNG (VỆT)
                    val lineTracks = trackLogs.filter { it.category == "LINE" }.sortedByDescending { it.startTimeUtc }
                    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(lineTracks) { trk -> TrackLogCard(trk, !hiddenTrackLogIds.contains(trk.id), showLinesGlobal, onToggleTrackLogVisibility, onDeleteTrackLog, onPreviewTrackLog, { selectedTrackForDetail = it }, onSendTrackLogReport, onUpdateTrackLogColor, { id, title -> renameItem = id to title; renameType = "TRACK" }, onBack) }
                    }
                }
                5 -> { // VÙNG
                    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(polygons.sortedByDescending { it.timestampUtc }) { poly -> PolygonCard(poly, !hiddenPolygonIds.contains(poly.id), showPolygonsGlobal, onTogglePolygonVisibility, onDeletePolygon, onNavigateToPoint, { selectedPolygonForDetail = it }, onSendPolygonReport, onUpdatePolygonColor, { id, title -> renameItem = id to title; renameType = "POLYGON" }, onBack) }
                    }
                }
                6 -> { // SỰ VỤ
                    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(patrolLogs.sortedByDescending { it.discoveryTimeUtc }) { pt -> PatrolLogCard(pt, !hiddenPatrolLogIds.contains(pt.id), showIncidentsGlobal, onTogglePatrolLogVisibility, onDeletePatrolLog, onEditPatrolLog, onSendEmailPatrolLog, onExportPatrolLogWord, { selectedPatrolForDetail = it }, onUpdatePatrolLogColor, { id, title -> renameItem = id to title; renameType = "PATROL" }) }
                    }
                }
                7 -> { // ĐỘNG THỰC VẬT
                    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(floraFaunaLogs.sortedByDescending { it.timestampUtc }) { log -> FloraFaunaLogCard(log, !hiddenFloraFaunaIds.contains(log.id), showFloraFaunaGlobal, onToggleFloraFaunaVisibility, onDeleteFloraFaunaLog, onEditFloraFaunaLog, onSendFloraFaunaReport, onExportFloraFaunaWord, { selectedFloraFaunaForDetail = it }, onUpdateFloraFaunaColor) }
                    }
                }
                8 -> { // TÁC ĐỘNG TN
                    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(naturalImpactLogs.sortedByDescending { it.timestampUtc }) { log -> NaturalImpactLogCard(log, !hiddenNaturalImpactIds.contains(log.id), showNaturalImpactGlobal, onToggleNaturalImpactVisibility, onDeleteNaturalImpactLog, onEditNaturalImpactLog, onSendNaturalImpactReport, onExportNaturalImpactWord, { selectedNaturalImpactForDetail = it }, onUpdateNaturalImpactColor) }
                    }
                }
                9 -> { // HẰNG NGÀY
                    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(dailyJournals.sortedByDescending { it.timestampUtc }) { journal -> DailyJournalCard(journal, true, showDailyJournalsGlobal, onDeleteDailyJournal, onEditDailyJournal, onExportDailyJournalWord, { selectedJournalForDetail = it }, onSendDailyJournalReport, onUpdateDailyJournalColor) }
                    }
                }
            }
        }

        // Dialogs
        if (selectedPolygonForDetail != null) {
            val poly = selectedPolygonForDetail!!
            val cm = vn2000CentralMeridian
            val zone = vn2000ZoneDegrees
            val (vx, vy) = CoordinateSystemConverter.wgs84ToVn2000(poly.centroidLat, poly.centroidLon, cm, zone)
            
            AlertDialog(onDismissRequest = { selectedPolygonForDetail = null }, title = { Text("CHI TIẾT VÙNG DIỆN TÍCH", fontWeight = FontWeight.Bold) },
                text = { Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(poly.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(poly.description); HorizontalDivider()
                        Text("Diện tích: ${GisAreaCalculator.formatArea(poly.areaSquareMeters)}", fontWeight = FontWeight.Bold)
                        Text("VN2000 Tâm: X=${String.format("%.1f", vx)}, Y=${String.format("%.1f", vy)}", color = MaterialTheme.colorScheme.secondary)
                        Text("Thời gian: ${dateFormat.format(Date(poly.timestampUtc))}")
                    }
                },
                confirmButton = { Button(onClick = { onNavigateToPoint(GpsPoint(poly.centroidLat, poly.centroidLon)); selectedPolygonForDetail = null; onBack() }) { Text("DẪN ĐƯỜNG") } },
                dismissButton = { TextButton(onClick = { selectedPolygonForDetail = null }) { Text("ĐÓNG") } }
            )
        }

        if (selectedWaypointForDetail != null) {
            val wp = selectedWaypointForDetail!!
            AlertDialog(onDismissRequest = { selectedWaypointForDetail = null }, title = { Text("CHI TIẾT ĐIỂM KHẢO SÁT", fontWeight = FontWeight.Bold) },
                text = { Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!wp.photoPath.isNullOrEmpty() && wp.photoPath != "null") { AsyncImage(model = wp.photoPath, contentDescription = null, modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                        Text(wp.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Text(wp.description); HorizontalDivider()
                        Text("VN2000: X=${String.format("%.2f", wp.vn2000X)}, Y=${String.format("%.2f", wp.vn2000Y)}", fontWeight = FontWeight.Bold)
                        Text("Thời gian: ${dateFormat.format(Date(wp.timestampUtc))}")
                    }
                },
                confirmButton = { Button(onClick = { onNavigateToPoint(GpsPoint(wp.latitude, wp.longitude)); selectedWaypointForDetail = null; onBack() }) { Text("DẪN ĐƯỜNG") } },
                dismissButton = { TextButton(onClick = { selectedWaypointForDetail = null }) { Text("ĐÓNG") } }
            )
        }

        if (selectedPatrolForDetail != null) {
            val pt = selectedPatrolForDetail!!
            AlertDialog(onDismissRequest = { selectedPatrolForDetail = null }, title = { Text("CHI TIẾT NHẬT KÝ TUẦN TRA", fontWeight = FontWeight.Bold) },
                text = { Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val firstPhoto = pt.photoPath?.split("|")?.firstOrNull { it.isNotEmpty() && it != "null" }
                        if (firstPhoto != null) { AsyncImage(model = firstPhoto, contentDescription = null, modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                        Text(pt.incidentType, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text("Cán bộ: ${pt.leaderName}"); Text("Địa điểm: ${pt.violationLocation}")
                        Text("Thời gian: ${dateFormat.format(Date(pt.discoveryTimeUtc))}"); HorizontalDivider()
                        Text("VN2000: X=${String.format("%.2f", pt.vn2000X)}, Y=${String.format("%.2f", pt.vn2000Y)}")
                    }
                },
                confirmButton = { Button(onClick = { onNavigateToPoint(GpsPoint(pt.latitude, pt.longitude)); selectedPatrolForDetail = null; onBack() }) { Text("DẪN ĐƯỜNG") } },
                dismissButton = { TextButton(onClick = { selectedPatrolForDetail = null }) { Text("ĐÓNG") } }
            )
        }

        if (selectedFloraFaunaForDetail != null) {
            val log = selectedFloraFaunaForDetail!!
            AlertDialog(onDismissRequest = { selectedFloraFaunaForDetail = null }, title = { Text("CHI TIẾT ĐỘNG THỰC VẬT", fontWeight = FontWeight.Bold) },
                text = { Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val firstPhoto = log.photoPath?.split("|")?.firstOrNull { it.isNotEmpty() && it != "null" }
                        if (firstPhoto != null) { AsyncImage(model = firstPhoto, contentDescription = null, modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                        Text("Mô tả: ${log.appearanceDescription}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        Text("Số lượng: ${log.count}"); Text("Sinh cảnh: ${log.habitatType}"); HorizontalDivider()
                        Text("Cán bộ: ${log.officerName}"); Text("Thời gian: ${dateFormat.format(Date(log.timestampUtc))}")
                        Text("VN2000: X=${String.format("%.1f", log.vn2000X)}, Y=${String.format("%.1f", log.vn2000Y)}")
                    }
                },
                confirmButton = { Button(onClick = { onNavigateToPoint(GpsPoint(log.latitude, log.longitude)); selectedFloraFaunaForDetail = null; onBack() }) { Text("DẪN ĐƯỜNG") } },
                dismissButton = { TextButton(onClick = { selectedFloraFaunaForDetail = null }) { Text("ĐÓNG") } }
            )
        }

        if (selectedNaturalImpactForDetail != null) {
            val log = selectedNaturalImpactForDetail!!
            AlertDialog(onDismissRequest = { selectedNaturalImpactForDetail = null }, title = { Text("CHI TIẾT TÁC ĐỘNG TỰ NHIÊN", fontWeight = FontWeight.Bold) },
                text = { Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val firstPhoto = log.photoPath?.split("|")?.firstOrNull { it.isNotEmpty() && it != "null" }
                        if (firstPhoto != null) { AsyncImage(model = firstPhoto, contentDescription = null, modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                        Text("Nguyên nhân: ${log.cause}", fontWeight = FontWeight.Bold, color = Color(0xFFF57F17))
                        Text("Diện tích: ${log.affectedArea}"); Text("Thiệt hại: ${log.resourceDamage}"); HorizontalDivider()
                        Text("Cán bộ: ${log.officerName}"); Text("Thời gian: ${dateFormat.format(Date(log.timestampUtc))}")
                        Text("VN2000: X=${String.format("%.1f", log.vn2000X)}, Y=${String.format("%.1f", log.vn2000Y)}")
                    }
                },
                confirmButton = { Button(onClick = { onNavigateToPoint(GpsPoint(log.latitude, log.longitude)); selectedNaturalImpactForDetail = null; onBack() }) { Text("DẪN ĐƯỜNG") } },
                dismissButton = { TextButton(onClick = { selectedNaturalImpactForDetail = null }) { Text("ĐÓNG") } }
            )
        }

        if (selectedJournalForDetail != null) {
            val journal = selectedJournalForDetail!!
            AlertDialog(onDismissRequest = { selectedJournalForDetail = null }, title = { Text("CHI TIẾT NHẬT KÝ HẰNG NGÀY", fontWeight = FontWeight.Bold) },
                text = { Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ngày: ${journal.dateStr}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(journal.content); HorizontalDivider()
                        if (journal.notes.isNotEmpty()) Text("Ghi chú: ${journal.notes}")
                    }
                },
                confirmButton = { Button(onClick = { onExportDailyJournalWord(journal); selectedJournalForDetail = null }) { Text("XUẤT WORD") } },
                dismissButton = { TextButton(onClick = { selectedJournalForDetail = null }) { Text("ĐÓNG") } }
            )
        }

        if (selectedTrackForDetail != null) {
            val trk = selectedTrackForDetail!!
            val sampledPoints = remember(trk.sampledPointsJson) {
                if (trk.sampledPointsJson.isNullOrEmpty()) emptyList()
                else {
                    try {
                        val array = org.json.JSONArray(trk.sampledPointsJson)
                        val list = mutableListOf<Map<String, Any>>()
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val map = mutableMapOf<String, Any>()
                            map["vn2000X"] = obj.optDouble("vn2000X", 0.0)
                            map["vn2000Y"] = obj.optDouble("vn2000Y", 0.0)
                            list.add(map)
                        }
                        list
                    } catch (e: Exception) { emptyList<Map<String, Any>>() }
                }
            }
            
            AlertDialog(onDismissRequest = { selectedTrackForDetail = null }, title = { Text("CHI TIẾT TRACKLOG", fontWeight = FontWeight.Bold) },
                text = { Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Tên: ${trk.title}", fontWeight = FontWeight.Bold)
                        Text("Chiều dài: ${String.format("%.2f km", trk.totalDistanceMeters / 1000.0)}")
                        Text("Thời gian: ${dateFormat.format(Date(trk.startTimeUtc))}")
                        
                        if (sampledPoints.isNotEmpty()) {
                            HorizontalDivider()
                            Text("TỌA ĐỘ MẪU (VN2000):", fontWeight = FontWeight.Black, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            sampledPoints.forEachIndexed { index, pt ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${index + 1}.", fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.width(20.dp))
                                    Text("X: ${String.format("%.1f", pt["vn2000X"])}", fontSize = 10.sp, modifier = Modifier.weight(1f))
                                    Text("Y: ${String.format("%.1f", pt["vn2000Y"])}", fontSize = 10.sp, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                },
                confirmButton = { Button(onClick = { onPreviewTrackLog(trk); selectedTrackForDetail = null; onBack() }) { Text("MỞ ĐƯỜNG") } },
                dismissButton = { TextButton(onClick = { selectedTrackForDetail = null }) { Text("ĐÓNG") } }
            )
        }
        
        renameItem?.let { (id, title) ->
            RenameDialog(initialTitle = title, onConfirm = { newTitle ->
                when (renameType) {
                    "WAYPOINT" -> onUpdateWaypointTitle(id, newTitle); "TRACK" -> onUpdateTrackLogTitle(id, newTitle)
                    "POLYGON" -> onUpdatePolygonTitle(id, newTitle); "PATROL" -> onUpdatePatrolLogTitle(id, newTitle)
                }
                renameItem = null
            }, onDismiss = { renameItem = null })
        }
    }
}
