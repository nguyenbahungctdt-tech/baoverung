package com.baoverung.app.platform

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual class FilePicker {
    private var launcher: androidx.activity.result.ActivityResultLauncher<String>? = null

    @Composable
    actual fun registerPicker(onFileSelected: (String?) -> Unit) {
        launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            onFileSelected(uri?.toString())
        }
    }

    actual fun launchPicker(fileType: String) {
        launcher?.launch(fileType)
    }
}
