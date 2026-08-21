package com.baoverung.app.data.local.dao

import androidx.room.*
import com.baoverung.app.data.local.entity.GisFeatureEntity

@Dao
interface GisFeatureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(features: List<GisFeatureEntity>)

    @Query("SELECT * FROM gis_features WHERE layerId = :layerId")
    suspend fun getFeaturesByLayer(layerId: Long): List<GisFeatureEntity>

    @Query("DELETE FROM gis_features WHERE layerId = :layerId")
    suspend fun deleteByLayer(layerId: Long)

    @Query("SELECT COUNT(*) FROM gis_features WHERE layerId = :layerId")
    suspend fun getCountByLayer(layerId: Long): Int

    @Query("""
        SELECT * FROM gis_features 
        WHERE layerId = :layerId 
        AND NOT (maxLat < :minLat OR minLat > :maxLat OR maxLon < :minLon OR minLon > :maxLon)
        LIMIT 40000
    """)
    suspend fun getFeaturesInBounds(layerId: Long, minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<GisFeatureEntity>

    @Query("""
        SELECT MIN(minLat) as minLat, MAX(maxLat) as maxLat, MIN(minLon) as minLon, MAX(maxLon) as maxLon 
        FROM gis_features 
        WHERE layerId = :layerId
    """)
    suspend fun getLayerExtent(layerId: Long): ExtentResult?
}

data class ExtentResult(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
)
