package com.cleanshield.pro.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cleanshield.pro.CleanShieldApp
import com.cleanshield.pro.ui.MainActivity
import com.cleanshield.pro.filter.FilterEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local DNS-only VPN. It routes only the virtual DNS resolver address, never
 * the device's default route. The current packet engine handles IPv4/UDP DNS.
 *
 * Explicit limitations: IPv6 DNS and DNS-over-TCP are not intercepted by this
 * implementation. Apps using their own DoH/DoT or hard-coded resolvers can
 * bypass local DNS filtering. Do not describe this as full-device traffic VPN.
 */
class CleanShieldVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private val isRunning = AtomicBoolean(false)
    private val resolverDegraded = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serviceJob: Job? = null
    private var resolver: DnsOverHttpsResolver? = null
    private lateinit var filterEngine: FilterEngine

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopProtection()
                return START_NOT_STICKY
            }
            ACTION_START -> startProtection()
            else -> return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    private fun startProtection() {
        if (isRunning.get()) return

        try {
            startForeground(NOTIFICATION_ID, createNotification("Starting DNS protection…", false))

            val app = CleanShieldApp.instance
            filterEngine = FilterEngine(app.filterRepository)
            resolver = DnsOverHttpsResolver(this, app.settingsRepository.getDohEndpoint())

            val builder = Builder()
                .setSession("Parsh CleanShieldPro")
                .addAddress(VPN_INTERFACE_ADDRESS, 30)
                .addDnsServer(VIRTUAL_DNS_ADDRESS)
                .addRoute(VIRTUAL_DNS_ADDRESS, 32)
                .setMtu(1500)
                .addDisallowedApplication(packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setBlocking(true)
            }
            val established = builder.establish()

            if (established == null) {
                throw IOException("Android did not establish the VPN interface")
            }

            vpnInterface = established
            isRunning.set(true)
            resolverDegraded.set(false)
            app.settingsRepository.isProtectionEnabled = true
            updateNotification("DNS protection active • IPv4 UDP")
            broadcastState(true, "DNS protection active")
            serviceJob = scope.launch { runDnsLoop(established) }
            Log.i(TAG, "DNS-only VPN established")
        } catch (error: Exception) {
            Log.e(TAG, "Could not start DNS protection", error)
            CleanShieldApp.instance.settingsRepository.isProtectionEnabled = false
            isRunning.set(false)
            closeResources()
            broadcastState(false, error.message ?: "Could not start VPN")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun runDnsLoop(tun: ParcelFileDescriptor) {
        val packet = ByteArray(65535)

        try {
            val input = FileInputStream(tun.fileDescriptor)
            val output = FileOutputStream(tun.fileDescriptor)
            while (isRunning.get() && scope.isActive) {
                val length = try {
                    input.read(packet)
                } catch (error: IOException) {
                    val transientRead = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                        (error.message?.contains("temporarily unavailable", ignoreCase = true) == true ||
                         error.message?.contains("EAGAIN", ignoreCase = true) == true)
                    if (transientRead) {
                        delay(10)
                        continue
                    }
                    throw error
                }
                if (length <= 0) break

                val query = DnsPacketCodec.parse(packet, length) ?: continue
                val dnsName = DnsPacketCodec.readQuestion(query.dnsPayload, 12)?.name ?: continue
                val stats = CleanShieldApp.instance.statsRepository

                val response = if (filterEngine.shouldBlock(dnsName)) {
                    stats.recordBlocked()
                    DnsPacketCodec.buildNxDomain(query)
                } else {
                    stats.recordQuery()
                    try {
                        val resolved = resolver?.resolve(query.dnsPayload)
                        if (resolverDegraded.compareAndSet(true, false)) {
                            updateNotification("DNS protection active • IPv4 UDP")
                            broadcastState(true, "DNS protection active")
                        }
                        resolved
                    } catch (error: IOException) {
                        Log.w(TAG, "DoH request failed: ${error.message}")
                        stats.recordResolverFailure()
                        if (resolverDegraded.compareAndSet(false, true)) {
                            updateNotification("DoH unavailable • DNS may fail")
                            broadcastState(true, "Protection is active, but the encrypted DNS resolver failed. Check connectivity or provider settings.")
                        }
                        DnsPacketCodec.buildServFail(query)
                    }
                } ?: continue

                val responsePacket = DnsPacketCodec.buildResponse(query, response)
                output.write(responsePacket)
                output.flush()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            if (isRunning.get()) {
                Log.e(TAG, "TUN packet loop stopped", error)
                broadcastState(false, "VPN packet interface stopped")
            }
        } finally {
            if (isRunning.get()) {
                isRunning.set(false)
                CleanShieldApp.instance.settingsRepository.isProtectionEnabled = false
                broadcastState(false, "DNS packet interface closed")
                closeResources()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun stopProtection() {
        isRunning.set(false)
        CleanShieldApp.instance.settingsRepository.isProtectionEnabled = false
        serviceJob?.cancel()
        closeResources()
        broadcastState(false, "Protection stopped")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun closeResources() {
        try {
            vpnInterface?.close()
        } catch (_: IOException) {
        }
        vpnInterface = null
        resolver?.close()
        resolver = null
    }

    private fun createNotification(message: String, ongoing: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CleanShieldApp.CHANNEL_ID)
            .setContentTitle("Parsh CleanShieldPro")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openApp)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(message, true))
    }

    private fun broadcastState(active: Boolean, message: String) {
        val event = Intent(ACTION_STATE_CHANGED)
            .setPackage(packageName)
            .putExtra(EXTRA_ACTIVE, active)
            .putExtra(EXTRA_MESSAGE, message)
        sendBroadcast(event)
    }

    override fun onRevoke() {
        stopProtection()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopProtection()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val TAG = "ParshCleanShieldVPN"
        const val ACTION_START = "com.cleanshield.pro.START"
        const val ACTION_STOP = "com.cleanshield.pro.STOP"
        const val ACTION_STATE_CHANGED = "com.cleanshield.pro.STATE_CHANGED"
        const val EXTRA_ACTIVE = "active"
        const val EXTRA_MESSAGE = "message"
        const val NOTIFICATION_ID = 1001
        private const val VPN_INTERFACE_ADDRESS = "10.77.0.1"
        private const val VIRTUAL_DNS_ADDRESS = "10.77.0.2"
    }
}
