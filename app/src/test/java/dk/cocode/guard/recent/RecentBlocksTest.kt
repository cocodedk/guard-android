package dk.cocode.guard.recent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentBlocksTest {
    private fun RecentBlocks.name(app: String?, target: String) = add(BlockEvent(BlockKind.Name, target, app))

    @Test
    fun groupsByAppNewestFirst() {
        val r = RecentBlocks()
        r.name("A", "a1.test")
        r.name("B", "b1.test")
        r.name("A", "a2.test")
        assertEquals(
            listOf(AppBlocks("A", 2, listOf("a2.test", "a1.test")), AppBlocks("B", 1, listOf("b1.test"))),
            r.groups(),
        )
    }

    @Test
    fun unknownAppIsItsOwnGroup() {
        val r = RecentBlocks()
        r.name(null, "x.test")
        r.name("Unknown", "y.test") // a real app that happens to be called that is not the null group
        r.name(null, "z.test")
        assertEquals(listOf(null, "Unknown"), r.groups().map { it.app })
        assertEquals(2, r.groups().first().count)
    }

    @Test
    fun latestIsDistinctNewestFirstAtMostThree() {
        val r = RecentBlocks()
        listOf("t1", "t2", "t3", "t2", "t4").forEach { r.name("A", it) }
        assertEquals(listOf("t4", "t2", "t3"), r.groups().single().latest)
        assertEquals(5, r.groups().single().count)
    }

    @Test
    fun addressAndNameCountTogether() {
        val r = RecentBlocks()
        r.name("A", "ads.test")
        r.add(BlockEvent(BlockKind.Address, "203.0.113.7", "A"))
        assertEquals(AppBlocks("A", 2, listOf("203.0.113.7", "ads.test")), r.groups().single())
    }

    @Test
    fun atMostMaxGroups() {
        val r = RecentBlocks()
        repeat(7) { r.name("app$it", "t.test") }
        assertEquals(5, r.groups().size)
        assertEquals("app6", r.groups().first().app)
        assertEquals(2, r.groups(max = 2).size)
    }

    @Test
    fun capacityDropsOldest() {
        val r = RecentBlocks(capacity = 3)
        listOf("t1", "t2", "t3", "t4").forEach { r.name("A", it) }
        val group = r.groups().single()
        assertEquals(3, group.count)
        assertEquals(listOf("t4", "t3", "t2"), group.latest)
    }

    @Test
    fun clearEmptiesEverything() {
        val r = RecentBlocks()
        r.name("A", "t.test")
        r.clear()
        assertTrue(r.groups().isEmpty())
    }
}
