package com.baoverung.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AutoGpxRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Logic to restart tracking if needed
        }
    }
}
