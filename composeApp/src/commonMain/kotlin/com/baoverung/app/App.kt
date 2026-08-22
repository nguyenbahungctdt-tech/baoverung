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
        var userSession by remember { mutableStateOf(UserSession(isLoggedIn = false)) }
        
        NavHost(
            navController = navController,
            startDestination = Screen.Login.route
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    currentSession = userSession,
                    platformSettings = platformSettings,
                    onLogin = { email, name, phone, unit, dept, key, expiry, perms, autoGpx, canSync ->
                        userSession = UserSession(
                            displayName = name, email = email, phoneNumber = phone, 
                            unit = unit, department = dept, registrationKey = key,
                            expiryDate = expiry, permissions = perms, autoGpx = autoGpx,
                            canSync = canSync, isLoggedIn = true
                        )
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
                
                MapScreen(
                    centerLat = 11.9404,
                    centerLon = 108.4378,
                    zoomLevel = 15f,
                    onMapChange = { _, _, _ -> },
                    currentLocation = loc,
                    compassAzimuth = 0f,
                    measurementMode = MeasurementMode.NONE,
                    measurementPoints = emptyList(),
                    targetNavPoint = null,
                    isTrackingGpx = isTracking,
                    trackedPoints = emptyList(),
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
                    onDeleteWaypoint = { /* TODO */ },
                    onDeleteTrackLog = { /* TODO */ },
                    onDeletePatrolLog = { /* TODO */ },
                    onNavigateToPoint = { /* TODO */ },
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
