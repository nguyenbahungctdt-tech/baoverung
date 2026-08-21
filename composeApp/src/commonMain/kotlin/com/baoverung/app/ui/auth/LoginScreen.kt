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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoverung.app.data.model.UserSession
import com.baoverung.app.platform.PlatformSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    currentSession: UserSession,
    platformSettings: PlatformSettings,
    onLogin: (email: String, name: String, phone: String, unit: String, department: String, registrationKey: String, expiry: String, perms: String, autoGpx: Boolean, canSync: Boolean) -> Unit,
    onForceSync: () -> Unit,
    onResetSync: () -> Unit,
    onContinueOffline: () -> Unit,
    onLogout: () -> Unit
) {
    val deviceId = remember { platformSettings.getDeviceId() }
    
    val units = listOf("Công ty TNHH MTV ĐTPT ĐẠI THÀNH", "Khác")
    val companyDepartments = listOf(
        "Ban Giám Đốc", "Phòng QL,SD&PTR", "Phân Trường I", "Phân Trường II", 
        "Phân Trường III", "Phân Trường IV", "Phân Trường V", "Phân Trường VI", "Khác"
    )

    // Suggestions (History-based hints)
    val suggestName = remember { platformSettings.getString("last_name", "") }
    val suggestEmail = remember { platformSettings.getString("last_email", "") }
    val suggestPhone = remember { platformSettings.getString("last_phone", "") }
    val suggestUnit = remember { platformSettings.getString("last_unit", "") }
    val suggestDept = remember { platformSettings.getString("last_dept", "") }
    val suggestKey = remember { platformSettings.getString("last_key", "") }

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
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(scrollState),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 40.dp, bottom = 24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // App Logo Placeholder (Resources in KMP require more setup)
            Surface(modifier = Modifier.size(110.dp), shape = CircleShape, color = Color.White) {
                Icon(Icons.Default.Forest, null, modifier = Modifier.padding(20.dp), tint = MaterialTheme.colorScheme.primary)
            }

            Text("BẢO VỆ RỪNG", fontWeight = FontWeight.Black, fontSize = 26.sp, color = MaterialTheme.colorScheme.primary)

            if (currentSession.isLoggedIn) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(currentSession.displayName, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                        Text("Đơn vị: ${currentSession.unit}", fontSize = 14.sp)
                        Button(onClick = onForceSync, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.CloudSync, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ĐỒNG BỘ DỮ LIỆU")
                        }
                        TextButton(onClick = onLogout, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                            Text("Đăng xuất")
                        }
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("ĐĂNG KÝ CÁN BỘ", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)

                        OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, label = { Text("Họ tên") }, leadingIcon = { Icon(Icons.Default.Person, null) }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = emailInput, onValueChange = { emailInput = it }, label = { Text("Gmail") }, leadingIcon = { Icon(Icons.Default.Email, null) }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = phoneInput, onValueChange = { phoneInput = it }, label = { Text("Số điện thoại") }, leadingIcon = { Icon(Icons.Default.Phone, null) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        
                        OutlinedTextField(value = keyInput, onValueChange = { keyInput = it }, label = { Text("Mã kích hoạt") }, leadingIcon = { Icon(Icons.Default.VpnKey, null) }, modifier = Modifier.fillMaxWidth())

                        if (errorMessage != null) {
                            Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }

                        Button(
                            onClick = {
                                if (nameInput.isBlank() || emailInput.isBlank() || keyInput.isBlank()) {
                                    errorMessage = "Vui lòng nhập đủ thông tin!"
                                } else {
                                    // Giả lập login thành công (Logic Firebase sẽ được thêm sau)
                                    onLogin(emailInput, nameInput, phoneInput, "Đại Thành", "Phòng QL", keyInput, "2027-01-01", "FULL", true, true)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Text("KÍCH HOẠT", fontWeight = FontWeight.Black)
                        }

                        TextButton(onClick = onContinueOffline, modifier = Modifier.fillMaxWidth()) {
                            Text("Dùng thử ngoại tuyến")
                        }
                    }
                }
            }
        }
    }
}
