package dk.cocode.guard.vpn

import dk.cocode.guard.blocklist.BlockList
import dk.cocode.guard.dns.blockedAnswer
import dk.cocode.guard.dns.parseQuery
import dk.cocode.guard.dns.servfail
import dk.cocode.guard.net.UdpPacket
import dk.cocode.guard.net.buildIpv4Udp
import dk.cocode.guard.net.parseIpv4Udp
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

const val DNS_ADDRESS = "10.111.222.2"
private val DNS_ADDRESS_BYTES = byteArrayOf(10, 111, 222.toByte(), 2)
private const val DNS_PORT = 53
private const val MAX_PACKET = 32_768
private const val QUERY_PARALLELISM = 16

/**
 * Reads packets from the tunnel on one dedicated thread and answers each DNS query on its own
 * coroutine, so a slow upstream reply never holds up the read loop. [onFailure] runs on that
 * thread if reading fails while the loop is meant to be running.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PacketLoop(
    private val input: InputStream,
    private val output: OutputStream,
    private val blockList: BlockList,
    private val upstream: DnsUpstream,
    private val onFailure: (Throwable) -> Unit,
) {
    // Limited so slow upstream replies cannot exhaust the shared IO pool.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(QUERY_PARALLELISM))
    private val writeLock = Any()
    private val countLock = Any()

    @Volatile
    private var running = true
    private val thread = Thread(::readLoop, "guard-packet-loop")

    fun start() = thread.start()

    /** The caller closes the tunnel too, which is what unblocks the read. */
    fun stop() {
        // Taking the lock means no counter update is mid-flight, and none can follow: a query still
        // running from this loop can never touch the count of a later start.
        synchronized(countLock) { running = false }
        scope.cancel()
    }

    private fun countBlocked() = synchronized(countLock) {
        if (running) ProtectionRepository.update { it.copy(blockedCount = it.blockedCount + 1) }
    }

    private fun readLoop() {
        val buf = ByteArray(MAX_PACKET)
        try {
            while (running) {
                val n = input.read(buf)
                if (n < 0) throw IOException("tunnel closed")
                val packet = parseIpv4Udp(buf, n) ?: continue
                if (packet.dstIp.contentEquals(DNS_ADDRESS_BYTES) && packet.dstPort == DNS_PORT) {
                    scope.launch { answer(packet) }
                }
            }
        } catch (e: Exception) {
            if (running) onFailure(e)
        }
    }

    private suspend fun answer(p: UdpPacket) {
        val query = p.payload
        val q = parseQuery(query)
        val reply = when {
            // A query our strict parser cannot read is forwarded unchanged, never dropped.
            q == null -> forward(query)
            blockList.isBlocked(q.name) -> {
                countBlocked()
                blockedAnswer(query, q)
            }
            !upstream.hasNetwork -> servfail(query, q)
            else -> forward(query)
        } ?: return
        val packet = UdpPacket(p.dstIp, p.srcIp, p.dstPort, p.srcPort, reply)
        try {
            synchronized(writeLock) { output.write(buildIpv4Udp(packet)) }
        } catch (e: IOException) {
            // A failed write while running means replies no longer reach apps: report it, never hide it.
            if (running) onFailure(e)
        }
    }

    private suspend fun forward(query: ByteArray): ByteArray? {
        if (query.size < 2 || !upstream.hasNetwork) return null
        val reply = upstream.query(query)?.takeIf { it.size >= 2 } ?: return null
        reply[0] = query[0]
        reply[1] = query[1]
        return reply
    }
}
