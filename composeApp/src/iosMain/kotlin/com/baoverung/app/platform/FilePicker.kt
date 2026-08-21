package com.baoverung.app.platform

import androidx.compose.runtime.Composable

actual class FilePicker {
    private var onSelected: ((String?) -> Unit)? = null

    @Composable
    actual fun registerPicker(onFileSelected: (String?) -> Unit) {
        this.onSelected = onFileSelected
    }

    actual fun launchPicker(fileType: String) {
        // Triển khai thực tế trên iOS sẽ được viết bằng mã Native trong Xcode 
        // hoặc sử dụng thư viện kmp-file-picker để tránh lỗi link thư viện hệ thống
        println("File picker launched for type: $fileType")
    }
}
