package dk.cocode.guard.vpn

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred

/** A DNS query for [name], as a client would send it. */
internal fun dnsQuery(name: String, type: Int = 1, id: Int = 0x1234): ByteArray {
    val q = ArrayList<Byte>()
    listOf(id shr 8, id, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0).forEach { q.add(it.toByte()) }
    name.split('.').forEach { l ->
        q.add(l.length.toByte())
        l.forEach { q.add(it.code.toByte()) }
    }
    listOf(0, type shr 8, type, 0, 1).forEach { q.add(it.toByte()) }
    return q.toByteArray()
}

/** The tunnel as a pair of in-memory queues: tests put packets in and read what the loop wrote. */
internal class FakeTunnel : InputStream() {
    val incoming = LinkedBlockingQueue<ByteArray>()
    val written = LinkedBlockingQueue<ByteArray>()
    val out = object : OutputStream() {
        override fun write(b: Int) = throw UnsupportedOperationException()
        override fun write(b: ByteArray) {
            written.put(b.copyOf())
        }
    }

    override fun read() = throw UnsupportedOperationException()
    override fun read(b: ByteArray): Int {
        val p = incoming.take()
        if (p.isEmpty()) return -1
        p.copyInto(b)
        return p.size
    }
}

internal class FakeUpstream(override var hasNetwork: Boolean = true, val reply: ByteArray? = null) : DnsUpstream {
    val queries = LinkedBlockingQueue<ByteArray>()
    override suspend fun query(payload: ByteArray): ByteArray? {
        queries.put(payload)
        return reply?.copyOf()
    }
}

/** Never answers until released, like a resolver that has stopped replying. */
internal class StuckUpstream : DnsUpstream {
    override val hasNetwork = true
    val asked = AtomicInteger()
    val release = CompletableDeferred<Unit>()
    override suspend fun query(payload: ByteArray): ByteArray? {
        asked.incrementAndGet()
        release.await()
        return null
    }
}
