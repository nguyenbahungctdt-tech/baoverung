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
import com.baoverung.app.data.model.GpsPoint
import com.baoverung.app.platform.PlatformSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class GpxTrackingService : Service(), LocationListener {

    private val binder = LocalBinder()
    private var locationManager: LocationManager? = null
    private lateinit var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient
    private var locationCallback: com.google.android.gms.location.LocationCallback? = null
    private lateinit var platformSettings: PlatformSettings
    
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
        platformSettings = PlatformSettings(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        initLastKnownAndStartListening()
    }

    private fun hasLocationPermission(): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineLocationGranted
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
                            if (usedInFix) used++
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
                locationManager?.registerGnssStatusCallback(gnssStatusCallback!!, Handler(Looper.getMainLooper()))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun initLastKnownAndStartListening() {
        if (!hasLocationPermission()) return
        registerGnssStatusCallback()
        try {
            val lastGps = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (lastGps != null) onLocationChanged(lastGps)
            startLocationListening()
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun startLocationListening() {
        if (!hasLocationPermission()) return
        
        try {
            val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 5000L
            ).build()

            locationCallback = object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                    locationResult.lastLocation?.let { onLocationChanged(it) }
                }
            }

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
        } catch (e: SecurityException) { e.printStackTrace() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_START_TRACKING) startTracking()
        else if (action == ACTION_STOP_TRACKING) stopTracking()
        return START_STICKY
    }

    fun startTracking() {
        if (!hasLocationPermission()) return
        if (_isTracking.value) return
        _isTracking.value = true
        _trackedPoints.value = emptyList()
        trackingStartTimeUtc = System.currentTimeMillis()

        val notification = buildNotification("Đang ghi Tracklog thực địa...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        startLocationListening()
    }

    fun stopTracking() {
        if (!_isTracking.value) return
        _isTracking.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onLocationChanged(location: Location) {
        val gpsPoint = GpsPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            speed = location.speed,
            accuracy = location.accuracy,
            satellitesCount = _satellitesCount.value,
            timestampUtc = location.time
        )

        _currentLocation.value = gpsPoint

        if (_isTracking.value) {
            _trackedPoints.value = _trackedPoints.value + gpsPoint
            updateNotification("Ghi nhận ${_trackedPoints.value.size} điểm | Sai số: ±${String.format("%.1f", location.accuracy)}m")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Ghi Tracklog - Bảo vệ rừng", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bảo vệ rừng")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdateTime < 10000L) return 
        lastNotificationUpdateTime = now
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(content))
    }

    companion object {
        const val CHANNEL_ID = "gpx_tracking_channel"
        const val NOTIFICATION_ID = 9981
        const val ACTION_START_TRACKING = "ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "ACTION_STOP_TRACKING"
    }
}
