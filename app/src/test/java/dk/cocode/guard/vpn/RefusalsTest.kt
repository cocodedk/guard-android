package dk.cocode.guard.vpn

import dk.cocode.guard.blocklist.parseRules
import dk.cocode.guard.iplist.RouteSet
import dk.cocode.guard.iplist.parseCidr
import dk.cocode.guard.net.PROTOCOL_TCP
import dk.cocode.guard.net.PROTOCOL_UDP
import dk.cocode.guard.net.addr
import dk.cocode.guard.net.tcpSegment
import dk.cocode.guard.net.udpDatagram
import dk.cocode.guard.net.v4Packet
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The refusal path through a real PacketLoop; the tunnel is a pair of in-memory queues. */
class RefusalsTest {
    private val incoming = LinkedBlockingQueue<ByteArray>()
    private val reading = Semaphore(0)
    private val written = LinkedBlockingQueue<ByteArray>()
    private val input = object : InputStream() {
        override fun read() = throw UnsupportedOperationException()
        override fun read(b: ByteArray): Int {
            reading.release() // a loop is now waiting on the tunnel
            val p = incoming.take()
            if (p.isEmpty()) return -1
            p.copyInto(b)
            return p.size
        }
    }
    private val output = object : OutputStream() {
        override fun write(b: Int) = throw UnsupportedOperationException()
        override fun write(b: ByteArray) {
            written.put(b.copyOf())
        }
    }
    private val noDns = object : DnsUpstream {
        override val hasNetwork = false
        override suspend fun query(payload: ByteArray): ByteArray? = null
    }

    private val client = addr("10.111.1.1")
    private val listed = addr("203.0.113.7")
    private val notices = CopyOnWriteArrayList<Triple<String, String, Int>>()
    private val apps = CopyOnWriteArrayList<String?>()
    private var loop: PacketLoop? = null

    @Before
    fun resetState() = ProtectionRepository.update { ProtectionState() }

    @After
    fun stopLoop() {
        loop?.stop()
        incoming.put(ByteArray(0))
    }

    private fun start() {
        val routes = RouteSet(mapOf("feodo" to listOf(parseCidr("203.0.113.0/24")!!)))
        val refusals = Refusals { address, list, number, app -> notices.add(Triple(address, list, number)); apps.add(app) }
        loop = PacketLoop(input, output, parseRules(emptySequence()), noDns, routes, refusals) {}
        loop!!.start()
    }

    private fun syn(srcPort: Int, to: ByteArray = listed, flags: Int = 0x02) =
        incoming.put(v4Packet(PROTOCOL_TCP, client, to, tcpSegment(srcPort, 443, 1000, flags)))

    private fun reply(): ByteArray {
        val bytes = written.poll(5, TimeUnit.SECONDS)
        assertNotNull(bytes)
        return bytes!!
    }

    @Test
    fun synToListedAddressIsResetAndCountedOnce() {
        start()
        syn(40000)
        syn(40000) // a retransmit of the same SYN
        syn(40001) // a new flow to the same address
        repeat(3) { assertEquals(0x14, reply()[33].toInt()) }
        assertEquals(2, ProtectionRepository.state.value.blockedAddressCount)
        assertEquals(listOf(Triple("203.0.113.7", "feodo", 1)), notices.toList())
    }

    @Test
    fun udpToListedAddressGetsIcmpProhibited() {
        start()
        incoming.put(v4Packet(PROTOCOL_UDP, client, listed, udpDatagram(40000, 9999, byteArrayOf(1, 2, 3))))
        val r = reply()
        assertEquals(3, r[20].toInt())
        assertEquals(13, r[21].toInt())
        assertEquals(1, ProtectionRepository.state.value.blockedAddressCount)
    }

    @Test
    fun otherPacketsGetNoReplyAndAreNotCounted() {
        start()
        syn(40000, flags = 0x10) // not a SYN
        syn(40000, to = addr("198.51.100.1")) // not listed
        syn(40001) // the one that is refused
        reply()
        assertTrue(written.isEmpty())
        assertEquals(1, ProtectionRepository.state.value.blockedAddressCount)
    }

    @Test
    fun stoppedLoopLeavesTheFlowToTheNextLoop() {
        val refusals = Refusals { address, list, number, app -> notices.add(Triple(address, list, number)); apps.add(app) }
        val routes = RouteSet(mapOf("feodo" to listOf(parseCidr("203.0.113.0/24")!!)))
        val old = PacketLoop(input, output, parseRules(emptySequence()), noDns, routes, refusals) {}
        old.start()
        assertTrue(reading.tryAcquire(5, TimeUnit.SECONDS))
        old.stop() // a tunnel swap retires it while its read is still waiting
        syn(40000) // the read it was waiting on still arrives
        reply() // the old loop refuses it, but must not count it or remember the flow
        assertEquals(0, ProtectionRepository.state.value.blockedAddressCount)
        loop = PacketLoop(input, output, parseRules(emptySequence()), noDns, routes, refusals) {}
        loop!!.start()
        syn(40000) // the same flow, now through the new tunnel
        reply()
        assertEquals(1, ProtectionRepository.state.value.blockedAddressCount)
        assertEquals(listOf(Triple("203.0.113.7", "feodo", 1)), notices.toList())
    }

    @Test
    fun noticeNamesTheAppAskedOncePerAddress() {
        val asked = CopyOnWriteArrayList<Int>()
        val routes = RouteSet(mapOf("feodo" to listOf(parseCidr("203.0.113.0/24")!!)))
        val refusals = Refusals { address, list, number, app -> notices.add(Triple(address, list, number)); apps.add(app) }
        loop = PacketLoop(input, output, parseRules(emptySequence()), noDns, routes, refusals, { proto, _, srcPort, _, dstPort ->
            asked.add(srcPort)
            "Chrome".takeIf { proto == PROTOCOL_TCP && dstPort == 443 }
        }) {}
        loop!!.start()
        syn(40000)
        syn(40001) // a second flow to the same address: counted, but no second notice and no second lookup
        reply()
        reply()
        assertEquals(listOf("Chrome"), apps.toList())
        assertEquals(listOf(40000), asked.toList())
        assertEquals(2, ProtectionRepository.state.value.blockedAddressCount)
    }

    @Test
    fun noticesStopAtTwenty() {
        val seen = ArrayList<Int>()
        val refusals = Refusals { _, _, number, _ -> seen.add(number) }
        repeat(25) { refusals.announce("203.0.113.$it", "feodo") }
        refusals.announce("203.0.113.0", "feodo") // already announced
        assertEquals((1..MAX_ADDRESS_NOTICES).toList(), seen)
    }
}
