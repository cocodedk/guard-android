package dk.cocode.guard.iplist

/** An address and prefix length; the address is 4 bytes (IPv4) or 16 bytes (IPv6) with its host bits zero. */
data class Cidr(val address: ByteArray, val prefix: Int) {
    override fun equals(other: Any?) = other is Cidr && prefix == other.prefix && address.contentEquals(other.address)

    override fun hashCode() = 31 * address.contentHashCode() + prefix

    /** The normalised form: dotted decimal, or lower-case compressed IPv6, then `/prefix`. */
    override fun toString() = "${ipText(address)}/$prefix"
}

/** Parses `address/prefix`; null for garbage, a bad prefix or host bits set. Never looks up a name. */
fun parseCidr(text: String): Cidr? {
    val parts = text.trim().split('/')
    if (parts.size != 2) return null
    val address = (if (':' in parts[0]) parseIpv6(parts[0]) else parseIpv4(parts[0])) ?: return null
    val prefix = parts[1].toIntOrNull() ?: return null
    if (prefix !in 0..address.size * 8) return null
    for (bit in prefix until address.size * 8) {
        if ((address[bit / 8].toInt() shr (7 - bit % 8)) and 1 != 0) return null
    }
    return Cidr(address, prefix)
}

private fun parseIpv4(s: String): ByteArray? {
    val parts = s.split('.')
    if (parts.size != 4) return null
    val out = ByteArray(4)
    for ((i, part) in parts.withIndex()) {
        if (part.length !in 1..3 || !part.all { it in '0'..'9' }) return null
        val v = part.toInt()
        if (v > 255) return null
        out[i] = v.toByte()
    }
    return out
}

private fun parseIpv6(s: String): ByteArray? {
    val gap = s.indexOf("::")
    if (gap != s.lastIndexOf("::")) return null
    val head = groups(if (gap < 0) s else s.substring(0, gap)) ?: return null
    val tail = if (gap < 0) emptyList() else groups(s.substring(gap + 2)) ?: return null
    val missing = 8 - head.size - tail.size
    if (if (gap < 0) missing != 0 else missing < 1) return null
    val all = head + List(missing) { 0 } + tail
    val out = ByteArray(16)
    for ((i, g) in all.withIndex()) {
        out[2 * i] = (g shr 8).toByte()
        out[2 * i + 1] = g.toByte()
    }
    return out
}

private fun groups(part: String): List<Int>? {
    if (part.isEmpty()) return emptyList()
    return part.split(':').map { g ->
        if (g.length !in 1..4 || !g.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        g.toInt(16)
    }
}

/** True when [ip] (4 or 16 bytes) lies inside the range. */
fun Cidr.contains(ip: ByteArray): Boolean = ip.size == address.size && sameBits(address, ip, prefix)

/** True when the two ranges share any address. */
fun Cidr.overlaps(other: Cidr): Boolean =
    address.size == other.address.size && sameBits(address, other.address, minOf(prefix, other.prefix))

private fun Cidr.covers(other: Cidr) = prefix <= other.prefix && overlaps(other)

/** Drops exact duplicates and every range that lies inside another one in the list. */
fun List<Cidr>.merged(): List<Cidr> {
    val kept = ArrayList<Cidr>()
    for (c in sortedBy { it.prefix }) if (kept.none { it.covers(c) }) kept.add(c)
    return kept
}

private fun sameBits(a: ByteArray, b: ByteArray, bits: Int): Boolean {
    for (i in 0 until bits / 8) if (a[i] != b[i]) return false
    val rest = bits % 8
    if (rest == 0) return true
    val mask = (0xFF shl (8 - rest)) and 0xFF
    return (a[bits / 8].toInt() xor b[bits / 8].toInt()) and mask == 0
}

/** One address in its normalised text form, without a prefix. */
fun ipText(ip: ByteArray): String {
    if (ip.size == 4) return ip.joinToString(".") { (it.toInt() and 0xFF).toString() }
    val g = IntArray(8) { ((ip[2 * it].toInt() and 0xFF) shl 8) or (ip[2 * it + 1].toInt() and 0xFF) }
    var start = -1
    var length = 0
    var i = 0
    while (i < 8) {
        if (g[i] != 0) {
            i++
            continue
        }
        var j = i
        while (j < 8 && g[j] == 0) j++
        if (j - i > length) {
            start = i
            length = j - i
        }
        i = j
    }
    fun hex(from: Int, to: Int) = (from until to).joinToString(":") { g[it].toString(16) }
    return if (length < 2) hex(0, 8) else hex(0, start) + "::" + hex(start + length, 8)
}
