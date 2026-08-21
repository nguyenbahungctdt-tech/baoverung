package com.baoverung.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.baoverung.app.data.local.dao.*
import com.baoverung.app.data.local.entity.*

@Database(
    entities = [
        WaypointEntity::class,
        TrackLogEntity::class,
        PatrolLogEntity::class,
        EmailQueueEntity::class,
        GisLayerEntity::class,
        DailyJournalEntity::class,
        PolygonEntity::class,
        GisFeatureEntity::class,
        FloraFaunaLogEntity::class,
        NaturalImpactLogEntity::class
    ],
    version = 16,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun waypointDao(): WaypointDao
    abstract fun trackLogDao(): TrackLogDao
    abstract fun patrolLogDao(): PatrolLogDao
    abstract fun emailQueueDao(): EmailQueueDao
    abstract fun gisLayerDao(): GisLayerDao
    abstract fun dailyJournalDao(): DailyJournalDao
    abstract fun polygonDao(): PolygonDao
    abstract fun gisFeatureDao(): GisFeatureDao
    abstract fun floraFaunaLogDao(): FloraFaunaLogDao
    abstract fun naturalImpactLogDao(): NaturalImpactLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vtool_survey_gis.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
