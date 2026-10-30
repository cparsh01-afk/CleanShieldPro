package com.cleanshield.pro.data

import android.content.Context
import android.content.SharedPreferences

class StatsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("cleanshield_stats", Context.MODE_PRIVATE)

    fun incrementBlocked() {
        val current = prefs.getLong("blocked_count", 0L)
        prefs.edit().putLong("blocked_count", current + 1).apply()
    }

    fun getBlockedCount(): Long = prefs.getLong("blocked_count", 0L)

    fun addDataSaved(bytes: Long) {
        val current = prefs.getLong("data_saved", 0L)
        prefs.edit().putLong("data_saved", current + bytes).apply()
    }

    fun getDataSaved(): Long = prefs.getLong("data_saved", 0L)

    fun resetStats() {
        prefs.edit()
            .putLong("blocked_count", 0L)
            .putLong("data_saved", 0L)
            .apply()
    }

    fun formatDataSaved(): String {
        val bytes = getDataSaved()
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
        }
    }
}
