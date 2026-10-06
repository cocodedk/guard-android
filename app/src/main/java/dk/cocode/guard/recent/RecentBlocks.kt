package dk.cocode.guard.recent

enum class BlockKind { Name, Address }

/** One blocked lookup or refused connection; [app] is null when Android cannot tell who asked. */
data class BlockEvent(val kind: BlockKind, val target: String, val app: String?)

/** What one app tried to reach: how many kept events, and its newest distinct targets. */
data class AppBlocks(val app: String?, val count: Int, val latest: List<String>)

private const val LATEST_TARGETS = 3

/**
 * The newest [capacity] block events of one start, kept in memory only. Lookups are answered on
 * several threads, so every call is synchronized.
 */
class RecentBlocks(private val capacity: Int = 200) {
    private val events = ArrayDeque<BlockEvent>()

    @Synchronized
    fun add(event: BlockEvent) {
        if (events.size >= capacity) events.removeFirst()
        events.addLast(event)
    }

    /** At most [max] apps, the one with the newest event first; null is its own group. */
    @Synchronized
    fun groups(max: Int = 5): List<AppBlocks> = events.asReversed().groupBy { it.app }
        .map { (app, list) -> AppBlocks(app, list.size, list.map { it.target }.distinct().take(LATEST_TARGETS)) }
        .take(max)

    @Synchronized
    fun clear() = events.clear()
}
