package dk.cocode.guard.vpn

import dk.cocode.guard.blocklist.parseRules
import dk.cocode.guard.net.UdpPacket
import dk.cocode.guard.net.buildIpv4Udp
import dk.cocode.guard.recent.AppBlocks
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Which app asked for a blocked name: the owner is looked up before the reply is written. */
class PacketLoopOwnerTest {
    private val tunnel = FakeTunnel()
    private val client = byteArrayOf(10, 111, 1, 1)
    private val dns = byteArrayOf(10, 111, 222.toByte(), 2)
    private val asked = CopyOnWriteArrayList<UdpPacket>()
    private var loop: PacketLoop? = null

    @Before
    fun resetState() {
        ProtectionRepository.starting(alwaysOn = false)
    }

    @After
    fun stopLoop() {
        loop?.stop()
        tunnel.incoming.put(ByteArray(0))
    }

    private fun start(upstream: DnsUpstream, owner: String?) {
        loop = PacketLoop(
            tunnel, tunnel.out, parseRules(sequenceOf("||ads.example.com^")), upstream,
            ownerOf = { asked.add(it); owner },
        ) {}
        loop!!.start()
    }

    private fun askAndWait(name: String) {
        tunnel.incoming.put(buildIpv4Udp(UdpPacket(client, dns, 40000, 53, dnsQuery(name))))
        assertNotNull(tunnel.written.poll(5, TimeUnit.SECONDS))
    }

    @Test
    fun blockedNameRecordsTheOwner() {
        start(FakeUpstream(), "Chrome")
        askAndWait("ads.example.com")
        assertEquals(listOf(AppBlocks("Chrome", 1, listOf("ads.example.com"))), ProtectionRepository.state.value.recentBlocks)
        val p = asked.single() // the query's own socket: its source, and the fake DNS address
        assertTrue(p.srcIp.contentEquals(client) && p.dstIp.contentEquals(dns))
        assertEquals(40000, p.srcPort)
        assertEquals(53, p.dstPort)
    }

    @Test
    fun blockedNameWithUnknownOwnerIsRecordedAsNull() {
        start(FakeUpstream(), null)
        askAndWait("ads.example.com")
        assertEquals(listOf(AppBlocks(null, 1, listOf("ads.example.com"))), ProtectionRepository.state.value.recentBlocks)
    }

    @Test
    fun forwardedNameAsksNothingAndRecordsNothing() {
        val up = FakeUpstream(reply = byteArrayOf(0, 0, -128, 0))
        start(up, "Chrome")
        askAndWait("fine.example.org")
        assertTrue(asked.isEmpty())
        assertTrue(ProtectionRepository.state.value.recentBlocks.isEmpty())
    }
}
