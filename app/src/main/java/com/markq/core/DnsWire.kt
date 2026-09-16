package com.markq.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.IDN
import java.net.InetAddress

data class DnsAnswer(
    val addresses: List<InetAddress>,
    val ttlSec: Int,
)

object DnsWire {
    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    const val CLASS_IN = 1

    fun isIpLiteral(hostname: String): Boolean =
        parseLiteral(hostname) != null

    fun parseLiteral(hostname: String): InetAddress? {
        val h = hostname.trim().removePrefix("[").removeSuffix("]")
        if (h.isEmpty() || !looksLikeIp(h)) return null
        return runCatching { InetAddress.getByName(h) }.getOrNull()
    }

    fun query(id: Int, hostname: String, type: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val data = DataOutputStream(out)
        data.writeShort(id and 0xFFFF)
        data.writeShort(0x0100) // RD
        data.writeShort(1) // QDCOUNT
        data.writeShort(0)
        data.writeShort(0)
        data.writeShort(0)
        writeName(data, hostname)
        data.writeShort(type)
        data.writeShort(CLASS_IN)
        data.flush()
        return out.toByteArray()
    }

    fun parseAnswers(message: ByteArray, queryHostname: String): DnsAnswer {
        if (message.size < 12) return DnsAnswer(emptyList(), 0)
        val input = DataInputStream(ByteArrayInputStream(message))
        input.readUnsignedShort() // id
        val flags = input.readUnsignedShort()
        val rcode = flags and 0xF
        val qd = input.readUnsignedShort()
        val an = input.readUnsignedShort()
        val ns = input.readUnsignedShort()
        val ar = input.readUnsignedShort()
        if (rcode != 0) return DnsAnswer(emptyList(), 0)
        repeat(qd) { skipName(input); input.skipBytes(4) }
        val addresses = ArrayList<InetAddress>()
        var minTtl = Int.MAX_VALUE
        repeat(an + ns + ar) {
            skipName(input)
            val type = input.readUnsignedShort()
            input.readUnsignedShort() // class
            val ttl = input.readInt()
            val rdlen = input.readUnsignedShort()
            val rdata = ByteArray(rdlen)
            input.readFully(rdata)
            if (type == TYPE_A && rdlen == 4) {
                addresses += InetAddress.getByAddress(queryHostname, rdata)
                minTtl = minOf(minTtl, ttl.coerceAtLeast(0))
            } else if (type == TYPE_AAAA && rdlen == 16) {
                addresses += InetAddress.getByAddress(queryHostname, rdata)
                minTtl = minOf(minTtl, ttl.coerceAtLeast(0))
            }
        }
        val ttl = if (minTtl == Int.MAX_VALUE) 0 else minTtl
        return DnsAnswer(addresses.distinct(), ttl)
    }

    fun asciiName(hostname: String): String {
        val trimmed = hostname.trim().trimEnd('.').lowercase()
        return IDN.toASCII(trimmed)
    }

    private fun looksLikeIp(host: String): Boolean {
        if (host.startsWith("[") && host.endsWith("]")) return true
        if (host.count { it == ':' } >= 2) return true
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { it.toIntOrNull() in 0..255 }
    }

    private fun writeName(data: DataOutputStream, hostname: String) {
        val ascii = asciiName(hostname)
        if (ascii.isEmpty()) {
            data.writeByte(0)
            return
        }
        ascii.split('.').forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            require(bytes.isNotEmpty() && bytes.size <= 63) { "bad DNS label" }
            data.writeByte(bytes.size)
            data.write(bytes)
        }
        data.writeByte(0)
    }

    private fun skipName(input: DataInputStream) {
        while (true) {
            val len = input.readUnsignedByte()
            when {
                len == 0 -> return
                len and 0xC0 == 0xC0 -> {
                    input.readUnsignedByte()
                    return
                }
                else -> input.skipBytes(len)
            }
        }
    }
}
