package com.baoverung.app.repository

import android.util.Log
import com.baoverung.app.data.local.entity.*
import com.baoverung.app.data.model.UserSession
import com.baoverung.app.gis.CoordinateSystemConverter
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONArray
import org.json.JSONObject

actual class CloudSyncRepository actual constructor() {

    init {
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            Log.e("CloudSync", "Firebase persistence error: ${e.message}")
        }
    }

    private fun jsonToList(json: String?): List<Map<String, Any>> {
        if (json.isNullOrEmpty()) return emptyList()
        val list = mutableListOf<Map<String, Any>>()
        try {
            val array = JSONArray(json)
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
            } else null
        } catch (e: Exception) {
            Log.e("CloudSync", "Storage error: ${e.localizedMessage}")
            null
        }
    }

    private suspend fun updatePersonnelInfo(user: UserSession) {
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

    actual suspend fun verifyActivationKey(
        key: String, 
        deviceId: String, 
        isLogin: Boolean,
        userInfo: Map<String, String>?
    ): KeyValidationResult {
        return try {
            val db = getDb() ?: return KeyValidationResult(false, "Firebase chưa được cấu hình!")
            val snapshot = try { db.reference.child("ActivationKeys").child(key).get().await() } catch(e: Exception) { null }
            if (snapshot == null || !snapshot.exists()) return KeyValidationResult(false, "Mã Key không tồn tại!")
            
            val expiry = snapshot.child("expiryTimestamp").value as? Long ?: 0L
            val active = snapshot.child("isActive").value as? Boolean ?: false
            val perms = snapshot.child("permissions").value as? String ?: "FULL"
            val boundId = snapshot.child("deviceId").value as? String ?: ""
            val autoGpx = snapshot.child("autoGpx").value as? Boolean ?: false
            val canSync = snapshot.child("canSync").value as? Boolean ?: true
            
            val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(expiry))
            if (!active) return KeyValidationResult(false, "Key bị khóa!")
            if (System.currentTimeMillis() > expiry) return KeyValidationResult(false, "Key hết hạn ($date)!")
            
            if (isLogin && userInfo != null) {
                val rName = snapshot.child("registeredName").value as? String ?: ""
                val rEmail = snapshot.child("registeredEmail").value as? String ?: ""
                val rUnit = snapshot.child("registeredUnit").value as? String ?: ""
                val rDept = snapshot.child("registeredDept").value as? String ?: ""
                
                if (rName.isNotEmpty() && rName != userInfo["name"]) return KeyValidationResult(false, "Họ tên không khớp!")
                if (rEmail.isNotEmpty() && rEmail != userInfo["email"]) return KeyValidationResult(false, "Email không khớp!")
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
                    return KeyValidationResult(true, date, perms, autoGpx, canSync)
                } else if (isLogin) return KeyValidationResult(false, "Thiếu thông tin đăng ký!")
            } else if (boundId != deviceId) {
                return KeyValidationResult(false, "Key đã được kích hoạt trên thiết bị khác!")
            }
            
            return KeyValidationResult(true, date, perms, autoGpx, canSync)
        } catch (e: Exception) { 
            KeyValidationResult(false, "Lỗi kết nối: ${e.localizedMessage}") 
        }
    }

    actual suspend fun syncWaypoint(user: UserSession, wp: WaypointEntity, cm: Double, province: String, zone: Int): Boolean {
        if (user.isOfflineMode) return false
        return try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(wp.timestampUtc))
            val sUnit = sanitizeKey(user.unit)
            val sOfficer = sanitizeKey(user.displayName)
            val path = "Units/$sUnit/$sOfficer/Surveys/$dateStr/Waypoints/${wp.id}"

            val cloudPhotoUrl = uploadFile(wp.photoPath, "SurveyMedia/$sUnit/$sOfficer/$dateStr/WP_${wp.id}.jpg")

            val data = mapOf(
                "id" to wp.id, "title" to wp.title, "latitude" to wp.latitude, "longitude" to wp.longitude,
                "altitude" to wp.altitude, "vn2000X" to wp.vn2000X, "vn2000Y" to wp.vn2000Y,
                "coordSystem" to "VN2000 - $province", "timestamp" to wp.timestampUtc,
                "photoUrl" to (cloudPhotoUrl ?: wp.photoPath)
            )

            getDbRef()?.child(path)?.setValue(data)?.await()
            updatePersonnelInfo(user)
            true
        } catch (e: Exception) { false }
    }

    actual suspend fun syncTrack(user: UserSession, trk: TrackLogEntity, cm: Double, province: String, zone: Int): Boolean {
        if (user.isOfflineMode) return false
        return try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(trk.startTimeUtc))
            val sUnit = sanitizeKey(user.unit)
            val sOfficer = sanitizeKey(user.displayName)
            val path = "Units/$sUnit/$sOfficer/Surveys/$dateStr/Tracks/${trk.id}"

            val data = mapOf(
                "id" to trk.id, "title" to trk.title, "startTime" to trk.startTimeUtc, "endTime" to trk.endTimeUtc,
                "distance" to trk.totalDistanceMeters, "pointsJson" to trk.pointsJson
            )

            getDbRef()?.child(path)?.setValue(data)?.await()
            updatePersonnelInfo(user)
            true
        } catch (e: Exception) { false }
    }

    actual suspend fun syncPolygon(user: UserSession, p: PolygonEntity, cm: Double, province: String, zone: Int): Boolean {
        if (user.isOfflineMode) return false
        return try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(p.timestampUtc))
            val sUnit = sanitizeKey(user.unit)
            val sOfficer = sanitizeKey(user.displayName)
            val path = "Units/$sUnit/$sOfficer/Surveys/$dateStr/Polygons/${p.id}"

            val data = mapOf(
                "id" to p.id, "title" to p.title, "areaSquareMeters" to p.areaSquareMeters,
                "centroidLat" to p.centroidLat, "centroidLon" to p.centroidLon, "pointsJson" to p.pointsJson
            )

            getDbRef()?.child(path)?.setValue(data)?.await()
            updatePersonnelInfo(user)
            true
        } catch (e: Exception) { false }
    }

    actual suspend fun syncPatrol(user: UserSession, p: PatrolLogEntity, cm: Double, province: String, zone: Int): Boolean {
        if (user.isOfflineMode) return false
        return try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(p.discoveryTimeUtc))
            val sUnit = sanitizeKey(user.unit)
            val sOfficer = sanitizeKey(user.displayName)
            val path = "Units/$sUnit/$sOfficer/Surveys/$dateStr/Patrols/${p.id}"

            val data = mapOf(
                "id" to p.id, "incidentType" to p.incidentType, "discoveryTime" to p.discoveryTimeUtc,
                "latitude" to p.latitude, "longitude" to p.longitude, "leader" to p.leaderName,
                "location" to p.violationLocation, "action" to p.onSiteAction
            )

            getDbRef()?.child(path)?.setValue(data)?.await()
            updatePersonnelInfo(user)
            true
        } catch (e: Exception) { false }
    }

    actual suspend fun syncFloraFauna(user: UserSession, l: FloraFaunaLogEntity, cm: Double, province: String, zone: Int): Boolean {
        if (user.isOfflineMode) return false
        return try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(l.timestampUtc))
            val sUnit = sanitizeKey(user.unit)
            val sOfficer = sanitizeKey(user.displayName)
            val path = "Units/$sUnit/$sOfficer/Surveys/$dateStr/FloraFauna/${l.id}"

            val data = mapOf(
                "id" to l.id, "officerName" to l.officerName, "timestamp" to l.timestampUtc,
                "latitude" to l.latitude, "longitude" to l.longitude, "description" to l.appearanceDescription
            )

            getDbRef()?.child(path)?.setValue(data)?.await()
            updatePersonnelInfo(user)
            true
        } catch (e: Exception) { false }
    }

    actual suspend fun syncNaturalImpact(user: UserSession, l: NaturalImpactLogEntity, cm: Double, province: String, zone: Int): Boolean {
        if (user.isOfflineMode) return false
        return try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(l.timestampUtc))
            val sUnit = sanitizeKey(user.unit)
            val sOfficer = sanitizeKey(user.displayName)
            val path = "Units/$sUnit/$sOfficer/Surveys/$dateStr/NaturalImpacts/${l.id}"

            val data = mapOf(
                "id" to l.id, "officerName" to l.officerName, "timestamp" to l.timestampUtc,
                "cause" to l.cause, "affectedArea" to l.affectedArea
            )

            getDbRef()?.child(path)?.setValue(data)?.await()
            updatePersonnelInfo(user)
            true
        } catch (e: Exception) { false }
    }

    actual suspend fun syncDailyJournal(user: UserSession, journal: DailyJournalEntity, cm: Double, province: String, zone: Int): Boolean {
        if (user.isOfflineMode) return false
        return try {
            val sUnit = sanitizeKey(user.unit)
            val sOfficer = sanitizeKey(user.displayName)
            val path = "Units/$sUnit/$sOfficer/Surveys/${journal.dateStr}/DailyJournal"

            val data = mapOf(
                "id" to journal.id, "date" to journal.dateStr, "content" to journal.content,
                "weather" to journal.weather, "patrolTeam" to journal.patrolTeam
            )

            getDbRef()?.child(path)?.setValue(data)?.await()
            updatePersonnelInfo(user)
            true
        } catch (e: Exception) { false }
    }
}
