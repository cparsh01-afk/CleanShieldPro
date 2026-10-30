package com.cleanshield.pro.data

import android.content.Context
import android.content.SharedPreferences

/** Stores aggregate counters only; DNS names and query contents are not persisted. */
class StatsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("cleanshield_stats", Context.MODE_PRIVATE)

    fun recordQuery() = increment(KEY_QUERIES)
    fun recordBlocked() {
        increment(KEY_QUERIES)
        increment(KEY_BLOCKED)
    }
    fun recordResolverFailure() = increment(KEY_FAILURES)

    fun getBlockedCount(): Long = prefs.getLong(KEY_BLOCKED, 0L)
    fun getQueryCount(): Long = prefs.getLong(KEY_QUERIES, 0L)
    fun getResolverFailureCount(): Long = prefs.getLong(KEY_FAILURES, 0L)

    @Deprecated("Data savings are not measured by DNS blocking")
    fun addDataSaved(bytes: Long) = Unit

    @Deprecated("Data savings are not measured by DNS blocking")
    fun getDataSaved(): Long = 0L

    @Deprecated("Data savings are not measured by DNS blocking")
    fun formatDataSaved(): String = "Not measured"

    fun resetStats() {
        prefs.edit()
            .putLong(KEY_BLOCKED, 0L)
            .putLong(KEY_QUERIES, 0L)
            .putLong(KEY_FAILURES, 0L)
            .apply()
    }

    private fun increment(key: String) {
        synchronized(this) {
            prefs.edit().putLong(key, prefs.getLong(key, 0L) + 1L).apply()
        }
    }

    companion object {
        private const val KEY_BLOCKED = "blocked_count"
        private const val KEY_QUERIES = "query_count"
        private const val KEY_FAILURES = "resolver_failures"
    }
}
