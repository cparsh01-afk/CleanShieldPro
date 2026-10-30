package com.cleanshield.pro.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.cleanshield.pro.vpn.CleanShieldVpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val preferences = context.getSharedPreferences("cleanshield_settings", Context.MODE_PRIVATE)
        val shouldStart = preferences.getBoolean("start_on_boot", false) &&
            preferences.getBoolean("protection_enabled", false)
        if (!shouldStart) return

        try {
            val serviceIntent = Intent(context, CleanShieldVpnService::class.java).apply {
                action = CleanShieldVpnService.ACTION_START
            }
            ContextCompat.startForegroundService(context, serviceIntent)
            Log.i("ParshBootReceiver", "Requested DNS protection startup after boot")
        } catch (error: Exception) {
            Log.e("ParshBootReceiver", "Could not request VPN startup after boot", error)
        }
    }
}
