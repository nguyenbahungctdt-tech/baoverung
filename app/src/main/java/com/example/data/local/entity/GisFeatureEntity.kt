package com.baoverung.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "gis_features",
    indices = [
        Index("layerId"),
        Index("minLat"),
        Index("maxLat"),
        Index("minLon"),
        Index("maxLon"),
        Index(value = ["layerId", "minLat", "maxLat", "minLon", "maxLon"], name = "idx_spatial_search")
    ]
)
data class GisFeatureEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val featureId: String,
    val layerId: Long,
    val shapeType: String, // POINT, LINE, POLYGON
    val pointsJson: String,
    val attributesJson: String,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
)
