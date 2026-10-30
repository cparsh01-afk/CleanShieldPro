package com.cleanshield.pro

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.cleanshield.pro.data.FilterRepository
import com.cleanshield.pro.data.StatsRepository
import com.cleanshield.pro.data.SettingsRepository

class CleanShieldApp : Application() {

    lateinit var filterRepository: FilterRepository
        private set
    lateinit var statsRepository: StatsRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        filterRepository = FilterRepository(this)
        statsRepository = StatsRepository(this)
        settingsRepository = SettingsRepository(this)

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CleanShield Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when ad & tracker protection is active"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "cleanshield_protection"
        lateinit var instance: CleanShieldApp
            private set
    }
}
