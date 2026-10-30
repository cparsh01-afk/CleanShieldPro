package com.cleanshield.pro.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.UnknownHostException

class DohBootstrapDnsTest {
    @Test
    fun cloudflareUsesPinnedBootstrapAddresses() {
        val result = DohBootstrapDns.lookup("cloudflare-dns.com")
        assertEquals(listOf("1.1.1.1", "1.0.0.1"), result.map { it.hostAddress })
    }

    @Test
    fun quad9UsesPinnedBootstrapAddresses() {
        val result = DohBootstrapDns.lookup("dns.quad9.net.")
        assertEquals(listOf("9.9.9.9", "149.112.112.112"), result.map { it.hostAddress })
    }

    @Test
    fun numericIpv4CustomEndpointDoesNotUseSystemDns() {
        val result = DohBootstrapDns.lookup("8.8.8.8")
        assertEquals(listOf("8.8.8.8"), result.map { it.hostAddress })
    }

    @Test
    fun unknownCustomHostnameFailsInsteadOfRecursingIntoVpn() {
        assertThrows(UnknownHostException::class.java) {
            DohBootstrapDns.lookup("dns.example.invalid")
        }
    }
}
