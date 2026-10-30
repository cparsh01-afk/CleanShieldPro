package com.cleanshield.pro.vpn

import java.nio.charset.StandardCharsets

/**
 * Strict IPv4/UDP DNS packet codec for the local DNS-only VPN.
 * It deliberately rejects fragmented, malformed and non-DNS packets.
 */
object DnsPacketCodec {
    data class DnsQuery(
        val sourceAddress: ByteArray,
        val destinationAddress: ByteArray,
        val sourcePort: Int,
        val dnsPayload: ByteArray
    )

    data class Question(val name: String, val endOffset: Int)

    fun parse(packet: ByteArray, length: Int): DnsQuery? {
        if (length < 28 || length > packet.size) return null
        if (((packet[0].toInt() ushr 4) and 0x0F) != 4) return null

        val ipHeaderLength = (packet[0].toInt() and 0x0F) * 4
        if (ipHeaderLength < 20 || length < ipHeaderLength + 8) return null

        val totalLength = readU16(packet, 2)
        if (totalLength < ipHeaderLength + 8 || totalLength > length) return null
        if (checksum(packet, 0, ipHeaderLength) != 0) return null
        if ((packet[9].toInt() and 0xFF) != 17) return null

        // Reject all IPv4 fragments; DNS datagrams should arrive unfragmented.
        val fragmentField = readU16(packet, 6)
        if ((fragmentField and 0x3FFF) != 0) return null

        val udpOffset = ipHeaderLength
        val sourcePort = readU16(packet, udpOffset)
        val destinationPort = readU16(packet, udpOffset + 2)
        val udpLength = readU16(packet, udpOffset + 4)
        if (sourcePort == 0 || destinationPort != 53) return null
        if (udpLength < 20 || udpOffset + udpLength != totalLength) return null
        val suppliedUdpChecksum = readU16(packet, udpOffset + 6)
        if (suppliedUdpChecksum != 0) {
            val pseudoHeader = ByteArray(12 + udpLength)
            packet.copyInto(pseudoHeader, 0, 12, 20)
            pseudoHeader[9] = 17
            writeU16(pseudoHeader, 10, udpLength)
            packet.copyInto(pseudoHeader, 12, udpOffset, udpOffset + udpLength)
            if (checksum(pseudoHeader, 0, pseudoHeader.size) != 0) return null
        }

        val dnsOffset = udpOffset + 8
        val dns = packet.copyOfRange(dnsOffset, udpOffset + udpLength)
        if (!isValidQuery(dns)) return null

        return DnsQuery(
            sourceAddress = packet.copyOfRange(12, 16),
            destinationAddress = packet.copyOfRange(16, 20),
            sourcePort = sourcePort,
            dnsPayload = dns
        )
    }

    fun isValidQuery(dns: ByteArray): Boolean {
        if (dns.size < 12 || dns.size > 65507) return false
        val flags = readU16(dns, 2)
        if ((flags and 0x8000) != 0) return false // QR must be zero
        if ((flags and 0x7800) != 0) return false // standard query only
        if (readU16(dns, 4) != 1) return false     // one question only
        if (readU16(dns, 6) != 0 || readU16(dns, 8) != 0) return false
        val additionalCount = readU16(dns, 10)
        if (additionalCount > 4) return false
        val question = readQuestion(dns, 12) ?: return false
        if (question.name.isBlank() || question.endOffset + 4 > dns.size) return false
        var cursor = question.endOffset + 4
        repeat(additionalCount) {
            val recordName = readQuestion(dns, cursor) ?: return false
            cursor = recordName.endOffset
            if (cursor + 10 > dns.size) return false
            val recordLength = readU16(dns, cursor + 8)
            cursor += 10
            if (recordLength > dns.size - cursor) return false
            cursor += recordLength
        }
        return cursor == dns.size
    }

    fun readQuestion(dns: ByteArray, start: Int): Question? {
        if (start !in dns.indices) return null
        val labels = ArrayList<String>()
        val visited = HashSet<Int>()
        var cursor = start
        var nextOffset = -1
        var jumped = false
        var expandedLength = 0

        while (cursor < dns.size) {
            if (!visited.add(cursor)) return null
            val length = dns[cursor].toInt() and 0xFF
            when {
                length == 0 -> {
                    if (!jumped) nextOffset = cursor + 1
                    break
                }
                (length and 0xC0) == 0xC0 -> {
                    if (cursor + 1 >= dns.size) return null
                    val pointer = ((length and 0x3F) shl 8) or (dns[cursor + 1].toInt() and 0xFF)
                    if (pointer >= dns.size) return null
                    if (!jumped) nextOffset = cursor + 2
                    cursor = pointer
                    jumped = true
                }
                (length and 0xC0) != 0 || length > 63 -> return null
                else -> {
                    if (cursor + 1 + length > dns.size) return null
                    val labelBytes = dns.copyOfRange(cursor + 1, cursor + 1 + length)
                    if (labelBytes.any { (it.toInt() and 0xFF) !in 33..126 }) return null
                    labels.add(String(labelBytes, StandardCharsets.US_ASCII))
                    expandedLength += length + 1
                    if (expandedLength > 255) return null
                    cursor += length + 1
                }
            }
        }

        if (nextOffset < 0 || nextOffset + 4 > dns.size) return null
        val name = labels.joinToString(".").trimEnd('.').lowercase()
        if (name.length > 253) return null
        return Question(name, nextOffset)
    }

    fun buildResponse(query: DnsQuery, dnsResponse: ByteArray): ByteArray {
        require(dnsResponse.size in 12..65507)
        val udpLength = 8 + dnsResponse.size
        val totalLength = 20 + udpLength
        require(totalLength <= 65535)

        val result = ByteArray(totalLength)
        result[0] = 0x45
        writeU16(result, 2, totalLength)
        writeU16(result, 6, 0x4000)
        result[8] = 64
        result[9] = 17
        query.destinationAddress.copyInto(result, 12)
        query.sourceAddress.copyInto(result, 16)
        writeU16(result, 10, checksum(result, 0, 20))

        writeU16(result, 20, 53)
        writeU16(result, 22, query.sourcePort)
        writeU16(result, 24, udpLength)
        dnsResponse.copyInto(result, 28)

        val pseudo = ByteArray(12 + udpLength)
        result.copyInto(pseudo, 0, 12, 20)
        pseudo[9] = 17
        writeU16(pseudo, 10, udpLength)
        result.copyInto(pseudo, 12, 20, totalLength)
        var udpChecksum = checksum(pseudo, 0, pseudo.size)
        if (udpChecksum == 0) udpChecksum = 0xFFFF
        writeU16(result, 26, udpChecksum)
        return result
    }

    fun buildErrorResponse(query: DnsQuery, rcode: Int): ByteArray? {
        val dns = query.dnsPayload
        val question = readQuestion(dns, 12) ?: return null
        val end = question.endOffset + 4
        val response = dns.copyOfRange(0, end)
        val requestFlags = readU16(dns, 2)
        val flags = 0x8000 or (requestFlags and (0x7800 or 0x0100 or 0x0010)) or 0x0080 or (rcode and 0x0F)
        writeU16(response, 2, flags)
        writeU16(response, 4, 1)
        writeU16(response, 6, 0)
        writeU16(response, 8, 0)
        writeU16(response, 10, 0)
        return response
    }

    fun buildNxDomain(query: DnsQuery): ByteArray? = buildErrorResponse(query, 3)
    fun buildServFail(query: DnsQuery): ByteArray? = buildErrorResponse(query, 2)

    fun responseMatches(query: ByteArray, response: ByteArray): Boolean {
        if (query.size < 12 || response.size < 12) return false
        if (readU16(query, 0) != readU16(response, 0)) return false
        if ((readU16(response, 2) and 0x8000) == 0) return false
        if (readU16(response, 4) != readU16(query, 4)) return false
        val q = readQuestion(query, 12) ?: return false
        val r = readQuestion(response, 12) ?: return false
        if (q.name.isBlank() || !q.name.equals(r.name, ignoreCase = true)) return false
        return q.endOffset + 4 <= query.size && r.endOffset + 4 <= response.size &&
            query[q.endOffset] == response[r.endOffset] &&
            query[q.endOffset + 1] == response[r.endOffset + 1] &&
            query[q.endOffset + 2] == response[r.endOffset + 2] &&
            query[q.endOffset + 3] == response[r.endOffset + 3]
    }

    private fun readU16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun writeU16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value ushr 8).toByte()
        data[offset + 1] = value.toByte()
    }

    private fun checksum(data: ByteArray, start: Int, length: Int): Int {
        var sum = 0L
        var i = start
        val end = start + length
        while (i + 1 < end) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) sum += (data[i].toInt() and 0xFF) shl 8
        while ((sum ushr 16) != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv().toInt() and 0xFFFF
    }
}
