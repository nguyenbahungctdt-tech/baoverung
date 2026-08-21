package com.baoverung.app.platform

import androidx.compose.runtime.Composable

/**
 * Giao diện chọn tệp đa nền tảng
 */
expect class FilePicker {
    @Composable
    fun registerPicker(onFileSelected: (String?) -> Unit)
    
    fun launchPicker(fileType: String) // image/*, .mbtiles, .kml, v.v.
}
