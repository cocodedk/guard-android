package dk.cocode.guard.blocklist

/**
 * Domain rules from the AdGuard DNS filter. A rule covers its domain and every subdomain, and an
 * exception anywhere in a name's suffix chain wins over any block. Rules are lowercase.
 */
class BlockList(private val blocked: Set<String>, private val allowed: Set<String>) {
    /** Number of block rules. */
    val size: Int get() = blocked.size

    fun isBlocked(name: String): Boolean {
        val n = name.lowercase().removeSuffix(".")
        var hit = false
        var start = 0
        while (start < n.length) {
            val suffix = n.substring(start)
            if (suffix in allowed) return false
            if (suffix in blocked) hit = true
            start = n.indexOf('.', start) + 1
            if (start == 0) break
        }
        return hit
    }
}
