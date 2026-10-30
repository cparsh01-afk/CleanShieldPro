package com.cleanshield.pro.filter

import android.util.Log
import com.cleanshield.pro.data.FilterRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * Phase 3 Filter Engine
 * Decides whether a domain/request should be blocked.
 * Supports multiple lists + custom rules + whitelist.
 */
class FilterEngine(private val repository: FilterRepository) {

    // In-memory set for fast lookups
    private val blockedDomains = ConcurrentHashMap.newKeySet<String>()
    private val whitelistedDomains = ConcurrentHashMap.newKeySet<String>()
    private val customBlocked = ConcurrentHashMap.newKeySet<String>()

    init {
        loadFilters()
    }

    fun loadFilters() {
        blockedDomains.clear()
        whitelistedDomains.clear()
        customBlocked.clear()

        // Load built-in + downloaded lists
        repository.getBlockedDomains().forEach { blockedDomains.add(it.lowercase()) }
        repository.getWhitelistedDomains().forEach { whitelistedDomains.add(it.lowercase()) }
        repository.getCustomBlocked().forEach { customBlocked.add(it.lowercase()) }

        Log.i(TAG, "Loaded ${blockedDomains.size} blocked domains, " +
                "${whitelistedDomains.size} whitelist, ${customBlocked.size} custom")
    }

    /**
     * Main decision function
     * Returns true if the domain should be BLOCKED
     */
    fun shouldBlock(host: String): Boolean {
        val domain = host.lowercase().removePrefix("www.")

        // Whitelist always wins
        if (isWhitelisted(domain)) return false

        // Custom user blocks
        if (customBlocked.contains(domain) || matchesAny(customBlocked, domain)) {
            return true
        }

        // Main filter lists
        if (blockedDomains.contains(domain) || matchesAny(blockedDomains, domain)) {
            return true
        }

        return false
    }

    private fun isWhitelisted(domain: String): Boolean {
        return whitelistedDomains.contains(domain) || matchesAny(whitelistedDomains, domain)
    }

    private fun matchesAny(set: Set<String>, domain: String): Boolean {
        // Simple suffix matching for subdomains
        // e.g. blocking "doubleclick.net" also blocks "ad.doubleclick.net"
        for (entry in set) {
            if (domain == entry || domain.endsWith(".$entry")) {
                return true
            }
        }
        return false
    }

    fun addCustomBlock(domain: String) {
        customBlocked.add(domain.lowercase())
        repository.addCustomBlocked(domain)
    }

    fun addWhitelist(domain: String) {
        whitelistedDomains.add(domain.lowercase())
        repository.addWhitelist(domain)
    }

    fun reload() {
        loadFilters()
    }

    companion object {
        private const val TAG = "FilterEngine"
    }
}
