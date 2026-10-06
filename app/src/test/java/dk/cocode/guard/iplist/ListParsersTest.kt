package dk.cocode.guard.iplist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ListParsersTest {
    private val metadata = """{"type":"metadata","timestamp":1,"size":1,"records":2}"""

    private fun cidr(text: String) = parseCidr(text)!!

    private fun v4Ranges(n: Int) = (0 until n).map { cidr("${11 + it / 250}.${it % 250}.0.0/16") }

    private fun v6Ranges(n: Int) = (0 until n).map { cidr("2a00:${it.toString(16)}::/32") }

    @Test
    fun spamhausLinesParse() {
        val body = """
            {"cidr":"192.0.2.0/24","sblid":"SBL1","rir":"ripencc"}
            {"cidr":"198.51.100.0/24","sblid":"SBL2","rir":"arin"}
            $metadata
        """.trimIndent()
        assertEquals(listOf("192.0.2.0/24", "198.51.100.0/24"), parseSpamhausJson(body)!!.map { it.toString() })
    }

    @Test
    fun spamhausWithoutMetadataIsRejected() {
        assertNull(parseSpamhausJson("""{"cidr":"192.0.2.0/24","sblid":"SBL1","rir":"ripencc"}"""))
    }

    @Test
    fun spamhausCutOffMetadataIsRejected() {
        val body = "{\"cidr\":\"192.0.2.0/24\"}\n" + metadata.dropLast(1)
        assertNull(parseSpamhausJson(body))
    }

    @Test
    fun spamhausCutOffLineAfterMetadataIsRejected() {
        val body = "{\"cidr\":\"192.0.2.0/24\"}\n$metadata\n{\"cidr\":\"203.0"
        assertNull(parseSpamhausJson(body))
    }

    @Test
    fun spamhausCutOffLineBeforeMetadataIsRejected() {
        val body = "{\"cidr\":\"192.0.2.0/24\"}\n{\"cidr\":\"203.0\n$metadata"
        assertNull(parseSpamhausJson(body))
    }

    @Test
    fun spamhausIpv6Parses() {
        val body = "{\"cidr\":\"2001:db8::/32\",\"sblid\":\"SBL3\",\"rir\":\"ripencc\"}\n$metadata"
        assertEquals(listOf("2001:db8::/32"), parseSpamhausJson(body)!!.map { it.toString() })
    }

    @Test
    fun feodoSkipsCommentsAndBlanks() {
        val body = "# Feodo\n\n  203.0.113.7  \n# end\n"
        assertEquals(listOf("203.0.113.7/32"), parseFeodo(body)!!.map { it.toString() })
    }

    @Test
    fun feodoBadLineRejectsAll() {
        assertNull(parseFeodo("203.0.113.7\nnot-an-ip\n"))
        assertNull(parseFeodo("203.0.113.7\n2001:db8::1\n"))
        assertNull(parseFeodo("203.0.113.0/24\n"))
    }

    @Test
    fun reservedEntriesAreDropped() {
        val kept = acceptList("feodo", listOf(cidr("10.1.2.3/32"), cidr("0.0.0.0/0"), cidr("203.0.113.7/32")))
        assertEquals(listOf("203.0.113.7/32"), kept!!.map { it.toString() })
    }

    @Test
    fun containedRangesAreMerged() {
        val kept = acceptList("feodo", listOf(cidr("203.0.113.9/32"), cidr("203.0.113.0/24"), cidr("203.0.113.9/32")))
        assertEquals(listOf("203.0.113.0/24"), kept!!.map { it.toString() })
    }

    @Test
    fun tooFewDropRangesIsRejected() {
        assertNull(acceptList("drop_v4", v4Ranges(499)))
        assertEquals(500, acceptList("drop_v4", v4Ranges(500))!!.size)
        assertNull(acceptList("drop_v6", v6Ranges(19)))
        assertEquals(20, acceptList("drop_v6", v6Ranges(20))!!.size)
    }

    @Test
    fun emptyFeodoIsAccepted() {
        assertNotNull(parseFeodo("# nothing listed today\n"))
        assertEquals(emptyList<Cidr>(), acceptList("feodo", parseFeodo("# nothing listed today\n")!!))
    }
}
