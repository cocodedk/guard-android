package dk.cocode.guard.iplist

/**
 * The three bad-address lists. [source] is shown as is on the screen; [title] names the list in a
 * notification. Nothing but these three URLs is ever fetched.
 */
enum class AddressList(val source: String, val id: String, val title: String, val url: String) {
    DropV4("Spamhaus DROP (IPv4)", "drop_v4", "Spamhaus DROP", "https://www.spamhaus.org/drop/drop_v4.json"),
    DropV6("Spamhaus DROP (IPv6)", "drop_v6", "Spamhaus DROPv6", "https://www.spamhaus.org/drop/drop_v6.json"),
    Feodo(
        "abuse.ch Feodo Tracker", "feodo", "abuse.ch Feodo Tracker",
        "https://feodotracker.abuse.ch/downloads/ipblocklist_recommended.txt",
    ),
}

enum class ListState { NotYet, Active, TooOld }

/** A list on disk: [fetched] is epoch ms, [entries] the ranges kept after filtering. */
data class StoredList(val fetched: Long, val entries: Int)

data class ListStatus(val list: AddressList, val state: ListState, val entries: Int, val fetched: Long?)

/** A stored list older than this is not used: its ranges may belong to innocent owners by now. */
const val MAX_LIST_AGE_MS = 7 * 24 * 60 * 60 * 1000L

/** One status per list, in [AddressList] order; only `Active` lists feed the route set. */
fun addressLists(stored: Map<AddressList, StoredList>, now: Long): List<ListStatus> = AddressList.entries.map { list ->
    val s = stored[list]
    when {
        s == null -> ListStatus(list, ListState.NotYet, 0, null)
        now - s.fetched <= MAX_LIST_AGE_MS -> ListStatus(list, ListState.Active, s.entries, s.fetched)
        else -> ListStatus(list, ListState.TooOld, 0, s.fetched)
    }
}
