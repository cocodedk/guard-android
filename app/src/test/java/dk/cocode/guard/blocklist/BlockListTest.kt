package dk.cocode.guard.blocklist

import dk.cocode.guard.dns.parseQuery
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

    @Test
    fun escapedDotIsNotABoundary() {
        val list = list(setOf("example.com"), setOf("cdn.example.com"))
        assertTrue(list.isBlocked("x\\.cdn.example.com"))
    }

    @Test
    fun escapedDotNeverMatchesARule() {
        assertFalse(list(setOf("x.cdn.example.com")).isBlocked("x\\.cdn.example.com"))
    }

    @Test
    fun escapedBackslashBeforeDotIsStillABoundary() {
        // Wire label `a\` followed by a real boundary: the exception on example.com must still apply.
        assertTrue(list(setOf("com"), setOf("example.com")).isBlocked("a\\\\.b.com"))
        assertFalse(list(setOf("com"), setOf("example.com")).isBlocked("a\\\\.example.com"))
    }

    @Test
    fun wireLabelWithDotIsBlockedByParentRule() {
        fun label(s: String) = byteArrayOf(s.length.toByte()) + s.toByteArray(Charsets.ISO_8859_1)
        val query = ByteArray(12).also { it[5] = 1 } +
            label("x.cdn") + label("example") + label("com") + byteArrayOf(0, 0, 1, 0, 1)
        val q = parseQuery(query)!!
        assertTrue(list(setOf("example.com"), setOf("cdn.example.com")).isBlocked(q.name))
    }
}
