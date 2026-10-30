package com.cleanshield.pro.filter

import android.util.Log
import com.cleanshield.pro.data.FilterRepository
import java.net.IDN
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory domain filter. A whitelist entry always overrides a block rule.
 * Matching is exact or on a DNS label boundary (not an arbitrary substring).
 */
class FilterEngine(private val repository: FilterRepository) {
    private val blockedDomains = ConcurrentHashMap.newKeySet<String>()
    private val whitelistedDomains = ConcurrentHashMap.newKeySet<String>()
    private val customBlocked = ConcurrentHashMap.newKeySet<String>()

    init {
        loadFilters()
    }

    @Synchronized
    fun loadFilters() {
        blockedDomains.clear()
        whitelistedDomains.clear()
        customBlocked.clear()
        repository.getBlockedDomains().mapNotNull(::normalizeDomain).forEach(blockedDomains::add)
        repository.getWhitelistedDomains().mapNotNull(::normalizeDomain).forEach(whitelistedDomains::add)
        repository.getCustomBlocked().mapNotNull(::normalizeDomain).forEach(customBlocked::add)
        Log.i(TAG, "Loaded ${blockedDomains.size} block rules, ${whitelistedDomains.size} whitelist rules")
    }

    fun shouldBlock(host: String): Boolean {
        val domain = normalizeDomain(host) ?: return false
        if (matchesDomainOrParent(whitelistedDomains, domain)) return false
        return matchesDomainOrParent(customBlocked, domain) ||
            matchesDomainOrParent(blockedDomains, domain)
    }

    @Synchronized
    fun addCustomBlock(domain: String): Boolean {
        val normalized = normalizeDomain(domain) ?: return false
        customBlocked.add(normalized)
        repository.addCustomBlocked(normalized)
        return true
    }

    @Synchronized
    fun addWhitelist(domain: String): Boolean {
        val normalized = normalizeDomain(domain) ?: return false
        whitelistedDomains.add(normalized)
        repository.addWhitelist(normalized)
        return true
    }

    fun reload() = loadFilters()

    private fun matchesDomainOrParent(rules: Set<String>, domain: String): Boolean {
        var candidate = domain
        while (true) {
            if (rules.contains(candidate)) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }

    private fun normalizeDomain(input: String): String? {
        val trimmed = input.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (trimmed.isEmpty() || trimmed.contains('/') || trimmed.contains(':') || trimmed.contains('*')) return null
        return try {
            val ascii = IDN.toASCII(trimmed, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
            if (ascii.length > 253 || ascii.split('.').any { it.isEmpty() || it.length > 63 }) null else ascii
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    companion object {
        private const val TAG = "FilterEngine"
    }
}
