package com.cleanshield.pro.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Local filter rules. No browsing history or DNS payloads are uploaded.
 * Remote-list synchronization is intentionally not advertised until implemented.
 */
class FilterRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("cleanshield_filters", Context.MODE_PRIVATE)

    private val builtInBlocked = setOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "pagead2.googlesyndication.com",
        "adservice.google.com",
        "ads.yahoo.com",
        "adsystem.com",
        "advertising.com",
        "adnxs.com",
        "connect.facebook.net",
        "scorecardresearch.com",
        "quantserve.com",
        "outbrain.com",
        "taboola.com",
        "criteo.com",
        "amazon-adsystem.com",
        "moatads.com",
        "adsafeprotected.com",
        "2mdn.net",
        "adform.net",
        "adition.com",
        "admob.com",
        "adsrvr.org",
        "adtech.de",
        "app-adforce.jp",
        "bidswitch.net",
        "casalemedia.com",
        "contextweb.com",
        "creativecdn.com",
        "demdex.net",
        "everesttech.net",
        "exelator.com",
        "google-analytics.com",
        "googletagmanager.com",
        "googletagservices.com",
        "hotjar.com",
        "krxd.net",
        "lijit.com",
        "mathtag.com",
        "media.net",
        "mediavine.com",
        "mgid.com",
        "openx.net",
        "pubmatic.com",
        "rfihub.com",
        "rubiconproject.com",
        "sharethrough.com",
        "sizmek.com",
        "smartadserver.com",
        "spotxchange.com",
        "stickyadstv.com",
        "tapad.com",
        "teads.tv",
        "tremorhub.com",
        "tribalfusion.com",
        "turn.com",
        "yieldmo.com",
        "zedo.com"
    )

    fun getBlockedDomains(): Set<String> {
        val custom = prefs.getStringSet(KEY_EXTRA_BLOCKED, emptySet()) ?: emptySet()
        return builtInBlocked + custom
    }

    fun getWhitelistedDomains(): Set<String> =
        prefs.getStringSet(KEY_WHITELIST, emptySet())?.toSet() ?: emptySet()

    fun getCustomBlocked(): Set<String> =
        prefs.getStringSet(KEY_CUSTOM_BLOCKED, emptySet())?.toSet() ?: emptySet()

    fun addCustomBlocked(domain: String) {
        updateSet(KEY_CUSTOM_BLOCKED) { it.add(domain.lowercase()) }
    }

    fun removeCustomBlocked(domain: String) {
        updateSet(KEY_CUSTOM_BLOCKED) { it.remove(domain.lowercase()) }
    }

    fun addWhitelist(domain: String) {
        updateSet(KEY_WHITELIST) { it.add(domain.lowercase()) }
    }

    fun removeWhitelist(domain: String) {
        updateSet(KEY_WHITELIST) { it.remove(domain.lowercase()) }
    }

    private fun updateSet(key: String, change: (MutableSet<String>) -> Unit) {
        val current = (prefs.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()
        change(current)
        prefs.edit().putStringSet(key, current).apply()
    }

    companion object {
        private const val KEY_EXTRA_BLOCKED = "extra_blocked"
        private const val KEY_WHITELIST = "whitelist"
        private const val KEY_CUSTOM_BLOCKED = "custom_blocked"
    }
}
