package dk.cocode.guard.blocklist

private val DOMAIN = Regex("[a-z0-9_-]+(\\.[a-z0-9_-]+)*")

// Sized for the shipped list (about 177,700 rules) so loading it never rehashes.
private const val EXPECTED_BLOCKED = 260_000
private const val EXPECTED_ALLOWED = 4_000

/** Keeps only `||domain^` blocks and `@@||domain^` / `@@||domain^|` exceptions; skips the rest. */
fun parseRules(lines: Sequence<String>): BlockList {
    val blocked = HashSet<String>(EXPECTED_BLOCKED)
    val allowed = HashSet<String>(EXPECTED_ALLOWED)
    for (line in lines) {
        when {
            line.startsWith("||") && line.endsWith("^") ->
                domain(line, 2, line.length - 1)?.let(blocked::add)
            line.startsWith("@@||") && line.endsWith("^") ->
                domain(line, 4, line.length - 1)?.let(allowed::add)
            line.startsWith("@@||") && line.endsWith("^|") ->
                domain(line, 4, line.length - 2)?.let(allowed::add)
        }
    }
    return BlockList(blocked, allowed)
}

private fun domain(line: String, from: Int, to: Int): String? {
    if (to <= from) return null
    val name = line.substring(from, to).lowercase()
    return name.takeIf { DOMAIN.matches(it) }
}
