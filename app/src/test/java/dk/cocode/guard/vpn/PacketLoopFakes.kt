package dk.cocode.guard.vpn

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred

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
