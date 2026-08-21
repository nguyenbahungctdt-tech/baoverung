package com.baoverung.app.data.local.dao

import androidx.room.*
import com.baoverung.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WaypointDao {
    @Query("SELECT * FROM waypoints ORDER BY timestampUtc DESC")
    fun getAllWaypoints(): Flow<List<WaypointEntity>>

    @Query("SELECT * FROM waypoints ORDER BY timestampUtc DESC")
    suspend fun getAllWaypointsList(): List<WaypointEntity>

    @Query("SELECT * FROM waypoints WHERE isSynced = 0")
    suspend fun getUnsyncedWaypoints(): List<WaypointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(waypoint: WaypointEntity): Long

    @Update
    suspend fun update(waypoint: WaypointEntity)

    @Delete
    suspend fun delete(waypoint: WaypointEntity)

    @Query("DELETE FROM waypoints WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface TrackLogDao {
    @Query("SELECT * FROM track_logs ORDER BY startTimeUtc DESC")
    fun getAllTrackLogs(): Flow<List<TrackLogEntity>>

    @Query("SELECT * FROM track_logs ORDER BY startTimeUtc DESC")
    suspend fun getAllTrackLogsList(): List<TrackLogEntity>

    @Query("SELECT * FROM track_logs WHERE id = :id")
    suspend fun getById(id: Long): TrackLogEntity?

    @Query("SELECT * FROM track_logs WHERE isSynced = 0")
    suspend fun getUnsyncedTracks(): List<TrackLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trackLog: TrackLogEntity): Long

    @Update
    suspend fun update(trackLog: TrackLogEntity)

    @Query("DELETE FROM track_logs WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface PatrolLogDao {
    @Query("SELECT * FROM patrol_logs ORDER BY discoveryTimeUtc DESC")
    fun getAllPatrolLogs(): Flow<List<PatrolLogEntity>>

    @Query("SELECT * FROM patrol_logs ORDER BY discoveryTimeUtc DESC")
    suspend fun getAllPatrolLogsList(): List<PatrolLogEntity>

    @Query("SELECT * FROM patrol_logs WHERE id = :id")
    suspend fun getById(id: Long): PatrolLogEntity?

    @Query("SELECT * FROM patrol_logs WHERE isSynced = 0")
    suspend fun getUnsyncedPatrols(): List<PatrolLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(patrolLog: PatrolLogEntity): Long

    @Update
    suspend fun update(patrolLog: PatrolLogEntity)

    @Query("DELETE FROM patrol_logs WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface DailyJournalDao {
    @Query("SELECT * FROM daily_journals ORDER BY timestampUtc DESC")
    fun getAllDailyJournals(): Flow<List<DailyJournalEntity>>

    @Query("SELECT * FROM daily_journals WHERE id = :id")
    suspend fun getById(id: Long): DailyJournalEntity?

    @Query("SELECT * FROM daily_journals WHERE dateStr = :dateStr LIMIT 1")
    suspend fun getByDate(dateStr: String): DailyJournalEntity?

    @Query("SELECT * FROM daily_journals WHERE isSynced = 0")
    suspend fun getUnsyncedJournals(): List<DailyJournalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(journal: DailyJournalEntity): Long

    @Update
    suspend fun update(journal: DailyJournalEntity)

    @Query("UPDATE daily_journals SET displayColorHex = :color WHERE id = :id")
    suspend fun updateColor(id: Long, color: String)

    @Query("DELETE FROM daily_journals WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface EmailQueueDao {
    @Query("SELECT * FROM email_queue WHERE isSent = 0 ORDER BY createdAtUtc ASC")
    fun getPendingEmailsFlow(): Flow<List<EmailQueueEntity>>

    @Query("SELECT * FROM email_queue WHERE isSent = 0 ORDER BY createdAtUtc ASC")
    suspend fun getPendingEmailsList(): List<EmailQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(email: EmailQueueEntity): Long

    @Update
    suspend fun update(email: EmailQueueEntity)

    @Query("DELETE FROM email_queue WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface GisLayerDao {
    @Query("SELECT * FROM gis_layers ORDER BY priority ASC, id ASC")
    fun getAllGisLayers(): Flow<List<GisLayerEntity>>

    @Query("SELECT * FROM gis_layers ORDER BY priority ASC, id ASC")
    suspend fun getAllGisLayersList(): List<GisLayerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(layer: GisLayerEntity): Long

    @Update
    suspend fun update(layer: GisLayerEntity)

    @Query("UPDATE gis_layers SET name = :newName WHERE id = :id")
    suspend fun updateName(id: Long, newName: String)

    @Query("UPDATE gis_layers SET priority = :priority WHERE id = :id")
    suspend fun updatePriority(id: Long, priority: Int)

    @Query("UPDATE gis_layers SET labelColumn = :columnName WHERE id = :id")
    suspend fun updateLabelColumn(id: Long, columnName: String?)

    @Query("UPDATE gis_layers SET strokeColorHex = :strokeColor, fillColorHex = :fillColor WHERE id = :id")
    suspend fun updateColors(id: Long, strokeColor: String, fillColor: String)

    @Query("DELETE FROM gis_layers WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface PolygonDao {
    @Query("SELECT * FROM polygons ORDER BY timestampUtc DESC")
    fun getAllPolygons(): Flow<List<PolygonEntity>>

    @Query("SELECT * FROM polygons ORDER BY timestampUtc DESC")
    suspend fun getAllPolygonsList(): List<PolygonEntity>

    @Query("SELECT * FROM polygons WHERE id = :id")
    suspend fun getById(id: Long): PolygonEntity?

    @Query("SELECT * FROM polygons WHERE isSynced = 0")
    suspend fun getUnsyncedPolygons(): List<PolygonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(polygon: PolygonEntity): Long

    @Update
    suspend fun update(polygon: PolygonEntity)

    @Query("DELETE FROM polygons WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface FloraFaunaLogDao {
    @Query("SELECT * FROM flora_fauna_logs ORDER BY timestampUtc DESC")
    fun getAllLogs(): Flow<List<FloraFaunaLogEntity>>

    @Query("SELECT * FROM flora_fauna_logs ORDER BY timestampUtc DESC")
    suspend fun getAllLogsList(): List<FloraFaunaLogEntity>

    @Query("SELECT * FROM flora_fauna_logs WHERE id = :id")
    suspend fun getById(id: Long): FloraFaunaLogEntity?

    @Query("SELECT * FROM flora_fauna_logs WHERE isSynced = 0")
    suspend fun getUnsyncedLogs(): List<FloraFaunaLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: FloraFaunaLogEntity): Long

    @Update
    suspend fun update(log: FloraFaunaLogEntity)

    @Query("DELETE FROM flora_fauna_logs WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface NaturalImpactLogDao {
    @Query("SELECT * FROM natural_impact_logs ORDER BY timestampUtc DESC")
    fun getAllLogs(): Flow<List<NaturalImpactLogEntity>>

    @Query("SELECT * FROM natural_impact_logs ORDER BY timestampUtc DESC")
    suspend fun getAllLogsList(): List<NaturalImpactLogEntity>

    @Query("SELECT * FROM natural_impact_logs WHERE id = :id")
    suspend fun getById(id: Long): NaturalImpactLogEntity?

    @Query("SELECT * FROM natural_impact_logs WHERE isSynced = 0")
    suspend fun getUnsyncedLogs(): List<NaturalImpactLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: NaturalImpactLogEntity): Long

    @Update
    suspend fun update(log: NaturalImpactLogEntity)

    @Query("DELETE FROM natural_impact_logs WHERE id = :id")
    suspend fun deleteById(id: Long)
}
