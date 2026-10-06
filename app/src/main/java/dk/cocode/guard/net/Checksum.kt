package dk.cocode.guard.net

internal fun ByteArray.u8(i: Int) = this[i].toInt() and 0xFF
internal fun ByteArray.u16(i: Int) = (u8(i) shl 8) or u8(i + 1)

internal fun ByteArray.put16(i: Int, v: Int) {
    this[i] = (v shr 8).toByte()
    this[i + 1] = v.toByte()
}

/** The one's-complement sum of the 16-bit words in [from] until [to]; an odd last byte is padded with zero. */
internal fun sum(buf: ByteArray, from: Int, to: Int, initial: Int = 0): Int {
    var s = initial
    var i = from
    while (i + 1 < to) {
        s += buf.u16(i)
        i += 2
    }
    if (i < to) s += buf.u8(i) shl 8
    return s
}

/** Folds the carries of [sum] and inverts it. */
internal fun checksum(sum: Int): Int {
    var s = sum
    while (s ushr 16 != 0) s = (s and 0xFFFF) + (s ushr 16)
    return s.inv() and 0xFFFF
}
