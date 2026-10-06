package dk.cocode.guard.iplist

private val RESERVED: List<Cidr> = listOf(
    // IPv4: this network, private, carrier-grade NAT, loopback, link-local, protocol, benchmarking,
    // multicast and the rest of the reserved space.
    "0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8", "169.254.0.0/16", "172.16.0.0/12",
    "192.0.0.0/24", "192.168.0.0/16", "198.18.0.0/15", "224.0.0.0/4", "240.0.0.0/4",
    // IPv6: the low block, NAT64 (IPv4 on IPv6-only mobile networks), unique local, link-local, multicast.
    "::/8", "64:ff9b::/96", "fc00::/7", "fe80::/10", "ff00::/8",
).map { requireNotNull(parseCidr(it)) }

/** True when the range overlaps an address block that must never be blocked, the tunnel's own included. */
fun isReserved(c: Cidr): Boolean = RESERVED.any { it.overlaps(c) }
