package com.baoverung.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoverung.app.R
import com.baoverung.app.data.model.UserSession

@Composable
fun LoginScreen(
    currentSession: UserSession,
    onLogin: (email: String, name: String, phone: String, unit: String, department: String, registrationKey: String, expiry: String, perms: String, autoGpx: Boolean, canSync: Boolean) -> Unit,
    onForceSync: () -> Unit,
    onResetSync: () -> Unit,
    onContinueOffline: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val androidId = remember { 
        android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }
    
    val units = listOf("Công ty TNHH MTV ĐTPT ĐẠI THÀNH", "Khác")
    val companyDepartments = listOf(
        "Ban Giám Đốc",
        "Phòng QL,SD&PTR",
        "Phân Trường I",
        "Phân Trường II",
        "Phân Trường III",
        "Phân Trường IV",
        "Phân Trường V",
        "Phân Trường VI",
        "Khác"
    )

    val cloudRepo = remember { com.baoverung.app.repository.CloudSyncRepository() }

    // Suggestions (History-based hints)
    val prefs = remember { context.getSharedPreferences("vtool_prefs", android.content.Context.MODE_PRIVATE) }
    val suggestName = remember { prefs.getString("last_name", "") ?: "" }
    val suggestEmail = remember { prefs.getString("last_email", "") ?: "" }
    val suggestPhone = remember { prefs.getString("last_phone", "") ?: "" }
    val suggestUnit = remember { prefs.getString("last_unit", "") ?: "" }
    val suggestDept = remember { prefs.getString("last_dept", "") ?: "" }
    val suggestKey = remember { prefs.getString("last_key", "") ?: "" }

    val scrollState = rememberScrollState()
    var emailInput by remember { mutableStateOf(suggestEmail) }
    var nameInput by remember { mutableStateOf(suggestName) }
    var phoneInput by remember { mutableStateOf(suggestPhone) }
    
    val initialUnit = if (suggestUnit.isEmpty()) "" else if (units.contains(suggestUnit)) suggestUnit else "Khác"
    val initialOtherUnit = if (suggestUnit.isNotEmpty() && !units.contains(suggestUnit)) suggestUnit else ""
    
    var unitInput by remember { mutableStateOf(initialUnit) }
    var otherUnitInput by remember { mutableStateOf(initialOtherUnit) }
    var departmentInput by remember { mutableStateOf(suggestDept) }
    var keyInput by remember { mutableStateOf(suggestKey) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCheckingKey by remember { mutableStateOf(false) }
    var showUnitDropdown by remember { mutableStateOf(false) }
    var showDepartmentDropdown by remember { mutableStateOf(false) }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .testTag("login_screen_box"),
        contentAlignment = Alignment.TopCenter
    ) {
        // Decorative Forest Background (Simulated with a gradient)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(top = 40.dp, bottom = 24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // App Logo Icon with modern border
            Surface(
                modifier = Modifier.size(110.dp),
                shape = CircleShape,
                color = Color.White,
                border = BorderStroke(4.dp, MaterialTheme.colorScheme.surface),
                shadowElevation = 12.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.app_icon_forestry),
                        contentDescription = "Bảo vệ rừng",
                        modifier = Modifier
                            .size(85.dp)
                            .clip(CircleShape)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "BẢO VỆ RỪNG",
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Công ty TNHH MTV ĐTPT Đại Thành",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (currentSession.isLoggedIn) {
                // Logged In Card - Professional Look
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "PHIÊN ĐĂNG NHẬP HOẠT ĐỘNG",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Text(currentSession.displayName, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)

                        Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Text("Họ tên: ${currentSession.displayName}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Gmail: ${currentSession.email}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Số điện thoại: ${currentSession.phoneNumber}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Đơn vị: ${currentSession.unit}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Bộ phận: ${currentSession.department}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Quyền hạn: ${currentSession.permissions}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Hạn sử dụng:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(currentSession.expiryDate, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Button(
                            onClick = onForceSync,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.CloudSync, null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("ĐỒNG BỘ DỮ LIỆU", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onResetSync,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Icon(Icons.Default.History, null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("LÀM MỚI LỊCH SỬ")
                        }

                        TextButton(
                            onClick = onLogout,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Đăng xuất tài khoản", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                // Registration Form - Forestry Theme
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "ĐĂNG KÝ THÔNG TIN CÁN BỘ",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )

                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Họ tên cán bộ thực hiện") },
                            leadingIcon = { Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            supportingText = { if (suggestName.isNotEmpty()) Text("Gợi ý: $suggestName", modifier = Modifier.clickable { nameInput = suggestName }) }
                        )

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("Địa chỉ Gmail") },
                            leadingIcon = { Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            supportingText = { if (suggestEmail.isNotEmpty()) Text("Gợi ý: $suggestEmail", modifier = Modifier.clickable { emailInput = suggestEmail }) }
                        )

                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { input -> if (input.all { it.isDigit() } && input.length <= 10) phoneInput = input },
                            label = { Text("Số điện thoại liên lạc") },
                            leadingIcon = { Icon(Icons.Default.Phone, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            supportingText = { if (suggestPhone.isNotEmpty()) Text("Gợi ý: $suggestPhone", modifier = Modifier.clickable { phoneInput = suggestPhone }) }
                        )

                        @OptIn(ExperimentalMaterial3Api::class)
                        ExposedDropdownMenuBox(
                            expanded = showUnitDropdown,
                            onExpandedChange = { showUnitDropdown = !showUnitDropdown }
                        ) {
                            OutlinedTextField(
                                value = unitInput,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Đơn vị công tác") },
                                leadingIcon = { Icon(Icons.Default.Business, null, tint = MaterialTheme.colorScheme.primary) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showUnitDropdown) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                shape = RoundedCornerShape(12.dp),
                                supportingText = { if (suggestUnit.isNotEmpty()) Text("Gợi ý: $suggestUnit", modifier = Modifier.clickable { unitInput = suggestUnit }) }
                            )
                            ExposedDropdownMenu(
                                expanded = showUnitDropdown,
                                onDismissRequest = { showUnitDropdown = false }
                            ) {
                                units.forEach { unit ->
                                    DropdownMenuItem(text = { Text(unit) }, onClick = { 
                                        unitInput = unit
                                        if (unit == "Khác") {
                                            departmentInput = "" 
                                        }
                                        showUnitDropdown = false 
                                    })
                                }
                            }
                        }

                        if (unitInput == "Khác") {
                            OutlinedTextField(
                                value = otherUnitInput,
                                onValueChange = { otherUnitInput = it },
                                label = { Text("Nhập tên đơn vị công tác") },
                                leadingIcon = { Icon(Icons.Default.Business, null, tint = MaterialTheme.colorScheme.primary) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            
                            OutlinedTextField(
                                value = departmentInput,
                                onValueChange = { departmentInput = it },
                                label = { Text("Nhập bộ phận / Phân trường") },
                                leadingIcon = { Icon(Icons.Default.Forest, null, tint = MaterialTheme.colorScheme.primary) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                        }

                        if (unitInput == "Công ty TNHH MTV ĐTPT ĐẠI THÀNH") {
                            @OptIn(ExperimentalMaterial3Api::class)
                            ExposedDropdownMenuBox(
                                expanded = showDepartmentDropdown,
                                onExpandedChange = { showDepartmentDropdown = !showDepartmentDropdown }
                            ) {
                                OutlinedTextField(
                                    value = departmentInput,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Bộ phận / Phân trường") },
                                    leadingIcon = { Icon(Icons.Default.Forest, null, tint = MaterialTheme.colorScheme.primary) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDepartmentDropdown) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                                    shape = RoundedCornerShape(12.dp),
                                    supportingText = { if (suggestDept.isNotEmpty()) Text("Gợi ý: $suggestDept", modifier = Modifier.clickable { departmentInput = suggestDept }) }
                                )
                                ExposedDropdownMenu(
                                    expanded = showDepartmentDropdown,
                                    onDismissRequest = { showDepartmentDropdown = false }
                                ) {
                                    companyDepartments.forEach { dept ->
                                        DropdownMenuItem(text = { Text(dept) }, onClick = { departmentInput = dept; showDepartmentDropdown = false })
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            label = { Text("Mã kích hoạt ứng dụng") },
                            leadingIcon = { Icon(Icons.Default.VpnKey, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            supportingText = { if (suggestKey.isNotEmpty()) Text("Gợi ý: $suggestKey", modifier = Modifier.clickable { keyInput = suggestKey }) }
                        )

                        if (errorMessage != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = errorMessage!!,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(12.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        val scope = rememberCoroutineScope()
                        Button(
                            onClick = {
                                val finalUnit = if (unitInput == "Khác") otherUnitInput else unitInput
                                if (nameInput.isBlank() || emailInput.isBlank() || phoneInput.isBlank() || finalUnit.isBlank() || departmentInput.isBlank() || keyInput.isBlank()) {
                                    errorMessage = "Vui lòng nhập đầy đủ thông tin!"
                                } else {
                                    isCheckingKey = true
                                    errorMessage = null
                                    scope.launch {
                                        val userInfo = mapOf(
                                            "name" to nameInput,
                                            "email" to emailInput,
                                            "phone" to phoneInput,
                                            "unit" to finalUnit,
                                            "dept" to departmentInput
                                        )
                                        val validation = cloudRepo.verifyActivationKey(keyInput, androidId, isLogin = true, userInfo = userInfo)
                                        isCheckingKey = false
                                        if (validation.isValid) {
                                            onLogin(emailInput, nameInput, phoneInput, finalUnit, departmentInput, keyInput, validation.message ?: "N/A", validation.permissions, validation.autoGpx, validation.canSync)
                                        } else {
                                            errorMessage = validation.message
                                        }
                                    }
                                }
                            },
                            enabled = !isCheckingKey,
                            modifier = Modifier.fillMaxWidth().height(56.dp).testTag("login_submit_btn"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (isCheckingKey) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                            } else {
                                Icon(Icons.Default.CloudUpload, null)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("KÍCH HOẠT VÀ ĐĂNG KÝ", fontWeight = FontWeight.Black)
                            }
                        }

                        TextButton(
                            onClick = onContinueOffline,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.WifiOff, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Dùng thử ngoại tuyến", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Hệ thống quản lý dữ liệu Bảo vệ rừng - Đại Thành",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Tác giả: Nguyễn Bá Hưng - 0983.407.464",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
