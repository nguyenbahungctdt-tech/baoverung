package com.baoverung.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import com.baoverung.app.App
import com.baoverung.app.data.local.DatabaseBuilder
import com.baoverung.app.data.local.getAppDatabase
import com.baoverung.app.platform.PlatformSettings
import com.baoverung.app.repository.SurveyRepository
import com.baoverung.app.repository.CloudSyncRepository
import com.baoverung.app.ui.MainViewModel
import kotlinx.coroutines.MainScope

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "Bạn cần cấp đủ quyền để sử dụng ứng dụng!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("BVR", "MainActivity onCreate started")
        enableEdgeToEdge()
        
        android.util.Log.d("BVR", "Checking permissions")
        checkAndRequestPermissions()

        setContent {
            android.util.Log.d("BVR", "Setting content")
            val platformSettings = remember { PlatformSettings(applicationContext) }
            val db = remember { getAppDatabase(DatabaseBuilder(applicationContext).createBuilder()) }
            val repository = remember { SurveyRepository(db) }
            val cloudSyncRepository = remember { CloudSyncRepository() }
            val viewModel = remember { MainViewModel(repository, cloudSyncRepository, MainScope()) }
            
            App(viewModel, platformSettings)
            
            // Check for Manage External Storage after UI loads
            LaunchedEffect(Unit) {
                android.util.Log.d("BVR", "LaunchedEffect for storage")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (!Environment.isExternalStorageManager()) {
                        android.util.Log.d("BVR", "Requesting external storage management")
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.addCategory("android.intent.category.DEFAULT")
                            intent.data = Uri.parse(String.format("package:%s", packageName))
                            startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent()
                            intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                            startActivity(intent)
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
}
