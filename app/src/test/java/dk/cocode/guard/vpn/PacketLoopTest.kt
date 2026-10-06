package dk.cocode.guard.vpn

import dk.cocode.guard.blocklist.parseRules
import dk.cocode.guard.net.UdpPacket
import dk.cocode.guard.net.buildIpv4Udp
import dk.cocode.guard.net.parseIpv4Udp
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The tunnel and the network are fakes (PacketLoopFakes.kt); no sockets, no Android. */
class PacketLoopTest {
    private val tunnel = FakeTunnel()
    private val client = byteArrayOf(10, 111, 1, 1)
    private val dns = byteArrayOf(10, 111, 222.toByte(), 2)
    private var loop: PacketLoop? = null
    @Volatile
    private var failure: Throwable? = null

    @Before
    fun resetState() = ProtectionRepository.update { ProtectionState() }

    @After
    fun stopLoop() {
        loop?.stop()
        tunnel.incoming.put(ByteArray(0))
    }

    private fun query(name: String, type: Int = 1, id: Int = 0x1234): ByteArray {
        val q = ArrayList<Byte>()
        listOf(id shr 8, id, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0).forEach { q.add(it.toByte()) }
        name.split('.').forEach { l ->
            q.add(l.length.toByte())
            l.forEach { q.add(it.code.toByte()) }
        }
        listOf(0, type shr 8, type, 0, 1).forEach { q.add(it.toByte()) }
        return q.toByteArray()
    }

    private fun send(payload: ByteArray, to: ByteArray = dns, port: Int = 53) =
        tunnel.incoming.put(buildIpv4Udp(UdpPacket(client, to, 40000, port, payload)))

    private fun start(upstream: DnsUpstream, list: String = "||ads.example.com^") {
        loop = PacketLoop(tunnel, tunnel.out, parseRules(sequenceOf(list)), upstream) { failure = it }
        loop!!.start()
    }

    private fun reply(): UdpPacket {
        val bytes = tunnel.written.poll(5, TimeUnit.SECONDS)
        assertNotNull(bytes)
        return parseIpv4Udp(bytes!!, bytes.size)!!
    }

    @Test
    fun blockedNameIsAnsweredLocallyAndCounted() {
        val up = FakeUpstream()
        start(up)
        send(query("ads.example.com"))
        val r = reply()
        assertEquals(53, r.srcPort)
        assertEquals(40000, r.dstPort)
        assertTrue(r.dstIp.contentEquals(client))
        assertEquals(1, r.payload[7].toInt()) // ANCOUNT
        assertTrue(up.queries.isEmpty())
        assertEquals(1, ProtectionRepository.state.value.blockedCount)
    }

    @Test
    fun stoppedLoopNeverCountsAgain() {
        start(FakeUpstream())
        loop!!.stop()
        send(query("ads.example.com"))
        assertNull(tunnel.written.poll(300, TimeUnit.MILLISECONDS))
        assertEquals(0, ProtectionRepository.state.value.blockedCount)
    }

    @Test
    fun otherNameIsForwardedWithTheQueryId() {
        val up = FakeUpstream(reply = query("www.example.com", id = 0x9999))
        start(up)
        send(query("www.example.com"))
        val r = reply()
        assertEquals(0x12, r.payload[0].toInt())
        assertEquals(0x34, r.payload[1].toInt())
        assertEquals(0, ProtectionRepository.state.value.blockedCount)
    }

    @Test
    fun rejectedQueryIsAnsweredFormerrNeverForwarded() {
        val up = FakeUpstream(reply = query("ads.example.com"))
        start(up)
        val twoQuestions = query("ads.example.com").also { it[5] = 2 }
        send(twoQuestions)
        val r = reply()
        assertEquals(12, r.payload.size)
        assertEquals(0x12, r.payload[0].toInt())
        assertEquals(1, r.payload[3].toInt() and 0x0F) // RCODE FORMERR
        assertTrue(up.queries.isEmpty())
    }

    @Test
    fun truncatedPayloadIsDroppedNeverForwarded() {
        val up = FakeUpstream(reply = query("www.example.com"))
        start(up)
        send(byteArrayOf(0x12, 0x34, 0x01))
        send(query("ads.example.com")) // a later query still gets through
        reply()
        assertTrue(up.queries.isEmpty())
        assertTrue(tunnel.written.isEmpty())
    }

    @Test
    fun pendingQueriesAreCapped() {
        val up = StuckUpstream()
        start(up)
        repeat(MAX_PENDING + 20) { send(query("www.example.com", id = it)) }
        val deadline = System.currentTimeMillis() + 5_000
        while (up.asked.get() < MAX_PENDING && System.currentTimeMillis() < deadline) Thread.sleep(10)
        Thread.sleep(300)
        assertEquals(MAX_PENDING, up.asked.get())
        up.release.complete(Unit)
        // Once the stuck queries end, new ones are admitted again.
        val freed = System.currentTimeMillis() + 5_000
        while (up.asked.get() == MAX_PENDING && System.currentTimeMillis() < freed) {
            send(query("www.example.com"))
            Thread.sleep(50)
        }
        assertTrue(up.asked.get() > MAX_PENDING)
    }

    @Test
    fun noNetworkAnswersServfailAtOnce() {
        val up = FakeUpstream(hasNetwork = false)
        start(up)
        send(query("www.example.com"))
        val r = reply()
        assertEquals(2, r.payload[3].toInt() and 0x0F) // RCODE
        assertTrue(up.queries.isEmpty())
    }

    @Test
    fun upstreamTimeoutSendsNothing() {
        val up = FakeUpstream(reply = null)
        start(up)
        send(query("www.example.com"))
        assertNotNull(up.queries.poll(5, TimeUnit.SECONDS))
        assertNull(tunnel.written.poll(300, TimeUnit.MILLISECONDS))
    }

    @Test
    fun otherTrafficIsDroppedSilently() {
        val up = FakeUpstream(reply = query("www.example.com"))
        start(up)
        send(query("www.example.com"), port = 853)
        send(query("www.example.com"), to = byteArrayOf(8, 8, 8, 8))
        send(query("ads.example.com")) // a later query still gets through
        reply()
        assertTrue(up.queries.isEmpty())
        assertTrue(tunnel.written.isEmpty())
    }

    @Test
    fun writeFailureIsReportedWhileRunning() {
        val broken = object : OutputStream() {
            override fun write(b: Int) = throw IOException("tunnel gone")
        }
        loop = PacketLoop(tunnel, broken, parseRules(sequenceOf("||ads.example.com^")), FakeUpstream()) { failure = it }
        loop!!.start()
        send(query("ads.example.com"))
        val deadline = System.currentTimeMillis() + 5_000
        while (failure == null && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(failure is IOException)
    }

    @Test
    fun readFailureIsReportedWhileRunning() {
        start(FakeUpstream())
        tunnel.incoming.put(ByteArray(0))
        val deadline = System.currentTimeMillis() + 5_000
        while (failure == null && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(failure is IOException)
    }
}
