package dk.cocode.guard.ui

import dk.cocode.guard.iplist.AddressList
import dk.cocode.guard.iplist.ListState
import dk.cocode.guard.iplist.ListStatus
import dk.cocode.guard.vpn.ProtectionState
import dk.cocode.guard.vpn.ProtectionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardModelTest {
    private fun state(lists: List<ListStatus> = emptyList()) = ProtectionState(
        status = ProtectionStatus.Protected, blockedCount = 7, blockedAddressCount = 3, listSize = 1234, addressLists = lists,
    )

    private fun active(list: AddressList) = ListStatus(list, ListState.Active, 40, 5_000L)

    @Test
    fun countsComeFromState() {
        val d = dashboard(state())
        assertEquals(7, d.lookups)
        assertEquals(3, d.connections)
    }

    @Test
    fun nameListFirstThenAddressListsInOrder() {
        val lists = AddressList.entries.map { active(it) }
        val rows = dashboard(state(lists)).rows
        assertEquals(listOf(NAME_LIST) + AddressList.entries.map { it.source }, rows.map { it.name })
        assertEquals(ListRow(NAME_LIST, ListLook.Active, 1234, null), rows.first())
    }

    @Test
    fun activeListHasCountAndDate() {
        val row = dashboard(state(listOf(active(AddressList.DropV4)))).rows[1]
        assertEquals(ListRow(AddressList.DropV4.source, ListLook.Active, 40, 5_000L), row)
    }

    @Test
    fun tooOldIsPausedWithDate() {
        val row = dashboard(state(listOf(ListStatus(AddressList.DropV6, ListState.TooOld, 0, 9_000L)))).rows[1]
        assertEquals(ListLook.Paused, row.look)
        assertEquals(9_000L, row.fetched)
        assertEquals(null, row.count)
    }

    @Test
    fun notYetHasNoCountOrDate() {
        val row = dashboard(state(listOf(ListStatus(AddressList.Feodo, ListState.NotYet, 0, null)))).rows[1]
        assertEquals(ListRow(AddressList.Feodo.source, ListLook.NotYet, null, null), row)
    }

    @Test
    fun addressBlockingOffWhenNoListActive() {
        assertTrue(dashboard(state()).addressBlockingOff)
        val paused = ListStatus(AddressList.DropV4, ListState.TooOld, 0, 1L)
        assertTrue(dashboard(state(listOf(paused))).addressBlockingOff)
        assertFalse(dashboard(state(listOf(paused, active(AddressList.Feodo)))).addressBlockingOff)
    }
}
