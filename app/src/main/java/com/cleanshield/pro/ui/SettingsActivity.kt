package com.cleanshield.pro.ui

import android.os.Bundle
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import com.cleanshield.pro.CleanShieldApp
import com.cleanshield.pro.R

class SettingsActivity : AppCompatActivity() {

    private val settings by lazy { CleanShieldApp.instance.settingsRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Settings"

        val batterySaver = findViewById<Switch>(R.id.switchBatterySaver)
        val blockAds = findViewById<Switch>(R.id.switchBlockAds)
        val blockTrackers = findViewById<Switch>(R.id.switchBlockTrackers)
        val startOnBoot = findViewById<Switch>(R.id.switchStartOnBoot)
        val aggressive = findViewById<Switch>(R.id.switchAggressive)

        batterySaver.isChecked = settings.batterySaverMode
        blockAds.isChecked = settings.blockAds
        blockTrackers.isChecked = settings.blockTrackers
        startOnBoot.isChecked = settings.startOnBoot
        aggressive.isChecked = settings.aggressiveMode

        batterySaver.setOnCheckedChangeListener { _, isChecked ->
            settings.batterySaverMode = isChecked
        }
        blockAds.setOnCheckedChangeListener { _, isChecked ->
            settings.blockAds = isChecked
        }
        blockTrackers.setOnCheckedChangeListener { _, isChecked ->
            settings.blockTrackers = isChecked
        }
        startOnBoot.setOnCheckedChangeListener { _, isChecked ->
            settings.startOnBoot = isChecked
        }
        aggressive.setOnCheckedChangeListener { _, isChecked ->
            settings.aggressiveMode = isChecked
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
