package dk.cocode.guard.net

private const val TCP_HEADER = 20
private const val SYN = 0x02
private const val ACK = 0x10
private const val RST_ACK = 0x14

/**
 * The RST+ACK that refuses the TCP SYN in [buf]: addresses and ports swapped, sequence 0,
 * acknowledgement = the SYN's sequence + 1 + its payload, window 0. Null unless SYN is set and ACK clear.
 */
fun tcpResetFor(buf: ByteArray, p: IpPacket): ByteArray? {
    val h = p.headerLength
    if (p.protocol != PROTOCOL_TCP || p.totalLength < h + TCP_HEADER) return null
    val flags = buf.u8(h + 13)
    if (flags and SYN == 0 || flags and ACK != 0) return null
    val dataOffset = (buf.u8(h + 12) shr 4) * 4
    if (dataOffset < TCP_HEADER || h + dataOffset > p.totalLength) return null
    val seq = (buf.u16(h + 4) shl 16) or buf.u16(h + 6)
    val ack = seq + 1 + (p.totalLength - h - dataOffset) // Int overflow is the 32-bit wrap-around

    // The reply's IP header is fixed-size, whatever options the incoming one carried.
    val out = p.replyShell(PROTOCOL_TCP, TCP_HEADER)
    val r = headerLength(p.version)
    out.put16(r, buf.u16(h + 2))
    out.put16(r + 2, buf.u16(h))
    out.put16(r + 8, ack ushr 16)
    out.put16(r + 10, ack)
    out[r + 12] = 0x50 // five words, no options
    out[r + 13] = RST_ACK.toByte()
    out.put16(r + 16, checksum(sum(out, r, out.size, pseudoSum(out, p.version, PROTOCOL_TCP))))
    return out
}
