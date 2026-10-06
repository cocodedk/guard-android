package dk.cocode.guard.vpn

import dk.cocode.guard.iplist.RouteSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val HOUR_MS = 60 * 60 * 1000L

/** Decides when the lists are fetched: a failing source is tried at most once an hour. */
class AddressListUpdates(
    private val store: AddressListStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    // Kept in memory only: the time of the last attempt, not of the last success.
    @Volatile
    private var lastAttempt: Long? = null

    /** Drops a download in flight; called when protection stops. Any thread. */
    fun cancel() = store.cancel()

    /** The lists on disk. Blocks: call off the main thread. */
    fun load(): LoadedLists = store.load(clock())

    /**
     * Downloads if a list is due and the last attempt was an hour or more ago, then loads. Blocks too.
     * Downloading stops once [active] turns false.
     */
    fun check(active: () -> Boolean = { true }): LoadedLists {
        val now = clock()
        val last = lastAttempt
        if (store.needsRefresh(now) && (last == null || now - last >= HOUR_MS)) {
            lastAttempt = now
            store.refresh(active)
        }
        return load()
    }

    /**
     * Now and every hour: checks the lists, publishes their state, and calls [swap] when the lists
     * differ from [current] (a download changed them, or a list aged past 7 days), so the route set
     * the loop attributes addresses with is never an expired one. Runs on the main thread.
     */
    suspend fun watch(current: () -> RouteSet, swap: (RouteSet) -> Unit, onFailure: () -> Unit) {
        try {
            while (true) {
                val lists = withContext(Dispatchers.IO) { check { isActive } }
                ProtectionRepository.update { it.copy(addressLists = lists.statuses) }
                if (lists.routes != current()) swap(lists.routes)
                delay(HOUR_MS)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure()
        }
    }
}
