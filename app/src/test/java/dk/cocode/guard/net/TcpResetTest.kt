package dk.cocode.guard.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TcpResetTest {
    private val src4 = addr("10.111.1.1")
    private val dst4 = addr("203.0.113.7")
    private val src6 = addr("2001:db8::1")
    private val dst6 = addr("2001:db8::2")

    private fun reset(buf: ByteArray): ByteArray? = tcpResetFor(buf, parseIpPacket(buf, buf.size)!!)

    @Test
    fun resetForIpv4Syn() {
        val syn = v4Packet(PROTOCOL_TCP, src4, dst4, tcpSegment(40000, 443, 1000, 0x02, byteArrayOf(9, 9)))
        val r = reset(syn)
        assertNotNull(r)
        r!!
        assertEquals(40, r.size)
        assertArrayEquals(dst4, r.copyOfRange(12, 16))
        assertArrayEquals(src4, r.copyOfRange(16, 20))
        assertEquals(64, r.u8(8))
        assertEquals(443, r.u16(20))
        assertEquals(40000, r.u16(22))
        assertEquals(0, r.u16(24) + r.u16(26)) // sequence 0
        assertEquals(1000 + 1 + 2, (r.u16(28) shl 16) or r.u16(30)) // SYN's sequence + 1 + payload
        assertEquals(0x14, r.u8(33)) // RST+ACK
        assertEquals(0, r.u16(34)) // window 0
        assertEquals(0xFFFF, onesSum(r, 0, 20))
        assertEquals(0xFFFF, onesSum(r, 20, r.size, onesSum(r, 12, 20) + PROTOCOL_TCP + 20))
    }

    @Test
    fun resetForIpv6Syn() {
        val syn = v6Packet(PROTOCOL_TCP, src6, dst6, tcpSegment(40000, 443, 0xFFFFFFFFL, 0x02))
        val r = reset(syn)
        assertNotNull(r)
        r!!
        assertEquals(60, r.size)
        assertArrayEquals(dst6, r.copyOfRange(8, 24))
        assertArrayEquals(src6, r.copyOfRange(24, 40))
        assertEquals(443, r.u16(40))
        assertEquals(40000, r.u16(42))
        assertEquals(0, (r.u16(48) shl 16) or r.u16(50)) // the SYN's sequence + 1 wraps to 0
        assertEquals(0x14, r.u8(53))
        assertEquals(0xFFFF, onesSum(r, 40, r.size, onesSum(r, 8, 40) + PROTOCOL_TCP + 20))
    }

    @Test
    fun resetForIpv4SynWithOptions() {
        val syn = v4Packet(PROTOCOL_TCP, src4, dst4, tcpSegment(40000, 443, 1000, 0x02), optionWords = 2)
        val r = reset(syn)!!
        assertEquals(40, r.size) // the reply has no options
        assertEquals(443, r.u16(20))
        assertEquals(1001, (r.u16(28) shl 16) or r.u16(30))
        assertEquals(0xFFFF, onesSum(r, 20, r.size, onesSum(r, 12, 20) + PROTOCOL_TCP + 20))
    }

    @Test
    fun noResetForSynAck() {
        assertNull(reset(v4Packet(PROTOCOL_TCP, src4, dst4, tcpSegment(40000, 443, 1, 0x12))))
    }

    @Test
    fun noResetWithoutSyn() {
        assertNull(reset(v4Packet(PROTOCOL_TCP, src4, dst4, tcpSegment(40000, 443, 1, 0x10))))
        assertNull(reset(v6Packet(PROTOCOL_TCP, src6, dst6, tcpSegment(40000, 443, 1, 0x18))))
        assertNull(reset(v4Packet(PROTOCOL_UDP, src4, dst4, udpDatagram(40000, 443, ByteArray(20)))))
    }
}
