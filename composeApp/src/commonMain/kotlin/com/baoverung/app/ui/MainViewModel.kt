package com.baoverung.app.ui

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
    private val cloudSyncRepository: com.baoverung.app.repository.CloudSyncRepository,
    private val scope: CoroutineScope
) {
    val cloudSync = cloudSyncRepository
    
    private val _currentLocation = MutableStateFlow<GpsPoint?>(null)
    val currentLocation = _currentLocation.asStateFlow()

    private val _isTrackingGpx = MutableStateFlow(false)
    val isTrackingGpx = _isTrackingGpx.asStateFlow()

    val waypoints = repository.waypoints.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val trackLogs = repository.trackLogs.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val patrolLogs = repository.patrolLogs.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val dailyJournals = repository.dailyJournals.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val gisLayers = repository.gisLayers.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val floraFaunaLogs = repository.floraFaunaLogs.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val naturalImpactLogs = repository.naturalImpactLogs.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val polygons = repository.polygons.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                    altitude = loc.altitude,
                    vn2000X = 0.0, // Should calculate this
                    vn2000Y = 0.0,
                    accuracy = loc.accuracy,
                    satellitesCount = loc.satellitesCount,
                    userEmail = userEmail
                )
            )
        }
    }

    fun deleteWaypoint(id: Long) {
        scope.launch(Dispatchers.IO) {
            waypoints.value.find { it.id == id }?.let { repository.deleteWaypoint(it) }
        }
    }

    fun deleteTrackLog(id: Long) {
        scope.launch(Dispatchers.IO) {
            trackLogs.value.find { it.id == id }?.let { repository.deleteTrackLog(it) }
        }
    }

    fun deletePatrolLog(id: Long) {
        scope.launch(Dispatchers.IO) {
            patrolLogs.value.find { it.id == id }?.let { repository.deletePatrolLog(it) }
        }
    }
}
