package com.baoverung.app

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.baoverung.app.data.local.AppDatabase
import com.baoverung.app.data.local.DatabaseBuilder
import com.baoverung.app.data.local.getAppDatabase
import com.baoverung.app.platform.PlatformSettings
import com.baoverung.app.repository.SurveyRepository
import com.baoverung.app.ui.MainViewModel
import kotlinx.coroutines.MainScope

fun MainViewController() = ComposeUIViewController {
    val platformSettings = remember { PlatformSettings() }
    val db = remember<AppDatabase> { getAppDatabase(DatabaseBuilder().createBuilder()) }
    val repository = remember { SurveyRepository(db) }
    val viewModel = remember { MainViewModel(repository, MainScope()) }
    
    App(viewModel, platformSettings)
}
