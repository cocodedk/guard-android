package dk.cocode.guard.iplist

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressListsTest {
    private val now = 10L * 24 * 60 * 60 * 1000
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun nothingStoredIsAllNotYet() {
        val statuses = addressLists(emptyMap(), now)
        assertEquals(AddressList.entries, statuses.map { it.list })
        for (s in statuses) assertEquals(ListStatus(s.list, ListState.NotYet, 0, null), s)
    }

    @Test
    fun freshListIsActive() {
        val s = addressLists(mapOf(AddressList.DropV4 to StoredList(now - day, 1641)), now)[0]
        assertEquals(ListStatus(AddressList.DropV4, ListState.Active, 1641, now - day), s)
    }

    @Test
    fun oldListIsTooOld() {
        val s = addressLists(mapOf(AddressList.DropV4 to StoredList(now - 8 * day, 1641)), now)[0]
        assertEquals(ListStatus(AddressList.DropV4, ListState.TooOld, 0, now - 8 * day), s)
    }

    @Test
    fun listsAreIndependent() {
        val stored = mapOf(
            AddressList.DropV4 to StoredList(now - day, 1641),
            AddressList.DropV6 to StoredList(now - 9 * day, 91),
        )
        assertEquals(
            listOf(ListState.Active, ListState.TooOld, ListState.NotYet),
            addressLists(stored, now).map { it.state },
        )
    }

    @Test
    fun sevenDaysIsTheEdge() {
        fun state(fetched: Long) = addressLists(mapOf(AddressList.Feodo to StoredList(fetched, 0)), now)[2].state
        assertEquals(ListState.Active, state(now - 7 * day))
        assertEquals(ListState.TooOld, state(now - 7 * day - 1))
    }
}
