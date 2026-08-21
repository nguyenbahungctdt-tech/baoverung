package com.baoverung.app.repository

import android.util.Log
import com.baoverung.app.data.local.entity.PatrolLogEntity
import com.baoverung.app.data.local.entity.TrackLogEntity
import com.baoverung.app.data.local.entity.WaypointEntity
import com.baoverung.app.data.local.entity.DailyJournalEntity
import com.baoverung.app.data.model.UserSession
import com.baoverung.app.gis.CoordinateSystemConverter
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class KeyValidationResult(
    val isValid: Boolean,
    val message: String? = null,
    val permissions: String = "FULL",
    val autoGpx: Boolean = false,
    val canSync: Boolean = true
)

class CloudSyncRepository {

    init {
        try {
            // Only try if Firebase is initialized
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            Log.e("CloudSync", "Firebase not initialized or persistence already set: ${e.message}")
        }
    }

    private fun jsonToList(json: String?): List<Map<String, Any>> {
        if (json.isNullOrEmpty()) return emptyList()
        val list = mutableListOf<Map<String, Any>>()
        try {
            val array = org.json.JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val map = mutableMapOf<String, Any>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = obj.get(key)
                }
                list.add(map)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun getDb() = try { FirebaseDatabase.getInstance() } catch (e: Exception) { null }
    private fun getDbRef() = getDb()?.reference
    private fun getStorage() = try { FirebaseStorage.getInstance() } catch (e: Exception) { null }

    private fun sanitizeKey(input: String): String {
        return input.trim().replace(".", "_").replace("#", "_")
            .replace("$", "_").replace("[", "_").replace("]", "_")
            .replace("/", "-")
    }

    private suspend fun uploadFile(localPath: String?, remotePath: String): String? {
        if (localPath.isNullOrEmpty()) return null
        val storage = getStorage() ?: return null
        return try {
            val file = File(localPath)
            if (file.exists()) {
                val ref = storage.reference.child(remotePath)
                ref.putFile(android.net.Uri.fromFile(file)).await()
                return ref.downloadUrl.await().toString()
            } else {
                Log.w("CloudSync", "File not found: $localPath")
                null
            }
        } catch (e: Exception) {
            Log.e("CloudSync", "Storage error: ${e.localizedMessage}")
            null
        }
    }

    suspend fun updatePersonnelInfo(user: UserSession) {
        try {
            val sOfficer = sanitizeKey(user.displayName)
            val data = mapOf(
                "name" to user.displayName,
                "phone" to user.phoneNumber,
                "email" to user.email,
                "unit" to user.unit,
                "department" to user.department,
                "registrationKey" to user.registrationKey,
                "lastActive" to System.currentTimeMillis(),
                "permissions" to user.permissions,
                "canSync" to user.canSync
            )
            getDbRef()?.child("GlobalOfficers")?.child(sOfficer)?.setValue(data)?.await()
        } catch (e: Exception) {
            Log.e("CloudSync", "Personnel update failed: ${e.localizedMessage}")
        }
    }

    suspend fun syncWaypoint(user: UserSession, wp: WaypointEntity, cm: Double, province: String, zone: Int): Boolean {
        if (user.isOfflineMode) return false
        return try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(wp.timestampUtc))
            val sUnit = sanitizeKey(user.unit)
            val sOfficer = sanitizeKey(user.displayName)
            val path = "Units/$sUnit/$sOfficer/Surveys/$dateStr/Waypoints/${wp.id}"

            val cloudPhotoUrl = if (!wp.photoPath.isNullOrEmpty()) {
                val fileName = "WP_${wp.id}_${System.currentTimeMillis()}.jpg"
                uploadFile(wp.photoPath, "SurveyMedia/$sUnit/$sOfficer/$dateStr/$fileName")
            } else null

            val data = mapOf(
                "id" to wp.id,
                "title" to wp.title,
                "description" to wp.description,
                "latitude" to wp.latitude,
                "longitude" to wp.longitude,
                "altitude" to wp.altitude,
                "vn2000X" to wp.vn2000X,
                "vn2000Y" to wp.vn2000Y,
                "coordSystem" to "VN2000 - $province (KTT $cm, Múi $zone)",
                "centralMeridian" to cm,
                "province" to province,
                "timestamp" to wp.timestampUtc,
                "photoUrl" to (cloudPhotoUrl ?: wp.photoPath),
                "displayColorHex" to wp.displayColorHex,
                "department" to user.department
            )

            getDbRef()?.child(path)?.setValue(data)?.await()
            updatePersonnelInfo(user)
            Log.d("CloudSync", "Synced WP ${wp.id}")
            true
        } catch (e: Exception) {
            Log.e("CloudSync", "WP sync error: ${e.localizedMessage}")
            false
        }
    }

    suspend fun syncTrack(user: UserSession, trk: TrackLogEntity, cm: Double, province: String, zone: Int): Boolean {
        if (user.isOfflineMode) return false
        return try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(trk.startTimeUtc))
            val sUnit = sanitizeKey(user.unit)
            val sOfficer = sanitizeKey(user.displayName)
            val path = "Units/$sUnit/$sOfficer/Surveys/$dateStr/Tracks/${trk.id}"

            val sampledList = jsonToList(trk.sampledPointsJson)
            val representativePoints = sampledList.map { pt ->
                val lat = pt["latitude"] as? Double ?: 0.0
                val lon = pt["longitude"] as? Double ?: 0.0
                val (vx, vy) = CoordinateSystemConverter.wgs84ToVn2000(lat, lon, cm, zone)
                mapOf(
                    "latitude" to lat,
                    "longitude" to lon,
                    "vn2000X" to vx,
                    "vn2000Y" to vy,
                    "altitude" to (pt["altitude"] as? Double ?: 0.0),
                    "timestamp" to (pt["timestampUtc"] as? Long ?: 0L)
                )
            }

            val data = mapOf(
                "id" to trk.id,
                "title" to trk.title,
                "startTime" to trk.startTimeUtc,
                "endTime" to trk.endTimeUtc,
                "distance" to trk.totalDistanceMeters,
                "pointsJson" to trk.pointsJson,
                "sampledPoints" to representativePoints,
                "coordSystem" to "VN2000 - $province (KTT $cm, Múi $zone)",
                "centralMeridian" to cm,
                "province" to province,
                "category" to trk.category,
                "displayColorHex" to trk.displayColorHex,
                "department" to user.department
            )

            getDbRef()?.child(path)?.setValue(data)?.await()
            updatePersonnelInfo(user)
            Log.d("CloudSync", "Synced Track ${trk.id}")
            true
        } catch (e: Exception) {
            Log.e("CloudSync", "Track sync error: ${e.localizedMessage}")
            false
        }
    }

    suspend fun syncPolygon(user: UserSession, p: com.baoverung.app.data.local.entity.PolygonEntity, cm: Double, province: String, zone: Int): Boolean {
        if (user.isOfflineMode) return false
        return try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(p.timestampUtc))
            val sUnit = sanitizeKey(user.unit)
            val sOfficer = sanitizeKey(user.displayName)
            val path = "Units/$sUnit/$sOfficer/Surveys/$dateStr/Polygons/${p.id}"

            val (cvx, cvy) = CoordinateSystemConverter.wgs84ToVn2000(p.centroidLat, p.centroidLon, cm, zone)

            val data = mapOf(
                "id" to p.id,
                "title" to p.title,
                "description" to p.description,
                "areaSquareMeters" to p.areaSquareMeters,
                "perimeterMeters" to p.perimeterMeters,
                "centroidLat" to p.centroidLat,
                "centroidLon" to p.centroidLon,
                "centroidVn2000X" to cvx,
                "centroidVn2000Y" to cvy,
                "pointsJson" to p.pointsJson,
                "coordSystem" to "VN2000 - $province (KTT $cm, Múi $zone)",
                "centralMeridian" to cm,
                "province" to province,
                "displayColorHex" to p.displayColorHex,
                "timestamp" to p.timestampUtc,
                "department" to user.department
            )

            getDbRef()?.child(path)?.setValue(data)?.await()
            updatePersonnelInfo(user)
            Log.d("CloudSync", "Synced Polygon ${p.id}")
            true
        } catch (e: Exception) {
            Log.e("CloudSync", "Polygon sync error: ${e.localizedMessage}")
            false
        }
    }

    suspend fun syncPatrol(user: UserSession, p: PatrolLogEntity, cm: Double, province: String, zone: Int): Boolean {
        if (user.isOfflineMode) return false
        return try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(p.discoveryTimeUtc))
            val sUnit = sanitizeKey(user.unit)
            val sOfficer = sanitizeKey(user.displayName)
            val path = "Units/$sUnit/$sOfficer/Surveys/$dateStr/Patrols/${p.id}"

            val cloudPhotoUrl = if (!p.photoPath.isNullOrEmpty()) {
                val fileName = "PL_${p.id}_${System.currentTimeMillis()}.jpg"
                uploadFile(p.photoPath, "SurveyMedia/$sUnit/$sOfficer/$dateStr/$fileName")
            } else null

            val data = mapOf(
                "id" to p.id,
                "incidentType" to p.incidentType,
                "discoveryTime" to p.discoveryTimeUtc,
                "latitude" to p.latitude,
                "longitude" to p.longitude,
                "altitude" to p.altitude,
                "vn2000X" to p.vn2000X,
                "vn2000Y" to p.vn2000Y,
                "coordSystem" to "VN2000 - $province (KTT $cm, Múi $zone)",
                "centralMeridian" to cm,
                "province" to province,
                "leader" to p.leaderName,
                "violationTime" to p.violationTime,
                "location" to p.violationLocation,
                "violatorName" to p.violatorName,
                "violatorPhone" to p.violatorPhone,
                "confiscatedTools" to p.confiscatedTools,
                "action" to p.onSiteAction,
                "onSiteRecordings" to p.onSiteRecordings,
                "notes" to p.notes,
                "photoUrl" to (cloudPhotoUrl ?: p.photoPath),
                "displayColorHex" to p.displayColorHex,
                "department" to user.department
            )

            getDbRef()?.child(path)?.setValue(data)?.await()
            updatePersonnelInfo(user)
            Log.d("CloudSync", "Synced Patrol ${p.id}")
            true
        } catch (e: Exception) {
            Log.e("CloudSync", "Patrol sync error: ${e.localizedMessage}")
            false
        }
    }

    suspend fun syncFloraFauna(user: UserSession, l: com.baoverung.app.data.local.entity.FloraFaunaLogEntity, cm: Double, province: String, zone: Int): Boolean {
        if (user.isOfflineMode) return false
        return try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(l.timestampUtc))
            val sUnit = sanitizeKey(user.unit)
            val sOfficer = sanitizeKey(user.displayName)
            val path = "Units/$sUnit/$sOfficer/Surveys/$dateStr/FloraFauna/${l.id}"

            val cloudPhotoUrl = if (!l.photoPath.isNullOrEmpty()) {
                val fileName = "FF_${l.id}_${System.currentTimeMillis()}.jpg"
                uploadFile(l.photoPath, "SurveyMedia/$sUnit/$sOfficer/$dateStr/$fileName")
            } else null

            val data = mapOf(
                "id" to l.id,
                "officerName" to l.officerName,
                "timestamp" to l.timestampUtc,
                "latitude" to l.latitude,
                "longitude" to l.longitude,
                "altitude" to l.altitude,
                "vn2000X" to l.vn2000X,
                "vn2000Y" to l.vn2000Y,
                "coordSystem" to "VN2000 - $province (KTT $cm, Múi $zone)",
                "centralMeridian" to cm,
                "province" to province,
                "appearanceDescription" to l.appearanceDescription,
                "features" to l.features,
                "count" to l.count,
                "habitatType" to l.habitatType,
                "temperature" to l.temperature,
                "humidity" to l.humidity,
                "canopyCover" to l.canopyCover,
                "surroundingPlants" to l.surroundingPlants,
                "specimens" to l.specimens,
                "photoUrl" to (cloudPhotoUrl ?: l.photoPath),
                "displayColorHex" to l.displayColorHex,
                "department" to user.department
            )

            getDbRef()?.child(path)?.setValue(data)?.await()
            updatePersonnelInfo(user)
            Log.d("CloudSync", "Synced FloraFauna ${l.id}")
            true
        } catch (e: Exception) {
            Log.e("CloudSync", "FloraFauna sync error: ${e.localizedMessage}")
            false
        }
    }

    suspend fun syncNaturalImpact(user: UserSession, l: com.baoverung.app.data.local.entity.NaturalImpactLogEntity, cm: Double, province: String, zone: Int): Boolean {
        if (user.isOfflineMode) return false
        return try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(l.timestampUtc))
            val sUnit = sanitizeKey(user.unit)
            val sOfficer = sanitizeKey(user.displayName)
            val path = "Units/$sUnit/$sOfficer/Surveys/$dateStr/NaturalImpacts/${l.id}"

            val cloudPhotoUrl = if (!l.photoPath.isNullOrEmpty()) {
                val fileName = "NI_${l.id}_${System.currentTimeMillis()}.jpg"
                uploadFile(l.photoPath, "SurveyMedia/$sUnit/$sOfficer/$dateStr/$fileName")
            } else null

            val data = mapOf(
                "id" to l.id,
                "officerName" to l.officerName,
                "timestamp" to l.timestampUtc,
                "latitude" to l.latitude,
                "longitude" to l.longitude,
                "altitude" to l.altitude,
                "vn2000X" to l.vn2000X,
                "vn2000Y" to l.vn2000Y,
                "coordSystem" to "VN2000 - $province (KTT $cm, Múi $zone)",
                "centralMeridian" to cm,
                "province" to province,
                "cause" to l.cause,
                "otherCause" to l.otherCause,
                "affectedArea" to l.affectedArea,
                "statusBefore" to l.statusBefore,
                "statusAfter" to l.statusAfter,
                "resourceDamage" to l.resourceDamage,
                "occurrenceTime" to l.occurrenceTime,
                "photoUrl" to (cloudPhotoUrl ?: l.photoPath),
                "displayColorHex" to l.displayColorHex,
                "department" to user.department
            )

            getDbRef()?.child(path)?.setValue(data)?.await()
            updatePersonnelInfo(user)
            Log.d("CloudSync", "Synced NaturalImpact ${l.id}")
            true
        } catch (e: Exception) {
            Log.e("CloudSync", "NaturalImpact sync error: ${e.localizedMessage}")
            false
        }
    }

    suspend fun syncDailyJournal(user: UserSession, journal: DailyJournalEntity, cm: Double, province: String, zone: Int): Boolean {
        if (user.isOfflineMode) return false
        return try {
            val sUnit = sanitizeKey(user.unit)
            val sOfficer = sanitizeKey(user.displayName)
            val path = "Units/$sUnit/$sOfficer/Surveys/${journal.dateStr}/DailyJournal"

            val data = mapOf(
                "id" to journal.id,
                "date" to journal.dateStr,
                "timestamp" to journal.timestampUtc,
                "content" to journal.content,
                "notes" to journal.notes,
                "weather" to journal.weather,
                "patrolTeam" to journal.patrolTeam,
                "patrolCompartment" to journal.patrolCompartment,
                "linkedData" to journal.linkedDataJson,
                "coordSystem" to "VN2000 - $province (KTT $cm, Múi $zone)",
                "centralMeridian" to cm,
                "province" to province,
                "department" to user.department
            )

            getDbRef()?.child(path)?.setValue(data)?.await()
            updatePersonnelInfo(user)
            Log.d("CloudSync", "Synced DailyJournal ${journal.dateStr}")
            true
        } catch (e: Exception) {
            Log.e("CloudSync", "Journal sync error: ${e.localizedMessage}")
            false
        }
    }

    suspend fun verifyActivationKey(
        key: String, 
        deviceId: String, 
        isLogin: Boolean = false,
        userInfo: Map<String, String>? = null
    ): KeyValidationResult {
        return try {
            val db = getDb() ?: return KeyValidationResult(false, "Firebase chưa được cấu hình!")
            val snapshot = try { db.reference.child("ActivationKeys").child(key).get().await() } catch(e: Exception) { null }
            if (snapshot == null || !snapshot.exists()) return KeyValidationResult(false, "Mã Key không tồn tại hoặc lỗi kết nối!")
            
            val expiry = snapshot.child("expiryTimestamp").value as? Long ?: 0L
            val active = snapshot.child("isActive").value as? Boolean ?: false
            val perms = snapshot.child("permissions").value as? String ?: "FULL"
            val boundId = snapshot.child("deviceId").value as? String ?: ""
            val autoGpx = snapshot.child("autoGpx").value as? Boolean ?: false
            val canSync = snapshot.child("canSync").value as? Boolean ?: true
            
            val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(expiry))
            if (!active) return KeyValidationResult(false, "Key bị khóa!")
            if (System.currentTimeMillis() > expiry) return KeyValidationResult(false, "Key hết hạn ($date)!")
            
            // --- STRICT MATCHING LOGIC ---
            if (isLogin && userInfo != null) {
                val rName = snapshot.child("registeredName").value as? String ?: ""
                val rEmail = snapshot.child("registeredEmail").value as? String ?: ""
                val rUnit = snapshot.child("registeredUnit").value as? String ?: ""
                val rDept = snapshot.child("registeredDept").value as? String ?: ""
                
                // If info already exists in DB (even if device not bound), it MUST match
                if (rName.isNotEmpty() && rName != userInfo["name"]) return KeyValidationResult(false, "Họ tên không khớp với thông tin đã đăng ký!")
                if (rEmail.isNotEmpty() && rEmail != userInfo["email"]) return KeyValidationResult(false, "Email không khớp với thông tin đã đăng ký!")
                if (rUnit.isNotEmpty() && rUnit != userInfo["unit"]) return KeyValidationResult(false, "Đơn vị không khớp với thông tin đã đăng ký!")
                if (rDept.isNotEmpty() && rDept != userInfo["dept"]) return KeyValidationResult(false, "Bộ phận không khớp với thông tin đã đăng ký!")
            }

            if (boundId.isEmpty()) { 
                if (isLogin && userInfo != null) {
                    val updates = mutableMapOf<String, Any>(
                        "deviceId" to deviceId,
                        "registeredName" to (userInfo["name"] ?: ""),
                        "registeredEmail" to (userInfo["email"] ?: ""),
                        "registeredPhone" to (userInfo["phone"] ?: ""),
                        "registeredUnit" to (userInfo["unit"] ?: ""),
                        "registeredDept" to (userInfo["dept"] ?: "")
                    )
                    db.reference.child("ActivationKeys").child(key).updateChildren(updates).await()

                    // Also add to GlobalOfficers immediately
                    val sOfficer = sanitizeKey(userInfo["name"] ?: "Unknown")
                    val officerData = mapOf(
                        "name" to (userInfo["name"] ?: ""),
                        "phone" to (userInfo["phone"] ?: ""),
                        "email" to (userInfo["email"] ?: ""),
                        "unit" to (userInfo["unit"] ?: ""),
                        "department" to (userInfo["dept"] ?: ""),
                        "registrationKey" to key,
                        "lastActive" to System.currentTimeMillis(),
                        "permissions" to perms,
                        "canSync" to canSync
                    )
                    db.reference.child("GlobalOfficers").child(sOfficer).setValue(officerData).await()

                    return KeyValidationResult(true, date, perms, autoGpx, canSync)
                } else if (isLogin) {
                    return KeyValidationResult(false, "Thiếu thông tin đăng ký!")
                } else {
                    return KeyValidationResult(false, "Key đã bị Admin reset. Vui lòng đăng nhập lại!")
                }
            } else {
                if (boundId != deviceId) {
                    return KeyValidationResult(false, "Mã Key này đã được kích hoạt trên thiết bị khác!")
                }
            }
            
            return KeyValidationResult(true, date, perms, autoGpx, canSync)
        } catch (e: Exception) { 
            Log.e("CloudSync", "Key verification error: ${e.localizedMessage}")
            KeyValidationResult(false, "Lỗi kết nối: ${e.localizedMessage}") 
        }
    }

    suspend fun clearAllCloudData(): Boolean {
        return try {
            val ref = getDbRef() ?: return false
            ref.child("Units").removeValue().await()
            ref.child("GlobalOfficers").removeValue().await()
            Log.d("CloudSync", "All cloud data cleared")
            true
        } catch (e: Exception) {
            Log.e("CloudSync", "Clear cloud data failed: ${e.localizedMessage}")
            false
        }
    }

    suspend fun createActivationKey(key: String, expiryTimestamp: Long, note: String): Boolean {
        return try {
            val db = getDb() ?: return false
            val data = mapOf(
                "expiryTimestamp" to expiryTimestamp,
                "isActive" to true,
                "permissions" to "FULL",
                "deviceId" to "",
                "note" to note
            )
            db.reference.child("ActivationKeys").child(key).setValue(data).await()
            true
        } catch (e: Exception) {
            Log.e("CloudSync", "Failed to create key $key: ${e.localizedMessage}")
            false
        }
    }
}
