package dk.cocode.guard.iplist

/** The [capacity] keys seen most recently; the one unseen for longest is forgotten first. Not thread-safe. */
class RecentFlows(private val capacity: Int) {
    // Access order: seeing a key again makes it the most recent, so an active flow is never forgotten.
    private val seen = object : LinkedHashMap<Any, Unit>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, Unit>) = size > capacity
    }

    /** True the first time [key] is seen (or again after it was forgotten). */
    fun firstTime(key: Any): Boolean = seen.put(key, Unit) == null
}
