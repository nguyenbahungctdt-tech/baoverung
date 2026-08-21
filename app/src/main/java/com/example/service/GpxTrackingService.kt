package com.baoverung.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.util.Log
import com.baoverung.app.data.local.UserPreferencesManager
import com.baoverung.app.data.model.GpsPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class GpxTrackingService : Service(), LocationListener {

    private val binder = LocalBinder()
    private var locationManager: LocationManager? = null
    private lateinit var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient
    private var locationCallback: com.google.android.gms.location.LocationCallback? = null
    private lateinit var prefs: UserPreferencesManager
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking

    private val _trackedPoints = MutableStateFlow<List<GpsPoint>>(emptyList())
    val trackedPoints: StateFlow<List<GpsPoint>> = _trackedPoints

    private val _currentLocation = MutableStateFlow<GpsPoint?>(null)
    val currentLocation: StateFlow<GpsPoint?> = _currentLocation

    private val _satellitesCount = MutableStateFlow(0)
    val satellitesCount: StateFlow<Int> = _satellitesCount
    
    private val _satellitesVisible = MutableStateFlow(0)
    val satellitesVisible: StateFlow<Int> = _satellitesVisible

    private val _satelliteDetails = MutableStateFlow<List<com.baoverung.app.data.model.SatelliteInfo>>(emptyList())
    val satelliteDetails: StateFlow<List<com.baoverung.app.data.model.SatelliteInfo>> = _satelliteDetails

    private var trackingStartTimeUtc: Long = 0
    private var lastNotificationUpdateTime: Long = 0

    inner class LocalBinder : Binder() {
        fun getService(): GpxTrackingService = this@GpxTrackingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        prefs = UserPreferencesManager(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        initLastKnownAndStartListening()
        
        // Removed IDLE timer as requested
    }

    private fun hasLocationPermission(): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineLocationGranted || coarseLocationGranted
    }

    private var gnssStatusCallback: GnssStatus.Callback? = null

    private fun registerGnssStatusCallback() {
        if (!hasLocationPermission()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (gnssStatusCallback == null) {
                gnssStatusCallback = object : GnssStatus.Callback() {
                    override fun onSatelliteStatusChanged(status: GnssStatus) {
                        val total = status.satelliteCount
                        var used = 0
                        val details = mutableListOf<com.baoverung.app.data.model.SatelliteInfo>()
                        for (i in 0 until total) {
                            val usedInFix = status.usedInFix(i)
                            if (usedInFix) {
                                used++
                            }
                            details.add(
                                com.baoverung.app.data.model.SatelliteInfo(
                                    svid = status.getSvid(i),
                                    constellationType = status.getConstellationType(i),
                                    azimuth = status.getAzimuthDegrees(i),
                                    elevation = status.getElevationDegrees(i),
                                    cn0DbHz = status.getCn0DbHz(i),
                                    usedInFix = usedInFix
                                )
                            )
                        }
                        _satellitesCount.value = used
                        _satellitesVisible.value = total
                        _satelliteDetails.value = details
                    }
                }
            }
            try {
                locationManager?.registerGnssStatusCallback(
                    gnssStatusCallback!!,
                    Handler(Looper.getMainLooper())
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun initLastKnownAndStartListening() {
        if (!hasLocationPermission()) return
        registerGnssStatusCallback()
        try {
            val lastGps = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNet = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val bestLoc = when {
                lastGps != null && lastNet != null -> if (lastGps.time > lastNet.time) lastGps else lastNet
                lastGps != null -> lastGps
                else -> lastNet
            }
            if (bestLoc != null) {
                onLocationChanged(bestLoc)
            }
            startLocationListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startLocationListening() {
        if (!hasLocationPermission()) return
        registerGnssStatusCallback()
        
        try {
            val interval = prefs.trackingIntervalSeconds * 1000L
            // Tối ưu hóa để bắt vị trí nhanh nhất và chính xác nhất cho lâm nghiệp
            val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, interval
            ).apply {
                setMinUpdateIntervalMillis(interval / 2) // Cập nhật nhanh theo cấu hình
                setMinUpdateDistanceMeters(0f)   // Lấy tất cả thay đổi nhỏ nhất
                setWaitForAccurateLocation(true) // Ưu tiên độ chính xác cho điểm đầu tiên
                setMaxUpdateDelayMillis(0L)      // Không trì hoãn, đẩy dữ liệu lên ngay
            }.build()

            locationCallback = object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                    locationResult.lastLocation?.let { onLocationChanged(it) }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            
            // Duy trì GPS provider song song để đảm bảo dữ liệu vệ tinh thô luôn sẵn sàng
            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START_TRACKING -> startTracking()
            ACTION_STOP_TRACKING -> stopTracking()
        }
        return START_STICKY
    }

    fun startTracking() {
        if (!hasLocationPermission()) return
        if (_isTracking.value) return
        _isTracking.value = true
        _trackedPoints.value = emptyList()
        trackingStartTimeUtc = System.currentTimeMillis()
        
        if (prefs.getUserSession().autoGpx) {
            prefs.autoGpxSessionStartTime = trackingStartTimeUtc
        }

        val notification = buildNotification("Đang ghi Tracklog thực địa...")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                Log.e("GpxTrackingService", "Foreground service start not allowed", e)
                _isTracking.value = false
                return
            } else {
                Log.e("GpxTrackingService", "Error starting foreground service", e)
                _isTracking.value = false
                return
            }
        }
        startLocationListening()
    }

    fun stopTracking() {
        if (!_isTracking.value) return
        _isTracking.value = false
        
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onLocationChanged(location: Location) {
        // TỐI ƯU HÓA CHO LÂM NGHIỆP:
        // 1. Ưu tiên tín hiệu GPS trong môi trường rừng rậm
        if (location.provider == LocationManager.NETWORK_PROVIDER && _currentLocation.value != null) {
            val last = _currentLocation.value!!
            if (last.accuracy < 15f && location.accuracy > 30f) {
                return // Bỏ qua vị trí mạng nếu GPS đang rất tốt
            }
        }

        // 2. Lọc bỏ các điểm nhảy vọt không thực tế (Jump Filter)
        val lastPoint = _currentLocation.value
        if (lastPoint != null) {
            val dist = FloatArray(1)
            Location.distanceBetween(lastPoint.latitude, lastPoint.longitude, location.latitude, location.longitude, dist)
            val timeDiffSec = (location.time - lastPoint.timestampUtc) / 1000.0
            if (timeDiffSec > 0) {
                val speedKmh = (dist[0] / timeDiffSec) * 3.6
                // Trong rừng đi bộ không quá 15km/h, nếu nhảy vọt > 50km/h thì bỏ qua (nhiễu đa đường truyền)
                if (speedKmh > 50.0 && location.accuracy > 20f) return 
            }
        }

        // 3. Chấp nhận sai số cao hơn dưới tán rừng (tới 60m) nhưng phải ổn định
        if (location.accuracy > 60f) return

        val satCountFromExtras = location.extras?.getInt("satellites") ?: 0
        if (satCountFromExtras > 0) {
            _satellitesCount.value = satCountFromExtras
        }

        val gpsPoint = GpsPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude - prefs.antennaHeight,
            speed = location.speed,
            accuracy = location.accuracy,
            satellitesCount = _satellitesCount.value,
            timestampUtc = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
        )

        _currentLocation.value = gpsPoint

        if (_isTracking.value) {
            // TỐI ƯU CHO TUẦN TRA LÂM NGHIỆP:
            // - Di chuyển > bộ lọc (thường 0.5-2m) hoặc đứng yên 1 phút có dịch chuyển nhẹ 5m
            val shouldSave = if (lastPoint == null) true else {
                val dist = FloatArray(1)
                Location.distanceBetween(lastPoint.latitude, lastPoint.longitude, gpsPoint.latitude, gpsPoint.longitude, dist)
                
                val timeDiff = gpsPoint.timestampUtc - lastPoint.timestampUtc
                val filterDist = prefs.gpsFilterDistance
                
                when {
                    dist[0] > filterDist && gpsPoint.accuracy < 50f -> true 
                    dist[0] > 15f -> true // Di chuyển lớn, bất kể sai số
                    timeDiff > 60000L && dist[0] > 5f -> true 
                    else -> false
                }
            }

            if (shouldSave) {
                _trackedPoints.value = _trackedPoints.value + gpsPoint
                prefs.lastGpxActivityTime = System.currentTimeMillis()
                updateNotification("Ghi nhận ${_trackedPoints.value.size} điểm | Sai số: ±${String.format("%.1f", location.accuracy)}m")
            } else {
                updateNotification("Đang đứng yên hoặc sai số quá lớn | ${_trackedPoints.value.size} điểm")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssStatusCallback != null) {
            try {
                locationManager?.unregisterGnssStatusCallback(gnssStatusCallback!!)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        try {
            locationManager?.removeUpdates(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ghi Tracklog - Bảo vệ rừng Đại Thành",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tracklog - Bảo vệ rừng Đại Thành")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdateTime < 10000L) return // Update every 10 seconds
        lastNotificationUpdateTime = now
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(content))
    }

    companion object {
        const val CHANNEL_ID = "gpx_tracking_channel"
        const val NOTIFICATION_ID = 9981
        const val ACTION_START_TRACKING = "ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "ACTION_STOP_TRACKING"
        const val ACTION_AUTO_CLOSE_APP = "com.baoverung.app.ACTION_AUTO_CLOSE_APP"
    }
}
