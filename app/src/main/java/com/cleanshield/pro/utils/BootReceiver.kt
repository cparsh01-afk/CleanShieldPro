package com.cleanshield.pro.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.cleanshield.pro.CleanShieldApp
import com.cleanshield.pro.vpn.CleanShieldVpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val settings = CleanShieldApp.instance.settingsRepository
            if (settings.startOnBoot && settings.isProtectionEnabled) {
                Log.i("BootReceiver", "Starting CleanShield on boot")
                val serviceIntent = Intent(context, CleanShieldVpnService::class.java).apply {
                    action = CleanShieldVpnService.ACTION_START
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
