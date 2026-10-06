package dk.cocode.guard.vpn

import dk.cocode.guard.iplist.AddressList
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The real download path through a fake HTTP connection: no sockets, no name lookups. */
class AddressListDownloadTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class FakeConnection(private val code: Int, private val body: InputStream) :
        HttpURLConnection(URL("https://example.invalid/")) {
        override fun connect() = Unit
        override fun disconnect() = body.close()
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getInputStream() = body
    }

    /** Blocks every read until closed, like a server that stopped sending. */
    private class StalledBody : InputStream() {
        val closed = CountDownLatch(1)
        override fun read(): Int = read(ByteArray(1), 0, 1)
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            closed.await()
            throw IOException("closed")
        }
        override fun close() = closed.countDown()
    }

    private val opened = CopyOnWriteArrayList<FakeConnection>()
    private val feodo get() = File(folder.root, "feodo.txt")

    private fun store(code: Int = 200, body: (String) -> InputStream) = AddressListStore(folder.root, open = { url ->
        FakeConnection(code, body(url)).also { opened.add(it) }
    })

    private fun text(s: String) = ByteArrayInputStream(s.toByteArray())

    @Test
    fun okBodyIsStored() {
        store { if (it == AddressList.Feodo.url) text("203.0.113.7\n") else text("garbage") }.refresh()
        assertEquals("203.0.113.7/32", feodo.readText())
        assertEquals(3, opened.size)
    }

    @Test
    fun spamhausDateAndCopyrightStayWithTheFile() {
        // Spamhaus asks that "the date and © text should remain with the file and data".
        val meta = """{"type":"metadata","timestamp":1791254642,"copyright":"(c) 2026 The Spamhaus Project SLU"}"""
        val ranges = (0 until 40 step 2).joinToString("\n") { """{"cidr":"2a10:${it.toString(16)}::/32"}""" }
        val s = store { if (it == AddressList.DropV6.url) text("$ranges\n$meta\n") else text("garbage") }
        s.refresh()
        val stored = File(folder.root, "drop_v6.txt").readLines()
        assertEquals("# $meta", stored.first())
        val loaded = s.load(System.currentTimeMillis()).statuses.single { it.list == AddressList.DropV6 }
        assertEquals(20, loaded.entries) // the header line is not read as a range
    }

    @Test
    fun redirectIsNotFollowedAndStoresNothing() {
        store(code = 301) { text("203.0.113.7\n") }.refresh()
        assertFalse(feodo.exists())
        assertTrue(opened.none { it.instanceFollowRedirects })
    }

    @Test
    fun errorStatusStoresNothing() {
        store(code = 500) { text("203.0.113.7\n") }.refresh()
        assertFalse(feodo.exists())
    }

    @Test
    fun oversizeBodyIsRejected() {
        store { text("203.0.113.7\n" + "#".repeat(2 * 1024 * 1024)) }.refresh()
        assertFalse(feodo.exists())
    }

    @Test
    fun cancelDropsDownloadInFlight() {
        val stalled = StalledBody()
        val started = CountDownLatch(1)
        val s = AddressListStore(folder.root, open = { FakeConnection(200, stalled).also { started.countDown() } })
        val protecting = AtomicBoolean(true)
        val worker = Thread { s.refresh { protecting.get() } }.apply { start() }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        protecting.set(false) // protection stops: no further list is fetched...
        s.cancel() // ...and the one in flight is dropped at once
        worker.join(5_000)
        assertFalse(worker.isAlive)
        assertFalse(feodo.exists())
    }
}
