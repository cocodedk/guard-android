package dk.cocode.guard.vpn

import dk.cocode.guard.iplist.RecentFlows
import dk.cocode.guard.iplist.ipText
import dk.cocode.guard.net.IpPacket
import dk.cocode.guard.net.icmpUnreachableFor
import dk.cocode.guard.net.tcpResetFor
import dk.cocode.guard.net.u16

private const val MAX_FLOWS = 1_024
private const val UDP_HEADER = 8

/** The most address notifications one start may post, so busy malware cannot flood the shade. */
const val MAX_ADDRESS_NOTICES = 20

/** A refused connection: protocol, source port, destination address, destination port. */
private data class Flow(val protocol: Int, val srcPort: Int, val dst: String, val dstPort: Int)

/**
 * What one start remembers about refused connections, shared by every [PacketLoop] of that start so a
 * tunnel swap neither counts a flow twice nor posts more notifications. [onNewAddress] gets the
 * address, the id of the list holding it and the notification number (1 to [MAX_ADDRESS_NOTICES]).
 */
class Refusals(private val onNewAddress: (String, String, Int) -> Unit = { _, _, _ -> }) {
    private val flows = RecentFlows(MAX_FLOWS)
    private val noticed = HashSet<String>()

    /** True the first time this flow is refused, so retransmits and later packets of it are not counted. */
    @Synchronized
    internal fun firstTime(buf: ByteArray, p: IpPacket): Boolean {
        val h = p.headerLength
        val flow = Flow(p.protocol, buf.u16(h), ipText(p.dstIp), buf.u16(h + 2))
        return flows.firstTime(flow)
    }

    /** Posts one notification the first time [address] is refused, up to [MAX_ADDRESS_NOTICES] a start. */
    @Synchronized
    internal fun announce(address: String, listId: String) {
        if (noticed.size < MAX_ADDRESS_NOTICES && noticed.add(address)) onNewAddress(address, listId, noticed.size)
    }

}

/** The reply that refuses [p]: a TCP reset for a SYN, ICMP "prohibited" for UDP. Null means drop without a reply. */
internal fun refusalFor(buf: ByteArray, p: IpPacket): ByteArray? = when (p.protocol) {
    6 -> tcpResetFor(buf, p)
    17 -> if (p.totalLength >= p.headerLength + UDP_HEADER) icmpUnreachableFor(buf, p) else null
    else -> null
}
