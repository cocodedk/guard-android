package dk.cocode.guard.iplist

private val CIDR_FIELD = Regex(""""cidr"\s*:\s*"([^"]+)"""")
private val METADATA = Regex(""""type"\s*:\s*"metadata"""")

private const val MIN_DROP_V4 = 500
private const val MIN_DROP_V6 = 20

/**
 * Spamhaus JSON lines: one whole object per line, the last the metadata. Null when any line is cut
 * off, or anything follows the metadata or it is missing: a broken download never replaces a good copy.
 */
fun parseSpamhausJson(body: String): List<Cidr>? {
    val lines = body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    if (lines.isEmpty() || !METADATA.containsMatchIn(lines.last())) return null
    if (lines.any { !(it.startsWith("{") && it.endsWith("}")) }) return null
    return lines.dropLast(1).mapNotNull { line -> CIDR_FIELD.find(line)?.let { parseCidr(it.groupValues[1]) } }
}

/** Feodo text: `#` comments and blank lines are skipped, every other line must be one IPv4 address. Null otherwise. */
fun parseFeodo(body: String): List<Cidr>? {
    val out = ArrayList<Cidr>()
    for (raw in body.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) continue
        out.add(parseCidr("$line/32")?.takeIf { it.address.size == 4 } ?: return null)
    }
    return out
}

/** Drops reserved ranges, merges the rest and rejects a list too small to be a real download. Null when rejected. */
fun acceptList(id: String, cidrs: List<Cidr>): List<Cidr>? {
    val kept = cidrs.filterNot(::isReserved).merged()
    val minimum = when (id) {
        "drop_v4" -> MIN_DROP_V4
        "drop_v6" -> MIN_DROP_V6
        else -> 0
    }
    return kept.takeIf { it.size >= minimum }
}
