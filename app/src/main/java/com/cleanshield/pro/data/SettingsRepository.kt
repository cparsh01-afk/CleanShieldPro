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

    var dohProvider: String
        get() = prefs.getString(KEY_DOH_PROVIDER, PROVIDER_CLOUDFLARE) ?: PROVIDER_CLOUDFLARE
        set(value) = prefs.edit().putString(KEY_DOH_PROVIDER, value).apply()

    var customDohEndpoint: String
        get() = prefs.getString(KEY_CUSTOM_DOH, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_DOH, value.trim()).apply()

    fun getDohEndpoint(): String = when (dohProvider) {
        PROVIDER_QUAD9 -> QUAD9_ENDPOINT
        PROVIDER_CUSTOM -> customDohEndpoint
        else -> CLOUDFLARE_ENDPOINT
    }

    companion object {
        const val PROVIDER_CLOUDFLARE = "Cloudflare"
        const val PROVIDER_QUAD9 = "Quad9"
        const val PROVIDER_CUSTOM = "Custom"
        const val CLOUDFLARE_ENDPOINT = "https://cloudflare-dns.com/dns-query"
        const val QUAD9_ENDPOINT = "https://dns.quad9.net/dns-query"
        private const val KEY_DOH_PROVIDER = "doh_provider"
        private const val KEY_CUSTOM_DOH = "custom_doh_endpoint"
    }
}
