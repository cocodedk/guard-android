package dk.cocode.guard.vpn

import dk.cocode.guard.recent.AppBlocks
import dk.cocode.guard.recent.BlockEvent
import dk.cocode.guard.recent.BlockKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionRepositoryTest {
    private fun block() = ProtectionRepository.record(BlockEvent(BlockKind.Name, "ads.test", "Chrome"))

    @Test
    fun recordPublishesTheGroups() {
        ProtectionRepository.starting(alwaysOn = false)
        block()
        assertEquals(listOf(AppBlocks("Chrome", 1, listOf("ads.test"))), ProtectionRepository.state.value.recentBlocks)
    }

    @Test
    fun startAndStopClearRecentBlocks() {
        ProtectionRepository.starting(alwaysOn = false)
        block()
        ProtectionRepository.starting(alwaysOn = false)
        assertTrue(ProtectionRepository.state.value.recentBlocks.isEmpty())
        block()
        ProtectionRepository.ended(ProtectionStatus.Off)
        assertTrue(ProtectionRepository.state.value.recentBlocks.isEmpty())
        // The kept events are gone too, not just the published list.
        ProtectionRepository.starting(alwaysOn = false)
        ProtectionRepository.record(BlockEvent(BlockKind.Name, "other.test", null))
        assertEquals(listOf(AppBlocks(null, 1, listOf("other.test"))), ProtectionRepository.state.value.recentBlocks)
        ProtectionRepository.ended(ProtectionStatus.Stopped(StopReason.Error))
        assertTrue(ProtectionRepository.state.value.recentBlocks.isEmpty())
    }
}
