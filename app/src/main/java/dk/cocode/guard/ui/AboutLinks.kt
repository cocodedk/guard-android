package dk.cocode.guard.ui

enum class AboutLink { Website, Privacy, Source, Issues, Contact }

private const val SITE = "https://android.guard.cocode.dk/"

/** The Danish site paths for "da", the /en/ paths for any other language. */
fun aboutUrl(link: AboutLink, language: String): String {
    val base = if (language == "da") SITE else SITE + "en/"
    return when (link) {
        AboutLink.Website -> base
        AboutLink.Privacy -> base + "privacy/"
        AboutLink.Source -> "https://github.com/cocodedk/guard-android"
        AboutLink.Issues -> "https://github.com/cocodedk/guard-android/issues"
        AboutLink.Contact -> "mailto:bb@cocode.dk"
    }
}

fun versionLine(name: String, code: Long): String = "$name ($code)"
