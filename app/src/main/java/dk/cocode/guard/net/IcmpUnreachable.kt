package dk.cocode.guard.net

private const val ICMP_HEADER = 8
private const val IPV6_MIN_MTU = 1280

/**
 * The ICMP "unreachable" that refuses the UDP packet in [buf], sent from the blocked address back to
 * the sender. IPv4: type 3 code 13 (administratively prohibited) quoting the header and 8 bytes.
 * IPv6: type 1 code 1 quoting as much as keeps the reply within 1,280 bytes.
 */
fun icmpUnreachableFor(buf: ByteArray, p: IpPacket): ByteArray {
    val v4 = p.version == 4
    val quoted = if (v4) {
        minOf(p.headerLength + 8, p.totalLength)
    } else {
        minOf(p.totalLength, IPV6_MIN_MTU - p.headerLength - ICMP_HEADER)
    }
    val out = p.replyShell(if (v4) PROTOCOL_ICMP else PROTOCOL_ICMPV6, ICMP_HEADER + quoted)
    val r = headerLength(p.version) // the reply's IP header, not the incoming one with its options
    out[r] = if (v4) 3 else 1
    out[r + 1] = if (v4) 13 else 1
    buf.copyInto(out, r + ICMP_HEADER, 0, quoted)
    val initial = if (v4) 0 else pseudoSum(out, 6, PROTOCOL_ICMPV6) // ICMPv4 has no pseudo-header
    out.put16(r + 2, checksum(sum(out, r, out.size, initial)))
    return out
}
