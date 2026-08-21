package com.baoverung.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.*
import com.baoverung.app.data.local.UserPreferencesManager
import com.baoverung.app.gis.CoordinateSystem
import com.baoverung.app.gis.CoordinateSystemConverter
import com.baoverung.app.ui.components.SystemSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: UserPreferencesManager,
    emailQueueSize: Int,
    activeCoordSystemId: String,
    
    // Visibility States
    showLabelsGlobal: Boolean,
    showImagesGlobal: Boolean,
    showPointsGlobal: Boolean,
    showTracklogsGlobal: Boolean,
    showLinesGlobal: Boolean,
    showPolygonsGlobal: Boolean,
    showIncidentsGlobal: Boolean,
    showDailyJournalsGlobal: Boolean,
    showFloraFaunaGlobal: Boolean,
    showNaturalImpactGlobal: Boolean,

    // Detailed Category Values
    imageColor: String,
    imageIconType: String,
    imageIconSize: Int,
    showImageLabels: Boolean,
    imageLabelSize: Int,
    imageQuality: Int,
    imageResize: Int,

    pointColor: String,
    pointIconType: String,
    pointIconSize: Int,
    showPointLabels: Boolean,
    pointLabelSize: Int,

    tracklogColor: String,
    tracklogWidth: Float,
    tracklogStyle: String,
    showTracklogLabels: Boolean,
    showTracklogValue: Boolean,
    tracklogFontSize: Int,

    lineColor: String,
    lineWidth: Float,
    lineStyle: String,
    showLineLabels: Boolean,
    showLineValue: Boolean,
    lineFontSize: Int,

    polygonBoundaryColor: String,
    polygonFillColor: String,
    polygonWidth: Float,
    polygonStyle: String,
    showPolygonLabels: Boolean,
    showPolygonValue: Boolean,
    polygonFontSize: Int,

    incidentColor: String,
    incidentIconType: String,
    incidentIconSize: Int,
    showIncidentLabels: Boolean,
    incidentFontSize: Int,

    floraFaunaColor: String,
    floraFaunaIconType: String,
    floraFaunaIconSize: Int,
    showFloraFaunaLabels: Boolean,
    floraFaunaFontSize: Int,

    naturalImpactColor: String,
    naturalImpactIconType: String,
    naturalImpactIconSize: Int,
    showNaturalImpactLabels: Boolean,
    naturalImpactFontSize: Int,

    // System States
    distanceUnit: String,
    areaUnit: String,
    gpsFilterDistance: Float,
    trackingIntervalSeconds: Int,
    antennaHeight: Float,
    useAGps: Boolean,
    keepScreenOn: Boolean,
    shakeToMoveMap: Boolean,
    fixMbTilesDisplay: Boolean,
    defaultIncidentLeader: String,
    defaultIncidentField: String,
    
    onSaveVn2000Settings: (province: String, cm: Double, zone: Int) -> Unit,
    onSetActiveCoordSystem: (String) -> Unit,
    onSendPendingEmails: (android.content.Context) -> Unit,

    // Callbacks
    onUpdateFontEncoding: (String) -> Unit,
    onUpdateShowLabelsGlobal: (Boolean) -> Unit,
    onUpdateShowImagesGlobal: (Boolean) -> Unit,
    onUpdateShowPointsGlobal: (Boolean) -> Unit,
    onUpdateShowTracklogsGlobal: (Boolean) -> Unit,
    onUpdateShowLinesGlobal: (Boolean) -> Unit,
    onUpdateShowPolygonsGlobal: (Boolean) -> Unit,
    onUpdateShowIncidentsGlobal: (Boolean) -> Unit,
    onUpdateShowDailyJournalsGlobal: (Boolean) -> Unit,
    onUpdateShowFloraFaunaGlobal: (Boolean) -> Unit,
    onUpdateShowNaturalImpactGlobal: (Boolean) -> Unit,

    onUpdateImageColor: (String) -> Unit,
    onUpdateImageIconType: (String) -> Unit,
    onUpdateImageIconSize: (Int) -> Unit,
    onUpdateShowImageLabels: (Boolean) -> Unit,
    onUpdateImageLabelSize: (Int) -> Unit,
    onUpdateImageQuality: (Int) -> Unit,
    onUpdateImageResize: (Int) -> Unit,

    onUpdatePointColor: (String) -> Unit,
    onUpdatePointIconType: (String) -> Unit,
    onUpdatePointIconSize: (Int) -> Unit,
    onUpdateShowPointLabels: (Boolean) -> Unit,
    onUpdatePointLabelSize: (Int) -> Unit,

    onUpdateTracklogColor: (String) -> Unit,
    onUpdateTracklogWidth: (Float) -> Unit,
    onUpdateTracklogStyle: (String) -> Unit,
    onUpdateShowTracklogLabels: (Boolean) -> Unit,
    onUpdateShowTracklogValue: (Boolean) -> Unit,
    onUpdateTracklogFontSize: (Int) -> Unit,

    onUpdateLineColor: (String) -> Unit,
    onUpdateLineWidth: (Float) -> Unit,
    onUpdateLineStyle: (String) -> Unit,
    onUpdateShowLineLabels: (Boolean) -> Unit,
    onUpdateShowLineValue: (Boolean) -> Unit,
    onUpdateLineFontSize: (Int) -> Unit,

    onUpdatePolygonBoundaryColor: (String) -> Unit,
    onUpdatePolygonFillColor: (String) -> Unit,
    onUpdatePolygonWidth: (Float) -> Unit,
    onUpdatePolygonStyle: (String) -> Unit,
    onUpdateShowPolygonLabels: (Boolean) -> Unit,
    onUpdateShowPolygonValue: (Boolean) -> Unit,
    onUpdatePolygonFontSize: (Int) -> Unit,

    onUpdateIncidentColor: (String) -> Unit,
    onUpdateIncidentIconType: (String) -> Unit,
    onUpdateIncidentIconSize: (Int) -> Unit,
    onUpdateShowIncidentLabels: (Boolean) -> Unit,
    onUpdateIncidentFontSize: (Int) -> Unit,

    onUpdateFloraFaunaColor: (String) -> Unit,
    onUpdateFloraFaunaIconType: (String) -> Unit,
    onUpdateFloraFaunaIconSize: (Int) -> Unit,
    onUpdateShowFloraFaunaLabels: (Boolean) -> Unit,
    onUpdateFloraFaunaFontSize: (Int) -> Unit,

    onUpdateNaturalImpactColor: (String) -> Unit,
    onUpdateNaturalImpactIconType: (String) -> Unit,
    onUpdateNaturalImpactIconSize: (Int) -> Unit,
    onUpdateShowNaturalImpactLabels: (Boolean) -> Unit,
    onUpdateNaturalImpactFontSize: (Int) -> Unit,

    // Misc
    onUpdateDistanceUnit: (String) -> Unit,
    onUpdateAreaUnit: (String) -> Unit,
    onUpdateGpsFilterDistance: (Float) -> Unit,
    onUpdateTrackingIntervalSeconds: (Int) -> Unit,
    onUpdateAntennaHeight: (Float) -> Unit,
    onUpdateUseAGps: (Boolean) -> Unit,
    onUpdateLongPressOnMapEnabled: (Boolean) -> Unit,
    onUpdateGetAddressOnPress: (Boolean) -> Unit,
    onUpdateShakeToMoveMap: (Boolean) -> Unit,
    onUpdateKeepScreenOn: (Boolean) -> Unit,
    onUpdateFixMbTilesDisplay: (Boolean) -> Unit,
    onUpdateDefaultIncidentLeader: (String) -> Unit,
    onUpdateDefaultIncidentField: (String) -> Unit,

    // Map UI (Keep existing)
    showViewAngle: Boolean,
    showViewLine: Boolean,
    showMoveDirection: Boolean,
    showMoveLine: Boolean,
    showCompass: Boolean,
    showSatelliteInfo: Boolean,
    showZoomControls: Boolean,
    showRotationControls: Boolean,
    showZoomLevel: Boolean,
    showMapCenter: Boolean,
    onUpdateShowViewAngle: (Boolean) -> Unit,
    onUpdateShowViewLine: (Boolean) -> Unit,
    onUpdateShowMoveDirection: (Boolean) -> Unit,
    onUpdateShowMoveLine: (Boolean) -> Unit,
    onUpdateShowCompass: (Boolean) -> Unit,
    onUpdateShowSatelliteInfo: (Boolean) -> Unit,
    onUpdateShowZoomControls: (Boolean) -> Unit,
    onUpdateShowRotationControls: (Boolean) -> Unit,
    onUpdateShowZoomLevel: (Boolean) -> Unit,
    onUpdateShowMapCenter: (Boolean) -> Unit,

    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val currentCoordSystem = remember(activeCoordSystemId, prefs.vn2000CentralMeridian, prefs.vn2000ZoneDegrees, prefs.vn2000ProvinceName) {
        val base = CoordinateSystemConverter.SYSTEMS.find { it.id == activeCoordSystemId }
        if (base != null && base.projection != "VN2000") base
        else {
            val cmStr = CoordinateSystemConverter.formatDegreeToDm(prefs.vn2000CentralMeridian)
            CoordinateSystem(
                id = activeCoordSystemId,
                name = if (activeCoordSystemId.startsWith("VN2000_3")) "VN2000 Múi 3° KT $cmStr (${prefs.vn2000ProvinceName})" else base?.name ?: activeCoordSystemId,
                projection = base?.projection ?: "VN2000",
                centralMeridian = prefs.vn2000CentralMeridian,
                zoneDegrees = prefs.vn2000ZoneDegrees
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CẤU HÌNH HỆ THỐNG", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .background(MaterialTheme.colorScheme.surface),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            
            // --- SECTION: HỆ TỌA ĐỘ ---
            SettingGroupCard(title = "HỆ TỌA ĐỘ HIỂN THỊ CHÍNH", icon = Icons.Default.Public) {
                SystemSelector(
                    selected = currentCoordSystem,
                    onSelect = { sys ->
                        onSetActiveCoordSystem(sys.id)
                        if (sys.projection == "VN2000") {
                            val provName = if (sys.name.contains("(") && sys.name.contains(")")) 
                                sys.name.substringAfterLast("(").substringBeforeLast(")") 
                                else prefs.vn2000ProvinceName
                            onSaveVn2000Settings(provName, sys.centralMeridian, sys.zoneDegrees)
                        }
                    }
                )
            }

            // --- PHÔNG CHỮ & HIỂN THỊ CHUNG ---
            SettingGroupCard(title = "PHÔNG CHỮ & HIỂN THỊ CHUNG", icon = Icons.Default.FontDownload) {
                DropdownItem(
                    title = "Bảng mã hóa font",
                    currentValue = prefs.fontEncoding,
                    onSelect = { onUpdateFontEncoding(it) }
                )
                SwitchItem(title = "Hiển thị lớp nhãn (Global)", checked = showLabelsGlobal, onCheckedChange = { onUpdateShowLabelsGlobal(it) })
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SwitchItem(title = "Hiển thị Hình ảnh", checked = showImagesGlobal, onCheckedChange = { onUpdateShowImagesGlobal(it) })
                SwitchItem(title = "Hiển thị Điểm", checked = showPointsGlobal, onCheckedChange = { onUpdateShowPointsGlobal(it) })
                SwitchItem(title = "Hiển thị Tracklog", checked = showTracklogsGlobal, onCheckedChange = { onUpdateShowTracklogsGlobal(it) })
                SwitchItem(title = "Hiển thị Đường (vệt)", checked = showLinesGlobal, onCheckedChange = { onUpdateShowLinesGlobal(it) })
                SwitchItem(title = "Hiển thị Vùng", checked = showPolygonsGlobal, onCheckedChange = { onUpdateShowPolygonsGlobal(it) })
                SwitchItem(title = "Hiển thị Nhật ký sự vụ", checked = showIncidentsGlobal, onCheckedChange = { onUpdateShowIncidentsGlobal(it) })
                SwitchItem(title = "Hiển thị Động thực vật", checked = showFloraFaunaGlobal, onCheckedChange = { onUpdateShowFloraFaunaGlobal(it) })
                SwitchItem(title = "Hiển thị Tác động tự nhiên", checked = showNaturalImpactGlobal, onCheckedChange = { onUpdateShowNaturalImpactGlobal(it) })
                SwitchItem(title = "Hiển thị Nhật ký hằng ngày", checked = showDailyJournalsGlobal, onCheckedChange = { onUpdateShowDailyJournalsGlobal(it) })
            }

            SettingGroupCard(title = "CÀI ĐẶT HÌNH ẢNH", icon = Icons.Default.CameraAlt) {
                ColorItem(title = "Màu sắc biểu tượng", colorHex = imageColor, onColorSelected = { onUpdateImageColor(it) })
                DropdownItem(title = "Kiểu biểu tượng (Hình ảnh)", currentValue = imageIconType, onSelect = { onUpdateImageIconType(it) })
                ValueItem(title = "Kích thước biểu tượng", value = "${imageIconSize.toInt()}", onValueClick = { showNumberDialog(context, "Kích thước", imageIconSize.toInt(), 20, 100) { onUpdateImageIconSize(it) } })
                SwitchItem(title = "Hiển thị nhãn tên", checked = showImageLabels, onCheckedChange = { onUpdateShowImageLabels(it) })
                ValueItem(title = "Cỡ chữ nhãn", value = "$imageLabelSize", onValueClick = { showNumberDialog(context, "Cỡ chữ", imageLabelSize, 8, 30) { onUpdateImageLabelSize(it) } })
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Cấu hình ảnh lưu & đóng dấu:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                ValueItem(title = "Chất lượng nén ảnh (JPEG %)", description = "95% là tốt nhất, 50% tiết kiệm bộ nhớ", value = "$imageQuality%", onValueClick = { showNumberDialog(context, "Chất lượng", imageQuality, 10, 100) { onUpdateImageQuality(it) } })
                ValueItem(title = "Kích thước tối đa (pixel)", description = "Thu nhỏ ảnh để gửi Gmail nhanh hơn", value = "${imageResize}px", onValueClick = { showNumberDialog(context, "Kích thước", imageResize, 800, 4000) { onUpdateImageResize(it) } })
            }

            // --- 2. CÀI ĐẶT ĐIỂM ---
            SettingGroupCard(title = "CÀI ĐẶT ĐIỂM", icon = Icons.Default.LocationOn) {
                ColorItem(title = "Màu sắc biểu tượng", colorHex = pointColor, onColorSelected = { onUpdatePointColor(it) })
                DropdownItem(title = "Kiểu biểu tượng (Điểm)", currentValue = pointIconType, onSelect = { onUpdatePointIconType(it) })
                ValueItem(title = "Kích thước biểu tượng", value = "${pointIconSize.toInt()}", onValueClick = { showNumberDialog(context, "Kích thước", pointIconSize.toInt(), 20, 100) { onUpdatePointIconSize(it) } })
                SwitchItem(title = "Hiển thị nhãn tên", checked = showPointLabels, onCheckedChange = { onUpdateShowPointLabels(it) })
                ValueItem(title = "Cỡ chữ nhãn", value = "$pointLabelSize", onValueClick = { showNumberDialog(context, "Cỡ chữ", pointLabelSize, 8, 30) { onUpdatePointLabelSize(it) } })
            }

            // --- 3. CÀI ĐẶT TRACKLOG ---
            SettingGroupCard(title = "CÀI ĐẶT TRACKLOG", icon = Icons.Default.DirectionsRun) {
                ColorItem(title = "Màu sắc đường", colorHex = tracklogColor, onColorSelected = { onUpdateTracklogColor(it) })
                ValueItem(title = "Độ rộng đường", value = "${tracklogWidth.toInt()}", onValueClick = { showNumberDialog(context, "Độ rộng", tracklogWidth.toInt(), 1, 15) { onUpdateTracklogWidth(it.toFloat()) } })
                DropdownItem(title = "Kiểu nét vẽ (Tracklog)", currentValue = tracklogStyle, onSelect = { onUpdateTracklogStyle(it) })
                SwitchItem(title = "Hiển thị nhãn tên", checked = showTracklogLabels, onCheckedChange = { onUpdateShowTracklogLabels(it) })
                SwitchItem(title = "Hiển thị chiều dài", checked = showTracklogValue, onCheckedChange = { onUpdateShowTracklogValue(it) })
                ValueItem(title = "Cỡ chữ nhãn", value = "$tracklogFontSize", onValueClick = { showNumberDialog(context, "Cỡ chữ", tracklogFontSize, 8, 30) { onUpdateTracklogFontSize(it) } })
            }

            // --- 4. CÀI ĐẶT ĐƯỜNG (VỆT) ---
            SettingGroupCard(title = "CÀI ĐẶT ĐƯỜNG (VỆT)", icon = Icons.Default.Timeline) {
                ColorItem(title = "Màu sắc đường", colorHex = lineColor, onColorSelected = { onUpdateLineColor(it) })
                ValueItem(title = "Độ rộng đường", value = "${lineWidth.toInt()}", onValueClick = { showNumberDialog(context, "Độ rộng", lineWidth.toInt(), 1, 15) { onUpdateLineWidth(it.toFloat()) } })
                DropdownItem(title = "Kiểu nét vẽ (Đường)", currentValue = lineStyle, onSelect = { onUpdateLineStyle(it) })
                SwitchItem(title = "Hiển thị nhãn tên", checked = showLineLabels, onCheckedChange = { onUpdateShowLineLabels(it) })
                SwitchItem(title = "Hiển thị chiều dài", checked = showLineValue, onCheckedChange = { onUpdateShowLineValue(it) })
                ValueItem(title = "Cỡ chữ nhãn", value = "$lineFontSize", onValueClick = { showNumberDialog(context, "Cỡ chữ", lineFontSize, 8, 30) { onUpdateLineFontSize(it) } })
            }

            // --- 5. CÀI ĐẶT VÙNG ---
            SettingGroupCard(title = "CÀI ĐẶT VÙNG (LÔ RỪNG)", icon = Icons.Default.Hexagon) {
                ColorItem(title = "Màu sắc ranh giới", colorHex = polygonBoundaryColor, onColorSelected = { onUpdatePolygonBoundaryColor(it) })
                ValueItem(title = "Độ rộng ranh giới", value = "${polygonWidth.toInt()}", onValueClick = { showNumberDialog(context, "Độ rộng", polygonWidth.toInt(), 1, 15) { onUpdatePolygonWidth(it.toFloat()) } })
                ColorItem(title = "Màu nền bên trong", colorHex = polygonFillColor, onColorSelected = { onUpdatePolygonFillColor(it) })
                DropdownItem(title = "Kiểu ranh giới (Vùng)", currentValue = polygonStyle, onSelect = { onUpdatePolygonStyle(it) })
                SwitchItem(title = "Hiển thị nhãn tên", checked = showPolygonLabels, onCheckedChange = { onUpdateShowPolygonLabels(it) })
                SwitchItem(title = "Hiển thị diện tích", checked = showPolygonValue, onCheckedChange = { onUpdateShowPolygonValue(it) })
                ValueItem(title = "Cỡ chữ nhãn", value = "$polygonFontSize", onValueClick = { showNumberDialog(context, "Cỡ chữ", polygonFontSize, 8, 30) { onUpdatePolygonFontSize(it) } })
            }

            // --- 6. CÀI ĐẶT NHẬT KÝ SỰ VỤ ---
            SettingGroupCard(title = "CÀI ĐẶT NHẬT KÝ SỰ VỤ", icon = Icons.Default.Assignment) {
                ColorItem(title = "Màu sắc mặc định", colorHex = incidentColor, onColorSelected = { onUpdateIncidentColor(it) })
                DropdownItem(title = "Kiểu biểu tượng (Sự vụ)", currentValue = incidentIconType, onSelect = { onUpdateIncidentIconType(it) })
                ValueItem(title = "Kích thước biểu tượng", value = "${incidentIconSize.toInt()}", onValueClick = { showNumberDialog(context, "Kích thước", incidentIconSize.toInt(), 20, 100) { onUpdateIncidentIconSize(it) } })
                SwitchItem(title = "Hiển thị nhãn tên", checked = showIncidentLabels, onCheckedChange = { onUpdateShowIncidentLabels(it) })
                ValueItem(title = "Cỡ chữ nhãn", value = "$incidentFontSize", onValueClick = { showNumberDialog(context, "Cỡ chữ", incidentFontSize, 8, 30) { onUpdateIncidentFontSize(it) } })
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                var leaderInput by remember { mutableStateOf(defaultIncidentLeader) }
                OutlinedTextField(
                    value = leaderInput,
                    onValueChange = { leaderInput = it; onUpdateDefaultIncidentLeader(it) },
                    label = { Text("Tổ trưởng mặc định") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownItem(title = "Lĩnh vực mặc định", currentValue = defaultIncidentField, onSelect = { onUpdateDefaultIncidentField(it) })
            }

            // --- 7. CÀI ĐẶT ĐỘNG THỰC VẬT ---
            SettingGroupCard(title = "CÀI ĐẶT ĐỘNG THỰC VẬT", icon = Icons.Default.Eco) {
                ColorItem(title = "Màu sắc biểu tượng", colorHex = floraFaunaColor, onColorSelected = { onUpdateFloraFaunaColor(it) })
                DropdownItem(title = "Kiểu biểu tượng (Động thực vật)", currentValue = floraFaunaIconType, onSelect = { onUpdateFloraFaunaIconType(it) })
                ValueItem(title = "Kích thước biểu tượng", value = "${floraFaunaIconSize.toInt()}", onValueClick = { showNumberDialog(context, "Kích thước", floraFaunaIconSize.toInt(), 20, 100) { onUpdateFloraFaunaIconSize(it) } })
                SwitchItem(title = "Hiển thị nhãn tên", checked = showFloraFaunaLabels, onCheckedChange = { onUpdateShowFloraFaunaLabels(it) })
                ValueItem(title = "Cỡ chữ nhãn", value = "$floraFaunaFontSize", onValueClick = { showNumberDialog(context, "Cỡ chữ", floraFaunaFontSize, 8, 30) { onUpdateFloraFaunaFontSize(it) } })
            }

            // --- 8. CÀI ĐẶT TÁC ĐỘNG TỰ NHIÊN ---
            SettingGroupCard(title = "CÀI ĐẶT TÁC ĐỘNG TỰ NHIÊN", icon = Icons.Default.Warning) {
                ColorItem(title = "Màu sắc biểu tượng", colorHex = naturalImpactColor, onColorSelected = { onUpdateNaturalImpactColor(it) })
                DropdownItem(title = "Kiểu biểu tượng (Tác động TN)", currentValue = naturalImpactIconType, onSelect = { onUpdateNaturalImpactIconType(it) })
                ValueItem(title = "Kích thước biểu tượng", value = "${naturalImpactIconSize.toInt()}", onValueClick = { showNumberDialog(context, "Kích thước", naturalImpactIconSize.toInt(), 20, 100) { onUpdateNaturalImpactIconSize(it) } })
                SwitchItem(title = "Hiển thị nhãn tên", checked = showNaturalImpactLabels, onCheckedChange = { onUpdateShowNaturalImpactLabels(it) })
                ValueItem(title = "Cỡ chữ nhãn", value = "$naturalImpactFontSize", onValueClick = { showNumberDialog(context, "Cỡ chữ", naturalImpactFontSize, 8, 30) { onUpdateNaturalImpactFontSize(it) } })
            }

            // --- CÀI ĐẶT BẢN ĐỒ ---
            SettingGroupCard(title = "CÀI ĐẶT BẢN ĐỒ", icon = Icons.Default.Map) {
                SwitchItem(title = "Vị trí góc hướng nhìn", description = "Hiển thị quạt quét 45 độ theo hướng nhìn", checked = showViewAngle, onCheckedChange = { onUpdateShowViewAngle(it) })
                SwitchItem(title = "Vị trí đường hướng nhìn", checked = showViewLine, onCheckedChange = { onUpdateShowViewLine(it) })
                SwitchItem(title = "Đường hướng di chuyển", checked = showMoveLine, onCheckedChange = { onUpdateShowMoveLine(it) })
                SwitchItem(title = "Vị trí hướng di chuyển (Radar)", checked = showMoveDirection, onCheckedChange = { onUpdateShowMoveDirection(it) })
                SwitchItem(title = "La bàn chỉ hướng", checked = showCompass, onCheckedChange = { onUpdateShowCompass(it) })
                SwitchItem(title = "Thông số vệ tinh", checked = showSatelliteInfo, onCheckedChange = { onUpdateShowSatelliteInfo(it) })
                SwitchItem(title = "Công cụ thu phóng (+/-)", checked = showZoomControls, onCheckedChange = { onUpdateShowZoomControls(it) })
                SwitchItem(title = "Công cụ xoay bản đồ", checked = showRotationControls, onCheckedChange = { onUpdateShowRotationControls(it) })
                SwitchItem(title = "Hiển thị mức Zoom", checked = showZoomLevel, onCheckedChange = { onUpdateShowZoomLevel(it) })
                SwitchItem(title = "Tâm bản đồ (+)", checked = showMapCenter, onCheckedChange = { onUpdateShowMapCenter(it) })
            }

            // --- THÔNG SỐ KỸ THUẬT & CẢM BIẾN ---
            SettingGroupCard(title = "THÔNG SỐ KỸ THUẬT & CẢM BIẾN", icon = Icons.Default.Build) {
                DropdownItem(title = "Đơn vị đo độ dài", currentValue = distanceUnit, onSelect = { onUpdateDistanceUnit(it) })
                DropdownItem(title = "Đơn vị đo diện tích", currentValue = areaUnit, onSelect = { onUpdateAreaUnit(it) })
                ValueItem(title = "Khoảng cách lọc GPS (m)", value = "$gpsFilterDistance", onValueClick = { showNumberDialog(context, "Lọc GPS", gpsFilterDistance.toInt(), 0, 10) { onUpdateGpsFilterDistance(it.toFloat()) } })
                ValueItem(title = "Khoảng thời gian ghi (giây)", value = "$trackingIntervalSeconds", onValueClick = { showNumberDialog(context, "Khoảng thời gian", trackingIntervalSeconds, 1, 60) { onUpdateTrackingIntervalSeconds(it) } })
                ValueItem(title = "Chiều cao Antenna (m)", value = "$antennaHeight", onValueClick = { showNumberDialog(context, "Chiều cao", antennaHeight.toInt(), 0, 50) { onUpdateAntennaHeight(it.toFloat()) } })
                SwitchItem(title = "Sử dụng A-GPS", checked = useAGps, onCheckedChange = { onUpdateUseAGps(it) })
                SwitchItem(title = "Lắc máy để di chuyển bản đồ", checked = shakeToMoveMap, onCheckedChange = { onUpdateShakeToMoveMap(it) })
                SwitchItem(title = "Màn hình luôn sáng", checked = keepScreenOn, onCheckedChange = { onUpdateKeepScreenOn(it) })
                SwitchItem(title = "Sửa lỗi nạp MBTiles", checked = fixMbTilesDisplay, onCheckedChange = { onUpdateFixMbTilesDisplay(it) })
            }

            // --- BÁO CÁO & THÔNG TIN ---
            SettingGroupCard(title = "ĐỊA CHỈ NHẬN BÁO CÁO", icon = Icons.Default.Email) {
                var emailInput by remember { mutableStateOf(prefs.defaultRecipientEmail) }
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it; prefs.defaultRecipientEmail = it },
                    label = { Text("Email nhận báo cáo thực địa") },
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Đang chờ gửi: $emailQueueSize", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Button(onClick = { onSendPendingEmails(context) }) {
                        Icon(Icons.Default.Send, null); Spacer(Modifier.width(4.dp)); Text("GỬI NGAY")
                    }
                }
            }

            // Software Info
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("PHẦN MỀM CHUYÊN NGÀNH LÂM NGHIỆP", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    Text("Hệ thống Quản lý tuần tra Bảo vệ rừng - Đại Thành", fontSize = 12.sp)
                    Text("Phiên bản v1.0 (Cập nhật 05/08/2026)", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Tác giả: Nguyễn Bá Hưng - 0983.407.464", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingGroupCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(text = title, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SwitchItem(title: String, description: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
        supportingContent = if (description != null) { { Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } else null,
        trailingContent = { 
            Switch(
                checked = checked, 
                onCheckedChange = onCheckedChange, 
                modifier = Modifier.scale(0.8f)
            ) 
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}

@Composable
fun ValueItem(title: String, description: String? = null, value: String, onValueClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
        supportingContent = if (description != null) { { Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } else null,
        trailingContent = { 
            Surface(
                shape = RoundedCornerShape(8.dp), 
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)), 
                modifier = Modifier.widthIn(min = 64.dp).clickable { onValueClick() },
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            ) {
                Text(value, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            }
        }
    )
}

@Composable
fun DropdownItem(title: String, currentValue: String, onSelect: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    ValueItem(title, value = currentValue, onValueClick = { showDialog = true })
    
    if (showDialog) {
        val options = when {
            title.contains("mã hóa font") -> listOf("TCVN3", "VNI", "Unicode")
            title.contains("Đơn vị đo độ dài") -> listOf("Auto", "m", "km")
            title.contains("Đơn vị đo diện tích") -> listOf("Auto", "m2", "ha")
            title.contains("biểu tượng (Hình ảnh)") -> listOf("camera", "picture", "gallery", "photo", "lens")
            title.contains("biểu tượng (Sự vụ)") -> listOf("a4", "note", "ledger", "report", "contract")
            title.contains("biểu tượng (Động thực vật)") -> listOf("forest", "tree", "eco", "nature", "grass")
            title.contains("biểu tượng (Tác động TN)") -> listOf("warning", "alert", "fire", "storm", "flood")
            title.contains("biểu tượng") -> listOf("tree", "star", "pin", "circle", "flag")
            title.contains("vẽ") || title.contains("ranh giới") -> listOf("solid", "dashed", "dotted", "dash_dot", "long_dash")
            title.contains("Lĩnh vực mặc định") -> listOf("Lâm nghiệp", "Đất đai")
            else -> emptyList()
        }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { opt ->
                        ListItem(
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (title.contains("biểu tượng")) {
                                        IconPreview(opt, modifier = Modifier.size(24.dp))
                                        Spacer(Modifier.width(12.dp))
                                    } else if (title.contains("vẽ") || title.contains("ranh giới")) {
                                        LineStylePreview(opt, modifier = Modifier.width(40.dp).height(20.dp))
                                        Spacer(Modifier.width(12.dp))
                                    }
                                    Text(opt)
                                }
                            },
                            modifier = Modifier.clickable { onSelect(opt); showDialog = false },
                            trailingContent = { if (opt == currentValue) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("ĐÓNG") } }
        )
    }
}

@Composable
fun IconPreview(type: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val iconSize = size.minDimension
        val p = center
        val color = Color(0xFF1976D2)
        when (type) {
            "camera" -> {
                val camRect = androidx.compose.ui.geometry.Rect(p.x - iconSize * 0.4f, p.y - iconSize * 0.25f, p.x + iconSize * 0.4f, p.y + iconSize * 0.25f)
                drawRoundRect(color, camRect.topLeft, camRect.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
                drawCircle(Color.White, radius = iconSize * 0.15f, center = p)
            }
            "picture", "gallery", "photo" -> {
                val rect = androidx.compose.ui.geometry.Rect(p.x - iconSize * 0.35f, p.y - iconSize * 0.35f, p.x + iconSize * 0.35f, p.y + iconSize * 0.35f)
                drawRect(color, rect.topLeft, rect.size, style = Stroke(width = 2f))
                drawCircle(color, radius = iconSize * 0.1f, center = Offset(p.x - iconSize * 0.15f, p.y - iconSize * 0.15f))
                val triPath = Path()
                triPath.moveTo(rect.left + 4f, rect.bottom - 4f)
                triPath.lineTo(p.x, p.y)
                triPath.lineTo(rect.right - 4f, rect.bottom - 4f)
                triPath.close()
                drawPath(triPath, color)
            }
            "lens" -> {
                drawCircle(color, radius = iconSize * 0.4f, center = p, style = Stroke(width = 4f))
                drawCircle(color, radius = iconSize * 0.2f, center = p)
            }
            "a4", "report", "contract" -> {
                val rect = androidx.compose.ui.geometry.Rect(p.x - iconSize * 0.3f, p.y - iconSize * 0.4f, p.x + iconSize * 0.3f, p.y + iconSize * 0.4f)
                drawRect(color, rect.topLeft, rect.size, style = Stroke(width = 2f))
                for (i in 0..3) {
                    val lineY = rect.top + iconSize * 0.2f + i * iconSize * 0.15f
                    drawLine(color, Offset(rect.left + 6f, lineY), Offset(rect.right - 6f, lineY), strokeWidth = 1.5f)
                }
            }
            "note", "ledger" -> {
                val rect = androidx.compose.ui.geometry.Rect(p.x - iconSize * 0.35f, p.y - iconSize * 0.35f, p.x + iconSize * 0.35f, p.y + iconSize * 0.35f)
                drawRoundRect(color, rect.topLeft, rect.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
                drawLine(Color.White, Offset(rect.left + 6f, p.y), Offset(rect.right - 6f, p.y), strokeWidth = 2f)
                drawLine(Color.White, Offset(p.x, rect.top + 6f), Offset(p.x, rect.bottom - 6f), strokeWidth = 2f)
            }
            "forest", "eco", "nature", "grass" -> {
                val path = Path()
                path.moveTo(p.x, p.y - iconSize * 0.4f)
                path.cubicTo(p.x + iconSize * 0.4f, p.y - iconSize * 0.2f, p.x + iconSize * 0.4f, p.y + iconSize * 0.2f, p.x, p.y + iconSize * 0.4f)
                path.cubicTo(p.x - iconSize * 0.4f, p.y + iconSize * 0.2f, p.x - iconSize * 0.4f, p.y - iconSize * 0.2f, p.x, p.y - iconSize * 0.4f)
                drawPath(path, color)
                drawLine(Color.White, Offset(p.x - 2f, p.y + 10f), Offset(p.x + 2f, p.y - 10f), strokeWidth = 2f)
            }
            "warning", "alert" -> {
                val triPath = Path()
                triPath.moveTo(p.x, p.y - iconSize * 0.45f)
                triPath.lineTo(p.x - iconSize * 0.4f, p.y + iconSize * 0.35f)
                triPath.lineTo(p.x + iconSize * 0.4f, p.y + iconSize * 0.35f)
                triPath.close()
                drawPath(triPath, color)
                drawCircle(Color.White, radius = 2f, center = Offset(p.x, p.y + iconSize * 0.2f))
                drawLine(Color.White, Offset(p.x, p.y - iconSize * 0.2f), Offset(p.x, p.y + iconSize * 0.05f), strokeWidth = 3f)
            }
            "fire" -> {
                val fPath = Path()
                fPath.moveTo(p.x, p.y + iconSize * 0.4f)
                fPath.quadraticTo(p.x - iconSize * 0.4f, p.y, p.x, p.y - iconSize * 0.5f)
                fPath.quadraticTo(p.x + iconSize * 0.4f, p.y, p.x, p.y + iconSize * 0.4f)
                drawPath(fPath, color)
            }
            "storm", "flood" -> {
                drawCircle(color, radius = iconSize * 0.35f, center = p, style = Stroke(width = 3f))
                drawLine(color, Offset(p.x - 10f, p.y + 10f), Offset(p.x + 10f, p.y - 10f), strokeWidth = 3f)
            }
            "tree" -> {
                drawLine(Color(0xFF5D4037), Offset(p.x, p.y), Offset(p.x, p.y + iconSize * 0.3f), strokeWidth = 2f)
                val treePath = Path()
                treePath.moveTo(p.x, p.y - iconSize * 0.4f)
                treePath.lineTo(p.x - iconSize * 0.3f, p.y + iconSize * 0.1f)
                treePath.lineTo(p.x + iconSize * 0.3f, p.y + iconSize * 0.1f)
                treePath.close()
                drawPath(treePath, color)
            }
            "star" -> {
                val starPath = Path()
                val innerRadius = iconSize * 0.2f
                val outerRadius = iconSize * 0.45f
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) outerRadius else innerRadius
                    val angle = Math.toRadians((i * 36 - 90).toDouble())
                    val x = p.x + r * cos(angle).toFloat()
                    val y = p.y + r * sin(angle).toFloat()
                    if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
                }
                starPath.close()
                drawPath(starPath, color)
            }
            "pin" -> {
                val pinPath = Path()
                pinPath.moveTo(p.x, p.y + iconSize * 0.4f)
                pinPath.cubicTo(p.x - iconSize * 0.5f, p.y - iconSize * 0.4f, p.x + iconSize * 0.5f, p.y - iconSize * 0.4f, p.x, p.y + iconSize * 0.4f)
                drawPath(pinPath, color)
                drawCircle(Color.White, radius = iconSize * 0.15f, center = Offset(p.x, p.y - iconSize * 0.1f))
            }
            "circle" -> {
                drawCircle(color, radius = iconSize * 0.4f, center = p)
                drawCircle(Color.White, radius = iconSize * 0.2f, center = p)
            }
            "flag" -> {
                drawLine(Color.Gray, Offset(p.x - iconSize * 0.2f, p.y + iconSize * 0.4f), Offset(p.x - iconSize * 0.2f, p.y - iconSize * 0.4f), strokeWidth = 2f)
                val flagPath = Path()
                flagPath.moveTo(p.x - iconSize * 0.2f, p.y - iconSize * 0.4f)
                flagPath.lineTo(p.x + iconSize * 0.3f, p.y - iconSize * 0.25f)
                flagPath.lineTo(p.x - iconSize * 0.2f, p.y - iconSize * 0.1f)
                flagPath.close()
                drawPath(flagPath, color)
            }
            else -> {
                drawCircle(color, radius = iconSize * 0.3f, center = p)
            }
        }
    }
}

@Composable
fun LineStylePreview(style: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val path = Path()
        path.moveTo(0f, size.height / 2)
        path.lineTo(size.width, size.height / 2)
        
        val pathEffect = when (style) {
            "dashed" -> PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            "dotted" -> PathEffect.dashPathEffect(floatArrayOf(2f, 5f), 0f)
            "dash_dot" -> PathEffect.dashPathEffect(floatArrayOf(10f, 5f, 2f, 5f), 0f)
            "long_dash" -> PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
            else -> null
        }
        
        drawPath(
            path = path,
            color = Color.Black,
            style = Stroke(width = 4f, pathEffect = pathEffect)
        )
    }
}

@Composable
fun ColorItem(title: String, colorHex: String, onColorSelected: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val color = try { Color(android.graphics.Color.parseColor(colorHex)) } catch (e: Exception) { Color.Gray }
    
    ListItem(
        headlineContent = { Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
        trailingContent = { 
            Surface(
                modifier = Modifier.size(36.dp).clickable { showDialog = true },
                shape = RoundedCornerShape(8.dp),
                color = color,
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                shadowElevation = 2.dp
            ) {}
        }
    )

    if (showDialog) {
        val colors = listOf("#FF1976D2", "#FFD32F2F", "#FF388E3C", "#FFFBC02D", "#FF7B1FA2", "#FFFF3D00", "#FF2E7D32", "#FF9C27B0", "#FF000000", "#FF607D8B")
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Chọn màu sắc", fontWeight = FontWeight.Bold) },
            text = {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    colors.forEach { c ->
                        Surface(
                            modifier = Modifier.size(44.dp).clickable { onColorSelected(c); showDialog = false },
                            shape = CircleShape,
                            color = Color(android.graphics.Color.parseColor(c)),
                            border = if (c == colorHex) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
                        ) {}
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("ĐÓNG") } }
        )
    }
}

fun showNumberDialog(context: android.content.Context, label: String, current: Int, min: Int, max: Int, onConfirm: (Int) -> Unit) {
    val input = android.widget.EditText(context)
    input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
    input.setText(current.toString())
    input.setPadding(48, 32, 48, 32)
    
    android.app.AlertDialog.Builder(context)
        .setTitle("Chỉnh sửa $label")
        .setMessage("Nhập giá trị từ $min đến $max")
        .setView(input)
        .setPositiveButton("XÁC NHẬN") { dialog, _ ->
            val v = input.text.toString().toIntOrNull() ?: current
            onConfirm(v.coerceIn(min, max))
            dialog.dismiss()
        }
        .setNegativeButton("HỦY", null)
        .show()
}
