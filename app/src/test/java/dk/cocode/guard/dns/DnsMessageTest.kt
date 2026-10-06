package dk.cocode.guard.dns

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DnsMessageTest {
    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    private fun query(
        name: String = "www.example.com", type: Int = 1, flags: Int = 0x0100, questions: Int = 1, id: Int = 0xABCD,
    ): ByteArray {
        val out = ArrayList<Byte>()
        fun put16(v: Int) {
            out += (v shr 8).toByte()
            out += v.toByte()
        }
        put16(id); put16(flags); put16(questions); put16(0); put16(0); put16(0)
        for (label in name.split(".")) {
            out += label.length.toByte()
            label.forEach { out += it.code.toByte() }
        }
        out += 0.toByte()
        put16(type); put16(1)
        return out.toByteArray()
    }

    private fun parsed(payload: ByteArray) = checkNotNull(parseQuery(payload))

    @Test
    fun parsesAQuery() {
        val q = parsed(query())
        assertEquals("www.example.com", q.name)
        assertEquals(1, q.type)
        assertEquals(0xABCD, q.id)
        assertEquals(query().size, q.questionEnd)
    }

    @Test
    fun backslashInLabelIsEscaped() {
        // Wire labels `a\`, `example`, `com`: the backslash is doubled so the next dot stays a boundary.
        assertEquals("a\\\\.example.com", parsed(query(name = "a\\.example.com")).name)
    }

    @Test
    fun rejectsResponse() {
        assertNull(parseQuery(query(flags = 0x8180)))
    }

    @Test
    fun rejectsTwoQuestions() {
        assertNull(parseQuery(query(questions = 2)))
    }

    @Test
    fun dotInsideLabelIsParsedButNeverReadAsTwoLabels() {
        // One label "a.b" followed by "com" must not read as the name "a.b.com".
        val payload = query("axb.com")
        payload[14] = '.'.code.toByte()
        val name = parsed(payload).name
        assertEquals("a\\.b.com", name)
        assertFalse(name == "a.b.com")
    }

    @Test
    fun rejectsTruncatedName() {
        val full = query()
        assertNull(parseQuery(full.copyOf(20)))
        assertNull(parseQuery(full.copyOf(full.size - 1)))
        assertNull(parseQuery(full.copyOf(8)))
    }

    @Test
    fun rejectsCompressionPointerInQuestion() {
        val q = query()
        q[12] = 0xC0.toByte()
        assertNull(parseQuery(q))
    }

    @Test
    fun blockedAnswerForA() {
        val q = query()
        val answer = blockedAnswer(q, parsed(q))
        assertEquals(0xABCD, u16(answer, 0))
        assertEquals(0x8180, u16(answer, 2))
        assertEquals(1, u16(answer, 4))
        assertEquals(1, u16(answer, 6))
        val at = q.size
        assertEquals(0xC00C, u16(answer, at))
        assertEquals(1, u16(answer, at + 2))
        assertEquals(1, u16(answer, at + 4))
        assertEquals(60, u16(answer, at + 8))
        assertEquals(4, u16(answer, at + 10))
        assertArrayEquals(ByteArray(4), answer.copyOfRange(at + 12, answer.size))
    }

    @Test
    fun blockedAnswerForAaaa() {
        val q = query(type = 28)
        val answer = blockedAnswer(q, parsed(q))
        assertEquals(1, u16(answer, 6))
        assertEquals(28, u16(answer, q.size + 2))
        assertEquals(16, u16(answer, q.size + 10))
        assertArrayEquals(ByteArray(16), answer.copyOfRange(q.size + 12, answer.size))
    }

    @Test
    fun blockedAnswerForOtherType() {
        val q = query(type = 65)
        val answer = blockedAnswer(q, parsed(q))
        assertEquals(0, u16(answer, 6))
        assertEquals(0, u16(answer, 2) and 0x000F)
        assertEquals(q.size, answer.size)
    }

    @Test
    fun servfailHasRcode2() {
        val q = query()
        val answer = servfail(q, parsed(q))
        assertNotNull(answer)
        assertEquals(0xABCD, u16(answer, 0))
        assertEquals(0x8182, u16(answer, 2))
        assertEquals(0, u16(answer, 6))
    }

    @Test
    fun formerrHasRcode1AndHeaderOnly() {
        val answer = checkNotNull(formerr(query(questions = 2)))
        assertEquals(12, answer.size)
        assertEquals(0xABCD, u16(answer, 0))
        assertEquals(0x8181, u16(answer, 2))
        assertEquals(0, u16(answer, 4))
    }

    @Test
    fun formerrDropsResponsesAndShortPayloads() {
        assertNull(formerr(query(flags = 0x8180)))
        assertNull(formerr(ByteArray(11)))
    }
}
