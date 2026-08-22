package com.baoverung.app

import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.baoverung.app.data.model.UserSession
import com.baoverung.app.ui.auth.LoginScreen
import com.baoverung.app.ui.map.MapScreen
import com.baoverung.app.ui.map.MeasurementMode
import com.baoverung.app.ui.navigation.Screen
import com.baoverung.app.ui.patrol.*
import com.baoverung.app.ui.gis_layers.GisLayersScreen
import com.baoverung.app.ui.converter.CoordinateConverterScreen
import com.baoverung.app.ui.waypoints.WaypointsAndTracksScreen
import com.baoverung.app.ui.settings.SettingsScreen
import com.baoverung.app.ui.theme.MyApplicationTheme
import com.baoverung.app.platform.PlatformSettings
import com.baoverung.app.ui.MainViewModel

@Composable
fun App(viewModel: MainViewModel, platformSettings: PlatformSettings) {
    MyApplicationTheme {
        val navController = rememberNavController()
        
        // Initial state from platform settings
        val initialSession = remember {
            val isLoggedIn = platformSettings.getString("last_email", "").isNotEmpty()
            UserSession(
                displayName = platformSettings.getString("last_name", ""),
                email = platformSettings.getString("last_email", ""),
                phoneNumber = platformSettings.getString("last_phone", ""),
                unit = platformSettings.getString("last_unit", ""),
                department = platformSettings.getString("last_dept", ""),
                registrationKey = platformSettings.getString("last_key", ""),
                isLoggedIn = isLoggedIn
            )
        }
        
        var userSession by remember { mutableStateOf(initialSession) }
        
        // Auto-navigation logic
        LaunchedEffect(userSession.isLoggedIn) {
            if (userSession.isLoggedIn) {
                navController.navigate(Screen.Map.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            }
        }
        
        NavHost(
            navController = navController,
            startDestination = if (userSession.isLoggedIn) Screen.Map.route else Screen.Login.route
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    currentSession = userSession,
                    platformSettings = platformSettings,
                    cloudSyncRepository = viewModel.cloudSync,
                    onLogin = { email, name, phone, unit, dept, key, expiry, perms, autoGpx, canSync ->
                        userSession = UserSession(
                            displayName = name, email = email, phoneNumber = phone, 
                            unit = unit, department = dept, registrationKey = key,
                            expiryDate = expiry, permissions = perms, autoGpx = autoGpx,
                            canSync = canSync, isLoggedIn = true
                        )
                        // Save last login info
                        platformSettings.putString("last_name", name)
                        platformSettings.putString("last_email", email)
                        platformSettings.putString("last_phone", phone)
                        platformSettings.putString("last_unit", unit)
                        platformSettings.putString("last_dept", dept)
                        platformSettings.putString("last_key", key)
                        
                        navController.navigate(Screen.Map.route)
                    },
                    onForceSync = {},
                    onResetSync = {},
                    onContinueOffline = {
                        userSession = UserSession(displayName = "Khách", isLoggedIn = true, isOfflineMode = true)
                        navController.navigate(Screen.Map.route)
                    },
                    onLogout = { userSession = UserSession(isLoggedIn = false) }
                )
            }

            composable(Screen.Map.route) {
                val loc by viewModel.currentLocation.collectAsState()
                val isTracking by viewModel.isTrackingGpx.collectAsState()
                val gisLayers by viewModel.gisLayers.collectAsState()
                
                MapScreen(
                    centerLat = 11.9404,
                    centerLon = 108.4378,
                    zoomLevel = 15f,
                    onMapChange = { _, _, _ -> },
                    currentLocation = loc,
                    compassAzimuth = 0f,
                    satellitesVisible = 0,
                    measurementMode = MeasurementMode.NONE,
                    measurementPoints = emptyList(),
                    targetNavPoint = null,
                    isTrackingGpx = isTracking,
                    trackedPoints = emptyList(),
                    gisLayers = gisLayers,
                    selectedMapSource = "Google Satellite",
                    onSelectMapSource = {},
                    onSetMeasurementMode = {},
                    onAddMeasurementPoint = {},
                    onClearMeasurement = {},
                    onToggleGpxTracking = { viewModel.toggleGpxTracking() },
                    onOpenAddWaypoint = {},
                    onOpenPatrolForm = { navController.navigate(Screen.PatrolForm.route) },
                    onOpenGisLayers = { navController.navigate(Screen.GisLayers.route) },
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    onOpenDataManagement = { navController.navigate(Screen.WaypointsData.route) }
                )
            }

            composable(Screen.PatrolForm.route) {
                val loc by viewModel.currentLocation.collectAsState()
                PatrolLogFormScreen(
                    currentLocation = loc,
                    userEmail = userSession.email,
                    userName = userSession.displayName,
                    centralMeridian = 107.75,
                    zoneDegrees = 3,
                    onSubmitPatrolLog = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> 
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.GisLayers.route) {
                val layers by viewModel.gisLayers.collectAsState()
                GisLayersScreen(
                    layers = layers,
                    onToggleVisibility = { /* TODO */ },
                    onDeleteLayer = { /* TODO */ },
                    onImportFile = { path ->
                        // Logic nhập file
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            
            composable(Screen.CoordinateConverter.route) {
                CoordinateConverterScreen(
                    centralMeridian = 107.75,
                    zoneDegrees = 3,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.WaypointsData.route) {
                val wps by viewModel.waypoints.collectAsState()
                val trks by viewModel.trackLogs.collectAsState()
                val patrols by viewModel.patrolLogs.collectAsState()
                val flora by viewModel.floraFaunaLogs.collectAsState()
                val impact by viewModel.naturalImpactLogs.collectAsState()
                val poly by viewModel.polygons.collectAsState()
                val journals by viewModel.dailyJournals.collectAsState()

                WaypointsAndTracksScreen(
                    waypoints = wps,
                    trackLogs = trks,
                    patrolLogs = patrols,
                    floraFaunaLogs = flora,
                    naturalImpactLogs = impact,
                    polygons = poly,
                    dailyJournals = journals,
                    onDeleteWaypoint = { viewModel.deleteWaypoint(it) },
                    onDeleteTrackLog = { viewModel.deleteTrackLog(it) },
                    onDeletePatrolLog = { viewModel.deletePatrolLog(it) },
                    onNavigateToPoint = { pt ->
                        // Navigation logic
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenConverter = { navController.navigate(Screen.CoordinateConverter.route) }
                )
            }
        }
    }
}
