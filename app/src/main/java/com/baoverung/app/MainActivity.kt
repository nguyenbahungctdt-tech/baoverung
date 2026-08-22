package com.baoverung.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.baoverung.app.App
import com.baoverung.app.data.local.DatabaseBuilder
import com.baoverung.app.data.local.getAppDatabase
import com.baoverung.app.platform.PlatformSettings
import com.baoverung.app.repository.SurveyRepository
import com.baoverung.app.repository.CloudSyncRepository
import com.baoverung.app.ui.MainViewModel
import kotlinx.coroutines.MainScope

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val platformSettings = remember { PlatformSettings(applicationContext) }
            val db = remember { getAppDatabase(DatabaseBuilder(applicationContext).createBuilder()) }
            val repository = remember { SurveyRepository(db) }
            val cloudSyncRepository = remember { CloudSyncRepository() }
            val viewModel = remember { MainViewModel(repository, cloudSyncRepository, MainScope()) }
            
            App(viewModel, platformSettings)
        }
    }
}
