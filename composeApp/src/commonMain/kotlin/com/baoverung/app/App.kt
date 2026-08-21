package com.baoverung.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.baoverung.app.data.model.UserSession
import com.baoverung.app.ui.auth.LoginScreen
import com.baoverung.app.ui.map.MapScreen
import com.baoverung.app.ui.map.MeasurementMode
import com.baoverung.app.ui.navigation.Screen
import com.baoverung.app.ui.theme.MyApplicationTheme
import com.baoverung.app.platform.PlatformSettings

import com.baoverung.app.ui.patrol.DailyJournalFormScreen
import com.baoverung.app.ui.patrol.FloraFaunaFormScreen
import com.baoverung.app.ui.patrol.NaturalImpactFormScreen
import com.baoverung.app.ui.patrol.PatrolLogFormScreen

@Composable
fun App(platformSettings: PlatformSettings) {
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
                MapScreen(
                    centerLat = 11.9404,
                    centerLon = 108.4378,
                    zoomLevel = 15f,
                    onMapChange = { _, _, _ -> },
                    currentLocation = null,
                    compassAzimuth = 0f,
                    measurementMode = MeasurementMode.NONE,
                    measurementPoints = emptyList(),
                    targetNavPoint = null,
                    isTrackingGpx = false,
                    trackedPoints = emptyList(),
                    selectedMapSource = "Google Satellite",
                    onSelectMapSource = {},
                    onSetMeasurementMode = {},
                    onAddMeasurementPoint = {},
                    onClearMeasurement = {},
                    onToggleGpxTracking = {},
                    onOpenAddWaypoint = {},
                    onOpenPatrolForm = { navController.navigate(Screen.PatrolForm.route) }
                )
            }

            composable(Screen.PatrolForm.route) {
                PatrolLogFormScreen(
                    currentLocation = null,
                    userEmail = userSession.email,
                    userName = userSession.displayName,
                    centralMeridian = 107.75,
                    zoneDegrees = 3,
                    onSubmitPatrolLog = { _, _, _, _, _, _, _, _, _ -> 
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.FloraFaunaForm.route) {
                FloraFaunaFormScreen(
                    currentLocation = null,
                    userName = userSession.displayName,
                    onSubmit = { _, _, _, _, _, _, _, _, _, _, _ -> 
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.NaturalImpactForm.route) {
                NaturalImpactFormScreen(
                    currentLocation = null,
                    userName = userSession.displayName,
                    onSubmit = { _, _, _, _, _, _, _, _, _ -> 
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.DailyJournal.route) {
                DailyJournalFormScreen(
                    userName = userSession.displayName,
                    onSave = { _, _, _, _, _, _ -> 
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
