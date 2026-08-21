package com.baoverung.app.ui

import com.baoverung.app.data.local.AppDatabase
import com.baoverung.app.data.local.entity.*
import com.baoverung.app.data.model.*
import com.baoverung.app.repository.SurveyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: SurveyRepository,
    private val scope: CoroutineScope
) {
    private val _currentLocation = MutableStateFlow<GpsPoint?>(null)
    val currentLocation = _currentLocation.asStateFlow()

    private val _isTrackingGpx = MutableStateFlow(false)
    val isTrackingGpx = _isTrackingGpx.asStateFlow()

    val waypoints = repository.waypoints.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val trackLogs = repository.trackLogs.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val patrolLogs = repository.patrolLogs.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val dailyJournals = repository.dailyJournals.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val gisLayers = repository.gisLayers.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateLocation(lat: Double, lon: Double) {
        _currentLocation.value = GpsPoint(lat, lon)
    }

    fun toggleGpxTracking() {
        _isTrackingGpx.value = !_isTrackingGpx.value
    }

    fun saveWaypoint(title: String, description: String, userEmail: String) {
        val loc = _currentLocation.value ?: return
        scope.launch(Dispatchers.IO) {
            repository.saveWaypoint(
                WaypointEntity(
                    title = title,
                    description = description,
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    altitude = 0.0,
                    vn2000X = 0.0,
                    vn2000Y = 0.0,
                    accuracy = 0f,
                    satellitesCount = 0,
                    userEmail = userEmail
                )
            )
        }
    }
}
