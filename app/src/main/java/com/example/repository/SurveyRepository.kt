package com.baoverung.app.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.baoverung.app.data.local.AppDatabase
import com.baoverung.app.data.local.UserPreferencesManager
import com.baoverung.app.data.local.entity.*
import com.baoverung.app.data.model.GisFeature
import com.baoverung.app.data.model.GpsPoint
import com.baoverung.app.data.model.UserSession
import com.baoverung.app.gis.*
import com.baoverung.app.gis.CoordinateSystemConverter.toNonAccent
import com.baoverung.app.util.WordExportHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import android.util.LruCache

class SurveyRepository(private val context: Context) {
    val db = AppDatabase.getDatabase(context)
    val prefs = UserPreferencesManager(context)

    // Requirement 2: Memory-safe cache for GIS features to prevent OOM and repeated DB hits
    private val featureCache = LruCache<String, List<GisFeature>>(12)

    fun clearCaches() {
        featureCache.evictAll()
        System.gc()
    }

    val waypointsFlow: Flow<List<WaypointEntity>> = db.waypointDao().getAllWaypoints()
    val trackLogsFlow: Flow<List<TrackLogEntity>> = db.trackLogDao().getAllTrackLogs()
    val patrolLogsFlow: Flow<List<PatrolLogEntity>> = db.patrolLogDao().getAllPatrolLogs()
    val dailyJournalsFlow: Flow<List<DailyJournalEntity>> = db.dailyJournalDao().getAllDailyJournals()
    val floraFaunaLogsFlow: Flow<List<FloraFaunaLogEntity>> = db.floraFaunaLogDao().getAllLogs()
    val naturalImpactLogsFlow: Flow<List<NaturalImpactLogEntity>> = db.naturalImpactLogDao().getAllLogs()
    val emailQueueFlow: Flow<List<EmailQueueEntity>> = db.emailQueueDao().getPendingEmailsFlow()
    val polygonsFlow: Flow<List<PolygonEntity>> = db.polygonDao().getAllPolygons()
    val gisLayersFlow: Flow<List<GisLayerEntity>> = db.gisLayerDao().getAllGisLayers()

    fun formatTrackPointsToJson(points: List<GpsPoint>, includeVn2000: Boolean = false): String {
        val array = org.json.JSONArray(); val cm = prefs.vn2000CentralMeridian; val zd = prefs.vn2000ZoneDegrees
        for (pt in points) { 
            val obj = org.json.JSONObject()
            val lat = if (pt.latitude.isNaN()) 0.0 else pt.latitude
            val lon = if (pt.longitude.isNaN()) 0.0 else pt.longitude
            obj.put("latitude", lat)
            obj.put("longitude", lon)
            obj.put("altitude", pt.altitude)
            obj.put("speed", pt.speed.toDouble())
            obj.put("accuracy", pt.accuracy.toDouble())
            obj.put("satellitesCount", pt.satellitesCount)
            obj.put("timestampUtc", pt.timestampUtc)
            if (includeVn2000) { 
                val (vx, vy) = CoordinateSystemConverter.wgs84ToVn2000(lat, lon, cm, zd)
                obj.put("vn2000X", if (vx.isNaN()) 0.0 else vx)
                obj.put("vn2000Y", if (vy.isNaN()) 0.0 else vy)
            }
            array.put(obj) 
        }
        return array.toString()
    }

    fun parseTrackPoints(pointsJson: String): List<GpsPoint> {
        if (pointsJson.isEmpty() || pointsJson == "null") return emptyList()
        val list = ArrayList<GpsPoint>(500) // Optimized: Preset capacity
        try { 
            val array = org.json.JSONArray(pointsJson)
            val len = array.length()
            for (i in 0 until len) { 
                val obj = array.getJSONObject(i)
                list.add(GpsPoint(
                    latitude = obj.optDouble("latitude", 0.0), 
                    longitude = obj.optDouble("longitude", 0.0), 
                    altitude = obj.optDouble("altitude", 0.0), 
                    speed = obj.optDouble("speed", 0.0).toFloat(), 
                    accuracy = obj.optDouble("accuracy", 0.0).toFloat(), 
                    satellitesCount = obj.optInt("satellitesCount", 0), 
                    timestampUtc = obj.optLong("timestampUtc", 0L)
                )) 
            } 
        } catch (e: Exception) { }
        return list
    }

    fun formatAttributesToJson(attributes: Map<String, String>): String {
        val obj = org.json.JSONObject(); attributes.forEach { (k, v) -> obj.put(k, v) }; return obj.toString()
    }

    fun parseAttributes(json: String): Map<String, String> {
        if (json.isEmpty() || json == "{}" || json == "null") return emptyMap()
        val map = mutableMapOf<String, String>(); try { val obj = org.json.JSONObject(json); val keys = obj.keys(); while (keys.hasNext()) { val key = keys.next(); map[key] = obj.optString(key, "") } } catch (e: Exception) {}
        return map
    }

    fun getUserSession(): UserSession = prefs.getUserSession()
    fun saveUserSession(session: UserSession) = prefs.saveUserSession(session)
    fun getExportDirectory(): File { val publicDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "BaoVeRung"); if (publicDir.exists() || publicDir.mkdirs()) return publicDir; val appDir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "BaoVeRung"); if (!appDir.exists()) appDir.mkdirs(); return appDir }

    suspend fun saveWaypoint(title: String, description: String, latitude: Double, longitude: Double, altitude: Double, accuracy: Float, satellitesCount: Int, photoPath: String? = null, category: String = "Lâm nghiệp"): Long = withContext(Dispatchers.IO) {
        val cm = prefs.vn2000CentralMeridian; val zone = prefs.vn2000ZoneDegrees; val (vx, vy) = CoordinateSystemConverter.wgs84ToVn2000(latitude, longitude, cm, zone); val user = getUserSession(); val entity = WaypointEntity(title = title, description = description, latitude = latitude, longitude = longitude, altitude = altitude, vn2000X = vx, vn2000Y = vy, accuracy = accuracy, satellitesCount = satellitesCount, photoPath = photoPath, displayOrder = if (photoPath != null) getTodayPhotoCount() + 1 else 0, timestampUtc = System.currentTimeMillis(), userEmail = user.email.ifEmpty { "nguyenbahung.ctdt@gmail.com" }, category = category, displayColorHex = if (photoPath != null) prefs.imageColor else prefs.pointColor); db.waypointDao().insert(entity)
    }

    suspend fun deleteWaypoint(id: Long) = withContext(Dispatchers.IO) { db.waypointDao().deleteById(id) }
    suspend fun markWaypointSynced(id: Long) = withContext(Dispatchers.IO) { val list = db.waypointDao().getAllWaypointsList(); val item = list.find { it.id == id }; if (item != null) db.waypointDao().update(item.copy(isSynced = true)) }
    suspend fun getTodayWaypointCount(): Int = withContext(Dispatchers.IO) { val start = getStartOfDayTimestamp(); db.waypointDao().getAllWaypointsList().count { it.timestampUtc >= start } }
    suspend fun getTodayTrackCount(): Int = withContext(Dispatchers.IO) { val start = getStartOfDayTimestamp(); db.trackLogDao().getAllTrackLogsList().count { it.startTimeUtc >= start } }
    suspend fun getTodayPolygonCount(): Int = withContext(Dispatchers.IO) { val start = getStartOfDayTimestamp(); db.polygonDao().getAllPolygonsList().count { it.timestampUtc >= start } }
    suspend fun getTodayPhotoCount(): Int = withContext(Dispatchers.IO) { val start = getStartOfDayTimestamp(); val picsDir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "BaoVeRung"); if (!picsDir.exists()) return@withContext 0; picsDir.listFiles()?.count { it.lastModified() >= start && listOf("jpg", "jpeg", "png").contains(it.extension.lowercase()) } ?: 0 }
    fun getStartOfDayTimestamp(): Long { val calendar = Calendar.getInstance(); calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0); return calendar.timeInMillis }
    suspend fun getUnsyncedWaypoints() = db.waypointDao().getUnsyncedWaypoints()
    suspend fun resetWaypointsSyncStatus() = withContext(Dispatchers.IO) { db.waypointDao().getAllWaypointsList().forEach { db.waypointDao().update(it.copy(isSynced = false)) } }

    suspend fun saveTrackLog(title: String, startTimeUtc: Long, endTimeUtc: Long, points: List<GpsPoint>): Long = withContext(Dispatchers.IO) {
        val ptsJson = formatTrackPointsToJson(points, true); val dist = GisAreaCalculator.calculatePathLength(points); val user = getUserSession()
        val n = points.size; val k = when { n < 500 -> 5; n < 2000 -> 10; else -> 15 }
        val sampled = if (n <= k) points else { val list = mutableListOf<GpsPoint>(); list.add(points.first()); if (dist > 0) { val seg = dist / (k - 1); var acc = 0.0; var last = points.first(); var target = seg; for (i in 1 until points.size - 1) { val d = GisAreaCalculator.calculateDistance(last.latitude, last.longitude, points[i].latitude, points[i].longitude); acc += d; last = points[i]; if (acc >= target && list.size < k - 1) { list.add(points[i]); target += seg } } } else { for (i in 1 until k - 1) list.add(points[(i * (n - 1)) / (k - 1)]) }; if (list.size < k) list.add(points.last()); list }
        val entity = TrackLogEntity(title = title, startTimeUtc = startTimeUtc, endTimeUtc = endTimeUtc, totalDistanceMeters = dist, pointsJson = ptsJson, sampledPointsJson = formatTrackPointsToJson(sampled, true), userEmail = user.email.ifEmpty { "nguyenbahung.ctdt@gmail.com" }, displayColorHex = prefs.tracklogColor, category = "GPX")
        val trackId = db.trackLogDao().insert(entity); val gpxFile = File(getExportDirectory(), "Track_${trackId}.gpx"); GpxExporter.exportTrackLogToGpx(entity.copy(id = trackId), points, gpxFile, user.displayName, user.phoneNumber)
        db.trackLogDao().update(entity.copy(id = trackId, gpxFilePath = gpxFile.absolutePath)); queueEmailReport("[BVR] Tracklog: $title", "Báo cáo Tracklog đính kèm.", gpxFile.absolutePath); trackId
    }

    suspend fun deleteTrackLog(id: Long) = withContext(Dispatchers.IO) { db.trackLogDao().deleteById(id) }
    suspend fun updateTrackCategory(id: Long, category: String, colorHex: String? = null) = withContext(Dispatchers.IO) { val item = db.trackLogDao().getById(id); if (item != null) db.trackLogDao().update(item.copy(category = category, displayColorHex = colorHex ?: item.displayColorHex, isSynced = false)) }
    suspend fun markTrackSynced(id: Long) = withContext(Dispatchers.IO) { val item = db.trackLogDao().getById(id); if (item != null) db.trackLogDao().update(item.copy(isSynced = true)) }
    suspend fun getUnsyncedTracks() = db.trackLogDao().getUnsyncedTracks()
    suspend fun getUnsyncedPolygons() = db.polygonDao().getUnsyncedPolygons()
    suspend fun getUnsyncedPatrols() = db.patrolLogDao().getUnsyncedPatrols()
    suspend fun getUnsyncedJournals() = db.dailyJournalDao().getUnsyncedJournals()
    suspend fun getUnsyncedFloraFauna() = db.floraFaunaLogDao().getUnsyncedLogs()
    suspend fun getUnsyncedNaturalImpacts() = db.naturalImpactLogDao().getUnsyncedLogs()
    suspend fun resetTracksSyncStatus() = withContext(Dispatchers.IO) { db.trackLogDao().getAllTrackLogsList().forEach { db.trackLogDao().update(it.copy(isSynced = false)) } }
    suspend fun resetPolygonsSyncStatus() = withContext(Dispatchers.IO) { db.polygonDao().getAllPolygonsList().forEach { db.polygonDao().update(it.copy(isSynced = false)) } }
    suspend fun resetPatrolsSyncStatus() = withContext(Dispatchers.IO) { db.patrolLogDao().getAllPatrolLogsList().forEach { db.patrolLogDao().update(it.copy(isSynced = false)) } }
    suspend fun resetJournalsSyncStatus() = withContext(Dispatchers.IO) { db.dailyJournalDao().getAllDailyJournals().first().forEach { db.dailyJournalDao().update(it.copy(isSynced = false)) } }
    suspend fun markPolygonSynced(id: Long) = withContext(Dispatchers.IO) { val item = db.polygonDao().getById(id); if (item != null) db.polygonDao().update(item.copy(isSynced = true)) }
    suspend fun markPatrolSynced(id: Long) = withContext(Dispatchers.IO) { val item = db.patrolLogDao().getById(id); if (item != null) db.patrolLogDao().update(item.copy(isSynced = true)) }
    suspend fun markJournalSynced(id: Long) = withContext(Dispatchers.IO) { val item = db.dailyJournalDao().getById(id); if (item != null) db.dailyJournalDao().update(item.copy(isSynced = true)) }
    suspend fun markFloraFaunaSynced(id: Long) = withContext(Dispatchers.IO) { val item = db.floraFaunaLogDao().getById(id); if (item != null) db.floraFaunaLogDao().update(item.copy(isSynced = true)) }
    suspend fun markNaturalImpactSynced(id: Long) = withContext(Dispatchers.IO) { val item = db.naturalImpactLogDao().getById(id); if (item != null) db.naturalImpactLogDao().update(item.copy(isSynced = true)) }

    suspend fun cacheLayerFeatures(layer: GisLayerEntity, onProgress: (suspend (Int, Int) -> Unit)? = null) = withContext(Dispatchers.IO) {
        val file = File(layer.filePath)
        val cm = layer.centralMeridian
        val zd = layer.zoneDegrees
        Log.d("SurveyRepository", "Caching layer ${layer.name} (${layer.fileType}) - START")
        
        if (!file.exists()) {
            Log.e("SurveyRepository", "File not found: ${layer.filePath}")
            return@withContext
        }

        featureCache.evictAll()
        db.gisFeatureDao().deleteByLayer(layer.id)
        
        var totalAdded = 0
        val chunk = mutableListOf<GisFeatureEntity>()
        
        val insertChunk: suspend () -> Unit = {
            if (chunk.isNotEmpty()) {
                db.withTransaction {
                    db.gisFeatureDao().insertAll(chunk.toList())
                }
                totalAdded += chunk.size
                chunk.clear()
            }
        }

        val onFeat: suspend (GisFeature) -> Unit = { feat ->
            try {
                if (totalAdded < 5) {
                    Log.d("SurveyRepository", "Sample feature parsed: ID=${feat.id}, Type=${feat.shapeType}, Pts=${feat.points.size}, Centroid=${feat.centroidLat},${feat.centroidLon}")
                }
                chunk.add(GisFeatureEntity(
                    featureId = feat.id, 
                    layerId = layer.id, 
                    shapeType = feat.shapeType.name, 
                    pointsJson = formatTrackPointsToJson(feat.points), 
                    attributesJson = formatAttributesToJson(feat.attributes), 
                    minLat = feat.minLat, maxLat = feat.maxLat, minLon = feat.minLon, maxLon = feat.maxLon
                ))
                if (chunk.size >= 100) insertChunk()
            } catch (e: Exception) {
                Log.e("SurveyRepository", "Error adding feature to chunk: ${e.message}")
            }
        }

        try {
            when (layer.fileType.uppercase()) {
                "TAB", "MIF" -> {
                    GdalMapInfoParser.parseMapInfoStreaming(context, file, layer.id, cm, zd, onProgress) { onFeat(it) }
                }
                "SHP" -> {
                    ShapefileParser.parseShapefileStreaming(file, layer.id, cm, zd, onProgress) { onFeat(it) }
                }
                "KML", "KMZ" -> KmlParser.parseKmlOrKmzStreaming(file, layer.id, onProgress) { onFeat(it) }
                else -> loadFeaturesFromLayer(layer).forEach { onFeat(it) }
            }
        } catch (e: Exception) {
            Log.e("SurveyRepository", "Critical error during layer caching: ${e.message}")
        } finally {
            insertChunk()
        }
        
        val finalCount = db.gisFeatureDao().getCountByLayer(layer.id)
        val extent = db.gisFeatureDao().getLayerExtent(layer.id)
        Log.i("SurveyRepository", "Cache complete. Count: $finalCount, Extent: ${extent?.minLat},${extent?.minLon} to ${extent?.maxLat},${extent?.maxLon}")
        
        if (finalCount == 0) {
            Log.w("SurveyRepository", "Layer ${layer.name} imported with 0 objects. Possible format mismatch.")
        }
    }

    suspend fun getFeaturesForLayer(layer: GisLayerEntity, forceReload: Boolean = false): List<GisFeature> = withContext(Dispatchers.IO) {
        val count = db.gisFeatureDao().getCountByLayer(layer.id)
        if (count > 0 && !forceReload) {
            val limit = 1000 // Limit for initial overview to avoid jank
            return@withContext db.gisFeatureDao().getFeaturesByLayer(layer.id).take(limit).map { entity -> 
                val pts = parseTrackPoints(entity.pointsJson)
                val centroid = if (pts.isNotEmpty()) GisAreaCalculator.calculateCentroid(pts) else GpsPoint(0.0, 0.0)
                GisFeature(id = entity.featureId, layerId = entity.layerId, shapeType = com.baoverung.app.data.model.GisShapeType.valueOf(entity.shapeType), points = pts, attributes = parseAttributes(entity.attributesJson), minLat = entity.minLat, maxLat = entity.maxLat, minLon = entity.minLon, maxLon = entity.maxLon, centroidLat = centroid.latitude, centroidLon = centroid.longitude) 
            }
        }
        if (forceReload || count == 0) cacheLayerFeatures(layer)
        emptyList<GisFeature>()
    }

    suspend fun getFeaturesInBounds(layerId: Long, minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<GisFeature> = withContext(Dispatchers.IO) { 
        // Create a cache key based on layer and rounded bounds (approx 100m precision)
        val cacheKey = "${layerId}_${(minLat*100).toInt()}_${(maxLat*100).toInt()}_${(minLon*100).toInt()}_${(maxLon*100).toInt()}"
        
        featureCache.get(cacheKey)?.let { return@withContext it }

        val features = db.gisFeatureDao().getFeaturesInBounds(layerId, minLat, maxLat, minLon, maxLon).map { entity -> 
            val pts = parseTrackPoints(entity.pointsJson)
            val centroid = if (pts.isNotEmpty()) GisAreaCalculator.calculateCentroid(pts) else GpsPoint(0.0, 0.0)
            GisFeature(id = entity.featureId, layerId = entity.layerId, shapeType = com.baoverung.app.data.model.GisShapeType.valueOf(entity.shapeType), points = pts, attributes = parseAttributes(entity.attributesJson), minLat = entity.minLat, maxLat = entity.maxLat, minLon = entity.minLon, maxLon = entity.maxLon, centroidLat = centroid.latitude, centroidLon = centroid.longitude) 
        } 
        
        featureCache.put(cacheKey, features)
        features
    }

    suspend fun getLayerExtent(layerId: Long): com.baoverung.app.data.local.dao.ExtentResult? = withContext(Dispatchers.IO) {
        db.gisFeatureDao().getLayerExtent(layerId)
    }

    suspend fun loadFeaturesFromLayer(layer: GisLayerEntity): List<GisFeature> = withContext(Dispatchers.IO) { 
        val file = File(layer.filePath)
        val cm = layer.centralMeridian
        val zd = layer.zoneDegrees
        val list = mutableListOf<GisFeature>()
        when (layer.fileType.uppercase()) { 
            "SHP" -> {
                ShapefileParser.parseShapefileStreaming(file, layer.id, cm, zd, null) { list.add(it) }
            }
            "KML", "KMZ" -> {
                KmlParser.parseKmlOrKmzStreaming(file, layer.id, null) { list.add(it) }
            }
            "TAB" -> {
                MapInfoTabParser.parseTabFileStreaming(file, layer.id, cm, zd, false, null) { list.add(it) }
            }
            "MIF" -> { 
                MifParser.parseMifFileStreaming(file, layer.id, cm, zd, null) { list.add(it) }
            }
            "GPX" -> GpxParser.parseGpx(file, layer.id).let { list.addAll(it) }
            "GEOJSON", "JSON" -> GeoJsonParser.parseGeoJson(file, layer.id).let { list.addAll(it) }
        }
        list
    }
    suspend fun getLayerFieldNames(layer: GisLayerEntity): List<String> = withContext(Dispatchers.IO) { val file = File(layer.filePath); when (layer.fileType.uppercase()) { "SHP" -> ShapefileParser.getFieldNames(File(layer.filePath.replace(".shp", ".dbf", ignoreCase = true))); "TAB" -> MapInfoTabParser.getFieldNames(file); "MIF" -> MifParser.getFieldNames(file); else -> emptyList() } }
    suspend fun updateGisLayerCoordSys(id: Long, cm: Double, zd: Int) = withContext(Dispatchers.IO) { 
        val layer = db.gisLayerDao().getAllGisLayersList().find { it.id == id }
        if (layer != null) { 
            featureCache.evictAll()
            db.gisLayerDao().update(layer.copy(centralMeridian = cm, zoneDegrees = zd))
            db.gisFeatureDao().deleteByLayer(id)
            cacheLayerFeatures(layer.copy(centralMeridian = cm, zoneDegrees = zd)) 
        } 
    }
    suspend fun toggleGisLayerVisibility(layer: GisLayerEntity) = withContext(Dispatchers.IO) { db.gisLayerDao().update(layer.copy(isVisible = !layer.isVisible)) }
    suspend fun deleteGisLayer(id: Long) = withContext(Dispatchers.IO) { db.gisLayerDao().deleteById(id); db.gisFeatureDao().deleteByLayer(id) }
    suspend fun updateGisLayerOpacity(layer: GisLayerEntity, opacity: Float) = withContext(Dispatchers.IO) { db.gisLayerDao().update(layer.copy(opacity = opacity)) }
    suspend fun updateGisLayerName(id: Long, name: String) = withContext(Dispatchers.IO) { val l = db.gisLayerDao().getAllGisLayersList().find { it.id == id }; if (l != null) { db.gisLayerDao().update(l.copy(name = name)) } }
    suspend fun updateGisLayerLabelColumn(id: Long, column: String?) = withContext(Dispatchers.IO) { val l = db.gisLayerDao().getAllGisLayersList().find { it.id == id }; if (l != null) { db.gisLayerDao().update(l.copy(labelColumn = column)) } }
    suspend fun updateGisLayerPriority(id: Long, priority: Int) = withContext(Dispatchers.IO) { db.gisLayerDao().updatePriority(id, priority) }
    suspend fun updateGisLayerColors(id: Long, stroke: String, fill: String) = withContext(Dispatchers.IO) { val l = db.gisLayerDao().getAllGisLayersList().find { it.id == id }; if (l != null) { db.gisLayerDao().update(l.copy(strokeColorHex = stroke, fillColorHex = fill)) } }
    
    private suspend fun checkLayerNameCollision(originalName: String): String {
        val layers = db.gisLayerDao().getAllGisLayersList()
        var candidate = originalName
        var counter = 1
        while (layers.any { it.name.equals(candidate, ignoreCase = true) }) {
            candidate = "$originalName ($counter)"
            counter++
        }
        return candidate
    }

    suspend fun addGisLayer(name: String, fileType: String, filePath: String, cm: Double = 107.75, zd: Int = 3, priority: Int = 0, labelColumn: String? = null): Long = withContext(Dispatchers.IO) { 
        val uniqueName = checkLayerNameCollision(name)
        val colors = listOf("#FF2E7D32", "#FF1976D2", "#FFD84315", "#FF7B1FA2", "#FFC2185B")
        val stroke = colors[(System.currentTimeMillis() % colors.size).toInt()]
        val entity = GisLayerEntity(name = uniqueName, fileType = fileType.uppercase(), filePath = filePath, isVisible = true, opacity = 0.8f, strokeColorHex = stroke, fillColorHex = "#334CAF50", centralMeridian = cm, zoneDegrees = zd, priority = priority, labelColumn = labelColumn)
        db.gisLayerDao().insert(entity) 
    }
    suspend fun importAssetLayer(assetName: String, layerName: String) = withContext(Dispatchers.IO) { try { val dest = File(context.filesDir, assetName); if (!dest.exists()) context.assets.open(assetName).use { i -> FileOutputStream(dest).use { o -> i.copyTo(o) } }; val existing = db.gisLayerDao().getAllGisLayersList(); if (existing.none { it.filePath == dest.absolutePath }) addGisLayer(layerName, assetName.substringAfterLast(".").uppercase(), dest.absolutePath); true } catch (e: Exception) { false } }

    suspend fun savePatrolLog(incidentType: String, lat: Double, lon: Double, alt: Double, acc: Float, sat: Int, leader: String, vTime: String, vLoc: String, vName: String, vId: String, vAddr: String, vPhone: String, tools: String, persons: String, action: String, recs: String, notes: String, photo: String?, field: String = "Lâm nghiệp", logId: Long? = null): Long = withContext(Dispatchers.IO) {
        val cm = prefs.vn2000CentralMeridian; val (vx, vy) = CoordinateSystemConverter.wgs84ToVn2000(lat, lon, cm, prefs.vn2000ZoneDegrees); val user = getUserSession(); val entity = PatrolLogEntity(id = logId ?: 0L, incidentType = incidentType, discoveryTimeUtc = System.currentTimeMillis(), latitude = lat, longitude = lon, altitude = alt, vn2000X = vx, vn2000Y = vy, accuracy = acc, satellitesCount = sat, leaderName = leader, violationTime = vTime, violationLocation = vLoc, violatorName = vName, violatorIdCard = vId, violatorAddress = vAddr, violatorPhone = vPhone, confiscatedTools = tools, relatedPersons = persons, onSiteAction = action, onSiteRecordings = recs, notes = notes, photoPath = photo, violationField = field, userEmail = user.email.ifEmpty { "nguyenbahung.ctdt@gmail.com" }, displayColorHex = prefs.incidentColor); val finalId = if (logId != null) { db.patrolLogDao().update(entity); logId } else db.patrolLogDao().insert(entity)
        val reportFile = File(getExportDirectory(), "BaoCao_SuVu_$finalId".toNonAccent() + ".docx"); WordExportHelper.exportPatrolLogToWord(context, entity.copy(id = finalId), cm, user.unit, user.email, user.phoneNumber, reportFile); queueEmailReport("[BVR] Nhật ký sự vụ: $incidentType", "Báo cáo sự vụ đính kèm.", "${reportFile.absolutePath}|${photo ?: ""}"); finalId
    }

    suspend fun saveFloraFaunaLog(
        lat: Double, lon: Double, alt: Double, acc: Float, sat: Int,
        officer: String, appearance: String, features: String, count: String,
        habitat: String, temp: String, humidity: String, canopy: String,
        surroundPlants: String, specimens: String, photo: String?, logId: Long? = null
    ): Long = withContext(Dispatchers.IO) {
        val cm = prefs.vn2000CentralMeridian; val (vx, vy) = CoordinateSystemConverter.wgs84ToVn2000(lat, lon, cm, prefs.vn2000ZoneDegrees)
        val user = getUserSession()
        val entity = FloraFaunaLogEntity(
            id = logId ?: 0L,
            officerName = officer, latitude = lat, longitude = lon, altitude = alt,
            vn2000X = vx, vn2000Y = vy, accuracy = acc, satellitesCount = sat,
            appearanceDescription = appearance, features = features, count = count,
            habitatType = habitat, temperature = temp, humidity = humidity,
            canopyCover = canopy, surroundingPlants = surroundPlants,
            specimens = specimens, photoPath = photo, userEmail = user.email,
            displayColorHex = "#FF2E7D32"
        )
        if (logId != null) { db.floraFaunaLogDao().update(entity); logId } else db.floraFaunaLogDao().insert(entity)
    }

    suspend fun saveNaturalImpactLog(
        lat: Double, lon: Double, alt: Double, acc: Float, sat: Int,
        officer: String, cause: String, otherCause: String, area: String,
        before: String, after: String, damage: String, time: String, photo: String?, logId: Long? = null
    ): Long = withContext(Dispatchers.IO) {
        val cm = prefs.vn2000CentralMeridian; val (vx, vy) = CoordinateSystemConverter.wgs84ToVn2000(lat, lon, cm, prefs.vn2000ZoneDegrees)
        val user = getUserSession()
        val entity = NaturalImpactLogEntity(
            id = logId ?: 0L,
            officerName = officer, latitude = lat, longitude = lon, altitude = alt,
            vn2000X = vx, vn2000Y = vy, accuracy = acc, satellitesCount = sat,
            cause = cause, otherCause = otherCause, affectedArea = area,
            statusBefore = before, statusAfter = after, resourceDamage = damage,
            occurrenceTime = time, photoPath = photo, userEmail = user.email,
            displayColorHex = "#FFFBC02D"
        )
        if (logId != null) { db.naturalImpactLogDao().update(entity); logId } else db.naturalImpactLogDao().insert(entity)
    }

    suspend fun deletePatrolLog(id: Long) = withContext(Dispatchers.IO) { db.patrolLogDao().deleteById(id) }
    suspend fun deleteFloraFaunaLog(id: Long) = withContext(Dispatchers.IO) { db.floraFaunaLogDao().deleteById(id) }
    suspend fun deleteNaturalImpactLog(id: Long) = withContext(Dispatchers.IO) { db.naturalImpactLogDao().deleteById(id) }
    suspend fun updateWaypointColor(id: Long, color: String) = withContext(Dispatchers.IO) { val list = db.waypointDao().getAllWaypointsList(); val item = list.find { it.id == id }; if (item != null) db.waypointDao().update(item.copy(displayColorHex = color, isSynced = false)) }
    suspend fun updateWaypointTitle(id: Long, title: String) = withContext(Dispatchers.IO) { val list = db.waypointDao().getAllWaypointsList(); val item = list.find { it.id == id }; if (item != null) db.waypointDao().update(item.copy(title = title, isSynced = false)) }
    suspend fun updateTrackLogColor(id: Long, color: String) = withContext(Dispatchers.IO) { val item = db.trackLogDao().getById(id); if (item != null) db.trackLogDao().update(item.copy(displayColorHex = color, isSynced = false)) }
    suspend fun updateTrackLogTitle(id: Long, title: String) = withContext(Dispatchers.IO) { val item = db.trackLogDao().getById(id); if (item != null) db.trackLogDao().update(item.copy(title = title, isSynced = false)) }
    suspend fun updatePatrolLogColor(id: Long, color: String) = withContext(Dispatchers.IO) { val item = db.patrolLogDao().getById(id); if (item != null) db.patrolLogDao().update(item.copy(displayColorHex = color, isSynced = false)) }
    suspend fun updateFloraFaunaColor(id: Long, color: String) = withContext(Dispatchers.IO) { val item = db.floraFaunaLogDao().getById(id); if (item != null) db.floraFaunaLogDao().update(item.copy(displayColorHex = color, isSynced = false)) }
    suspend fun updateNaturalImpactColor(id: Long, color: String) = withContext(Dispatchers.IO) { val item = db.naturalImpactLogDao().getById(id); if (item != null) db.naturalImpactLogDao().update(item.copy(displayColorHex = color, isSynced = false)) }
    suspend fun updatePatrolLogTitle(id: Long, title: String) = withContext(Dispatchers.IO) { val item = db.patrolLogDao().getById(id); if (item != null) db.patrolLogDao().update(item.copy(incidentType = title, isSynced = false)) }
    suspend fun updatePolygonColor(id: Long, color: String) = withContext(Dispatchers.IO) { val item = db.polygonDao().getById(id); if (item != null) db.polygonDao().update(item.copy(displayColorHex = color, isSynced = false)) }
    suspend fun updatePolygonTitle(id: Long, title: String) = withContext(Dispatchers.IO) { val item = db.polygonDao().getById(id); if (item != null) db.polygonDao().update(item.copy(title = title, isSynced = false)) }
    suspend fun updateDailyJournalColor(id: Long, color: String) = withContext(Dispatchers.IO) { db.dailyJournalDao().updateColor(id, color) }
    suspend fun updateAllTracksColor(color: String) = withContext(Dispatchers.IO) { val list = db.trackLogDao().getAllTrackLogsList(); for (t in list) if (t.category == "GPX") db.trackLogDao().update(t.copy(displayColorHex = color, isSynced = false)) }
    suspend fun updateAllLinesColor(color: String) = withContext(Dispatchers.IO) { val list = db.trackLogDao().getAllTrackLogsList(); for (t in list) if (t.category == "LINE") db.trackLogDao().update(t.copy(displayColorHex = color, isSynced = false)) }
    suspend fun updateAllPolygonsColor(color: String) = withContext(Dispatchers.IO) { val list = db.polygonDao().getAllPolygonsList(); for (p in list) db.polygonDao().update(p.copy(displayColorHex = color, isSynced = false)) }
    suspend fun updateAllIncidentsColor(color: String) = withContext(Dispatchers.IO) { val list = db.patrolLogDao().getAllPatrolLogsList(); for (p in list) db.patrolLogDao().update(p.copy(displayColorHex = color, isSynced = false)) }
    suspend fun updateAllFloraFaunaColor(color: String) = withContext(Dispatchers.IO) { val list = db.floraFaunaLogDao().getAllLogsList(); for (l in list) db.floraFaunaLogDao().update(l.copy(displayColorHex = color, isSynced = false)) }
    suspend fun updateAllNaturalImpactColor(color: String) = withContext(Dispatchers.IO) { val list = db.naturalImpactLogDao().getAllLogsList(); for (l in list) db.naturalImpactLogDao().update(l.copy(displayColorHex = color, isSynced = false)) }
    suspend fun updateAllWaypointsColor(color: String) = withContext(Dispatchers.IO) { val wps = db.waypointDao().getAllWaypointsList(); for (wp in wps) if (wp.photoPath.isNullOrEmpty()) db.waypointDao().update(wp.copy(displayColorHex = color, isSynced = false)) }
    suspend fun updateAllImagesColor(color: String) = withContext(Dispatchers.IO) { val wps = db.waypointDao().getAllWaypointsList(); for (wp in wps) if (!wp.photoPath.isNullOrEmpty()) db.waypointDao().update(wp.copy(displayColorHex = color, isSynced = false)) }
    suspend fun savePolygon(title: String, description: String, points: List<GpsPoint>): Long = withContext(Dispatchers.IO) {
        val user = getUserSession()
        val cLat = points.map { it.latitude }.average()
        val cLon = points.map { it.longitude }.average()
        val cm = prefs.vn2000CentralMeridian
        val zd = prefs.vn2000ZoneDegrees
        val (vx, vy) = CoordinateSystemConverter.wgs84ToVn2000(cLat, cLon, cm, zd)
        val entity = PolygonEntity(
            title = title,
            description = description,
            pointsJson = formatTrackPointsToJson(points),
            areaSquareMeters = GisAreaCalculator.calculatePolygonArea(points),
            perimeterMeters = GisAreaCalculator.calculatePolygonPerimeter(points),
            centroidLat = cLat,
            centroidLon = cLon,
            centroidVn2000X = vx,
            centroidVn2000Y = vy,
            userEmail = user.email.ifEmpty { "nguyenbahung.ctdt@gmail.com" },
            displayColorHex = prefs.polygonBoundaryColor
        )
        db.polygonDao().insert(entity)
    }
    suspend fun deletePolygon(id: Long) = withContext(Dispatchers.IO) { db.polygonDao().deleteById(id) }
    suspend fun saveDailyJournal(date: String, content: String, notes: String, weather: String = "", team: String = "", compartment: String = "", color: String = "#FF1976D2"): Long = withContext(Dispatchers.IO) { val user = getUserSession(); val existing = db.dailyJournalDao().getByDate(date); val entity = DailyJournalEntity(id = existing?.id ?: 0L, dateStr = date, content = content, notes = notes, userEmail = user.email.ifEmpty { "nguyenbahung.ctdt@gmail.com" }, linkedDataJson = getLinkedDataSummaryForDate(date), weather = weather, patrolTeam = team, patrolCompartment = compartment, displayColorHex = color); if (existing != null) { db.dailyJournalDao().update(entity); existing.id } else db.dailyJournalDao().insert(entity) }
    suspend fun getDailyJournalById(id: Long) = db.dailyJournalDao().getById(id)
    suspend fun deleteDailyJournal(id: Long) = withContext(Dispatchers.IO) { db.dailyJournalDao().deleteById(id) }
    suspend fun queueEmailReport(subject: String, body: String, attachmentPath: String?) = withContext(Dispatchers.IO) { db.emailQueueDao().insert(EmailQueueEntity(recipientEmail = prefs.defaultRecipientEmail, senderEmail = getUserSession().email.ifEmpty { "nguyenbahung.ctdt@gmail.com" }, subject = subject, body = body, attachmentPath = attachmentPath, createdAtUtc = System.currentTimeMillis())) }
    suspend fun markEmailSent(id: Long) = withContext(Dispatchers.IO) { val list = db.emailQueueDao().getPendingEmailsList(); val item = list.find { it.id == id }; if (item != null) db.emailQueueDao().update(item.copy(isSent = true)) }
    suspend fun getLinkedDataSummaryForDate(date: String): String = withContext(Dispatchers.IO) {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val waypoints = db.waypointDao().getAllWaypointsList().filter { sdf.format(java.util.Date(it.timestampUtc)) == date }
        val tracks = db.trackLogDao().getAllTrackLogsList().filter { sdf.format(java.util.Date(it.startTimeUtc)) == date }
        val patrols = db.patrolLogDao().getAllPatrolLogsList().filter { sdf.format(java.util.Date(it.discoveryTimeUtc)) == date }
        val polygons = db.polygonDao().getAllPolygonsList().filter { sdf.format(java.util.Date(it.timestampUtc)) == date }
        val floraFauna = db.floraFaunaLogDao().getAllLogsList().filter { sdf.format(java.util.Date(it.timestampUtc)) == date }
        val impacts = db.naturalImpactLogDao().getAllLogsList().filter { sdf.format(java.util.Date(it.timestampUtc)) == date }

        val sb = StringBuilder()
        if (waypoints.isNotEmpty()) { sb.append("📍 ĐIỂM (${waypoints.size}):\n"); waypoints.forEach { sb.append("- ${it.title}\n") } }
        if (tracks.isNotEmpty()) { sb.append("\n\uD83D\uDEE3️ TRACKLOG (${tracks.size}):\n"); tracks.forEach { sb.append("- ${it.title}\n") } }
        if (polygons.isNotEmpty()) { sb.append("\n📐 VÙNG (${polygons.size}):\n"); polygons.forEach { sb.append("- ${it.title}\n") } }
        if (patrols.isNotEmpty()) { sb.append("\n📑 SỰ VỤ (${patrols.size}):\n"); patrols.forEach { sb.append("- ${it.incidentType}\n") } }
        if (floraFauna.isNotEmpty()) { sb.append("\n🌿 ĐỘNG THỰC VẬT (${floraFauna.size}):\n"); floraFauna.forEach { sb.append("- ${it.appearanceDescription}\n") } }
        if (impacts.isNotEmpty()) { sb.append("\n⚠️ TÁC ĐỘNG TN (${impacts.size}):\n"); impacts.forEach { sb.append("- ${if(it.cause=="Khác") it.otherCause else it.cause}\n") } }
        sb.toString()
    }
    suspend fun exportFilteredDailyReportPackage(waypoints: List<WaypointEntity>, tracks: List<TrackLogEntity>, patrols: List<PatrolLogEntity>, polygons: List<PolygonEntity>, date: String): FullReportPackage = withContext(Dispatchers.IO) {
        val ts = System.currentTimeMillis()
        val dir = getExportDirectory()
        val user = getUserSession()
        val report = File(dir, "BaoCao_${date.replace("/", "_")}_$ts".toNonAccent() + ".docx")
        
        val gpxs = tracks.map { val f = File(dir, "track_${it.id}".toNonAccent() + ".gpx"); GpxExporter.exportTrackLogToGpx(it, parseTrackPoints(it.pointsJson), f); f }
        val words = mutableListOf<File>()
        patrols.forEach { val f = File(dir, "patrol_${it.id}".toNonAccent() + ".docx"); WordExportHelper.exportPatrolLogToWord(context, it, prefs.vn2000CentralMeridian, user.unit, user.email, user.phoneNumber, f)?.let { words.add(it) } }
        
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val ffLogs = db.floraFaunaLogDao().getAllLogsList().filter { sdf.format(java.util.Date(it.timestampUtc)) == date.replace("/", "-") }
        ffLogs.forEach { val f = File(dir, "ff_${it.id}".toNonAccent() + ".docx"); WordExportHelper.exportFloraFaunaLogToWord(context, it, prefs.vn2000CentralMeridian, user.unit, user.email, user.phoneNumber, f)?.let { words.add(it) } }
        
        val niLogs = db.naturalImpactLogDao().getAllLogsList().filter { sdf.format(java.util.Date(it.timestampUtc)) == date.replace("/", "-") }
        niLogs.forEach { val f = File(dir, "ni_${it.id}".toNonAccent() + ".docx"); WordExportHelper.exportNaturalImpactLogToWord(context, it, prefs.vn2000CentralMeridian, user.unit, user.email, user.phoneNumber, f)?.let { words.add(it) } }

        val pGpxs = polygons.map { val f = File(dir, "vung_${it.id}".toNonAccent() + ".gpx"); GpxExporter.exportPolygonToGpx(it, f); f }
        
        val photoFiles = mutableListOf<File>()
        waypoints.mapNotNull { it.photoPath?.let { p -> val f = File(p); if (f.exists()) photoFiles.add(f) } }
        patrols.mapNotNull { it.photoPath?.split("|")?.forEach { p -> val f = File(p); if (f.exists()) photoFiles.add(f) } }
        ffLogs.mapNotNull { it.photoPath?.split("|")?.forEach { p -> val f = File(p); if (f.exists()) photoFiles.add(f) } }
        niLogs.mapNotNull { it.photoPath?.split("|")?.forEach { p -> val f = File(p); if (f.exists()) photoFiles.add(f) } }

        WordExportHelper.exportSummaryToWord(context, "BAO CAO THUC DIA", "Bao cao ngay $date\nCan bo: ${user.displayName}\nDon vi: ${user.unit}", report)
        
        val all = mutableListOf(report)
        all.addAll(gpxs); all.addAll(words); all.addAll(pGpxs); all.addAll(photoFiles)
        FullReportPackage(report, gpxs, photoFiles, all)
    }

    suspend fun exportFullDailyReportPackage(): FullReportPackage = withContext(Dispatchers.IO) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val start = getStartOfDayTimestamp()
        val waypoints = db.waypointDao().getAllWaypointsList().filter { it.timestampUtc >= start }
        val tracks = db.trackLogDao().getAllTrackLogsList().filter { it.startTimeUtc >= start }
        val patrols = db.patrolLogDao().getAllPatrolLogsList().filter { it.discoveryTimeUtc >= start }
        val ffLogs = db.floraFaunaLogDao().getAllLogsList().filter { it.timestampUtc >= start }
        val niLogs = db.naturalImpactLogDao().getAllLogsList().filter { it.timestampUtc >= start }
        val polygons = db.polygonDao().getAllPolygonsList().filter { it.timestampUtc >= start }
        
        val user = getUserSession()
        val ts = System.currentTimeMillis()
        val dir = getExportDirectory()
        val reportFile = File(dir, "BaoCao_TongHop_$ts".toNonAccent() + ".docx")
        
        val gpxs = mutableListOf<File>()
        tracks.forEach { val pts = parseTrackPoints(it.pointsJson); val f = File(dir, "track_${it.id}".toNonAccent() + ".gpx"); GpxExporter.exportTrackLogToGpx(it, pts, f); gpxs.add(f) }
        
        val wayGpx = File(dir, "diem_$ts".toNonAccent() + ".gpx")
        if (waypoints.isNotEmpty()) GpxExporter.exportWaypointsToGpx(waypoints, wayGpx)
        
        val words = mutableListOf<File>()
        patrols.forEach { val f = File(dir, "patrol_${it.id}".toNonAccent() + ".docx"); WordExportHelper.exportPatrolLogToWord(context, it, prefs.vn2000CentralMeridian, user.unit, user.email, user.phoneNumber, f)?.let { words.add(it) } }
        ffLogs.forEach { val f = File(dir, "ff_${it.id}".toNonAccent() + ".docx"); WordExportHelper.exportFloraFaunaLogToWord(context, it, prefs.vn2000CentralMeridian, user.unit, user.email, user.phoneNumber, f)?.let { words.add(it) } }
        niLogs.forEach { val f = File(dir, "ni_${it.id}".toNonAccent() + ".docx"); WordExportHelper.exportNaturalImpactLogToWord(context, it, prefs.vn2000CentralMeridian, user.unit, user.email, user.phoneNumber, f)?.let { words.add(it) } }

        val polyGpxs = mutableListOf<File>()
        polygons.forEach { val f = File(dir, "vung_${it.id}".toNonAccent() + ".gpx"); GpxExporter.exportPolygonToGpx(it, f); polyGpxs.add(f) }
        
        val photoFiles = mutableListOf<File>()
        waypoints.mapNotNull { it.photoPath?.let { p -> val f = File(p); if (f.exists()) photoFiles.add(f) } }
        patrols.mapNotNull { it.photoPath?.split("|")?.forEach { p -> val f = File(p); if (f.exists()) photoFiles.add(f) } }
        ffLogs.mapNotNull { it.photoPath?.split("|")?.forEach { p -> val f = File(p); if (f.exists()) photoFiles.add(f) } }
        niLogs.mapNotNull { it.photoPath?.split("|")?.forEach { p -> val f = File(p); if (f.exists()) photoFiles.add(f) } }

        WordExportHelper.exportSummaryToWord(context, "BAO CAO TONG HOP", "Bao cao tong hop ngay $today\nCan bo: ${user.displayName}\nDon vi: ${user.unit}", reportFile)
        
        val all = mutableListOf<File>(reportFile)
        if (wayGpx.exists()) all.add(wayGpx)
        all.addAll(gpxs); all.addAll(words); all.addAll(polyGpxs); all.addAll(photoFiles)
        FullReportPackage(reportFile, gpxs, photoFiles, all, ZipExporter.createZipFile(all, File(dir, "BaoCao_$today".toNonAccent() + ".zip").absolutePath))
    }
}

data class FullReportPackage(val summaryReportFile: File, val gpxTrackFiles: List<File>, val photoFiles: List<File>, val allAttachmentFiles: List<File>, val zipFile: File? = null)
