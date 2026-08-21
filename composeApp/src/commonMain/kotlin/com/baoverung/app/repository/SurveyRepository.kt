package com.baoverung.app.repository

import com.baoverung.app.data.local.AppDatabase
import com.baoverung.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

class SurveyRepository(private val database: AppDatabase) {
    val waypoints: Flow<List<WaypointEntity>> = database.waypointDao().getAllWaypoints()
    val trackLogs: Flow<List<TrackLogEntity>> = database.trackLogDao().getAllTrackLogs()
    val patrolLogs: Flow<List<PatrolLogEntity>> = database.patrolLogDao().getAllPatrolLogs()
    val dailyJournals: Flow<List<DailyJournalEntity>> = database.dailyJournalDao().getAllDailyJournals()
    val gisLayers: Flow<List<GisLayerEntity>> = database.gisLayerDao().getAllGisLayers()

    suspend fun saveWaypoint(waypoint: WaypointEntity) {
        database.waypointDao().insert(waypoint)
    }

    suspend fun deleteWaypoint(waypoint: WaypointEntity) {
        database.waypointDao().delete(waypoint)
    }

    suspend fun saveTrackLog(track: TrackLogEntity) {
        database.trackLogDao().insert(track)
    }

    suspend fun savePatrolLog(patrol: PatrolLogEntity) {
        database.patrolLogDao().insert(patrol)
    }

    suspend fun saveDailyJournal(journal: DailyJournalEntity) {
        database.dailyJournalDao().insert(journal)
    }

    suspend fun saveGisLayer(layer: GisLayerEntity) {
        database.gisLayerDao().insert(layer)
    }
    
    suspend fun deleteGisLayer(layer: GisLayerEntity) {
        database.gisLayerDao().delete(layer)
    }
}
