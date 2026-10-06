package dk.cocode.guard.vpn

import dk.cocode.guard.iplist.AddressList
import dk.cocode.guard.iplist.ListState
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val DAY = 24L * 60 * 60 * 1000
private const val NOW = 100L * DAY

class AddressListStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val urls = ArrayList<String>()
    private var feodoBody: String? = "# c\n203.0.113.7\n"
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File(folder.root, "iplists")
        ProtectionRepository.update { ProtectionState() }
    }

    // Spamhaus bodies are too small to pass plausibility, so only Feodo can succeed here.
    private fun fetch(url: String): String? {
        urls.add(url)
        return if (url == AddressList.Feodo.url) feodoBody else "garbage"
    }

    private fun store() = AddressListStore(dir, ::fetch)

    private fun feodoFile() = File(dir, "feodo.txt")

    @Test
    fun goodDownloadIsStoredAndOthersKeepNothing() {
        store().refresh()
        assertEquals(AddressList.entries.map { it.url }, urls) // only the three listed URLs, in order
        assertEquals("203.0.113.7/32", feodoFile().readText())
        assertFalse(File(dir, "drop_v4.txt").exists())
        assertFalse(File(dir, "feodo.tmp").exists())
    }

    @Test
    fun failedDownloadKeepsLastGoodCopy() {
        store().refresh()
        feodoFile().setLastModified(NOW - 2 * DAY)
        feodoBody = "not an ip\n"
        store().refresh()
        assertEquals("203.0.113.7/32", feodoFile().readText())
        assertEquals(NOW - 2 * DAY, feodoFile().lastModified())
    }

    @Test
    fun stoppingDuringRefreshFetchesAndWritesNothingMore() {
        var calls = 0
        store().refresh { calls++ < 1 } // active for the first check only
        assertEquals(1, urls.size)
        assertFalse(feodoFile().exists())
    }

    @Test
    fun oldListIsNotUsedButStaysOnDisk() {
        store().refresh()
        feodoFile().setLastModified(NOW - 8 * DAY)
        val loaded = store().load(NOW)
        assertEquals(ListState.TooOld, loaded.statuses[2].state)
        assertEquals(0, loaded.routes.size)
        assertTrue(feodoFile().exists())
        feodoFile().setLastModified(NOW - DAY)
        assertEquals(1, store().load(NOW).routes.size)
    }

    @Test
    fun refreshIsDueWhenMissingOrOlderThanADay() {
        assertTrue(store().needsRefresh(NOW))
        dir.mkdirs()
        AddressList.entries.forEach {
            File(dir, "${it.id}.txt").also { f -> f.writeText("") }.setLastModified(NOW - DAY + 1000)
        }
        assertFalse(store().needsRefresh(NOW))
        feodoFile().setLastModified(NOW - DAY - 1000)
        assertTrue(store().needsRefresh(NOW))
    }

    @Test
    fun failingSourceIsTriedOnceAnHour() {
        var now = NOW
        val updates = AddressListUpdates(store(), { now })
        updates.check()
        val first = urls.size
        now += 30 * 60 * 1000
        updates.check()
        assertEquals(first, urls.size) // too soon
        now += 31 * 60 * 1000
        updates.check()
        assertEquals(2 * first, urls.size) // an hour has passed; the Spamhaus lists still fail
    }

    @Test
    fun watchSwapsWhenAListExpiresAndStopsWhenCancelled() = runBlocking {
        store().refresh()
        var now = NOW
        val updates = AddressListUpdates(store(), { now })
        feodoFile().setLastModified(NOW)
        val initial = updates.load().routes
        assertEquals(1, initial.size)
        val swapped = CompletableDeferred<Int>()
        now = NOW + 8 * DAY // the list has aged out when the check runs, and no source answers
        feodoBody = null
        val job = launch(Dispatchers.Default) {
            updates.watch({ initial }, { swapped.complete(it.size) }, { swapped.complete(-1) })
        }
        assertEquals(0, withTimeout(10_000) { swapped.await() })
        job.cancel()
        job.join()
    }
}
