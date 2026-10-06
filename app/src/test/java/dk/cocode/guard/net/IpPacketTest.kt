package dk.cocode.guard.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IpPacketTest {
    private val src4 = addr("10.111.1.1")
    private val dst4 = addr("203.0.113.7")
    private val src6 = addr("2001:db8::1")
    private val dst6 = addr("2001:db8::2")
    private val udp = udpDatagram(40000, 53, byteArrayOf(1, 2, 3))

    @Test
    fun parsesIpv4WithOptions() {
        val buf = v4Packet(PROTOCOL_UDP, src4, dst4, udp, optionWords = 1)
        val p = parseIpPacket(buf, buf.size)
        assertNotNull(p)
        assertEquals(4, p!!.version)
        assertEquals(PROTOCOL_UDP, p.protocol)
        assertEquals(24, p.headerLength)
        assertEquals(buf.size, p.totalLength)
        assertArrayEquals(src4, p.srcIp)
        assertArrayEquals(dst4, p.dstIp)
    }

    @Test
    fun parsesIpv6Tcp() {
        val buf = v6Packet(PROTOCOL_TCP, src6, dst6, tcpSegment(40000, 443, 1, 0x02))
        val p = parseIpPacket(buf, buf.size)
        assertNotNull(p)
        assertEquals(6, p!!.version)
        assertEquals(PROTOCOL_TCP, p.protocol)
        assertEquals(40, p.headerLength)
        assertEquals(buf.size, p.totalLength)
        assertArrayEquals(src6, p.srcIp)
        assertArrayEquals(dst6, p.dstIp)
    }

    @Test
    fun rejectsIpv6ExtensionHeader() {
        val buf = v6Packet(0, src6, dst6, udp) // next header 0: hop-by-hop options
        assertNull(parseIpPacket(buf, buf.size))
    }

    @Test
    fun rejectsTruncated() {
        val v4 = v4Packet(PROTOCOL_UDP, src4, dst4, udp)
        val v6 = v6Packet(PROTOCOL_UDP, src6, dst6, udp)
        assertNull(parseIpPacket(v4, v4.size - 1))
        assertNull(parseIpPacket(v6, v6.size - 1))
        assertNull(parseIpPacket(v4, 10))
        assertNull(parseIpPacket(v4, 0))
    }

    @Test
    fun rejectsIpv4Fragment() {
        val more = v4Packet(PROTOCOL_UDP, src4, dst4, udp).also { it.put16(6, 0x2000) }
        val later = v4Packet(PROTOCOL_UDP, src4, dst4, udp).also { it.put16(6, 0x0010) }
        assertNull(parseIpPacket(more, more.size))
        assertNull(parseIpPacket(later, later.size))
    }
}
