package dk.cocode.guard.iplist

/** The last [capacity] keys seen; the oldest is forgotten first. Not thread-safe. */
class RecentFlows(private val capacity: Int) {
    private val seen = object : LinkedHashMap<Any, Unit>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, Unit>) = size > capacity
    }

    /** True the first time [key] is seen (or again after it was forgotten). */
    fun firstTime(key: Any): Boolean = seen.put(key, Unit) == null
}
