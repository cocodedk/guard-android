package dk.cocode.guard.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class Ipv4UdpTest {
    private val packet = UdpPacket(
        srcIp = byteArrayOf(10, 111, -34, 1), dstIp = byteArrayOf(10, 111, -34, 2),
        srcPort = 54321, dstPort = 53, payload = byteArrayOf(1, 2, 3, 4, 5),
    )

    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    // One's complement sum of 16-bit words, folded; an odd last byte is padded with zero.
    private fun onesSum(b: ByteArray, from: Int, to: Int, initial: Int = 0): Int {
        var s = initial
        var i = from
        while (i < to) {
            s += if (i + 1 < to) u16(b, i) else (b[i].toInt() and 0xFF) shl 8
            i += 2
        }
        while (s ushr 16 != 0) s = (s and 0xFFFF) + (s ushr 16)
        return s
    }

    @Test
    fun parsesValidPacket() {
        val parsed = parseIpv4Udp(buildIpv4Udp(packet), buildIpv4Udp(packet).size)
        assertNotNull(parsed)
        assertEquals(54321, parsed!!.srcPort)
        assertEquals(53, parsed.dstPort)
        assertArrayEquals(packet.payload, parsed.payload)
    }

    @Test
    fun parsesHeaderWithOptions() {
        val plain = buildIpv4Udp(packet)
        val withOptions = plain.copyOfRange(0, 20) + ByteArray(4) + plain.copyOfRange(20, plain.size)
        withOptions[0] = 0x46
        withOptions[3] = (withOptions.size).toByte()
        val parsed = parseIpv4Udp(withOptions, withOptions.size)
        assertNotNull(parsed)
        assertEquals(54321, parsed!!.srcPort)
        assertArrayEquals(packet.payload, parsed.payload)
    }

    @Test
    fun rejectsTcp() {
        val tcp = buildIpv4Udp(packet).also { it[9] = 6 }
        assertNull(parseIpv4Udp(tcp, tcp.size))
    }

    @Test
    fun rejectsIpv6() {
        val v6 = buildIpv4Udp(packet).also { it[0] = 0x60 }
        assertNull(parseIpv4Udp(v6, v6.size))
    }

    @Test
    fun rejectsTruncated() {
        val built = buildIpv4Udp(packet)
        assertNull(parseIpv4Udp(built, built.size - 1))
    }

    @Test
    fun builtHeaderChecksumVerifies() {
        assertEquals(0xFFFF, onesSum(buildIpv4Udp(packet), 0, 20))
    }

    @Test
    fun builtUdpChecksumVerifies() {
        val built = buildIpv4Udp(packet)
        val udpLength = built.size - 20
        val pseudo = onesSum(built, 12, 20) + 17 + udpLength
        assertEquals(0xFFFF, onesSum(built, 20, built.size, pseudo))
    }

    @Test
    fun roundTrip() {
        val built = buildIpv4Udp(packet)
        val parsed = parseIpv4Udp(built, built.size)!!
        assertArrayEquals(packet.srcIp, parsed.srcIp)
        assertArrayEquals(packet.dstIp, parsed.dstIp)
        assertEquals(packet.srcPort, parsed.srcPort)
        assertEquals(packet.dstPort, parsed.dstPort)
        assertArrayEquals(packet.payload, parsed.payload)
    }
}
