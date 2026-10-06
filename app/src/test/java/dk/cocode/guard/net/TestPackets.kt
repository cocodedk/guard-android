package dk.cocode.guard.net

import dk.cocode.guard.iplist.parseCidr

/** Hand-built packets for tests; checksums of the inputs are left zero because the parsers ignore them. */
fun addr(text: String): ByteArray = parseCidr(text + if (':' in text) "/128" else "/32")!!.address

fun tcpSegment(srcPort: Int, dstPort: Int, seq: Long, flags: Int, payload: ByteArray = ByteArray(0)): ByteArray {
    val t = ByteArray(20 + payload.size)
    t.put16(0, srcPort)
    t.put16(2, dstPort)
    t.put16(4, (seq ushr 16).toInt())
    t.put16(6, seq.toInt())
    t[12] = 0x50
    t[13] = flags.toByte()
    t.put16(14, 65535)
    payload.copyInto(t, 20)
    return t
}

fun udpDatagram(srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
    val u = ByteArray(8 + payload.size)
    u.put16(0, srcPort)
    u.put16(2, dstPort)
    u.put16(4, u.size)
    payload.copyInto(u, 8)
    return u
}

fun v4Packet(protocol: Int, src: ByteArray, dst: ByteArray, transport: ByteArray, optionWords: Int = 0): ByteArray {
    val ihl = 20 + optionWords * 4
    val out = ByteArray(ihl + transport.size)
    out[0] = (0x40 or (ihl / 4)).toByte()
    out.put16(2, out.size)
    out[8] = 64
    out[9] = protocol.toByte()
    src.copyInto(out, 12)
    dst.copyInto(out, 16)
    transport.copyInto(out, ihl)
    return out
}

fun v6Packet(next: Int, src: ByteArray, dst: ByteArray, transport: ByteArray): ByteArray {
    val out = ByteArray(40 + transport.size)
    out[0] = 0x60
    out.put16(4, transport.size)
    out[6] = next.toByte()
    out[7] = 64
    src.copyInto(out, 8)
    dst.copyInto(out, 24)
    transport.copyInto(out, 40)
    return out
}

/** The one's-complement sum of 16-bit words, folded; a good checksum makes the whole sum 0xFFFF. */
fun onesSum(b: ByteArray, from: Int, to: Int, initial: Int = 0): Int {
    var s = initial
    var i = from
    while (i < to) {
        s += if (i + 1 < to) b.u16(i) else b.u8(i) shl 8
        i += 2
    }
    while (s ushr 16 != 0) s = (s and 0xFFFF) + (s ushr 16)
    return s
}
