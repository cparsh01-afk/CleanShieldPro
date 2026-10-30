package com.cleanshield.pro.vpn

import android.net.VpnService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory
import java.util.concurrent.TimeUnit

/**
 * RFC 8484 DNS-over-HTTPS resolver. OkHttp keeps normal TLS certificate
 * validation enabled. Every TCP socket is protected before it connects.
 */
class DnsOverHttpsResolver(
    private val vpnService: VpnService,
    private val endpoint: String
) : AutoCloseable {

    private val client = OkHttpClient.Builder()
        .socketFactory(ProtectedSocketFactory(vpnService))
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    init {
        val parsed = endpoint.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid DNS-over-HTTPS endpoint")
        require(parsed.isHttps && parsed.username.isEmpty() && parsed.password.isEmpty()) {
            "DNS-over-HTTPS endpoint must be an HTTPS URL without embedded credentials"
        }
    }

    @Throws(IOException::class)
    fun resolve(dnsQuery: ByteArray): ByteArray {
        if (!DnsPacketCodec.isValidQuery(dnsQuery)) {
            throw IOException("Invalid DNS query")
        }

        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/dns-message")
            .header("Cache-Control", "no-store")
            .post(dnsQuery.toRequestBody("application/dns-message".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("DoH server returned HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("DoH response body is empty")
            val contentType = body.contentType()?.toString().orEmpty()
            if (!contentType.startsWith("application/dns-message", ignoreCase = true)) {
                throw IOException("Unexpected DoH response content type")
            }
            if (body.contentLength() > MAX_DNS_MESSAGE_SIZE.toLong()) {
                throw IOException("DoH response is too large")
            }
            val bytes = body.byteStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_DNS_MESSAGE_SIZE) {
                        throw IOException("DoH response is too large")
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            if (bytes.size !in 12..MAX_DNS_MESSAGE_SIZE ||
                !DnsPacketCodec.responseMatches(dnsQuery, bytes)
            ) {
                throw IOException("DoH returned an invalid or mismatched DNS response")
            }
            return bytes
        }
    }

    override fun close() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
    }

    private class ProtectedSocketFactory(private val vpn: VpnService) : SocketFactory() {
        private fun protectedSocket(): Socket {
            val socket = Socket()
            if (!vpn.protect(socket)) {
                socket.close()
                throw IOException("Could not protect DoH socket from VPN routing")
            }
            return socket
        }

        override fun createSocket(): Socket = protectedSocket()

        override fun createSocket(host: String, port: Int): Socket =
            protectedSocket().apply { connect(InetSocketAddress(host, port), 8_000) }

        override fun createSocket(
            host: String, port: Int, localHost: InetAddress, localPort: Int
        ): Socket = protectedSocket().apply {
            bind(InetSocketAddress(localHost, localPort))
            connect(InetSocketAddress(host, port), 8_000)
        }

        override fun createSocket(host: InetAddress, port: Int): Socket =
            protectedSocket().apply { connect(InetSocketAddress(host, port), 8_000) }

        override fun createSocket(
            address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int
        ): Socket = protectedSocket().apply {
            bind(InetSocketAddress(localAddress, localPort))
            connect(InetSocketAddress(address, port), 8_000)
        }
    }

    companion object {
        private const val MAX_DNS_MESSAGE_SIZE = 65_507
    }
}
