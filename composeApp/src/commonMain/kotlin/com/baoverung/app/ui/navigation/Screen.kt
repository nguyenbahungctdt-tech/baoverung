package com.baoverung.app.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Map : Screen("map")
    object PatrolForm : Screen("patrol_form")
    object FloraFaunaForm : Screen("flora_fauna_form")
    object NaturalImpactForm : Screen("natural_impact_form")
    object DailyJournal : Screen("daily_journal")
    object GisLayers : Screen("gis_layers")
    object WaypointsData : Screen("waypoints_data")
    object Settings : Screen("settings")
    object CameraCapture : Screen("camera_capture")
}
