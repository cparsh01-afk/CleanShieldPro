package com.cleanshield.pro.ui

import android.app.Activity
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cleanshield.pro.CleanShieldApp
import com.cleanshield.pro.R
import com.cleanshield.pro.vpn.CleanShieldVpnService
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var statusDetailText: TextView
    private lateinit var blockedCountText: TextView
    private lateinit var queryCountText: TextView
    private lateinit var resolverText: TextView
    private lateinit var resolverFailureText: TextView
    private lateinit var networkText: TextView
    private lateinit var performanceText: TextView
    private lateinit var toggleButton: Button
    private var protectionActive = false
    private var receiverRegistered = false

    private val settings by lazy { CleanShieldApp.instance.settingsRepository }
    private val stats by lazy { CleanShieldApp.instance.statsRepository }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != CleanShieldVpnService.ACTION_STATE_CHANGED) return
            protectionActive = intent.getBooleanExtra(CleanShieldVpnService.EXTRA_ACTIVE, false)
            statusDetailText.text = intent.getStringExtra(CleanShieldVpnService.EXTRA_MESSAGE)
                ?: "Status updated"
            updateProtectionViews()
            refreshDashboard()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = "Parsh CleanShieldPro"
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST_CODE)
        }

        statusText = findViewById(R.id.statusText)
        statusDetailText = findViewById(R.id.statusDetailText)
        blockedCountText = findViewById(R.id.blockedCountText)
        queryCountText = findViewById(R.id.queryCountText)
        resolverText = findViewById(R.id.resolverText)
        resolverFailureText = findViewById(R.id.resolverFailureText)
        networkText = findViewById(R.id.networkText)
        performanceText = findViewById(R.id.performanceText)
        toggleButton = findViewById(R.id.toggleButton)

        toggleButton.setOnClickListener {
            if (protectionActive) stopProtection() else requestVpnPermission()
        }
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(CleanShieldVpnService.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(stateReceiver, filter)
        }
        receiverRegistered = true
        protectionActive = settings.isProtectionEnabled && hasVpnTransport()
        if (!protectionActive && settings.isProtectionEnabled) {
            settings.isProtectionEnabled = false
        }
        updateProtectionViews()
        refreshDashboard()
    }

    override fun onPause() {
        if (receiverRegistered) {
            unregisterReceiver(stateReceiver)
            receiverRegistered = false
        }
        super.onPause()
    }

    private fun requestVpnPermission() {
        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent != null) {
            @Suppress("DEPRECATION")
            startActivityForResult(permissionIntent, VPN_REQUEST_CODE)
        } else {
            startProtection()
        }
    }

    @Deprecated("Kept for compatibility with the existing activity flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            startProtection()
        } else if (requestCode == VPN_REQUEST_CODE) {
            Toast.makeText(this, "VPN permission was not granted", Toast.LENGTH_LONG).show()
        }
    }

    private fun startProtection() {
        val serviceIntent = Intent(this, CleanShieldVpnService::class.java).apply {
            action = CleanShieldVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        statusDetailText.text = "Waiting for Android to establish the VPN…"
        Toast.makeText(this, "Starting DNS protection…", Toast.LENGTH_SHORT).show()
    }

    private fun stopProtection() {
        val serviceIntent = Intent(this, CleanShieldVpnService::class.java).apply {
            action = CleanShieldVpnService.ACTION_STOP
        }
        startService(serviceIntent)
        statusDetailText.text = "Stopping protection…"
    }

    private fun updateProtectionViews() {
        statusText.text = if (protectionActive) "PROTECTION ACTIVE" else "PROTECTION OFF"
        statusText.setTextColor(getColor(if (protectionActive) R.color.status_good else R.color.status_bad))
        toggleButton.text = if (protectionActive) "Stop Protection" else "Start Protection"
    }

    private fun refreshDashboard() {
        blockedCountText.text = stats.getBlockedCount().toString()
        queryCountText.text = stats.getQueryCount().toString()
        resolverText.text = settings.dohProvider
        resolverFailureText.text = "Resolver failures: ${stats.getResolverFailureCount()}"
        networkText.text = getNetworkSummary()
        performanceText.text = getPerformanceSummary()
    }

    private fun hasVpnTransport(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java)
        return manager.allNetworks.any { network ->
            manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }

    private fun getNetworkSummary(): String {
        val manager = getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return "Offline"
        val caps = manager.getNetworkCapabilities(network) ?: return "Network status unavailable"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Connected • Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Connected • Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Connected • Ethernet"
            else -> if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) "Connected" else "No internet"
        }
    }

    private fun getPerformanceSummary(): String {
        val activityManager = getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memory)
        val usedRam = (memory.totalMem - memory.availMem).coerceAtLeast(0L)
        val ram = "${formatBytes(usedRam)} / ${formatBytes(memory.totalMem)} RAM"

        val stat = StatFs(filesDir.absolutePath)
        val totalStorage = stat.totalBytes
        val availableStorage = stat.availableBytes
        val storage = "${formatBytes(totalStorage - availableStorage)} / ${formatBytes(totalStorage)} storage"

        val batteryManager = getSystemService(BatteryManager::class.java)
        val battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val batteryText = if (battery in 0..100) "$battery% battery" else "Battery unavailable"
        return "$ram\n$storage\n$batteryText"
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) String.format(Locale.US, "%.1f GB", mb / 1024.0)
        else String.format(Locale.US, "%.0f MB", mb)
    }

    companion object {
        private const val VPN_REQUEST_CODE = 100
        private const val NOTIFICATION_REQUEST_CODE = 101
    }
}
