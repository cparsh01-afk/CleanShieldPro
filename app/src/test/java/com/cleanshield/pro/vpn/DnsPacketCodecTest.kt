package com.cleanshield.pro.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DnsPacketCodecTest {
    @Test
    fun parsesIpv4UdpDnsQueryWithEdnsOptRecord() {
        val dns = sampleDnsQuery()
        val packet = wrapIpv4Udp(dns)
        val parsed = DnsPacketCodec.parse(packet, packet.size)

        assertNotNull(parsed)
        assertEquals("www.example.com", DnsPacketCodec.readQuestion(parsed!!.dnsPayload, 12)?.name)
    }

    @Test
    fun blockedResponseIsNxdomainAndPreservesQuestion() {
        val parsed = DnsPacketCodec.parse(wrapIpv4Udp(sampleDnsQuery()), wrapIpv4Udp(sampleDnsQuery()).size)!!
        val blocked = DnsPacketCodec.buildNxDomain(parsed)

        assertNotNull(blocked)
        val responseFlags = ByteBuffer.wrap(blocked!!).order(ByteOrder.BIG_ENDIAN).getShort(2).toInt()
        assertEquals(3, responseFlags and 0x0F)
        assertEquals("www.example.com", DnsPacketCodec.readQuestion(blocked, 12)?.name)
    }

    @Test
    fun responsePacketReversesAddressesAndPorts() {
        val packet = wrapIpv4Udp(sampleDnsQuery())
        val query = DnsPacketCodec.parse(packet, packet.size)!!
        val response = DnsPacketCodec.buildResponse(query, DnsPacketCodec.buildServFail(query)!!)

        assertEquals(0x45, response[0].toInt() and 0xFF)
        assertEquals(17, response[9].toInt() and 0xFF)
        assertTrue(response.copyOfRange(12, 16).contentEquals(query.destinationAddress))
        assertTrue(response.copyOfRange(16, 20).contentEquals(query.sourceAddress))
        assertEquals(53, u16(response, 20))
        assertEquals(query.sourcePort, u16(response, 22))
    }

    @Test
    fun rejectsNonDnsAndMalformedPackets() {
        val packet = wrapIpv4Udp(sampleDnsQuery())
        packet[9] = 6 // TCP, not UDP
        put16(packet, 10, 0)
        put16(packet, 10, checksum(packet, 0, 20))
        assertEquals(null, DnsPacketCodec.parse(packet, packet.size))
    }

    private fun sampleDnsQuery(): ByteArray {
        val question = byteArrayOf(
            3, 119, 119, 119, 7, 101, 120, 97, 109, 112, 108, 101,
            3, 99, 111, 109, 0,
            0, 1, 0, 1
        )
        val opt = byteArrayOf(0, 0, 41, 4, -48, 0, 0, 0, 0, 0, 0)
        val dns = ByteArray(12 + question.size + opt.size)
        put16(dns, 0, 0x1234)
        put16(dns, 2, 0x0100)
        put16(dns, 4, 1)
        put16(dns, 10, 1)
        question.copyInto(dns, 12)
        opt.copyInto(dns, 12 + question.size)
        return dns
    }

    private fun wrapIpv4Udp(dns: ByteArray): ByteArray {
        val udpLength = 8 + dns.size
        val packet = ByteArray(20 + udpLength)
        packet[0] = 0x45
        put16(packet, 2, packet.size)
        packet[8] = 64
        packet[9] = 17
        packet[12] = 192.toByte()
        packet[13] = 0
        packet[14] = 2
        packet[15] = 10
        packet[16] = 10
        packet[17] = 77
        packet[18] = 0
        packet[19] = 2
        put16(packet, 20, 53000)
        put16(packet, 22, 53)
        put16(packet, 24, udpLength)
        dns.copyInto(packet, 28)
        put16(packet, 10, checksum(packet, 0, 20))
        return packet
    }

    private fun checksum(data: ByteArray, start: Int, length: Int): Int {
        var sum = 0L
        var index = start
        val end = start + length
        while (index + 1 < end) {
            sum += ((data[index].toInt() and 0xFF) shl 8) or (data[index + 1].toInt() and 0xFF)
            index += 2
        }
        if (index < end) sum += (data[index].toInt() and 0xFF) shl 8
        while ((sum ushr 16) != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv().toInt() and 0xFFFF
    }

    private fun put16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value ushr 8).toByte()
        data[offset + 1] = value.toByte()
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
}
