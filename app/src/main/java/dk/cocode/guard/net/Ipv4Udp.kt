package dk.cocode.guard.net

data class UdpPacket(
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val payload: ByteArray,
)

private const val IP_HEADER = 20
private const val UDP_HEADER = 8

/** Parses an IPv4 + UDP packet from the first [length] bytes of [buf]; null for anything else. */
fun parseIpv4Udp(buf: ByteArray, length: Int): UdpPacket? {
    if (length < IP_HEADER || length > buf.size || buf.u8(0) shr 4 != 4) return null
    val ihl = (buf.u8(0) and 0x0F) * 4
    val total = buf.u16(2)
    if (ihl < IP_HEADER || total > length || total < ihl + UDP_HEADER) return null
    if (buf.u8(9) != PROTOCOL_UDP) return null
    // A fragment carries no UDP header (or only part of the payload); DNS queries never need one.
    if (buf.u16(6) and 0x3FFF != 0) return null
    val udpLength = buf.u16(ihl + 4)
    if (udpLength < UDP_HEADER || ihl + udpLength > total) return null
    return UdpPacket(
        srcIp = buf.copyOfRange(12, 16),
        dstIp = buf.copyOfRange(16, 20),
        srcPort = buf.u16(ihl),
        dstPort = buf.u16(ihl + 2),
        payload = buf.copyOfRange(ihl + UDP_HEADER, ihl + udpLength),
    )
}

/** Builds an IPv4 + UDP packet: TTL 64, DF set, valid header and UDP checksums. */
fun buildIpv4Udp(p: UdpPacket): ByteArray {
    val udpLength = UDP_HEADER + p.payload.size
    val total = IP_HEADER + udpLength
    val out = ByteArray(total)
    out[0] = 0x45
    out.put16(2, total)
    out.put16(6, 0x4000)
    out[8] = 64
    out[9] = PROTOCOL_UDP.toByte()
    p.srcIp.copyInto(out, 12)
    p.dstIp.copyInto(out, 16)
    out.put16(10, checksum(sum(out, 0, IP_HEADER)))

    out.put16(IP_HEADER, p.srcPort)
    out.put16(IP_HEADER + 2, p.dstPort)
    out.put16(IP_HEADER + 4, udpLength)
    p.payload.copyInto(out, IP_HEADER + UDP_HEADER)
    // The pseudo-header is the two addresses (already adjacent at 12..20), the protocol and the length.
    val pseudo = sum(out, 12, 20) + PROTOCOL_UDP + udpLength
    // Zero means "no checksum" in UDP, so a computed zero is sent as all ones.
    out.put16(IP_HEADER + 6, checksum(sum(out, IP_HEADER, total, pseudo)).let { if (it == 0) 0xFFFF else it })
    return out
}
