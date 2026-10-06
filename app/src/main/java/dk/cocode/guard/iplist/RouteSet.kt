package dk.cocode.guard.iplist

/**
 * The ranges of the active lists, by list id; [routes] is every range once, none inside another.
 * Two sets are equal when their lists are, so a change of membership counts even if the routes match.
 */
class RouteSet(private val lists: Map<String, List<Cidr>>) {
    val routes: List<Cidr> = lists.values.flatten().merged()
    val size: Int get() = routes.size

    /** The id of a list that holds [ip], or null when no list does. */
    fun listFor(ip: ByteArray): String? = lists.entries.firstOrNull { (_, cidrs) -> cidrs.any { it.contains(ip) } }?.key

    override fun equals(other: Any?) = other is RouteSet && lists == other.lists

    override fun hashCode() = lists.hashCode()
}
