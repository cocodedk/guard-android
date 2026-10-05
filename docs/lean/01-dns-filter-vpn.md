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
  .addRoute("10.111.222.2", 32).addDisallowedApplication(packageName).setMtu(1500)
  .setSession(app_name).setConfigureIntent(<PendingIntent to MainActivity>).setBlocking(true)`.
  The only route is the fake DNS address, so only DNS enters the tunnel. The app excludes itself,
  so its own upstream sockets use the real network.
- **Read loop** (one dedicated thread): read a packet from the tunnel `FileInputStream`. Keep only
  IPv4 + UDP + destination `10.111.222.2:53`; drop everything else silently (including TCP).
- **Each query** (handled on `Dispatchers.IO.limitedParallelism(16)`, so slow upstream replies
  cannot exhaust the shared IO pool; the read loop never waits for the network):
  1. Parse the DNS question. If the payload is not a parseable query with exactly one question,
     forward it unchanged (never break DNS because our parser is strict).
  2. If `BlockList.isBlocked(name)`: build the blocked answer, increment the blocked counter, and
     write the reply packet to the tunnel.
  3. Otherwise forward the payload byte-for-byte to the upstream server on UDP port 53 from a new
     `DatagramSocket` (no `protect()` needed: the app is excluded from its own tunnel), wait at
     most 5 s, and write the reply payload
     back unchanged in a UDP/IPv4 packet with source and destination swapped. On timeout or
     socket error, send nothing (the asking app retries).
  4. If no upstream server is known, answer `SERVFAIL` at once.
- **Writes** to the tunnel `FileOutputStream` are serialized (one lock).
- **Upstream:** the first DNS server in the `LinkProperties` of the default network, kept current
  with `ConnectivityManager.registerDefaultNetworkCallback` (it sees the real network because the
  app is excluded from its own tunnel). Unregister when protection stops.
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
  - `protection` ("Beskyttelse" / "Protection"), importance LOW: the ongoing notification
    "Beskyttet" / "Protected", text "Farlige sider og reklamer blokeres." / "Dangerous sites and
    ads are blocked.", tap opens the app, action "Stop" / "Stop" stops protection.
  - `alerts` ("Advarsler" / "Alerts"), importance HIGH: the stop notification (below).
- **Stop by the owner** (button or notification action): close the tunnel, stop the thread,
  unregister the callback, `stopForeground(STOP_FOREGROUND_REMOVE)`, `stopSelf()`. No alert.
- **Stop for any other reason** (`onRevoke()`, typically another VPN app took over; or an
  exception in the read loop or in `establish()`): do the same cleanup, then post the alert
  notification "Beskyttelsen er stoppet" / "Protection has stopped" with the reason text from the
  strings table below, and set the state to `Stopped(reason)`. Never fail silently.
- Whenever the system starts the service (including always-on VPN), it starts protection.

## Permissions (exactly these; nothing else is added)

- `<service android:name=".vpn.GuardVpnService" android:permission="android.permission.BIND_VPN_SERVICE"
  android:exported="true" android:foregroundServiceType="systemExempted">` with an intent filter
  for `android.net.VpnService`.
- `<uses-permission>`: `android.permission.INTERNET` (the upstream socket),
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
enum class StopReason { OtherVpn, Error }
data class ProtectionState(
    val status: ProtectionStatus = ProtectionStatus.Off,
    val blockedCount: Int = 0,      // since the current start; reset to 0 on each start
    val listSize: Int = 0,          // usable block rules loaded
    val privateDnsStrict: Boolean = false,
)
```

`privateDnsStrict` is `LinkProperties.privateDnsServerName != null` on API 28+, read from the same
default-network callback (and once when the app opens), `false` below API 28. Only strict mode (a
host name set) bypasses the filter; Android's "Automatic" mode falls back to the tunnel's DNS.

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
enum class HomeCard { PermissionRefused, StoppedOtherVpn, StoppedError, PrivateDns, NotificationsOff }
enum class HomeAction { Start, Starting, Stop, StartAgain, TryAgain }
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
| Protected | Beskyttet (Ok, filled shield with check) | `detail_protected` | PrivateDns if strict; NotificationsOff if refused | Stop beskyttelse |
| Stopped(OtherVpn) | Ikke beskyttet (Urgent, shield with slash) | — | StoppedOtherVpn | Start igen |
| Stopped(Error) | Ikke beskyttet (Urgent, shield with slash) | — | StoppedError | Start igen |

- The counter and list line show only while Protected.
- Start: on API 33+, if notifications are not granted, first ask for `POST_NOTIFICATIONS` (the
  answer does not block starting). Then `VpnService.prepare(context)`: a non-null intent is
  launched with `ActivityResultContracts.StartActivityForResult`; `RESULT_OK` starts the service,
  anything else sets `PermissionRefused`. A null intent starts the service directly.
- The PrivateDns card's button opens `Settings.ACTION_WIRELESS_SETTINGS`, falling back to
  `Settings.ACTION_SETTINGS` if no activity handles it.
- The NotificationsOff card's button opens the app's notification settings
  (`Settings.ACTION_APP_NOTIFICATION_SETTINGS` with `EXTRA_APP_PACKAGE`).
- Icons are drawn as vector drawables in `res/drawable/` (no icon library is added): `ic_shield_off`
  (outline), `ic_shield_on` (filled with check), `ic_shield_stopped` (with slash), `ic_warning`.

### Accessibility — required

- Title: `semantics { heading() }`.
- Status block: icon is decorative (`contentDescription = null`); the status text has
  `liveRegion = LiveRegionMode.Polite`, so every status change is spoken. The counter is **not** a
  live region (it would talk over everything).
- Each card is one TalkBack stop for its text (`semantics(mergeDescendants = true)` on the text
  part) and its button is a separate stop with its own label; the card's title is a `heading()`.
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
| card_refused_body | Appen skal have Androids VPN-tilladelse for at se DNS-opslag. Den bruges kun på telefonen; intet sendes til en server. | The app needs Android's VPN permission to see DNS lookups. It is used only on the phone; nothing is sent to a server. |
| card_other_vpn_title | Beskyttelsen er stoppet | Protection has stopped |
| card_other_vpn_body | En anden VPN-app tog over. Telefonen bruger nu almindelig DNS uden blokering. | Another VPN app took over. The phone now uses normal DNS without blocking. |
| card_error_title | Beskyttelsen er stoppet | Protection has stopped |
| card_error_body | Der skete en fejl. Telefonen bruger nu almindelig DNS uden blokering. | Something went wrong. The phone now uses normal DNS without blocking. |
| card_private_dns_title | Privat DNS går uden om filteret | Private DNS bypasses the filter |
| card_private_dns_body | Privat DNS er slået til med en bestemt server, så intet bliver blokeret. Slå det fra eller vælg Automatisk under Netværk og internet. | Private DNS is set to a specific server, so nothing is blocked. Turn it off or choose Automatic under Network and internet. |
| card_private_dns_action | Åbn netværksindstillinger | Open network settings |
| card_notifications_title | Notifikationer er slået fra | Notifications are off |
| card_notifications_body | Appen kan ikke sige til, hvis beskyttelsen stopper. | The app can't tell you if protection stops. |
| card_notifications_action | Åbn notifikationsindstillinger | Open notification settings |
| closing_line | Kun DNS-opslag går gennem appen. Ingen server, ingen konto. | Only DNS lookups pass through the app. No server, no account. |
| channel_protection | Beskyttelse | Protection |
| channel_alerts | Advarsler | Alerts |
| notif_protected_title | Beskyttet | Protected |
| notif_protected_text | Farlige sider og reklamer blokeres. | Dangerous sites and ads are blocked. |
| notif_action_stop | Stop | Stop |
| notif_stopped_title | Beskyttelsen er stoppet | Protection has stopped |

The alert notification's text is `card_other_vpn_body` or `card_error_body`. `list_line` formats
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
- `vpn/GuardVpnService.kt`, `vpn/PacketLoop.kt`, `vpn/Upstream.kt` (callback, current server,
  private-DNS flag), `vpn/ProtectionRepository.kt` (state above).
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
    Stopped(OtherVpn), Stopped(Error)) asserting status text, tone, cards in order, action and
    `showCounter`; plus `privateDnsCardOnlyWhenProtectedAndStrict` and
    `notificationsCardWhenRefused`.

The existing `ContrastTest` must still pass. `./gradlew buildSmoke --no-daemon` is the gate.

## Checked by hand on the owner's phone (not part of the gate)

With TalkBack on: start, confirm Android's dialog, hear "Beskyttet"; open a known ad host in a
browser and see it fail; turn on a second VPN app and get the alert; set Private DNS to a host
name and see the card; font size at maximum with nothing clipped.

## Out of scope

Start on boot, list updates or downloads, an allowlist screen, per-site alerts, blocking DoH
endpoints, a history of lookups, TCP DNS (dropped), IPv6 transport inside the tunnel, the
owner-chosen upstream resolver (spec 02), battery-optimisation guidance, Google Play.
