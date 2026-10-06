package dk.cocode.guard.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromiseStringsTest {
    private val prefixes = listOf("promise_", "about_", "link_", "credit_")

    private val required = setOf(
        "promise_title", "promise_no_data", "promise_on_phone", "promise_only_dns", "promise_free",
        "promise_permissions", "promise_accessible", "promise_limit",
        "about_title", "about_version", "about_what", "about_vpn_title", "about_vpn_body",
        "about_free_title", "about_free_body", "about_links_title", "about_credits_title",
        "about_made_by_title", "about_made_by",
        "link_website", "link_privacy", "link_source", "link_issues", "link_no_browser", "link_contact",
        "credit_adguard", "credit_spamhaus", "credit_feodo", "credit_androidx",
    )

    // Unit tests run with the module directory as the working directory.
    private fun strings(dir: String): Map<String, String> {
        val xml = File("src/main/res/$dir/strings.xml").readText()
        return Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml).associate { it.groupValues[1] to it.groupValues[2] }
            .filterKeys { key -> prefixes.any { key.startsWith(it) } }
    }

    @Test
    fun everyKeyExistsInBothLanguages() {
        val da = strings("values")
        val en = strings("values-en")
        assertEquals(required, da.keys)
        assertEquals(required, en.keys)
    }

    @Test
    fun promisesNeverClaimToSendNothing() {
        val banned = mapOf(
            "values" to listOf("intet", "sender ingenting"),
            "values-en" to listOf("nothing", "transmit"),
        )
        for ((dir, words) in banned) {
            for ((key, text) in strings(dir).filterKeys { it.startsWith("promise_") }) {
                for (word in words) {
                    assertTrue("$dir $key says \"$word\"", !text.contains(word, ignoreCase = true))
                }
            }
        }
    }
}
