package com.cleanshield.pro.data

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("cleanshield_settings", Context.MODE_PRIVATE)

    var isProtectionEnabled: Boolean
        get() = prefs.getBoolean("protection_enabled", false)
        set(value) = prefs.edit().putBoolean("protection_enabled", value).apply()

    var batterySaverMode: Boolean
        get() = prefs.getBoolean("battery_saver", false)
        set(value) = prefs.edit().putBoolean("battery_saver", value).apply()

    var blockTrackers: Boolean
        get() = prefs.getBoolean("block_trackers", true)
        set(value) = prefs.edit().putBoolean("block_trackers", value).apply()

    var blockAds: Boolean
        get() = prefs.getBoolean("block_ads", true)
        set(value) = prefs.edit().putBoolean("block_ads", value).apply()

    var startOnBoot: Boolean
        get() = prefs.getBoolean("start_on_boot", false)
        set(value) = prefs.edit().putBoolean("start_on_boot", value).apply()

    var aggressiveMode: Boolean
        get() = prefs.getBoolean("aggressive_mode", false)
        set(value) = prefs.edit().putBoolean("aggressive_mode", value).apply()
}
