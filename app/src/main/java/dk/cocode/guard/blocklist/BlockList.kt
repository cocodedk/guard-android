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
            start = nextBoundary(n, start)
            if (start < 0) break
        }
        return hit
    }

    // A suffix starts only after a dot that is not escaped (preceded by an odd number of backslashes).
    // A suffix that still holds an escaped dot matches no rule, since rule domains never contain one.
    private fun nextBoundary(n: String, from: Int): Int {
        var i = from
        while (i < n.length) {
            if (n[i] == '.') {
                var slashes = 0
                while (i - 1 - slashes >= 0 && n[i - 1 - slashes] == '\\') slashes++
                if (slashes % 2 == 0) return i + 1
            }
            i++
        }
        return -1
    }
}
