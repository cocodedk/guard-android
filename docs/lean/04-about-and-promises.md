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
address lists are downloaded from their publishers. Nor may a promise say the app sends no
information about you: a forwarded lookup tells the network's DNS server which name was asked for,
and a list download shows the publisher the phone's IP address (the spec 04 grill, 2026-10-06).
What is true, and what the screen says: the app **collects** no information about you, there is no
Cocode server, and the next promises say plainly what does leave the phone and to whom.

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
| promise_no_data | Appen indsamler ingen oplysninger om dig. Der er ingen konto, ingen sporing, ingen statistik og ingen reklamer. | The app collects no information about you. There is no account, tracking, analytics or ads. |
| promise_on_phone | Blokeringen sker på telefonen. Der er ingen Cocode-server og ingen VPN-server. Lister over farlige adresser hentes direkte fra udgiverne, som kan se din IP-adresse, ligesom en hjemmeside kan det ved et besøg. | The blocking happens on the phone. There is no Cocode server and no VPN server. Lists of dangerous addresses are downloaded directly from their publishers, who can see your IP address, just as a website can when you visit it. |
| promise_only_dns | Kun DNS-opslag og forbindelser til kendte farlige adresser går gennem appen. Et DNS-opslag er måden, telefonen finder ud af, hvor en side ligger. Opslag, der ikke blokeres, går videre til netværkets egen DNS-server som før. Resten af din trafik rører appen ikke. | Only DNS lookups and connections to known dangerous addresses pass through the app. A DNS lookup is how the phone finds out where a site is. Lookups that aren't blocked go on to the network's own DNS server, as before. The app never touches the rest of your traffic. |
| promise_free | Appen er gratis, og alle kan læse koden. Den er fri software under licensen GPL-3.0. | The app is free of charge, and anyone can read its code. It is free software under the GPL-3.0 license. |
| promise_permissions | Appen beder kun om to tilladelser: VPN og notifikationer. Den har ikke adgang til dine kontakter, din placering eller dine filer. | The app asks for only two permissions: VPN and notifications. It has no access to your contacts, location or files. |
| promise_accessible | Kan bruges med TalkBack og stor skrift. | Works with TalkBack and large text sizes. |
| promise_limit | Én begrænsning: Apps med deres egen krypterede DNS går uden om navnefilteret, så i dem kan nogle farlige sider og reklamer slippe igennem. | One limit: apps that use their own encrypted DNS bypass the name filter, so some dangerous sites and ads can get through in them. |
| action_about | Om Guard for Android | About Guard for Android |
| action_back | Tilbage | Back |
| about_title | Om Guard for Android | About Guard for Android |
| about_version | Version %1$s | Version %1$s |
| about_what | Guard for Android blokerer farlige sider, reklamer og forbindelser til kendte farlige adresser i alle apps på telefonen, på ethvert netværk. Før en app åbner en side, slår telefonen op, hvor siden ligger. Det kaldes et DNS-opslag. Står sidens navn på appens liste over farlige sider og reklamer, får telefonen et tomt svar, og siden åbner ikke. Forbindelser til adresser på lister over kendte farlige adresser blokeres. | Guard for Android blocks dangerous sites, ads and connections to known dangerous addresses in every app on the phone, on any network. Before an app opens a site, the phone looks up where the site is. This is called a DNS lookup. If the site's name is on the app's list of dangerous sites and ads, the phone gets an empty answer and the site does not open. Connections to addresses on lists of known dangerous addresses are blocked. |
| about_vpn_title | Hvad vi mener med VPN | What we mean by VPN |
| about_vpn_body | Android kalder appen en VPN, fordi den bruger Androids VPN-funktion. Men den er ikke en VPN i den sædvanlige forstand: Der er ingen VPN-server, og din trafik sendes ikke via en ekstra server. Appen laver en lokal tunnel, en forbindelse der starter og slutter inde i appen på telefonen. Kun DNS-opslag og forbindelser til kendte farlige adresser går ind i den, så appen kan blokere dem. Al anden trafik går ud som før. Derfor viser Android en nøgle i statuslinjen, og derfor kan der kun køre én VPN-app ad gangen. Har du en anden VPN-app, kan du altså ikke bruge den samtidig med Guard for Android. | Android calls the app a VPN because it uses Android's VPN feature. But it is not a VPN in the usual sense: there is no VPN server, and your traffic does not go through an extra server. The app makes a local tunnel, a connection that starts and ends inside the app, on the phone. Only DNS lookups and connections to known dangerous addresses enter it, so the app can block them. All other traffic goes out as before. That is why Android shows a key in the status bar, and why only one VPN app can run at a time. If you use another VPN app, you can't run it together with Guard for Android. |
| about_free_title | Fri software | Free software |
| about_free_body | Appen er gratis og udgivet under GNU General Public License, version 3 eller senere (GPL-3.0). Du må bruge, læse, ændre og dele den. Koden finder du under "Se kildekoden på GitHub" nedenfor. | The app is free of charge and released under the GNU General Public License, version 3 or later (GPL-3.0). You may use, read, change and share it. You can find the code under "See the source code on GitHub" below. |
| about_links_title | Links | Links |
| link_website | Åbn hjemmesiden | Open the website |
| link_privacy | Læs privatlivspolitikken | Read the privacy policy |
| link_source | Se kildekoden på GitHub | See the source code on GitHub |
| link_issues | Meld en fejl på GitHub | Report a problem on GitHub |
| link_no_browser | Der er ingen app på telefonen, der kan åbne linket. Installer en browser eller en mailapp, og prøv igen. | No app on the phone can open the link. Install a browser or an email app, then try again. |
| about_credits_title | Lister og tak | Lists and credits |
| credit_adguard | Navnelisten over farlige sider og reklamer er AdGuard DNS filter fra AdGuard (GPL-3.0). Den følger med i appen. | The name list of dangerous sites and ads is the AdGuard DNS filter from AdGuard (GPL-3.0). It comes with the app. |
| credit_spamhaus | Adresselisterne Spamhaus DROP og DROPv6 kommer fra The Spamhaus Project, og appen henter dem direkte derfra. © The Spamhaus Project SLU. | The Spamhaus DROP and DROPv6 address lists come from The Spamhaus Project, and the app downloads them directly from there. © The Spamhaus Project SLU. |
| credit_feodo | Adresselisten Feodo Tracker kommer fra abuse.ch (CC0), og appen henter den direkte derfra. | The Feodo Tracker address list comes from abuse.ch (CC0), and the app downloads it directly from there. |
| credit_androidx | Appen er bygget med AndroidX og Jetpack Compose (Apache-2.0). | The app is built with AndroidX and Jetpack Compose (Apache-2.0). |
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

The launcher shows "Guard"; the home heading says "Guard for Android". With TalkBack: the six promises read one per swipe after the button; the "Hvad vi mener med VPN" section reads as heading then paragraph; "Om Guard for Android" opens the
page; section headings can be jumped between; each link opens the browser or the mail app; the back
button and the back gesture return home; 200% font: nothing clipped on either screen.

## Out of scope

A full list of every library's license text, a settings page, a changelog screen, sharing the app,
rating prompts.
