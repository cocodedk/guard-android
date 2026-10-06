package dk.cocode.guard.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The one check that runs at each start and again while protecting. */
class CannotRunTest {
    @Test
    fun lockdownStops() = assertEquals(StopReason.Lockdown, cannotRun(lockdown = true, privateDnsStrict = false))

    @Test
    fun privateDnsServerStops() =
        assertEquals(StopReason.PrivateDns, cannotRun(lockdown = false, privateDnsStrict = true))

    @Test
    fun lockdownIsNamedWhenBoth() = assertEquals(StopReason.Lockdown, cannotRun(lockdown = true, privateDnsStrict = true))

    @Test
    fun runsWhenNeither() = assertNull(cannotRun(lockdown = false, privateDnsStrict = false))
}
