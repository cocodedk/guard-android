package dk.cocode.guard.blocklist

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleParserTest {
    private fun parse(vararg lines: String) = parseRules(lines.asSequence())

    @Test
    fun blockRuleIsParsed() {
        assertTrue(parse("||ads.example.com^").isBlocked("ads.example.com"))
    }

    @Test
    fun exceptionFormsAreParsed() {
        val list = parse("||example.com^", "@@||ok.example.com^", "@@||fine.example.com^|")
        assertFalse(list.isBlocked("ok.example.com"))
        assertFalse(list.isBlocked("fine.example.com"))
        assertTrue(list.isBlocked("bad.example.com"))
    }

    @Test
    fun otherLinesAreSkipped() {
        val list = parse(
            "! comment", "", "/^ads/", "||ads*.example.com^", "||x.example.com^\$important",
            "||x.example.com^\$badfilter", ".bbelements.com^",
        )
        assertEquals(0, list.size)
        assertFalse(list.isBlocked("x.example.com"))
        assertFalse(list.isBlocked("bbelements.com"))
    }

    @Test
    fun rulesAreLowercased() {
        assertTrue(parse("||Ads.Example.COM^").isBlocked("ads.example.com"))
    }

    @Test
    fun shippedListLoads() {
        // Unit tests run from the module directory.
        val list = File("src/main/assets/adguard-dns-filter.txt").useLines { parseRules(it) }
        assertTrue("size is ${list.size}", list.size >= 170_000)
    }
}
