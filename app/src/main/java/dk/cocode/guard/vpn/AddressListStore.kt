package dk.cocode.guard.vpn

import dk.cocode.guard.iplist.AddressList
import dk.cocode.guard.iplist.Cidr
import dk.cocode.guard.iplist.ListState
import dk.cocode.guard.iplist.ListStatus
import dk.cocode.guard.iplist.RouteSet
import dk.cocode.guard.iplist.StoredList
import dk.cocode.guard.iplist.acceptList
import dk.cocode.guard.iplist.addressLists
import dk.cocode.guard.iplist.parseCidr
import dk.cocode.guard.iplist.parseFeodo
import dk.cocode.guard.iplist.parseSpamhausJson
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** What is on disk: the status of each list and the route set of the `Active` ones. */
class LoadedLists(val statuses: List<ListStatus>, val routes: RouteSet)

private const val TIMEOUT_MS = 30_000
private const val MAX_BODY = 2 * 1024 * 1024
private const val REFRESH_AFTER_MS = 24 * 60 * 60 * 1000L

/**
 * The lists in [dir] (one `<id>.txt` of CIDR lines each, its modification time the fetch time) and
 * their download straight from the publishers. Tests replace [open] (the HTTP connection) or [fetch]
 * (the whole download). Every function but [cancel] blocks: call it off the main thread.
 */
class AddressListStore(
    private val dir: File,
    private val open: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection },
    fetch: ((String) -> String?)? = null,
) {
    private val fetch: (String) -> String? = fetch ?: ::download

    @Volatile
    private var inFlight: HttpURLConnection? = null

    // Set by [cancel], cleared by the next [refresh]: a cancel that lands before the connection is
    // recorded still stops it, since [download] looks again once it has recorded it.
    @Volatile
    private var cancelled = false

    private fun file(list: AddressList) = File(dir, "${list.id}.txt")

    /** Drops a download in flight, so stopping protection never waits out its timeouts. Any thread. */
    fun cancel() {
        cancelled = true
        inFlight?.disconnect()
    }

    fun load(now: Long): LoadedLists {
        val stored = HashMap<AddressList, List<Cidr>>()
        val info = HashMap<AddressList, StoredList>()
        for (list in AddressList.entries) {
            val f = file(list).takeIf { it.isFile } ?: continue
            val cidrs = f.readLines().filterNot { it.startsWith("#") }.mapNotNull { parseCidr(it) }
            stored[list] = cidrs
            info[list] = StoredList(f.lastModified(), cidrs.size)
        }
        val statuses = addressLists(info, now)
        val active = statuses.filter { it.state == ListState.Active }.associate { it.list.id to stored.getValue(it.list) }
        return LoadedLists(statuses, RouteSet(active))
    }

    /** True when any list has no stored copy or one fetched more than 24 hours ago. */
    fun needsRefresh(now: Long): Boolean = AddressList.entries.any {
        val f = file(it)
        !f.isFile || now - f.lastModified() > REFRESH_AFTER_MS
    }

    /**
     * Fetches all three lists one after another; a list that fails keeps its last good copy. Once
     * [active] turns false (protection stopped) nothing more is fetched or written.
     */
    fun refresh(active: () -> Boolean = { true }) {
        cancelled = false
        dir.mkdirs()
        for (list in AddressList.entries) {
            if (!active() || cancelled) return
            try {
                val body = fetch(list.url) ?: continue
                val parsed = (if (list == AddressList.Feodo) parseFeodo(body) else parseSpamhausJson(body)) ?: continue
                val cidrs = acceptList(list.id, parsed) ?: continue
                if (!active()) return
                val temp = File(dir, "${list.id}.tmp")
                temp.writeText(sourceHeader(list, body) + cidrs.joinToString("\n"))
                Files.move(temp.toPath(), file(list).toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (e: Exception) {
                // Network, disk or parse trouble: this list keeps its last good copy, the others go on.
            }
        }
    }

    // Spamhaus asks that "the date and © text should remain with the file and data": its metadata
    // line (timestamp, copyright, terms) is kept as a `#` comment at the top of the stored list.
    private fun sourceHeader(list: AddressList, body: String): String =
        if (list == AddressList.Feodo) "" else
            body.lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() }?.let { "# $it\n" }.orEmpty()

    // Redirects are not followed: only the three listed URLs are ever fetched. Null unless 200 and small enough.
    private fun download(url: String): String? {
        val connection = open(url)
        inFlight = connection
        try {
            if (cancelled) return null
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = false
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            connection.inputStream.use { input ->
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    body.write(chunk, 0, n)
                    if (body.size() > MAX_BODY) return null
                }
            }
            return body.toString(Charsets.UTF_8.name())
        } finally {
            inFlight = null
            connection.disconnect()
        }
    }
}
