package dk.cocode.guard.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AboutLinksTest {
    @Test
    fun danishGetsDanishSitePaths() {
        assertEquals("https://android.guard.cocode.dk/", aboutUrl(AboutLink.Website, "da"))
        assertEquals("https://android.guard.cocode.dk/privacy/", aboutUrl(AboutLink.Privacy, "da"))
    }

    @Test
    fun otherLanguagesGetEnglishPaths() {
        for (language in listOf("en", "de")) {
            assertEquals("https://android.guard.cocode.dk/en/", aboutUrl(AboutLink.Website, language))
            assertEquals("https://android.guard.cocode.dk/en/privacy/", aboutUrl(AboutLink.Privacy, language))
        }
    }

    @Test
    fun sourceIssuesAndContactIgnoreLanguage() {
        assertEquals("https://github.com/cocodedk/guard-android", aboutUrl(AboutLink.Source, "da"))
        assertEquals("https://github.com/cocodedk/guard-android", aboutUrl(AboutLink.Source, "en"))
        assertEquals("https://github.com/cocodedk/guard-android/issues", aboutUrl(AboutLink.Issues, "da"))
        assertEquals("https://github.com/cocodedk/guard-android/issues", aboutUrl(AboutLink.Issues, "en"))
        assertEquals("mailto:bb@cocode.dk", aboutUrl(AboutLink.Contact, "da"))
        assertEquals("mailto:bb@cocode.dk", aboutUrl(AboutLink.Contact, "en"))
    }

    @Test
    fun versionLineShowsNameAndCode() {
        assertEquals("0.1.0 (1001)", versionLine("0.1.0", 1001))
    }
}
