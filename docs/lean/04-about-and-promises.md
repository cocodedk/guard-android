# 04 — What the app promises, an About page, and the new name

## Goal

The owner asked for two things: the first screen must say plainly that the app sends nothing about
you and what else is good about it, and the app must have an **About** page. The home screen gets a
"Godt at vide" ("Good to know") block that lists the app's promises in plain words, and a button to
a new About page with the version, the license, links and credit for the lists. No new permission,
no new library.

Builds on specs 01–03 as merged. Read `CLAUDE.md` first; its rules apply, including accessibility
and the 200-line limit.

## The new name: Guard for Android

The owner renamed the app from "Cocode Guard" to **Guard for Android** (Google's brand rules allow
"for Android", not "Android" as part of the name). The box keeps its name, "Cocode Guard".

- `app_name` (the launcher label and the notification's app label) becomes `Guard`, still
  `translatable="false"`, so it fits under the launcher icon.
- New string `screen_title` = `Guard for Android` (`translatable="false"`), used for the home screen's
  heading instead of `app_name`, and as the About page's product name.
- Any other user-facing text that says "Cocode Guard" about the app says "Guard for Android".
- The application id `dk.cocode.guard`, the package names, the tunnel's session name source
  (`app_name`) and the repository are unchanged.

## Honesty rule for every promise

Each line must be true of the app as merged, word for word. In particular, never say the app sends
or transmits **nothing**: lookups that are not blocked go to the network's own DNS server, and the
address lists are downloaded from their publishers. What is true, and what the screen says, is that
the app sends **no information about you** to anyone and that there is no Cocode server.

## Home screen: "Godt at vide"

Replaces the single `closing_line` text at the bottom of `HomeScreen`, in every state, after the
primary button:

1. Heading `promise_title` (`heading()`).
2. Six promise lines, in this order, each one TalkBack stop, each with a small decorative check icon
   (`ic_check`, `contentDescription = null`, tinted `GuardColors.Ok`; the words carry the meaning):
   `promise_no_data`, `promise_on_phone`, `promise_only_dns`, `promise_free`, `promise_permissions`,
   `promise_accessible`.
3. The honest limit, in the quiet text color and without an icon: `promise_limit`.
4. A full-width outlined button, at least 48dp tall: `action_about` → opens the About page.

The block is plain text (no live region). `closing_line` is removed from both string files.

## The About page

A second screen, `ui/AboutScreen.kt`, shown instead of the home screen. No navigation library:
`MainActivity` keeps `var showAbout by rememberSaveable { mutableStateOf(false) }`. `action_about`
sets it true; the page's back button and the system back gesture (`BackHandler`, already in
`activity-compose`) set it false. The page scrolls (`verticalScroll`), respects
`safeDrawingPadding`, and has no fixed heights.

Top to bottom:

1. A text button `action_back` ("Tilbage") at the top start, at least 48dp tall.
2. Title `about_title` (`heading()`).
3. `about_version` with the installed `versionName` and `versionCode`, read with
   `packageManager.getPackageInfo(packageName, 0)` (no BuildConfig).
4. `about_what` (one paragraph).
5. Section heading `about_vpn_title`, then `about_vpn_body`: what the app means by "VPN" (the owner
   asked that the app say this plainly).
6. Section heading `about_free_title`, then `about_free_body`.
7. Section heading `about_links_title`, then four link buttons, each full-width, at least 48dp, with
   `Role.Button` semantics and a label that names the destination:
   - `link_website` → `https://android.guard.cocode.dk/` (Danish) or `/en/` (any other language)
   - `link_privacy` → `https://android.guard.cocode.dk/privacy/` (Danish) or `/en/privacy/`
   - `link_source` → `https://github.com/cocodedk/guard-android`
   - `link_issues` → `https://github.com/cocodedk/guard-android/issues`
   Each opens `Intent(ACTION_VIEW, uri)`. If no app can open it (`ActivityNotFoundException`), the page
   shows `link_no_browser` under the links instead of crashing.
8. Section heading `about_credits_title`, then three credit lines (plain text, one TalkBack stop
   each): `credit_adguard`, `credit_spamhaus`, `credit_feodo`, then `credit_androidx`.
   (Spamhaus asks that, in a product, credit be given to The Spamhaus Project; this is that credit.)
9. Section heading `about_made_by_title`, then `about_made_by` and a link button `link_contact` →
   `mailto:bb@cocode.dk`.

Every section heading is a `heading()`, so TalkBack users can jump between sections.

## Design — match what is already there

The owner asked that this look right, so reuse the existing look rather than inventing a new one:

- Colors only from `GuardColors` through `MaterialTheme.colorScheme`; no new color.
- The "Godt at vide" block sits on the same panel surface as the existing cards (`HomeCardView`):
  same background (`surfaceVariant` / Panel), corner radius, inner padding and spacing. Check icon
  20dp, aligned with the first line of its text, 12dp gap; lines spaced like the card body text.
- Type: section titles use the same style as the card titles (`titleMedium`), body text
  `bodyLarge`, the limit and the About page's credit lines `bodyMedium` in `onSurfaceVariant`.
- Buttons: the existing filled green button stays the one primary action on the home screen; "Om
  Guard for Android" and the About page's links are outlined buttons (`OutlinedButton`) so they never
  compete with Start/Stop. Same 48dp minimum height and full width as the primary button.
- The About page uses the home screen's column: the same 24dp outer padding and 16dp spacing, and
  24dp extra space before each section heading so sections read as groups.
- No images or illustrations; the shield icon stays on the home screen only.

## Pure logic (JVM-tested, `ui/AboutLinks.kt`)

```kotlin
enum class AboutLink { Website, Privacy, Source, Issues, Contact }
fun aboutUrl(link: AboutLink, language: String): String  // language = Locale.getDefault().language
fun versionLine(name: String, code: Long): String         // "0.1.0 (1001)"
```

`aboutUrl` returns the Danish site paths for `"da"` and the `/en/` paths for anything else; Source,
Issues and Contact do not depend on the language.

## Strings (Danish in `values/`, English in `values-en/`)

| Key | Danish | English |
|---|---|---|
| promise_title | Godt at vide | Good to know |
| promise_no_data | Appen sender ingen oplysninger om dig til nogen. Ingen konto, ingen sporing, ingen statistik, ingen reklamer. | The app sends no information about you to anyone. No account, no tracking, no analytics, no ads. |
| promise_on_phone | Alt sker på telefonen. Der er ingen Cocode-server og ingen VPN-server. | Everything happens on the phone. There is no Cocode server and no VPN server. |
| promise_only_dns | Kun DNS-opslag og forbindelser til kendte farlige adresser går gennem appen. Resten af din trafik rører den ikke. | Only DNS lookups and connections to known dangerous addresses pass through the app. It never touches the rest of your traffic. |
| promise_free | Gratis og fri software (GPL-3.0). Alle kan læse koden. | Free of charge and free software (GPL-3.0). Anyone can read the code. |
| promise_permissions | Du giver kun to tilladelser: VPN og notifikationer. Ingen adgang til kontakter, placering eller filer. | You grant only two permissions: VPN and notifications. No access to contacts, location or files. |
| promise_accessible | Lavet til TalkBack og stor skrift. | Made for TalkBack and large text. |
| promise_limit | Apps med deres egen sikre DNS går uden om navnefilteret. | Apps with their own secure DNS bypass the name filter. |
| action_about | Om Guard for Android | About Guard for Android |
| action_back | Tilbage | Back |
| about_title | Om Guard for Android | About Guard for Android |
| about_version | Version %1$s | Version %1$s |
| about_what | Guard for Android blokerer farlige sider, reklamer og kendte farlige adresser for alle apps på telefonen, på ethvert netværk. Det sker i en lokal tunnel, der ender inde i appen. | Guard for Android blocks dangerous sites, ads and known dangerous addresses for every app on the phone, on any network. It happens in a local tunnel that ends inside the app. |
| about_vpn_title | Hvad vi mener med VPN | What we mean by VPN |
| about_vpn_body | Android kalder appen en VPN, fordi den bruger Androids VPN-funktion. Men det er ikke en VPN i den sædvanlige forstand: der er ingen VPN-server, og din trafik bliver ikke sendt videre til nogen. Appen laver en lokal tunnel, der starter og ender inde i appen på telefonen. Kun DNS-opslag og forbindelser til kendte farlige adresser går ind i tunnelen, så appen kan blokere dem. Al anden trafik går ud præcis som før. Derfor viser Android en nøgle i statuslinjen, og derfor kan der kun køre én VPN-app ad gangen. | Android calls the app a VPN because it uses Android's VPN feature. But it is not a VPN in the usual sense: there is no VPN server, and your traffic is not sent on to anyone. The app makes a local tunnel that starts and ends inside the app, on the phone. Only DNS lookups and connections to known dangerous addresses enter the tunnel, so the app can block them. All other traffic goes out exactly as before. That is why Android shows a key in the status bar, and why only one VPN app can run at a time. |
| about_free_title | Fri software | Free software |
| about_free_body | Appen er gratis og udgivet under GNU General Public License, version 3 eller senere. Du må bruge, læse, ændre og dele den. | The app is free of charge and released under the GNU General Public License, version 3 or later. You may use, read, change and share it. |
| about_links_title | Links | Links |
| link_website | Åbn hjemmesiden | Open the website |
| link_privacy | Læs privatlivspolitikken | Read the privacy policy |
| link_source | Se kildekoden på GitHub | See the source code on GitHub |
| link_issues | Meld en fejl på GitHub | Report a problem on GitHub |
| link_no_browser | Der er ingen app på telefonen, der kan åbne linket. | No app on the phone can open the link. |
| about_credits_title | Lister og tak | Lists and credits |
| credit_adguard | Navnelisten er AdGuard DNS filter fra AdGuard, under GPL-3.0. | The name list is the AdGuard DNS filter by AdGuard, under GPL-3.0. |
| credit_spamhaus | Adresselisterne Spamhaus DROP og DROPv6 kommer fra The Spamhaus Project. © The Spamhaus Project SLU. | The Spamhaus DROP and DROPv6 address lists come from The Spamhaus Project. © The Spamhaus Project SLU. |
| credit_feodo | Adresselisten Feodo Tracker kommer fra abuse.ch, under CC0. | The Feodo Tracker address list comes from abuse.ch, under CC0. |
| credit_androidx | Bygget med AndroidX og Jetpack Compose (Apache-2.0). | Built with AndroidX and Jetpack Compose (Apache-2.0). |
| about_made_by_title | Lavet af | Made by |
| about_made_by | Babak Bandpey, Cocode (cocode.dk). | Babak Bandpey, Cocode (cocode.dk). |
| link_contact | Skriv til bb@cocode.dk | Write to bb@cocode.dk |

`app_name`, the list names and the e-mail address are not translated.

## Files

- `ui/PromisesSection.kt` (new): the "Godt at vide" block.
- `ui/AboutScreen.kt` (new), `ui/AboutLinks.kt` (new, Android-free).
- `res/drawable/ic_check.xml` (new vector).
- `ui/HomeScreen.kt`: replace the closing line with `PromisesSection`; keep the file under 200 lines.
- `MainActivity.kt`: `showAbout`, `BackHandler`, opening links.
- `res/values/strings.xml`, `res/values-en/strings.xml`.

## Acceptance tests (JVM, offline, under `app/src/test/java/dk/cocode/guard/`)

- `ui/AboutLinksTest`
  - `danishGetsDanishSitePaths`: Website → `https://android.guard.cocode.dk/`, Privacy →
    `https://android.guard.cocode.dk/privacy/`.
  - `otherLanguagesGetEnglishPaths`: `"en"` and `"de"` → `/en/` and `/en/privacy/`.
  - `sourceIssuesAndContactIgnoreLanguage`.
  - `versionLineShowsNameAndCode`: `versionLine("0.1.0", 1001)` = `"0.1.0 (1001)"`.
- `ui/PromiseStringsTest`: reads `src/main/res/values/strings.xml` and `values-en/strings.xml` and
  asserts that every `promise_*`, `about_*`, `link_*` and `credit_*` key exists in both files, and that
  neither file's promise strings contain "nothing"/"intet" or "transmit"/"sender ingenting" (the
  honesty rule above).
- `ContrastTest` must still pass (the check icon uses `GuardColors.Ok` on Night, already tested).

`./gradlew buildSmoke --no-daemon` is the gate.

## Checked by hand on the owner's phone (not part of the gate)

The launcher shows "Guard"; the home heading says "Guard for Android". With TalkBack: the six promises read one per swipe after the button; the "Hvad vi mener med VPN" section reads as heading then paragraph; "Om Cocode Guard" opens the
page; section headings can be jumped between; each link opens the browser or the mail app; the back
button and the back gesture return home; 200% font: nothing clipped on either screen.

## Out of scope

A full list of every library's license text, a settings page, a changelog screen, sharing the app,
rating prompts.
