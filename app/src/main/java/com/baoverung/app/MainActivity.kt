package com.baoverung.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.baoverung.app.App
import com.baoverung.app.platform.PlatformSettings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val platformSettings = remember { PlatformSettings(applicationContext) }
            App(platformSettings)
        }
    }
}
