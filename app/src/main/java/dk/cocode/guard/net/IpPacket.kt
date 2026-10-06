package dk.cocode.guard.net

internal const val PROTOCOL_TCP = 6
internal const val PROTOCOL_UDP = 17
internal const val PROTOCOL_ICMP = 1
internal const val PROTOCOL_ICMPV6 = 58

private const val IPV4_HEADER = 20
private const val IPV6_HEADER = 40

/** What the refusal path needs from an IPv4 or IPv6 packet; the transport header starts at [headerLength]. */
data class IpPacket(
    val version: Int,
    val protocol: Int,
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val headerLength: Int,
    val totalLength: Int,
)

/**
 * Parses the IP header of the first [length] bytes of [buf]. IPv4 may carry options; IPv6 must have
 * TCP or UDP right after its fixed header. Null for extension headers, fragments and truncated packets.
 */
fun parseIpPacket(buf: ByteArray, length: Int): IpPacket? {
    if (length < 1 || length > buf.size) return null
    return when (buf.u8(0) shr 4) {
        4 -> parseIpv4(buf, length)
        6 -> parseIpv6(buf, length)
        else -> null
    }
}

private fun parseIpv4(buf: ByteArray, length: Int): IpPacket? {
    if (length < IPV4_HEADER) return null
    val ihl = (buf.u8(0) and 0x0F) * 4
    val total = buf.u16(2)
    if (ihl < IPV4_HEADER || total < ihl || total > length) return null
    if (buf.u16(6) and 0x3FFF != 0) return null // a fragment has no usable transport header
    return IpPacket(4, buf.u8(9), buf.copyOfRange(12, 16), buf.copyOfRange(16, 20), ihl, total)
}

private fun parseIpv6(buf: ByteArray, length: Int): IpPacket? {
    if (length < IPV6_HEADER) return null
    val next = buf.u8(6)
    val total = IPV6_HEADER + buf.u16(4)
    if ((next != PROTOCOL_TCP && next != PROTOCOL_UDP) || total > length) return null
    return IpPacket(6, next, buf.copyOfRange(8, 24), buf.copyOfRange(24, 40), IPV6_HEADER, total)
}

/**
 * A reply to [this] with [payloadLength] zero bytes after its IP header: from the packet's destination
 * to its source, TTL 64, with the IPv4 header checksum already set.
 */
internal fun IpPacket.replyShell(protocol: Int, payloadLength: Int): ByteArray {
    val out = ByteArray(headerLength(version) + payloadLength)
    if (version == 4) {
        out[0] = 0x45
        out.put16(2, out.size)
        out.put16(6, 0x4000)
        out[8] = 64
        out[9] = protocol.toByte()
        dstIp.copyInto(out, 12)
        srcIp.copyInto(out, 16)
        out.put16(10, checksum(sum(out, 0, IPV4_HEADER)))
    } else {
        out[0] = 0x60
        out.put16(4, payloadLength)
        out[6] = protocol.toByte()
        out[7] = 64
        dstIp.copyInto(out, 8)
        srcIp.copyInto(out, 24)
    }
    return out
}

internal fun headerLength(version: Int) = if (version == 4) IPV4_HEADER else IPV6_HEADER

/** The pseudo-header part of a TCP or ICMPv6 checksum, for a transport part that fills the rest of [reply]. */
internal fun pseudoSum(reply: ByteArray, version: Int, protocol: Int): Int {
    val length = reply.size - headerLength(version)
    // The two addresses sit side by side: 12..20 in IPv4, 8..40 in IPv6.
    return (if (version == 4) sum(reply, 12, 20) else sum(reply, 8, 40)) + protocol + length
}
