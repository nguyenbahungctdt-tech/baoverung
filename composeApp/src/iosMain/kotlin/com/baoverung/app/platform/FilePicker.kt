package com.baoverung.app.platform

import androidx.compose.runtime.Composable

actual class FilePicker actual constructor() {
    private var onSelected: ((String?) -> Unit)? = null

    @Composable
    actual fun registerPicker(onFileSelected: (String?) -> Unit) {
        this.onSelected = onFileSelected
    }

    actual fun launchPicker(fileType: String) {
        // iOS implementation using native picker
        println("File picker launched for type: $fileType")
    }
}
