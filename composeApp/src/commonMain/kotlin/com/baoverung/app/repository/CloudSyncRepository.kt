package com.baoverung.app.repository

import com.baoverung.app.data.model.UserSession

data class KeyValidationResult(
    val isValid: Boolean,
    val message: String? = null,
    val permissions: String = "FULL",
    val autoGpx: Boolean = false,
    val canSync: Boolean = true
)

expect class CloudSyncRepository() {
    suspend fun verifyActivationKey(
        key: String, 
        deviceId: String, 
        isLogin: Boolean = false,
        userInfo: Map<String, String>? = null
    ): KeyValidationResult
    
    suspend fun syncWaypoint(user: UserSession, wp: com.baoverung.app.data.local.entity.WaypointEntity, cm: Double, province: String, zone: Int): Boolean
    suspend fun syncTrack(user: UserSession, trk: com.baoverung.app.data.local.entity.TrackLogEntity, cm: Double, province: String, zone: Int): Boolean
    suspend fun syncPolygon(user: UserSession, p: com.baoverung.app.data.local.entity.PolygonEntity, cm: Double, province: String, zone: Int): Boolean
    suspend fun syncPatrol(user: UserSession, p: com.baoverung.app.data.local.entity.PatrolLogEntity, cm: Double, province: String, zone: Int): Boolean
    suspend fun syncFloraFauna(user: UserSession, l: com.baoverung.app.data.local.entity.FloraFaunaLogEntity, cm: Double, province: String, zone: Int): Boolean
    suspend fun syncNaturalImpact(user: UserSession, l: com.baoverung.app.data.local.entity.NaturalImpactLogEntity, cm: Double, province: String, zone: Int): Boolean
    suspend fun syncDailyJournal(user: UserSession, journal: com.baoverung.app.data.local.entity.DailyJournalEntity, cm: Double, province: String, zone: Int): Boolean
}
