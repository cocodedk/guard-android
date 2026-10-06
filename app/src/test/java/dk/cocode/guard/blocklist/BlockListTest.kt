package dk.cocode.guard.blocklist

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockListTest {
    private fun list(blocked: Set<String>, allowed: Set<String> = emptySet()) = BlockList(blocked, allowed)

    @Test
    fun exactNameIsBlocked() {
        assertTrue(list(setOf("ads.example.com")).isBlocked("ads.example.com"))
    }

    @Test
    fun subdomainIsBlocked() {
        assertTrue(list(setOf("example.com")).isBlocked("a.b.example.com"))
    }

    @Test
    fun parentIsNotBlocked() {
        assertFalse(list(setOf("ads.example.com")).isBlocked("example.com"))
    }

    @Test
    fun lookalikeIsNotBlocked() {
        assertFalse(list(setOf("example.com")).isBlocked("notexample.com"))
    }

    @Test
    fun exceptionWinsOverParentBlock() {
        val list = list(setOf("example.com"), setOf("cdn.example.com"))
        assertFalse(list.isBlocked("cdn.example.com"))
        assertFalse(list.isBlocked("x.cdn.example.com"))
        assertTrue(list.isBlocked("ads.example.com"))
    }

    @Test
    fun caseAndTrailingDotIgnored() {
        assertTrue(list(setOf("ads.example.com")).isBlocked("Ads.Example.COM."))
    }
}
