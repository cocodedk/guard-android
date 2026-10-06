package dk.cocode.guard.dns

/** The one question of a query. [questionEnd] is the offset just past it in the payload. */
data class DnsQuestion(val id: Int, val name: String, val type: Int, val questionEnd: Int)

private const val HEADER = 12
private const val TYPE_A = 1
private const val TYPE_AAAA = 28
private const val FLAG_QR = 0x8000
private const val FLAG_OPCODE = 0x7800
private const val FLAG_RD = 0x0100
private const val FLAG_RA = 0x0080
private const val RCODE_SERVFAIL = 2
private const val TTL_SECONDS = 60

private fun ByteArray.u8(i: Int) = this[i].toInt() and 0xFF
private fun ByteArray.u16(i: Int) = (u8(i) shl 8) or u8(i + 1)

/** Parses a query with exactly one question; null for anything else, so the caller forwards it. */
fun parseQuery(payload: ByteArray): DnsQuestion? {
    if (payload.size < HEADER) return null
    if (payload.u16(2) and FLAG_QR != 0 || payload.u16(4) != 1) return null
    val labels = ArrayList<String>()
    var pos = HEADER
    while (true) {
        if (pos >= payload.size) return null
        val len = payload.u8(pos)
        if (len == 0) break
        // 0xC0 bits are a compression pointer (or a reserved form): not valid in a plain question.
        if (len and 0xC0 != 0 || pos + 1 + len > payload.size) return null
        // A literal dot inside a label would read as a label boundary once joined; forward it instead.
        if ((pos + 1 until pos + 1 + len).any { payload[it] == '.'.code.toByte() }) return null
        labels += String(payload, pos + 1, len, Charsets.ISO_8859_1)
        pos += 1 + len
    }
    val typeAt = pos + 1
    if (typeAt + 4 > payload.size) return null
    return DnsQuestion(payload.u16(0), labels.joinToString("."), payload.u16(typeAt), typeAt + 4)
}

/** An empty answer: `0.0.0.0` for A, `::` for AAAA, no answer for any other type. */
fun blockedAnswer(query: ByteArray, q: DnsQuestion): ByteArray {
    val rdLength = when (q.type) {
        TYPE_A -> 4
        TYPE_AAAA -> 16
        else -> return reply(query, q, flags(query), 0, ByteArray(0))
    }
    val answer = ByteArray(12 + rdLength)
    answer[0] = 0xC0.toByte()
    answer[1] = HEADER.toByte() // pointer to the question name at offset 12
    answer[3] = q.type.toByte()
    answer[5] = 1 // class IN
    answer[9] = TTL_SECONDS.toByte()
    answer[11] = rdLength.toByte()
    return reply(query, q, flags(query), 1, answer)
}

fun servfail(query: ByteArray, q: DnsQuestion): ByteArray =
    reply(query, q, FLAG_QR or (query.u16(2) and FLAG_RD) or FLAG_RA or RCODE_SERVFAIL, 0, ByteArray(0))

private fun flags(query: ByteArray) =
    FLAG_QR or (query.u16(2) and (FLAG_OPCODE or FLAG_RD)) or FLAG_RA

private fun reply(query: ByteArray, q: DnsQuestion, flags: Int, answers: Int, answer: ByteArray): ByteArray {
    val out = ByteArray(q.questionEnd + answer.size)
    query.copyInto(out, 0, 0, q.questionEnd)
    out[2] = (flags shr 8).toByte()
    out[3] = flags.toByte()
    // QDCOUNT stays 1; the answer, authority and additional counts are rewritten.
    out[6] = (answers shr 8).toByte()
    out[7] = answers.toByte()
    for (i in 8..11) out[i] = 0
    answer.copyInto(out, q.questionEnd)
    return out
}
