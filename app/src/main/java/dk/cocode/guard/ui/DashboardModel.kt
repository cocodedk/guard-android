package dk.cocode.guard.ui

import dk.cocode.guard.iplist.ListState
import dk.cocode.guard.vpn.ProtectionState

enum class ListLook { Active, Paused, NotYet }

/**
 * One row of the list panel. [count] is rules or entries, [fetched] is epoch ms. The name list is the
 * Active row with no [fetched]: it ships in the app and is never downloaded.
 */
data class ListRow(val name: String, val look: ListLook, val count: Int?, val fetched: Long?)

data class Dashboard(val lookups: Int, val connections: Int, val rows: List<ListRow>, val addressBlockingOff: Boolean)

const val NAME_LIST = "AdGuard DNS filter"

/** The numbers and lists the dashboard draws: the name list first, then the address lists in order. */
fun dashboard(state: ProtectionState): Dashboard {
    val nameList = ListRow(NAME_LIST, ListLook.Active, state.listSize, null)
    val addressRows = state.addressLists.map {
        when (it.state) {
            ListState.Active -> ListRow(it.list.source, ListLook.Active, it.entries, it.fetched)
            ListState.TooOld -> ListRow(it.list.source, ListLook.Paused, null, it.fetched)
            ListState.NotYet -> ListRow(it.list.source, ListLook.NotYet, null, null)
        }
    }
    return Dashboard(
        lookups = state.blockedCount,
        connections = state.blockedAddressCount,
        rows = listOf(nameList) + addressRows,
        addressBlockingOff = state.addressLists.none { it.state == ListState.Active },
    )
}
