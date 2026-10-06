# 01 — DNS filter over a local tunnel

## Goal

The owner taps **Start beskyttelse** and, from then on, every app on the phone gets an empty answer
for names on the AdGuard DNS filter, on any network. The owner always knows, in plain words and by
ear with TalkBack, whether the phone is protected. This is the app's first real feature; it
replaces the shell `HomeScreen`.

Design authority: `docs/superpowers/specs/2026-10-05-guard-android-setup-design.md` (Part 2 and
Accessibility). Read `CLAUDE.md` first; its rules apply.

## How it works

`VpnService` is used only as a **local tunnel that ends inside the app**. There is no server.

- **Tunnel:** `Builder().addAddress("10.111.222.1", 32).addDnsServer("10.111.222.2")
  .addRoute("10.111.222.2", 32).allowFamily(OsConstants.AF_INET).allowFamily(OsConstants.AF_INET6)
  .addDisallowedApplication(packageName).setMtu(1500).setMetered(false).setSession(app_name)
  .setConfigureIntent(<PendingIntent to MainActivity>).setBlocking(true)`.
  The only route is the fake DNS address, so only DNS enters the tunnel. `allowFamily` for both
  families keeps all other IPv4 and IPv6 traffic on the real network (without it, a tunnel with no
  IPv6 address blocks IPv6). The app excludes itself, so its own upstream sockets use the real
  network. `setMetered(false)`: Android otherwise marks a VPN metered whatever network lies under
  it, so on Wi-Fi every app would see a metered connection and hold back updates and backups.
- **Read loop** (one dedicated thread): read a packet from the tunnel `FileInputStream`. Keep only
  IPv4 + UDP + destination `10.111.222.2:53`; drop everything else silently (including TCP).
- **Each query** (handled on `Dispatchers.IO.limitedParallelism(16)`, so slow upstream replies
  cannot exhaust the shared IO pool; the read loop never waits for the network). That limit caps
  threads, not waiting queries, so at most 64 queries (`MAX_PENDING`) are in flight at once; a
  query past that is dropped and the asking app retries.
  1. Parse the DNS question. If the payload is not a parseable query with exactly one question,
     answer `FORMERR` (header only, same id) and never forward it: upstream might resolve a
     blocked name our strict parser could not read. A payload shorter than a header gets nothing.
  2. If `BlockList.isBlocked(name)`: build the blocked answer, increment the blocked counter, and
     write the reply packet to the tunnel.
  3. Otherwise forward the payload to the network's DNS through Android's own resolver (below),
     wait at most 5 s, set the reply's first two bytes back to the query's id, and write the reply
     in a UDP/IPv4 packet with source and destination swapped. On timeout or error, send nothing
     (the asking app retries).
  4. If the phone has no network (the default-network callback reports none), answer `SERVFAIL`
     at once.
- **Writes** to the tunnel `FileOutputStream` are serialized (one lock).
- **Forwarding — "Upstream":** `DnsResolver.getInstance().rawQuery(null, payload,
  DnsResolver.FLAG_EMPTY, executor, cancellationSignal, callback)`. `null` means the app's default
  network, which is the real network because the app is excluded from its own tunnel. Android's
  resolver then talks to the network's DNS server and encrypts the lookup itself whenever Private
  DNS is on, so the filter never turns an encrypted lookup into a plaintext one. Cancel the signal
  at 5 s. There is no other forwarding path.
- `ConnectivityManager.registerDefaultNetworkCallback` tracks whether a network exists and its
  `LinkProperties` (to stop when Private DNS names a server, below). Unregister when protection
  stops.
- **minSdk 29.** This spec raises `minSdk` in `app/build.gradle.kts` from 26 to 29 (Android 10).
  `DnsResolver`, `VpnService.isAlwaysOn()` and `isLockdownEnabled()` all arrive in API 29; below it
  the app could neither keep encrypted lookups encrypted nor tell when Always-on or lockdown is on,
  so it would offer a Stop Android ignores, or say "protected" while lockdown cuts the internet.
  With 29 there are no version branches for these in the code.
- **Block list:** `app/src/main/assets/adguard-dns-filter.txt` (already in the repository, about
  177,700 usable rules). Load it on `Dispatchers.IO` **before** `establish()`, so DNS never enters
  a tunnel that cannot answer yet. Load it once per process and keep the `BlockList` for later
  starts (the sets are presized, about 10–15 MB). Parsing rules:
  - Block rule: a line that is exactly `||<domain>^`.
  - Exception: a line that is exactly `@@||<domain>^` or `@@||<domain>^|`.
  - `<domain>` is one or more labels of `[a-z0-9_-]` joined by single dots, after lowercasing; no
    `*`, `/`, `$`, `|` or `^` inside it.
  - Every other line (comments `!`, blank lines, regex `/…/`, wildcards, `$` modifiers such as
    `$important` or `$badfilter`) is skipped.
  - A rule matches its domain and every subdomain (`example.com` matches `a.b.example.com`, not
    `notexample.com`). An exception anywhere in the name's suffix chain wins over any block.
  - Names are compared lowercased with one trailing dot removed.
- **Blocked answer** (same id and question as the query): flags `QR=1`, opcode copied, `AA=0`,
  `TC=0`, `RD` copied, `RA=1`, `RCODE=0`; `QDCOUNT=1`. Type A: one answer `0.0.0.0`; type AAAA:
  one answer `::`; any other type: no answer (`ANCOUNT=0`). The answer uses a name pointer to
  offset 12 (`0xC00C`), class IN, TTL 60.
- **SERVFAIL answer:** same id and question, `QR=1`, `RD` copied, `RA=1`, `RCODE=2`, no answers.

## Service and notifications

- `GuardVpnService` runs as a foreground service while protecting. On API 34+ it calls
  `startForeground` with `ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED`; below 34 without
  a type.
- Two notification channels, created on first use:
  - `protection` ("Beskyttelse" / "Protection"), importance LOW: the ongoing notification. Tap
    opens the app. While filtering works: title `notif_protected_title`, text
    `notif_protected_text`, action `notif_action_stop` (stops protection). When Always-on VPN
    holds protection on, the Stop action is left out. The notification updates whenever the state
    changes.
  - `alerts` ("Advarsler" / "Alerts"), importance HIGH: the stop notification (below). Each
    successful start cancels an earlier stop alert: it no longer holds, and left in place its
    `onlyAlertOnce` would silence the next one.
- **Stop by the owner** (button or notification action): close the tunnel, stop the thread,
  unregister the callback, `stopForeground(STOP_FOREGROUND_REMOVE)`, `stopSelf()`, state `Off`.
  No alert: the owner asked for it and the screen already says "Ikke beskyttet". This is the only
  path that ends protection without an alert.
- **Stop for any other reason**, with the same cleanup, then the alert notification
  `notif_stopped_title` with the reason's body text, and state `Stopped(reason)`. Never silently:
  - `onRevoke()` → `Revoked`. Android calls it when another VPN app takes over and when the VPN is
    turned off in Android's settings; the app cannot tell which, so the words name both.
  - An exception in the read loop or in `establish()`, or `establish()` returning null → `Error`.
  - **Lockdown** → `Lockdown`: at each start, before `establish()`, if `isLockdownEnabled()` is true,
    do not establish; while protecting, the same check runs again (below). Android's "Block connections without VPN" lets no traffic past the tunnel,
    and this tunnel carries only DNS, so the phone would have no internet.
  - **Private DNS set to a server** → `PrivateDns`: at each start, after the lockdown check and
    before `establish()`, and whenever the default-network callback reports `LinkProperties` with
    `privateDnsServerName != null`. In that mode ("strict") Android sends its encrypted lookups to
    that server through the tunnel's network, which routes only the fake DNS address, so every
    lookup fails: the phone has no internet at all. Checked on a Galaxy A52s (Android 14) on
    2026-10-06; with the tunnel down the same setting worked. The setting is read from the
    app's own default network (the real one, as the app is excluded from its tunnel).
- **Checked again while protecting:** Android need not restart the service when lockdown or
  Always-on change in its settings, so while `Protected` the service repeats the start check
  (`cannotRun(isLockdownEnabled(), privateDnsStrict)`, pure, lockdown first) every 30 s and
  whenever the screen comes back to the front (`onResume` sends the service a recheck intent,
  which never starts protection). A reason stops protection as above; otherwise `alwaysOn` is
  updated to `isAlwaysOn()` and the notification with it.
- **Always-on VPN:** whenever the system starts the service (Android's own "Always-on VPN"
  setting does this, also after boot), it starts protection. The service records
  `isAlwaysOn()` in the state at each start. While it is true the app offers no Stop: Android would
  restart the service, so the screen sends the owner to Android's VPN settings instead (see the
  journey). The app adds nothing of its own for boot: no boot receiver, no
  `RECEIVE_BOOT_COMPLETED`.

## Permissions (exactly these; nothing else is added)

- `<service android:name=".vpn.GuardVpnService" android:permission="android.permission.BIND_VPN_SERVICE"
  android:exported="true" android:foregroundServiceType="systemExempted">` with an intent filter
  for `android.net.VpnService`.
- `<uses-permission>`: `android.permission.INTERNET` (forwarding lookups),
  `android.permission.ACCESS_NETWORK_STATE` (`registerDefaultNetworkCallback`),
  `android.permission.POST_NOTIFICATIONS`, `android.permission.FOREGROUND_SERVICE`,
  `android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED`. Only the VPN consent and notifications
  are asked of the owner; the others are granted at install.

## State

One process-wide `ProtectionRepository` (an `object`) holds `StateFlow<ProtectionState>`:

```kotlin
sealed interface ProtectionStatus {
    data object Off : ProtectionStatus
    data object Starting : ProtectionStatus
    data object Protected : ProtectionStatus
    data object PermissionRefused : ProtectionStatus
    data class Stopped(val reason: StopReason) : ProtectionStatus
}
enum class StopReason { Revoked, Lockdown, PrivateDns, Error }
data class ProtectionState(
    val status: ProtectionStatus = ProtectionStatus.Off,
    val blockedCount: Int = 0,      // since the current start; reset to 0 on each start
    val listSize: Int = 0,          // usable block rules loaded
    val alwaysOn: Boolean = false,  // VpnService.isAlwaysOn() at the last start
)
```

Private DNS is "set to a server" when `LinkProperties.privateDnsServerName != null`; this is the
criterion, not `isPrivateDnsActive`. Only that mode (strict) stops protection. Android's
"Automatic" mode still sends other apps' lookups to the tunnel's DNS address (its encrypted attempt
on port 853 is dropped by the tunnel, and Android falls back to port 53), and the app's own
forwarding stays encrypted through `DnsResolver`, so Automatic needs no warning.

A pure function maps state to what the screen shows, so the journey is unit-tested:

```kotlin
data class HomeUi(
    val statusText: Int,          // string resource id
    val statusTone: Tone,         // Ok, Notice, Urgent
    val detailText: Int?,
    val cards: List<HomeCard>,    // in display order
    val primaryAction: HomeAction,
    val showCounter: Boolean,
)
enum class Tone { Ok, Notice, Urgent }
enum class HomeCard {
    PermissionRefused, StoppedRevoked, StoppedLockdown, StoppedPrivateDns, StoppedError, AlwaysOn, NotificationsOff,
}
enum class HomeAction { Start, Starting, Stop, StartAgain, TryAgain, None }
fun homeUi(state: ProtectionState, notificationsAllowed: Boolean): HomeUi
```

## The screen — the whole journey

One screen, `HomeScreen`, built with `GuardTheme`/`GuardColors` and Material 3. From top to bottom:
title (heading), status block, cards, counter and list line, primary button, a closing line. The
column scrolls (`verticalScroll`) and respects `safeDrawingPadding`; nothing has a fixed height.

| State | Status text (tone, icon) | Detail | Cards | Button |
|---|---|---|---|---|
| Off | Ikke beskyttet (Notice, shield outline) | `detail_off` | NotificationsOff if refused | Start beskyttelse |
| Android's VPN dialog showing | unchanged | | | |
| PermissionRefused | Ikke beskyttet (Notice) | — | PermissionRefused | Prøv igen |
| Starting | Starter … (Notice) | — | | Starter beskyttelse (disabled) |
| Protected | Beskyttet (Ok, filled shield with check) | `detail_protected` | AlwaysOn if always-on; NotificationsOff if refused | Stop beskyttelse, or none if always-on |
| Stopped(Revoked) | Ikke beskyttet (Urgent, shield with slash) | — | StoppedRevoked | Start igen |
| Stopped(Lockdown) | Ikke beskyttet (Urgent, shield with slash) | — | StoppedLockdown | Start igen |
| Stopped(PrivateDns) | Ikke beskyttet (Urgent, shield with slash) | — | StoppedPrivateDns | Start igen |
| Stopped(Error) | Ikke beskyttet (Urgent, shield with slash) | — | StoppedError | Start igen |

- The counter and list line show only in the Protected row.
- "None" means no primary button: the AlwaysOn card's own button is the way out.
- Start: on API 33+, if notifications are not granted, first ask for `POST_NOTIFICATIONS` (the
  answer does not block starting). Then `VpnService.prepare(context)`: a non-null intent is
  launched with `ActivityResultContracts.StartActivityForResult`; `RESULT_OK` starts the service,
  anything else sets `PermissionRefused`. A null intent starts the service directly.
- The StoppedPrivateDns card's button opens `Settings.ACTION_WIRELESS_SETTINGS`, falling back to
  `Settings.ACTION_SETTINGS` if no activity handles it.
- "Notifications refused" means the app's notifications are off, or its `alerts` channel is
  turned off (importance `NONE`): either way a stop could not be told.
- The NotificationsOff card's button opens the app's notification settings
  (`Settings.ACTION_APP_NOTIFICATION_SETTINGS` with `EXTRA_APP_PACKAGE`).
- The AlwaysOn and StoppedLockdown cards' button opens `Settings.ACTION_VPN_SETTINGS`.
- The PermissionRefused, StoppedRevoked and StoppedError cards have no button of their own; the
  primary button is their action.
- Icons are drawn as vector drawables in `res/drawable/` (no icon library is added): `ic_shield_off`
  (outline), `ic_shield_on` (filled with check), `ic_shield_stopped` (with slash).

### Accessibility — required

- Title: `semantics { heading() }`.
- Status block: icon is decorative (`contentDescription = null`); the status text has
  `liveRegion = LiveRegionMode.Polite`, so every status change is spoken. The counter is **not** a
  live region (it would talk over everything).
- Each card's title and body are one TalkBack stop (`semantics(mergeDescendants = true)` on the
  text part) and its button, if it has one, is a separate stop with its own label; the card's title
  is a `heading()`. The button stays separate so it can be found and activated on its own; this
  is what CLAUDE.md and the design mean by a card reading as one unit.
- Counter: visible "Blokeret siden start: 12"; spoken the same, as one unit.
- Buttons: full-width, at least 48dp tall, labels are the action ("Start beskyttelse", never
  "Start"). The disabled Starting button keeps its label and is announced as disabled.
- Colors only from `GuardColors`; state is always in words, never color alone. Any new color pair
  gets a line in `ContrastTest`.
- All text in `sp` via `MaterialTheme.typography`; at 200% font size nothing clips or overlaps.
- Notification texts are the same words as the screen.

### Strings (Danish in `values/`, English in `values-en/`)

| Key | Danish | English |
|---|---|---|
| status_off | Ikke beskyttet | Not protected |
| status_starting | Starter … | Starting … |
| status_protected | Beskyttet | Protected |
| detail_off | Tryk på Start beskyttelse for at blokere farlige sider og reklamer for alle apps, på ethvert netværk. | Tap Start protection to block dangerous sites and ads for every app, on any network. |
| detail_protected | Farlige sider og reklamer blokeres for alle apps. | Dangerous sites and ads are blocked for every app. |
| action_start | Start beskyttelse | Start protection |
| action_starting | Starter beskyttelse | Starting protection |
| action_stop | Stop beskyttelse | Stop protection |
| action_start_again | Start igen | Start again |
| action_try_again | Prøv igen | Try again |
| counter | Blokeret siden start: %1$d | Blocked since start: %1$d |
| list_line | Blokeringsliste: AdGuard DNS filter, %1$s navne | Block list: AdGuard DNS filter, %1$s names |
| card_refused_title | Tilladelsen blev afvist | Permission was refused |
| card_refused_body | Appen skal have Androids VPN-tilladelse for at se DNS-opslag. Den bruges kun på telefonen, og der er ingen Cocode-server: opslag, der ikke blokeres, går til netværkets egen DNS-server som før. | The app needs Android's VPN permission to see DNS lookups. It is used only on the phone, and there is no Cocode server: lookups that are not blocked go to the network's own DNS server, as before. |
| card_revoked_title | Beskyttelsen er stoppet | Protection has stopped |
| card_revoked_body | Android stoppede beskyttelsen, for eksempel fordi en anden VPN-app tog over, eller fordi VPN blev slået fra i indstillingerne. DNS-opslag bliver ikke længere filtreret. | Android stopped protection, for example because another VPN app took over or the VPN was turned off in settings. DNS lookups are no longer filtered. |
| card_lockdown_title | Beskyttelsen kan ikke køre | Protection can't run |
| card_lockdown_body | "Bloker forbindelser uden VPN" er slået til. Appen sender kun DNS-opslag gennem sin tunnel, så med den indstilling kan telefonen slet ikke komme på nettet. Slå indstillingen fra under VPN-indstillinger, og start igen. | "Block connections without VPN" is on. The app sends only DNS lookups through its tunnel, so with that setting the phone can't reach the internet at all. Turn the setting off in VPN settings, then start again. |
| card_error_title | Beskyttelsen er stoppet | Protection has stopped |
| card_error_body | Der skete en fejl. DNS-opslag bliver ikke længere filtreret. | Something went wrong. DNS lookups are no longer filtered. |
| card_always_on_title | Altid aktiveret VPN er slået til | Always-on VPN is on |
| card_always_on_body | Android holder beskyttelsen tændt. Slå Altid aktiveret VPN fra under VPN-indstillinger for at kunne stoppe den. | Android keeps protection on. Turn off Always-on VPN in VPN settings to be able to stop it. |
| card_vpn_settings_action | Åbn VPN-indstillinger | Open VPN settings |
| card_private_dns_title | Beskyttelsen kan ikke køre med Privat DNS | Protection can't run with Private DNS |
| card_private_dns_body | Privat DNS er sat til en bestemt server. Med den indstilling kan telefonen slet ikke komme på nettet, mens beskyttelsen kører, så den er stoppet. DNS-opslag bliver ikke filtreret. Slå Privat DNS fra, eller vælg Automatisk under Netværk og internet, og start igen. | Private DNS is set to a specific server. With that setting the phone can't reach the internet at all while protection runs, so it has stopped. DNS lookups are not filtered. Turn Private DNS off or choose Automatic under Network and internet, then start again. |
| card_private_dns_action | Åbn netværksindstillinger | Open network settings |
| card_notifications_title | Notifikationer er slået fra | Notifications are off |
| card_notifications_body | Appen kan ikke sige til, hvis beskyttelsen stopper. | The app can't tell you if protection stops. |
| card_notifications_action | Åbn notifikationsindstillinger | Open notification settings |
| closing_line | Kun DNS-opslag går gennem appen. Ingen server, ingen konto. Apps med deres egen sikre DNS går uden om filteret. | Only DNS lookups pass through the app. No server, no account. Apps with their own secure DNS bypass the filter. |
| channel_protection | Beskyttelse | Protection |
| channel_alerts | Advarsler | Alerts |
| notif_protected_title | Beskyttet | Protected |
| notif_protected_text | Farlige sider og reklamer blokeres. | Dangerous sites and ads are blocked. |
| notif_action_stop | Stop | Stop |
| notif_stopped_title | Beskyttelsen er stoppet | Protection has stopped |

The alert notification's text is `card_revoked_body`, `card_lockdown_body`, `card_private_dns_body`
or `card_error_body`. `list_line` formats
the count with the locale's grouping (`NumberFormat.getIntegerInstance()`). The shell strings
`status_not_built` and `home_explain` are removed.

## Files

Keep Android-free logic in plain Kotlin so it runs in JVM tests. Every code file under 200 lines.

- `blocklist/BlockList.kt` — `class BlockList(blocked: Set<String>, allowed: Set<String>)` with
  `fun isBlocked(name: String): Boolean` and `val size: Int` (= blocked rules).
- `blocklist/RuleParser.kt` — `fun parseRules(lines: Sequence<String>): BlockList`.
- `net/Ipv4Udp.kt` — `data class UdpPacket(srcIp: ByteArray, dstIp: ByteArray, srcPort: Int,
  dstPort: Int, payload: ByteArray)`; `fun parseIpv4Udp(buf: ByteArray, length: Int): UdpPacket?`
  (handles IHL > 5, rejects non-IPv4, non-UDP, lengths past `length`);
  `fun buildIpv4Udp(p: UdpPacket): ByteArray` (valid IPv4 header checksum and UDP checksum, TTL 64,
  DF set).
- `dns/DnsMessage.kt` — `data class DnsQuestion(id: Int, name: String, type: Int, questionEnd: Int)`;
  `fun parseQuery(payload: ByteArray): DnsQuestion?` (null when QR=1, QDCOUNT≠1, or any read
  would pass the end; compression pointers in the question are rejected);
  `fun blockedAnswer(query: ByteArray, q: DnsQuestion): ByteArray`;
  `fun servfail(query: ByteArray, q: DnsQuestion): ByteArray`.
- `vpn/GuardVpnService.kt`, `vpn/PacketLoop.kt`, `vpn/Upstream.kt` (forwarding through
  `DnsResolver`, the network callback, the Private DNS check), `vpn/ProtectionRepository.kt` (state above
  and `cannotRun`).
- `notify/Notifications.kt` — channels, ongoing and alert notifications.
- `ui/HomeUi.kt` (`homeUi` and its types), `ui/HomeScreen.kt`, `ui/HomeCards.kt`,
  `MainActivity.kt` (permission and VPN-consent launchers).

## Acceptance tests (JVM, offline, under `app/src/test/java/dk/cocode/guard/`)

No real sockets, no name lookups, no Android framework in these tests.

- `blocklist/RuleParserTest`
  - `blockRuleIsParsed`: `||ads.example.com^` → `isBlocked("ads.example.com")`.
  - `exceptionFormsAreParsed`: `@@||ok.example.com^` and `@@||ok.example.com^|` both allow.
  - `otherLinesAreSkipped`: `! comment`, blank, `/^ads/`, `||ads*.example.com^`,
    `||x.example.com^$important`, `||x.example.com^$badfilter`, `.bbelements.com^` add nothing.
  - `rulesAreLowercased`: `||Ads.Example.COM^` blocks `ads.example.com`.
  - `shippedListLoads`: parsing `src/main/assets/adguard-dns-filter.txt` gives `size >= 170_000`.
- `blocklist/BlockListTest`
  - `exactNameIsBlocked`, `subdomainIsBlocked` (`a.b.example.com`), `parentIsNotBlocked`
    (rule `ads.example.com` does not block `example.com`), `lookalikeIsNotBlocked`
    (`notexample.com`), `exceptionWinsOverParentBlock` (`||example.com^` + `@@||cdn.example.com^`:
    `cdn.example.com` and `x.cdn.example.com` allowed, `ads.example.com` blocked),
    `caseAndTrailingDotIgnored` (`Ads.Example.COM.`).
- `net/Ipv4UdpTest`
  - `parsesValidPacket`, `parsesHeaderWithOptions` (IHL 6), `rejectsTcp`, `rejectsIpv6`,
    `rejectsTruncated` (declared length past the buffer), `builtHeaderChecksumVerifies` (one's
    complement sum of the header is `0xFFFF`), `builtUdpChecksumVerifies` (pseudo-header sum
    `0xFFFF`), `roundTrip` (build then parse returns the same fields and payload).
- `dns/DnsMessageTest`
  - `parsesAQuery` (`www.example.com`, type 1), `rejectsResponse` (QR=1), `rejectsTwoQuestions`,
    `rejectsTruncatedName`, `rejectsCompressionPointerInQuestion`.
  - `blockedAnswerForA` (same id, flags `0x8180` when RD was set, ANCOUNT 1, pointer `0xC00C`,
    TTL 60, RDATA `0.0.0.0`), `blockedAnswerForAaaa` (16 zero bytes), `blockedAnswerForOtherType`
    (type 65: ANCOUNT 0, RCODE 0), `servfailHasRcode2`.
- `ui/HomeUiTest`
  - One test per row of the journey table (Off, PermissionRefused, Starting, Protected,
    Stopped(Revoked), Stopped(Lockdown), Stopped(PrivateDns), Stopped(Error)) asserting status
    text, tone, cards in order, action and `showCounter`; plus `stoppedNeverSaysProtected`,
    `alwaysOnHidesStopAndShowsCard` and `notificationsCardWhenRefused`.
- `vpn/CannotRunTest`: `lockdownStops`, `privateDnsServerStops`, `lockdownIsNamedWhenBoth`,
  `runsWhenNeither`.
- `vpn/PacketLoopTest` (in-memory tunnel, fake upstream): among others `pendingQueriesAreCapped`
  (a resolver that never answers sees exactly `MAX_PENDING` queries; after it answers, new ones
  are admitted) and `rejectedQueryIsAnsweredFormerrNeverForwarded`.

The existing `ContrastTest` must still pass. `./gradlew buildSmoke --no-daemon` is the gate.

## Checked by hand on the owner's phone (not part of the gate)

With TalkBack on: start, confirm Android's dialog, hear "Beskyttet"; open a known ad host in a
browser and see it fail; load an IPv6-only site and see it work; turn on a second VPN app and get
the alert; set Private DNS to a host name and get the Private DNS alert, with internet working again; turn on Always-on VPN
and see the card instead of Stop; turn on "Block connections without VPN" and get the lockdown
alert; font size at maximum with nothing clipped.

## Out of scope

A boot receiver of our own (Android's Always-on VPN setting is honoured, see above), list updates
or downloads, an allowlist screen, per-site alerts, blocking DoH
endpoints, a history of lookups, TCP DNS (dropped), IPv6 transport inside the tunnel, the
owner-chosen upstream resolver (a later spec), battery-optimisation guidance, Google Play.
