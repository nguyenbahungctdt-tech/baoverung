package com.baoverung.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.baoverung.app.data.local.UserPreferencesManager

class AutoGpxRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = UserPreferencesManager(context)
        val session = prefs.getUserSession()
        
        if (session.isLoggedIn && !session.isOfflineMode && session.autoGpx) {
            val lastActivity = prefs.lastGpxActivityTime
            val now = System.currentTimeMillis()
            
            // If idle for more than 1 hour (3600000 ms)
            if (now - lastActivity > 3600000L) {
                val serviceIntent = Intent(context, GpxTrackingService::class.java).apply {
                    action = GpxTrackingService.ACTION_START_TRACKING
                }
                androidx.core.app.NotificationCompat.Builder(context, "gpx_tracking_channel")
                    .setContentTitle("BVR Đại Thành")
                    .setContentText("Tự động kích hoạt lại giám sát GPX sau 1 giờ tạm dừng.")
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .setAutoCancel(true)
                
                try {
                    androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        // Schedule next check in 1 hour
        scheduleNextCheck(context)
    }

    companion object {
        fun scheduleNextCheck(context: Context) {
            val intent = Intent(context, AutoGpxRestartReceiver::class.java)
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context, 1001, intent, 
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val triggerAt = System.currentTimeMillis() + 3600000L // 1 hour
            
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
            } catch (e: Exception) {
                android.util.Log.e("AutoGpxRestart", "Could not schedule alarm: ${e.message}")
            }
        }
    }
}
