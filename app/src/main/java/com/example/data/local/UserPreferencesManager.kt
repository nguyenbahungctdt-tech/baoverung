package com.baoverung.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.baoverung.app.data.model.UserSession

class UserPreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("vtool_prefs", Context.MODE_PRIVATE)

    fun getUserSession(): UserSession {
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        val email = prefs.getString(KEY_USER_EMAIL, "") ?: ""
        val name = prefs.getString(KEY_USER_NAME, "") ?: ""
        val photoUrl = prefs.getString(KEY_USER_PHOTO, "") ?: ""
        val userId = prefs.getString(KEY_USER_ID, "") ?: ""
        val phone = prefs.getString(KEY_USER_PHONE, "") ?: ""
        val unit = prefs.getString(KEY_USER_UNIT, "") ?: ""
        val dept = prefs.getString(KEY_USER_DEPT, "") ?: ""
        val expiry = prefs.getString(KEY_USER_EXPIRY, "") ?: ""
        val regKey = prefs.getString(KEY_USER_REG_KEY, "") ?: ""
        val perms = prefs.getString(KEY_USER_PERMS, "FULL") ?: "FULL"
        val autoGpx = prefs.getBoolean(KEY_USER_AUTO_GPX, false)
        val canSync = prefs.getBoolean(KEY_USER_CAN_SYNC, true)
        val isOffline = prefs.getBoolean(KEY_IS_OFFLINE_MODE, false)

        val session = UserSession(
            userId = userId,
            displayName = if (name.isEmpty() && email.isNotEmpty()) email.substringBefore("@") else name,
            email = email,
            phoneNumber = phone,
            unit = unit,
            department = dept,
            registrationKey = regKey,
            expiryDate = expiry,
            permissions = perms,
            photoUrl = photoUrl,
            autoGpx = autoGpx,
            canSync = canSync,
            isLoggedIn = isLoggedIn,
            isOfflineMode = isOffline
        )

        // Force logout if session is incomplete (common during Auto Backup restore)
        if (isLoggedIn && !session.isComplete()) {
            clearSession()
            return UserSession(isLoggedIn = false)
        }

        return session
    }

    fun saveUserSession(session: UserSession) {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, session.isLoggedIn)
            .putString(KEY_USER_EMAIL, session.email)
            .putString(KEY_USER_NAME, session.displayName)
            .putString(KEY_USER_PHOTO, session.photoUrl)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_USER_PHONE, session.phoneNumber)
            .putString(KEY_USER_UNIT, session.unit)
            .putString(KEY_USER_DEPT, session.department)
            .putString(KEY_USER_REG_KEY, session.registrationKey)
            .putString(KEY_USER_EXPIRY, session.expiryDate)
            .putString(KEY_USER_PERMS, session.permissions)
            .putBoolean(KEY_USER_AUTO_GPX, session.autoGpx)
            .putBoolean(KEY_USER_CAN_SYNC, session.canSync)
            .putBoolean(KEY_IS_OFFLINE_MODE, session.isOfflineMode)
            // Save as history for suggestions/auto-fill next time
            .putString(KEY_LAST_EMAIL, session.email)
            .putString(KEY_LAST_NAME, session.displayName)
            .putString(KEY_LAST_PHONE, session.phoneNumber)
            .putString(KEY_LAST_UNIT, session.unit)
            .putString(KEY_LAST_DEPT, session.department)
            .putString(KEY_LAST_KEY, session.registrationKey)
            .apply()
    }

    fun updatePermissions(perms: String) {
        prefs.edit().putString(KEY_USER_PERMS, perms).apply()
    }

    fun updateExpiryDate(date: String) {
        prefs.edit().putString(KEY_USER_EXPIRY, date).apply()
    }

    fun clearSession() {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_PHOTO)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_PHONE)
            .remove(KEY_USER_UNIT)
            .remove(KEY_USER_DEPT)
            .remove(KEY_USER_REG_KEY)
            .remove(KEY_USER_EXPIRY)
            .remove(KEY_USER_AUTO_GPX)
            .remove(KEY_IS_OFFLINE_MODE)
            .apply()
    }

    // VN2000 Settings
    var vn2000CentralMeridian: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_VN2000_CM, java.lang.Double.doubleToRawLongBits(107.75)))
        set(value) = prefs.edit().putLong(KEY_VN2000_CM, java.lang.Double.doubleToRawLongBits(value)).apply()

    var vn2000ZoneDegrees: Int
        get() = prefs.getInt(KEY_VN2000_ZONE, 3)
        set(value) = prefs.edit().putInt(KEY_VN2000_ZONE, value).apply()

    var vn2000ProvinceName: String
        get() = prefs.getString(KEY_VN2000_PROVINCE, "Lâm Đồng") ?: "Lâm Đồng"
        set(value) = prefs.edit().putString(KEY_VN2000_PROVINCE, value).apply()

    var activeCoordinateSystem: String
        get() = prefs.getString(KEY_ACTIVE_COORD_SYSTEM, "VN2000") ?: "VN2000"
        set(value) = prefs.edit().putString(KEY_ACTIVE_COORD_SYSTEM, value).apply()

    // Default Email Recipient
    var defaultRecipientEmail: String
        get() = prefs.getString(KEY_RECIPIENT_EMAIL, "nguyenbahung.ctdt@gmail.com") ?: "nguyenbahung.ctdt@gmail.com"
        set(value) = prefs.edit().putString(KEY_RECIPIENT_EMAIL, value).apply()

    // Map Source
    var selectedMapSource: String
        get() = prefs.getString(KEY_MAP_SOURCE, "Google Satellite") ?: "Google Satellite"
        set(value) = prefs.edit().putString(KEY_MAP_SOURCE, value).apply()

    // Tracking Interval (seconds)
    var trackingIntervalSeconds: Int
        get() = prefs.getInt(KEY_TRACKING_INTERVAL, 5)
        set(value) = prefs.edit().putInt(KEY_TRACKING_INTERVAL, value).apply()

    // Auto Email Report Enabled
    var isAutoEmailReportEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_EMAIL_REPORT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_EMAIL_REPORT, value).apply()

    // Last Auto Report Export Date (YYYY-MM-DD)
    var lastAutoReportExportDate: String
        get() = prefs.getString(KEY_LAST_AUTO_REPORT_DATE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_AUTO_REPORT_DATE, value).apply()

    var isDefaultLayerImported: Boolean
        get() = prefs.getBoolean(KEY_DEFAULT_LAYER_IMPORTED, false)
        set(value) = prefs.edit().putBoolean(KEY_DEFAULT_LAYER_IMPORTED, value).apply()

    var isDefaultSddLayerImported: Boolean
        get() = prefs.getBoolean(KEY_DEFAULT_SDD_LAYER_IMPORTED, false)
        set(value) = prefs.edit().putBoolean(KEY_DEFAULT_SDD_LAYER_IMPORTED, value).apply()

    // Persistent Map State
    var lastMapLat: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LAST_MAP_LAT, java.lang.Double.doubleToRawLongBits(11.9404)))
        set(value) = prefs.edit().putLong(KEY_LAST_MAP_LAT, java.lang.Double.doubleToRawLongBits(value)).apply()

    var lastMapLon: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LAST_MAP_LON, java.lang.Double.doubleToRawLongBits(108.4378)))
        set(value) = prefs.edit().putLong(KEY_LAST_MAP_LON, java.lang.Double.doubleToRawLongBits(value)).apply()

    var lastMapZoom: Float
        get() = prefs.getFloat(KEY_LAST_MAP_ZOOM, 16f)
        set(value) = prefs.edit().putFloat(KEY_LAST_MAP_ZOOM, value).apply()

    // --- NEW SETTINGS ---

    // Font Encoding
    var fontEncoding: String
        get() = prefs.getString(KEY_FONT_ENCODING, "TCVN3") ?: "TCVN3"
        set(value) = prefs.edit().putString(KEY_FONT_ENCODING, value).apply()

    // Map UI Controls
    var showViewAngle: Boolean
        get() = prefs.getBoolean(KEY_SHOW_VIEW_ANGLE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_VIEW_ANGLE, value).apply()

    var showViewLine: Boolean
        get() = prefs.getBoolean(KEY_SHOW_VIEW_LINE, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_VIEW_LINE, value).apply()

    var showMoveDirection: Boolean
        get() = prefs.getBoolean(KEY_SHOW_MOVE_DIRECTION, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_MOVE_DIRECTION, value).apply()

    var showMoveLine: Boolean
        get() = prefs.getBoolean(KEY_SHOW_MOVE_LINE, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_MOVE_LINE, value).apply()

    var showCompass: Boolean
        get() = prefs.getBoolean(KEY_SHOW_COMPASS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_COMPASS, value).apply()

    var showSatelliteInfo: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SATELLITE_INFO, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_SATELLITE_INFO, value).apply()

    var showZoomControls: Boolean
        get() = prefs.getBoolean(KEY_SHOW_ZOOM_CONTROLS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_ZOOM_CONTROLS, value).apply()

    var showRotationControls: Boolean
        get() = prefs.getBoolean(KEY_SHOW_ROTATION_CONTROLS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_ROTATION_CONTROLS, value).apply()

    var showZoomLevel: Boolean
        get() = prefs.getBoolean(KEY_SHOW_ZOOM_LEVEL, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_ZOOM_LEVEL, value).apply()

    var showMapCenter: Boolean
        get() = prefs.getBoolean(KEY_SHOW_MAP_CENTER, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_MAP_CENTER, value).apply()

    // Visibility Scopes
    var showLabelsGlobal: Boolean
        get() = prefs.getBoolean(KEY_SHOW_LABELS_GLOBAL, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_LABELS_GLOBAL, value).apply()

    var showImagesGlobal: Boolean
        get() = prefs.getBoolean(KEY_SHOW_IMAGES_GLOBAL, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_IMAGES_GLOBAL, value).apply()

    var showPointsGlobal: Boolean
        get() = prefs.getBoolean(KEY_SHOW_POINTS_GLOBAL, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_POINTS_GLOBAL, value).apply()

    var showTracklogsGlobal: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TRACKLOGS_GLOBAL, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_TRACKLOGS_GLOBAL, value).apply()

    var showLinesGlobal: Boolean
        get() = prefs.getBoolean(KEY_SHOW_LINES_GLOBAL, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_LINES_GLOBAL, value).apply()

    var showPolygonsGlobal: Boolean
        get() = prefs.getBoolean(KEY_SHOW_POLYGONS_GLOBAL, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_POLYGONS_GLOBAL, value).apply()

    var showIncidentsGlobal: Boolean
        get() = prefs.getBoolean(KEY_SHOW_INCIDENTS_GLOBAL, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_INCIDENTS_GLOBAL, value).apply()

    var showDailyJournalsGlobal: Boolean
        get() = prefs.getBoolean(KEY_SHOW_DAILY_JOURNALS_GLOBAL, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_DAILY_JOURNALS_GLOBAL, value).apply()

    var showFloraFaunaGlobal: Boolean
        get() = prefs.getBoolean(KEY_SHOW_FLORA_FAUNA_GLOBAL, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_FLORA_FAUNA_GLOBAL, value).apply()

    var showNaturalImpactGlobal: Boolean
        get() = prefs.getBoolean(KEY_SHOW_NATURAL_IMPACT_GLOBAL, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_NATURAL_IMPACT_GLOBAL, value).apply()

    // --- CATEGORY SETTINGS ---

    // 1. Hình ảnh
    var imageIconType: String
        get() = prefs.getString(KEY_IMAGE_ICON_TYPE, "camera") ?: "camera"
        set(value) = prefs.edit().putString(KEY_IMAGE_ICON_TYPE, value).apply()

    var imageIconSize: Int
        get() = prefs.getInt(KEY_IMAGE_ICON_SIZE, 40)
        set(value) = prefs.edit().putInt(KEY_IMAGE_ICON_SIZE, value).apply()

    var imageColor: String
        get() = prefs.getString(KEY_IMAGE_COLOR, "#FFD32F2F") ?: "#FFD32F2F"
        set(value) = prefs.edit().putString(KEY_IMAGE_COLOR, value).apply()

    var showImageLabels: Boolean
        get() = prefs.getBoolean(KEY_SHOW_IMAGE_LABELS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_IMAGE_LABELS, value).apply()

    var imageLabelSize: Int
        get() = prefs.getInt(KEY_IMAGE_LABEL_SIZE, 14)
        set(value) = prefs.edit().putInt(KEY_IMAGE_LABEL_SIZE, value).apply()

    var imageQuality: Int
        get() = prefs.getInt(KEY_IMAGE_QUALITY, 90)
        set(value) = prefs.edit().putInt(KEY_IMAGE_QUALITY, value).apply()

    var imageResize: Int
        get() = prefs.getInt(KEY_IMAGE_RESIZE, 2400)
        set(value) = prefs.edit().putInt(KEY_IMAGE_RESIZE, value).apply()

    // 2. Điểm
    var pointIconType: String
        get() = prefs.getString(KEY_POINT_ICON_TYPE, "star") ?: "star"
        set(value) = prefs.edit().putString(KEY_POINT_ICON_TYPE, value).apply()

    var pointIconSize: Int
        get() = prefs.getInt(KEY_POINT_ICON_SIZE, 40)
        set(value) = prefs.edit().putInt(KEY_POINT_ICON_SIZE, value).apply()

    var pointColor: String
        get() = prefs.getString(KEY_POINT_COLOR, "#FF1976D2") ?: "#FF1976D2"
        set(value) = prefs.edit().putString(KEY_POINT_COLOR, value).apply()

    var showPointLabels: Boolean
        get() = prefs.getBoolean(KEY_SHOW_POINT_LABELS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_POINT_LABELS, value).apply()

    var pointLabelSize: Int
        get() = prefs.getInt(KEY_POINT_LABEL_SIZE, 14)
        set(value) = prefs.edit().putInt(KEY_POINT_LABEL_SIZE, value).apply()

    // 3. Tracklog
    var tracklogColor: String
        get() = prefs.getString(KEY_TRACKLOG_COLOR, "#FFFF3D00") ?: "#FFFF3D00"
        set(value) = prefs.edit().putString(KEY_TRACKLOG_COLOR, value).apply()

    var tracklogWidth: Float
        get() = prefs.getFloat(KEY_TRACKLOG_WIDTH, 3f)
        set(value) = prefs.edit().putFloat(KEY_TRACKLOG_WIDTH, value).apply()

    var tracklogStyle: String
        get() = prefs.getString(KEY_TRACKLOG_STYLE, "solid") ?: "solid"
        set(value) = prefs.edit().putString(KEY_TRACKLOG_STYLE, value).apply()

    var showTracklogLabels: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TRACKLOG_LABELS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_TRACKLOG_LABELS, value).apply()

    var showTracklogValue: Boolean // Length
        get() = prefs.getBoolean(KEY_SHOW_TRACKLOG_VALUE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_TRACKLOG_VALUE, value).apply()

    var tracklogFontSize: Int
        get() = prefs.getInt(KEY_TRACKLOG_FONT_SIZE, 14)
        set(value) = prefs.edit().putInt(KEY_TRACKLOG_FONT_SIZE, value).apply()

    // 4. Đường (vệt)
    var lineColor: String
        get() = prefs.getString(KEY_LINE_COLOR, "#FF9C27B0") ?: "#FF9C27B0"
        set(value) = prefs.edit().putString(KEY_LINE_COLOR, value).apply()

    var lineWidth: Float
        get() = prefs.getFloat(KEY_LINE_WIDTH, 2f)
        set(value) = prefs.edit().putFloat(KEY_LINE_WIDTH, value).apply()

    var lineStyle: String
        get() = prefs.getString(KEY_LINE_STYLE, "solid") ?: "solid"
        set(value) = prefs.edit().putString(KEY_LINE_STYLE, value).apply()

    var showLineLabels: Boolean
        get() = prefs.getBoolean(KEY_SHOW_LINE_LABELS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_LINE_LABELS, value).apply()

    var showLineValue: Boolean // Length
        get() = prefs.getBoolean(KEY_SHOW_LINE_VALUE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_LINE_VALUE, value).apply()

    var lineFontSize: Int
        get() = prefs.getInt(KEY_LINE_FONT_SIZE, 14)
        set(value) = prefs.edit().putInt(KEY_LINE_FONT_SIZE, value).apply()

    // 5. Vùng
    var polygonBoundaryColor: String
        get() = prefs.getString(KEY_POLYGON_BOUNDARY_COLOR, "#FF1976D2") ?: "#FF1976D2"
        set(value) = prefs.edit().putString(KEY_POLYGON_BOUNDARY_COLOR, value).apply()

    var polygonFillColor: String
        get() = prefs.getString(KEY_POLYGON_FILL_COLOR, "#334CAF50") ?: "#334CAF50"
        set(value) = prefs.edit().putString(KEY_POLYGON_FILL_COLOR, value).apply()

    var polygonWidth: Float
        get() = prefs.getFloat(KEY_POLYGON_WIDTH, 2f)
        set(value) = prefs.edit().putFloat(KEY_POLYGON_WIDTH, value).apply()

    var polygonStyle: String
        get() = prefs.getString(KEY_POLYGON_STYLE, "solid") ?: "solid"
        set(value) = prefs.edit().putString(KEY_POLYGON_STYLE, value).apply()

    var showPolygonLabels: Boolean
        get() = prefs.getBoolean(KEY_SHOW_POLYGON_LABELS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_POLYGON_LABELS, value).apply()

    var showPolygonValue: Boolean // Area
        get() = prefs.getBoolean(KEY_SHOW_POLYGON_VALUE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_POLYGON_VALUE, value).apply()

    var polygonFontSize: Int
        get() = prefs.getInt(KEY_POLYGON_FONT_SIZE, 14)
        set(value) = prefs.edit().putInt(KEY_POLYGON_FONT_SIZE, value).apply()

    // 6. Nhật ký sự vụ
    var incidentColor: String
        get() = prefs.getString(KEY_INCIDENT_COLOR, "#FFD32F2F") ?: "#FFD32F2F"
        set(value) = prefs.edit().putString(KEY_INCIDENT_COLOR, value).apply()

    var incidentIconType: String
        get() = prefs.getString(KEY_INCIDENT_ICON_TYPE, "a4") ?: "a4"
        set(value) = prefs.edit().putString(KEY_INCIDENT_ICON_TYPE, value).apply()

    var incidentIconSize: Int
        get() = prefs.getInt(KEY_INCIDENT_ICON_SIZE, 40)
        set(value) = prefs.edit().putInt(KEY_INCIDENT_ICON_SIZE, value).apply()

    var showIncidentLabels: Boolean
        get() = prefs.getBoolean(KEY_SHOW_INCIDENT_LABELS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_INCIDENT_LABELS, value).apply()

    var incidentFontSize: Int
        get() = prefs.getInt(KEY_INCIDENT_FONT_SIZE, 14)
        set(value) = prefs.edit().putInt(KEY_INCIDENT_FONT_SIZE, value).apply()

    // 7. Động thực vật
    var floraFaunaColor: String
        get() = prefs.getString(KEY_FF_COLOR, "#FF2E7D32") ?: "#FF2E7D32"
        set(value) = prefs.edit().putString(KEY_FF_COLOR, value).apply()

    var floraFaunaIconType: String
        get() = prefs.getString(KEY_FF_ICON_TYPE, "forest") ?: "forest"
        set(value) = prefs.edit().putString(KEY_FF_ICON_TYPE, value).apply()

    var floraFaunaIconSize: Int
        get() = prefs.getInt(KEY_FF_ICON_SIZE, 40)
        set(value) = prefs.edit().putInt(KEY_FF_ICON_SIZE, value).apply()

    var showFloraFaunaLabels: Boolean
        get() = prefs.getBoolean(KEY_FF_SHOW_LABELS, true)
        set(value) = prefs.edit().putBoolean(KEY_FF_SHOW_LABELS, value).apply()

    var floraFaunaFontSize: Int
        get() = prefs.getInt(KEY_FF_FONT_SIZE, 14)
        set(value) = prefs.edit().putInt(KEY_FF_FONT_SIZE, value).apply()

    // 8. Tác động tự nhiên
    var naturalImpactColor: String
        get() = prefs.getString(KEY_NI_COLOR, "#FFFBC02D") ?: "#FFFBC02D"
        set(value) = prefs.edit().putString(KEY_NI_COLOR, value).apply()

    var naturalImpactIconType: String
        get() = prefs.getString(KEY_NI_ICON_TYPE, "warning") ?: "warning"
        set(value) = prefs.edit().putString(KEY_NI_ICON_TYPE, value).apply()

    var naturalImpactIconSize: Int
        get() = prefs.getInt(KEY_NI_ICON_SIZE, 40)
        set(value) = prefs.edit().putInt(KEY_NI_ICON_SIZE, value).apply()

    var showNaturalImpactLabels: Boolean
        get() = prefs.getBoolean(KEY_NI_SHOW_LABELS, true)
        set(value) = prefs.edit().putBoolean(KEY_NI_SHOW_LABELS, value).apply()

    var naturalImpactFontSize: Int
        get() = prefs.getInt(KEY_NI_FONT_SIZE, 14)
        set(value) = prefs.edit().putInt(KEY_NI_FONT_SIZE, value).apply()

    var landmarkIconType: String
        get() = prefs.getString(KEY_LANDMARK_ICON_TYPE, "flag") ?: "flag"
        set(value) = prefs.edit().putString(KEY_LANDMARK_ICON_TYPE, value).apply()

    var landmarkColor: String
        get() = prefs.getString(KEY_LANDMARK_COLOR, "#FFD84315") ?: "#FFD84315"
        set(value) = prefs.edit().putString(KEY_LANDMARK_COLOR, value).apply()

    var landmarkIconSize: Int
        get() = prefs.getInt(KEY_LANDMARK_ICON_SIZE, 60)
        set(value) = prefs.edit().putInt(KEY_LANDMARK_ICON_SIZE, value).apply()

    var showLandmarkLabels: Boolean
        get() = prefs.getBoolean(KEY_SHOW_LANDMARK_LABELS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_LANDMARK_LABELS, value).apply()

    var landmarkLabelSize: Int
        get() = prefs.getInt(KEY_LANDMARK_LABEL_SIZE, 18)
        set(value) = prefs.edit().putInt(KEY_LANDMARK_LABEL_SIZE, value).apply()
        
    var showLandmarkCode: Boolean
        get() = prefs.getBoolean(KEY_SHOW_LANDMARK_CODE, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_LANDMARK_CODE, value).apply()

    // Misc Settings
    var distanceUnit: String
        get() = prefs.getString(KEY_DISTANCE_UNIT, "Auto") ?: "Auto"
        set(value) = prefs.edit().putString(KEY_DISTANCE_UNIT, value).apply()

    var areaUnit: String
        get() = prefs.getString(KEY_AREA_UNIT, "Auto") ?: "Auto"
        set(value) = prefs.edit().putString(KEY_AREA_UNIT, value).apply()

    var gpsFilterDistance: Float
        get() = prefs.getFloat(KEY_GPS_FILTER_DISTANCE, 0.5f)
        set(value) = prefs.edit().putFloat(KEY_GPS_FILTER_DISTANCE, value).apply()

    var antennaHeight: Float
        get() = prefs.getFloat(KEY_ANTENNA_HEIGHT, 0.0f)
        set(value) = prefs.edit().putFloat(KEY_ANTENNA_HEIGHT, value).apply()

    var useAGps: Boolean
        get() = prefs.getBoolean(KEY_USE_AGPS, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_AGPS, value).apply()

    var longPressOnMapEnabled: Boolean
        get() = prefs.getBoolean(KEY_LONG_PRESS_MAP, true)
        set(value) = prefs.edit().putBoolean(KEY_LONG_PRESS_MAP, value).apply()

    var getAddressOnPress: Boolean
        get() = prefs.getBoolean(KEY_GET_ADDRESS_ON_PRESS, false)
        set(value) = prefs.edit().putBoolean(KEY_GET_ADDRESS_ON_PRESS, value).apply()

    var shakeToMoveMap: Boolean
        get() = prefs.getBoolean(KEY_SHAKE_TO_MOVE_MAP, false)
        set(value) = prefs.edit().putBoolean(KEY_SHAKE_TO_MOVE_MAP, value).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()

    var fixMbTilesDisplay: Boolean
        get() = prefs.getBoolean(KEY_FIX_MBTILES_DISPLAY, true)
        set(value) = prefs.edit().putBoolean(KEY_FIX_MBTILES_DISPLAY, value).apply()

    var clusterLandmarks: Boolean
        get() = prefs.getBoolean(KEY_CLUSTER_LANDMARKS, false)
        set(value) = prefs.edit().putBoolean(KEY_CLUSTER_LANDMARKS, value).apply()

    var quickCreateLandmark: Boolean
        get() = prefs.getBoolean(KEY_QUICK_CREATE_LANDMARK, false)
        set(value) = prefs.edit().putBoolean(KEY_QUICK_CREATE_LANDMARK, value).apply()

    // Incident Defaults
    var defaultIncidentLeader: String
        get() = prefs.getString(KEY_DEFAULT_INCIDENT_LEADER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEFAULT_INCIDENT_LEADER, value).apply()

    var defaultIncidentField: String
        get() = prefs.getString(KEY_DEFAULT_INCIDENT_FIELD, "Lâm nghiệp") ?: "Lâm nghiệp"
        set(value) = prefs.edit().putString(KEY_DEFAULT_INCIDENT_FIELD, value).apply()

    // Watermark Settings
    var watermarkShowInfo: Boolean
        get() = prefs.getBoolean(KEY_WM_SHOW_INFO, true)
        set(value) = prefs.edit().putBoolean(KEY_WM_SHOW_INFO, value).apply()

    var watermarkPosition: String
        get() = prefs.getString(KEY_WM_POSITION, "BOTTOM_LEFT") ?: "BOTTOM_LEFT"
        set(value) = prefs.edit().putString(KEY_WM_POSITION, value).apply()

    var watermarkShowOfficer: Boolean
        get() = prefs.getBoolean(KEY_WM_SHOW_OFFICER, true)
        set(value) = prefs.edit().putBoolean(KEY_WM_SHOW_OFFICER, value).apply()

    var watermarkShowTime: Boolean
        get() = prefs.getBoolean(KEY_WM_SHOW_TIME, true)
        set(value) = prefs.edit().putBoolean(KEY_WM_SHOW_TIME, value).apply()

    var watermarkShowAddress: Boolean
        get() = prefs.getBoolean(KEY_WM_SHOW_ADDRESS, true)
        set(value) = prefs.edit().putBoolean(KEY_WM_SHOW_ADDRESS, value).apply()

    var watermarkShowWgs84: Boolean
        get() = prefs.getBoolean(KEY_WM_SHOW_WGS84, true)
        set(value) = prefs.edit().putBoolean(KEY_WM_SHOW_WGS84, value).apply()

    var watermarkShowVn2000: Boolean
        get() = prefs.getBoolean(KEY_WM_SHOW_VN2000, true)
        set(value) = prefs.edit().putBoolean(KEY_WM_SHOW_VN2000, value).apply()

    var watermarkShowAltitude: Boolean
        get() = prefs.getBoolean(KEY_WM_SHOW_ALTITUDE, true)
        set(value) = prefs.edit().putBoolean(KEY_WM_SHOW_ALTITUDE, value).apply()

    var watermarkShowAccuracy: Boolean
        get() = prefs.getBoolean(KEY_WM_SHOW_ACCURACY, true)
        set(value) = prefs.edit().putBoolean(KEY_WM_SHOW_ACCURACY, value).apply()

    var watermarkLabelColor: Int
        get() = prefs.getInt(KEY_WM_LABEL_COLOR, android.graphics.Color.parseColor("#FFD700"))
        set(value) = prefs.edit().putInt(KEY_WM_LABEL_COLOR, value).apply()

    var watermarkLabelSize: Float
        get() = prefs.getFloat(KEY_WM_LABEL_SIZE, 11f)
        set(value) = prefs.edit().putFloat(KEY_WM_LABEL_SIZE, value).apply()

    fun getWatermarkSettings(): com.baoverung.app.util.WatermarkHelper.WatermarkSettings {
        return com.baoverung.app.util.WatermarkHelper.WatermarkSettings(
            showInfo = watermarkShowInfo,
            position = watermarkPosition,
            showOfficer = watermarkShowOfficer,
            showTime = watermarkShowTime,
            showAddress = watermarkShowAddress,
            showWgs84 = watermarkShowWgs84,
            showVn2000 = watermarkShowVn2000,
            showAltitude = watermarkShowAltitude,
            showAccuracy = watermarkShowAccuracy,
            labelColor = watermarkLabelColor,
            labelSize = watermarkLabelSize
        )
    }

    fun saveWatermarkSettings(s: com.baoverung.app.util.WatermarkHelper.WatermarkSettings) {
        prefs.edit()
            .putBoolean(KEY_WM_SHOW_INFO, s.showInfo)
            .putString(KEY_WM_POSITION, s.position)
            .putBoolean(KEY_WM_SHOW_OFFICER, s.showOfficer)
            .putBoolean(KEY_WM_SHOW_TIME, s.showTime)
            .putBoolean(KEY_WM_SHOW_ADDRESS, s.showAddress)
            .putBoolean(KEY_WM_SHOW_WGS84, s.showWgs84)
            .putBoolean(KEY_WM_SHOW_VN2000, s.showVn2000)
            .putBoolean(KEY_WM_SHOW_ALTITUDE, s.showAltitude)
            .putBoolean(KEY_WM_SHOW_ACCURACY, s.showAccuracy)
            .putInt(KEY_WM_LABEL_COLOR, s.labelColor)
            .putFloat(KEY_WM_LABEL_SIZE, s.labelSize)
            .apply()
    }

    // Last GPX Activity Time (Timestamp)
    var lastGpxActivityTime: Long
        get() = prefs.getLong(KEY_LAST_GPX_ACTIVITY, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_GPX_ACTIVITY, value).apply()

    var autoGpxSessionStartTime: Long
        get() = prefs.getLong(KEY_AUTO_GPX_START_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_AUTO_GPX_START_TIME, value).apply()

    companion object {
        private const val KEY_LAST_GPX_ACTIVITY = "last_gpx_activity"
        private const val KEY_AUTO_GPX_START_TIME = "auto_gpx_start_time"
        private const val KEY_WM_SHOW_INFO = "wm_show_info"

        private const val KEY_LAST_NAME = "last_name"
        private const val KEY_LAST_EMAIL = "last_email"
        private const val KEY_LAST_PHONE = "last_phone"
        private const val KEY_LAST_UNIT = "last_unit"
        private const val KEY_LAST_DEPT = "last_dept"
        private const val KEY_LAST_KEY = "last_key"

        private const val KEY_WM_POSITION = "wm_position"
        private const val KEY_WM_SHOW_OFFICER = "wm_show_officer"
        private const val KEY_WM_SHOW_TIME = "wm_show_time"
        private const val KEY_WM_SHOW_ADDRESS = "wm_show_address"
        private const val KEY_WM_SHOW_WGS84 = "wm_show_wgs84"
        private const val KEY_WM_SHOW_VN2000 = "wm_show_vn2000"
        private const val KEY_WM_SHOW_ALTITUDE = "wm_show_altitude"
        private const val KEY_WM_SHOW_ACCURACY = "wm_show_accuracy"
        private const val KEY_WM_LABEL_COLOR = "wm_label_color"
        private const val KEY_WM_LABEL_SIZE = "wm_label_size"

        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_PHOTO = "user_photo"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_USER_UNIT = "user_unit"
        private const val KEY_USER_DEPT = "user_dept"
        private const val KEY_USER_REG_KEY = "user_reg_key"
        private const val KEY_USER_EXPIRY = "user_expiry"
        private const val KEY_USER_PERMS = "user_perms"
        private const val KEY_USER_AUTO_GPX = "user_auto_gpx"
        private const val KEY_USER_CAN_SYNC = "user_can_sync"
        private const val KEY_IS_OFFLINE_MODE = "is_offline_mode"

        private const val KEY_VN2000_CM = "vn2000_cm"
        private const val KEY_VN2000_ZONE = "vn2000_zone"
        private const val KEY_VN2000_PROVINCE = "vn2000_province"
        private const val KEY_ACTIVE_COORD_SYSTEM = "active_coord_system"
        private const val KEY_RECIPIENT_EMAIL = "recipient_email"
        private const val KEY_MAP_SOURCE = "map_source"
        private const val KEY_TRACKING_INTERVAL = "tracking_interval"
        private const val KEY_AUTO_EMAIL_REPORT = "auto_email_report"
        private const val KEY_LAST_AUTO_REPORT_DATE = "last_auto_report_date"
        private const val KEY_DEFAULT_LAYER_IMPORTED = "default_layer_imported"
        private const val KEY_DEFAULT_SDD_LAYER_IMPORTED = "default_sdd_layer_imported"

        private const val KEY_LAST_MAP_LAT = "last_map_lat"
        private const val KEY_LAST_MAP_LON = "last_map_lon"
        private const val KEY_LAST_MAP_ZOOM = "last_map_zoom"

        // New Settings Keys
        private const val KEY_FONT_ENCODING = "font_encoding"
        private const val KEY_SHOW_VIEW_ANGLE = "show_view_angle"
        private const val KEY_SHOW_VIEW_LINE = "show_view_line"
        private const val KEY_SHOW_MOVE_DIRECTION = "show_move_direction"
        private const val KEY_SHOW_MOVE_LINE = "show_move_line"
        private const val KEY_SHOW_COMPASS = "show_compass"
        private const val KEY_SHOW_SATELLITE_INFO = "show_satellite_info"
        private const val KEY_SHOW_ZOOM_CONTROLS = "show_zoom_controls"
        private const val KEY_SHOW_ROTATION_CONTROLS = "show_rotation_controls"
        private const val KEY_SHOW_ZOOM_LEVEL = "show_zoom_level"
        private const val KEY_SHOW_MAP_CENTER = "show_map_center"
        
        private const val KEY_SHOW_LABELS_GLOBAL = "show_labels_global"
        private const val KEY_SHOW_IMAGES_GLOBAL = "show_images_global"
        private const val KEY_SHOW_POINTS_GLOBAL = "show_points_global"
        private const val KEY_SHOW_TRACKLOGS_GLOBAL = "show_tracklogs_global"
        private const val KEY_SHOW_LINES_GLOBAL = "show_lines_global"
        private const val KEY_SHOW_POLYGONS_GLOBAL = "show_polygons_global"
        private const val KEY_SHOW_INCIDENTS_GLOBAL = "show_incidents_global"
        private const val KEY_SHOW_DAILY_JOURNALS_GLOBAL = "show_daily_journals_global"
        private const val KEY_SHOW_FLORA_FAUNA_GLOBAL = "show_flora_fauna_global"
        private const val KEY_SHOW_NATURAL_IMPACT_GLOBAL = "show_natural_impact_global"

        private const val KEY_IMAGE_ICON_TYPE = "image_icon_type"
        private const val KEY_IMAGE_ICON_SIZE = "image_icon_size"
        private const val KEY_IMAGE_COLOR = "image_color"
        private const val KEY_SHOW_IMAGE_LABELS = "show_image_labels"
        private const val KEY_IMAGE_LABEL_SIZE = "image_label_size"
        private const val KEY_IMAGE_QUALITY = "image_quality"
        private const val KEY_IMAGE_RESIZE = "image_resize"

        private const val KEY_POINT_ICON_TYPE = "point_icon_type"
        private const val KEY_POINT_ICON_SIZE = "point_icon_size"
        private const val KEY_POINT_COLOR = "point_color"
        private const val KEY_SHOW_POINT_LABELS = "show_point_labels"
        private const val KEY_POINT_LABEL_SIZE = "point_label_size"

        private const val KEY_TRACKLOG_COLOR = "tracklog_color"
        private const val KEY_TRACKLOG_WIDTH = "tracklog_width"
        private const val KEY_TRACKLOG_STYLE = "tracklog_style"
        private const val KEY_SHOW_TRACKLOG_LABELS = "show_tracklog_labels"
        private const val KEY_SHOW_TRACKLOG_VALUE = "show_tracklog_value"
        private const val KEY_TRACKLOG_FONT_SIZE = "tracklog_font_size"

        private const val KEY_LINE_COLOR = "line_color"
        private const val KEY_LINE_WIDTH = "line_width"
        private const val KEY_LINE_STYLE = "line_style"
        private const val KEY_SHOW_LINE_LABELS = "show_line_labels"
        private const val KEY_SHOW_LINE_VALUE = "show_line_value"
        private const val KEY_LINE_FONT_SIZE = "line_font_size"

        private const val KEY_POLYGON_BOUNDARY_COLOR = "polygon_boundary_color"
        private const val KEY_POLYGON_FILL_COLOR = "polygon_fill_color"
        private const val KEY_POLYGON_WIDTH = "polygon_width"
        private const val KEY_POLYGON_STYLE = "polygon_style"
        private const val KEY_SHOW_POLYGON_LABELS = "show_polygon_labels"
        private const val KEY_SHOW_POLYGON_VALUE = "show_polygon_value"
        private const val KEY_POLYGON_FONT_SIZE = "polygon_font_size"

        private const val KEY_INCIDENT_COLOR = "incident_color"
        private const val KEY_INCIDENT_ICON_TYPE = "incident_icon_type"
        private const val KEY_INCIDENT_ICON_SIZE = "incident_icon_size"
        private const val KEY_SHOW_INCIDENT_LABELS = "show_incident_labels"
        private const val KEY_INCIDENT_FONT_SIZE = "incident_font_size"

        private const val KEY_FF_COLOR = "ff_color"
        private const val KEY_FF_ICON_TYPE = "ff_icon_type"
        private const val KEY_FF_ICON_SIZE = "ff_icon_size"
        private const val KEY_FF_SHOW_LABELS = "ff_show_labels"
        private const val KEY_FF_FONT_SIZE = "ff_font_size"

        private const val KEY_NI_COLOR = "ni_color"
        private const val KEY_NI_ICON_TYPE = "ni_icon_type"
        private const val KEY_NI_ICON_SIZE = "ni_icon_size"
        private const val KEY_NI_SHOW_LABELS = "ni_show_labels"
        private const val KEY_NI_FONT_SIZE = "ni_font_size"

        private const val KEY_LANDMARK_ICON_TYPE = "landmark_icon_type"
        private const val KEY_LANDMARK_COLOR = "landmark_color"
        private const val KEY_LANDMARK_ICON_SIZE = "landmark_icon_size"
        private const val KEY_SHOW_LANDMARK_LABELS = "show_landmark_labels"
        private const val KEY_LANDMARK_LABEL_SIZE = "landmark_label_size"
        private const val KEY_SHOW_LANDMARK_CODE = "show_landmark_code"

        private const val KEY_DISTANCE_UNIT = "distance_unit"
        private const val KEY_AREA_UNIT = "area_unit"
        private const val KEY_GPS_FILTER_DISTANCE = "gps_filter_distance"
        private const val KEY_ANTENNA_HEIGHT = "antenna_height"
        private const val KEY_USE_AGPS = "use_agps"
        private const val KEY_LONG_PRESS_MAP = "long_press_map"
        private const val KEY_GET_ADDRESS_ON_PRESS = "get_address_on_press"
        private const val KEY_SHAKE_TO_MOVE_MAP = "shake_to_move_map"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_FIX_MBTILES_DISPLAY = "fix_mbtiles_display"
        private const val KEY_CLUSTER_LANDMARKS = "cluster_landmarks"
        private const val KEY_QUICK_CREATE_LANDMARK = "quick_create_landmark"

        private const val KEY_DEFAULT_LINE_STYLE = "default_line_style"
        private const val KEY_DEFAULT_INCIDENT_LEADER = "default_incident_leader"
        private const val KEY_DEFAULT_INCIDENT_FIELD = "default_incident_field"
    }
}
