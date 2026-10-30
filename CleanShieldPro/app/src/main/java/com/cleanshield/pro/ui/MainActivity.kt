package com.cleanshield.pro.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cleanshield.pro.CleanShieldApp
import com.cleanshield.pro.R
import com.cleanshield.pro.vpn.CleanShieldVpnService

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var blockedCountText: TextView
    private lateinit var dataSavedText: TextView
    private lateinit var toggleButton: Button
    private lateinit var settingsButton: Button

    private val settings by lazy { CleanShieldApp.instance.settingsRepository }
    private val stats by lazy { CleanShieldApp.instance.statsRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        blockedCountText = findViewById(R.id.blockedCountText)
        dataSavedText = findViewById(R.id.dataSavedText)
        toggleButton = findViewById(R.id.toggleButton)
        settingsButton = findViewById(R.id.settingsButton)

        toggleButton.setOnClickListener {
            if (settings.isProtectionEnabled) {
                stopProtection()
            } else {
                requestVpnPermission()
            }
        }

        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        val active = settings.isProtectionEnabled
        statusText.text = if (active) "Protection: ON" else "Protection: OFF"
        statusText.setTextColor(
            ContextCompat.getColor(
                this,
                if (active) android.R.color.holo_green_dark else android.R.color.holo_red_dark
            )
        )
        toggleButton.text = if (active) "Stop Protection" else "Start Protection"
        blockedCountText.text = "Blocked: ${stats.getBlockedCount()}"
        dataSavedText.text = "Data saved: ${stats.formatDataSaved()}"
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST_CODE)
        } else {
            startProtection()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            startProtection()
        } else if (requestCode == VPN_REQUEST_CODE) {
            Toast.makeText(this, "VPN permission is required", Toast.LENGTH_LONG).show()
        }
    }

    private fun startProtection() {
        val intent = Intent(this, CleanShieldVpnService::class.java).apply {
            action = CleanShieldVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        settings.isProtectionEnabled = true
        updateUI()
        Toast.makeText(this, "CleanShield protection started", Toast.LENGTH_SHORT).show()
    }

    private fun stopProtection() {
        val intent = Intent(this, CleanShieldVpnService::class.java).apply {
            action = CleanShieldVpnService.ACTION_STOP
        }
        startService(intent)
        settings.isProtectionEnabled = false
        updateUI()
        Toast.makeText(this, "Protection stopped", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val VPN_REQUEST_CODE = 100
    }
}
