package com.baoverung.app.platform

import androidx.compose.runtime.Composable

expect class CameraManager {
    @Composable
    fun CameraPreview(
        modifier: androidx.compose.ui.Modifier,
        onPhotoCaptured: (String) -> Unit
    )
    
    fun capturePhoto()
}
