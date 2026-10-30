package com.cleanshield.pro.vpn

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Bootstrap DNS for the encrypted resolver connection.
 *
 * Calling the platform DNS resolver while this VPN is active can send the
 * resolver hostname lookup back into this VPN and deadlock. Known providers
 * therefore use pinned bootstrap IPs while TLS still validates the URL host.
 * Custom DoH endpoints must use an IP-literal hostname until the app exposes
 * an explicit bootstrap-IP setting.
 */
internal object DohBootstrapDns : Dns {
    private val addresses = mapOf(
        "cloudflare-dns.com" to listOf("1.1.1.1", "1.0.0.1"),
        "dns.quad9.net" to listOf("9.9.9.9", "149.112.112.112")
    )

    override fun lookup(hostname: String): List<InetAddress> {
        val host = hostname.trim().trimEnd('.').lowercase()
        val known = addresses[host]
        if (known != null) {
            return known.map { ip ->
                val octets = ip.split('.').mapNotNull(String::toIntOrNull)
                if (octets.size != 4 || octets.any { it !in 0..255 }) {
                    throw UnknownHostException("Invalid built-in DoH bootstrap address")
                }
                InetAddress.getByAddress(host, octets.map(Int::toByte).toByteArray())
            }
        }

        // Accept numeric IPv4 literals without invoking system DNS.
        val octets = host.split('.')
        if (octets.size == 4 && octets.all { it.isNotEmpty() && it.all(Char::isDigit) }) {
            val values = octets.mapNotNull(String::toIntOrNull)
            if (values.size == 4 && values.all { it in 0..255 }) {
                return listOf(InetAddress.getByAddress(host, values.map(Int::toByte).toByteArray()))
            }
        }

        // A colon indicates an IPv6 literal; parsing it does not require DNS.
        if (':' in host) {
            return try {
                listOf(InetAddress.getByName(host))
            } catch (error: Exception) {
                throw UnknownHostException("Invalid IPv6 DoH endpoint literal: $host").apply {
                    initCause(error)
                }
            }
        }

        throw UnknownHostException(
            "No safe bootstrap IP is configured for custom DoH host '$host'. " +
                "Use Cloudflare, Quad9, or an IP-literal HTTPS endpoint."
        )
    }
}
