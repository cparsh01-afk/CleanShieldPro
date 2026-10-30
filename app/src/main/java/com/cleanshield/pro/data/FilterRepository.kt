package com.cleanshield.pro.data

import android.content.Context
import android.content.SharedPreferences
import java.io.BufferedReader
import java.io.InputStreamReader

class FilterRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("cleanshield_filters", Context.MODE_PRIVATE)

    // Built-in starter list (Phase 3 will download full lists)
    private val builtInBlocked = listOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "pagead2.googlesyndication.com",
        "adservice.google.com",
        "ads.yahoo.com",
        "adsystem.com",
        "advertising.com",
        "adnxs.com",
        "facebook.com/tr",
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
        "advertising.com",
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
        val custom = prefs.getStringSet("extra_blocked", emptySet()) ?: emptySet()
        return (builtInBlocked + custom).toSet()
    }

    fun getWhitelistedDomains(): Set<String> {
        return prefs.getStringSet("whitelist", emptySet()) ?: emptySet()
    }

    fun getCustomBlocked(): Set<String> {
        return prefs.getStringSet("custom_blocked", emptySet()) ?: emptySet()
    }

    fun addCustomBlocked(domain: String) {
        val current = getCustomBlocked().toMutableSet()
        current.add(domain.lowercase())
        prefs.edit().putStringSet("custom_blocked", current).apply()
    }

    fun addWhitelist(domain: String) {
        val current = getWhitelistedDomains().toMutableSet()
        current.add(domain.lowercase())
        prefs.edit().putStringSet("whitelist", current).apply()
    }

    fun removeWhitelist(domain: String) {
        val current = getWhitelistedDomains().toMutableSet()
        current.remove(domain.lowercase())
        prefs.edit().putStringSet("whitelist", current).apply()
    }

    // Future Phase 3: download full EasyList / EasyPrivacy here
    fun updateRemoteLists() {
        // Placeholder for downloading and parsing filter lists
        // In full production this would use OkHttp to fetch:
        // https://easylist.to/easylist/easylist.txt
        // https://easylist.to/easylist/easyprivacy.txt
        // Then parse and store domains.
    }
}
