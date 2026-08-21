package com.baoverung.app

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.baoverung.app.platform.PlatformSettings

fun MainViewController() = ComposeUIViewController {
    val platformSettings = remember { PlatformSettings() }
    App(platformSettings)
}
