package com.baoverung.app.platform

import platform.Foundation.NSUserDefaults
import platform.UIKit.UIDevice

actual class PlatformSettings {
    private val userDefaults = NSUserDefaults.standardUserDefaults

    actual fun getString(key: String, defaultValue: String): String {
        return userDefaults.stringForKey(key) ?: defaultValue
    }

    actual fun putString(key: String, value: String) {
        userDefaults.setObject(value, key)
    }

    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return if (userDefaults.objectForKey(key) != null) userDefaults.boolForKey(key) else defaultValue
    }

    actual fun putBoolean(key: String, value: Boolean) {
        userDefaults.setBool(value, key)
    }

    actual fun getDeviceId(): String {
        return UIDevice.currentDevice.identifierForVendor?.UUIDString ?: "unknown_ios"
    }
}
