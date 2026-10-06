# 02 — Block known-bad IP addresses

## Goal

Spec 01 blocks dangerous **names**. An app that connects straight to an IP address never asks DNS,
so typing a malicious site's address, or malware with a hard-coded command server, gets past it.
This spec closes that gap: while protection runs, every connection from any app to an address on a
trusted public list of bad addresses is refused at once, counted, and named in a notification. The
owner always knows, in plain words and by ear with TalkBack, whether address blocking is active and
how old its lists are.

Builds on spec 01 as merged (including the Private DNS stop and `setMetered(false)`). Design
authority: `docs/superpowers/specs/2026-10-05-guard-android-setup-design.md`. Read `CLAUDE.md`
first; its rules apply. Sister issue for the box: cocodedk/network-defence#3.

## The rule this spec relies on

Only DNS and traffic to listed bad addresses enter the tunnel. Traffic to a listed address is
answered inside the app with a refusal and never leaves the phone; nothing is ever relayed to the
internet, and there is no remote server. All other traffic stays on the real network and never
reaches the app.

## The lists

| Id | Name on screen | Source (HTTPS) | Format | Size on 2026-10-06 |
|---|---|---|---|---|
| `drop_v4` | Spamhaus DROP | `https://www.spamhaus.org/drop/drop_v4.json` | JSON lines | 1,641 ranges, ~100 KB (~15 KB gzip) |
| `drop_v6` | Spamhaus DROPv6 | `https://www.spamhaus.org/drop/drop_v6.json` | JSON lines | 91 ranges, ~6 KB |
| `feodo` | abuse.ch Feodo Tracker | `https://feodotracker.abuse.ch/downloads/ipblocklist_recommended.txt` | plain text | 1 address |

- **Spamhaus JSON lines:** one JSON object per line. A range line has `"cidr":"<a.b.c.d/n>"` (or an
  IPv6 CIDR), plus fields we ignore (`sblid`, `rir`). The last line is the metadata object with
  `"type":"metadata"`. Parse each line with a small pattern for the `cidr` value; do **not** use
  `org.json` (it is a stub in JVM tests). A body without the metadata line is truncated: reject it.
- **Feodo text:** lines starting with `#` and blank lines are skipped; every other line, trimmed, must
  be a single IPv4 address, which becomes a `/32`. Any other line rejects the whole body.
- **Not used:** FireHOL level1. It repackages Spamhaus DROP and also lists bogon and private ranges,
  which would cut the phone off from its own network.
- **Terms.** Spamhaus DROP is free to use, but its content is copyrighted and there is no licence
  to redistribute it. So it is **never** committed to the repository or shipped in the APK: the
  phone downloads it itself, and tests use small hand-written fixtures. Spamhaus also forbids using
  its name in marketing or other promotional material, so the name appears **only on the app
  screen and in notifications**, as plain attribution of where an entry came from, and never on the
  website's promotional pages, in the store listing or in release notes. The privacy pages and the
  README name it all the same: they state who receives the phone's IP address when a list is
  downloaded, a disclosure, not promotion. Feodo Tracker's terms are at
  `https://feodotracker.abuse.ch/blocklist/`; it is also downloaded, not shipped.

### Which ranges are kept

After parsing, every entry is a CIDR (address + prefix length) checked like this:

- The address must have its host bits zero for the prefix (`192.0.2.0/24`, not `192.0.2.7/24`);
  otherwise the entry is skipped. IPv4 prefix 0–32, IPv6 prefix 0–128; anything else is skipped.
- **Reserved ranges are never blocked.** An entry that overlaps any of these is skipped (a real
  bad range never does; overlap means a broken list): IPv4 `0.0.0.0/8`, `10.0.0.0/8`,
  `100.64.0.0/10`, `127.0.0.0/8`, `169.254.0.0/16`, `172.16.0.0/12`, `192.0.0.0/24`,
  `192.168.0.0/16`, `198.18.0.0/15`, `224.0.0.0/4`, `240.0.0.0/4`; IPv6 `::/8`, `64:ff9b::/96`
  (NAT64, which carries IPv4 on IPv6-only mobile networks), `fc00::/7`, `fe80::/10`, `ff00::/8`.
  These cover the tunnel's own addresses (`10.111.222.1`, `10.111.222.2`, `fd47:7561:7264::1`).
  `0.0.0.0/0` and `::/0` overlap them too, so they are skipped.
- Exact duplicates are merged, and a range that lies inside another kept range is dropped, first
  within each list and then across lists (a Feodo `/32` inside a DROP range adds nothing). The
  result is the **route set**.
- **Plausibility:** a parsed and filtered `drop_v4` with fewer than 500 ranges, or `drop_v6` with
  fewer than 20, is rejected as a broken download. `feodo` may be empty (it often is nearly so).

## How it works

### Download

- **Who and how:** the app itself, with `java.net.HttpURLConnection` (no new library), HTTPS only,
  connect and read timeout 30 s each, response code 200 required, body at most 2 MB (stop reading
  and reject beyond that). Android's `HttpURLConnection` accepts gzip on its own. The app is
  excluded from its own tunnel, so downloads use the real network. Nothing but these three URLs
  is ever fetched, and what is fetched is data that is parsed, never code.
- **When:** only while protection runs. Protection off means no downloads and no background work.
  - At each start, after the status is `Protected`, if any list has no stored copy or its stored
    copy was fetched more than 24 h ago, fetch all three, one after another, on `Dispatchers.IO`.
  - While protecting, a coroutine in the service wakes every hour and does the same check. The time
    of the last **attempt** is kept in memory, and a new attempt starts only 1 h or more after the
    previous one, so a failing source is tried at most once an hour.
  - Starting never waits for a download: the tunnel comes up with whatever stored lists are fresh.
- **Each list on its own:** a list whose download fails (network error, non-200, too big, parse
  error, missing metadata, below plausibility) keeps its last good copy untouched. A good download
  is written to `filesDir/iplists/<id>.txt` as normalised CIDR lines, one per line, through a temp
  file and an atomic rename; the file's modification time is its fetch time.
- **Age:** a stored list fetched more than **7 days** ago is not used at all (Spamhaus warns that
  old copies block networks that have since been reassigned to innocent owners). It stays on disk
  and is replaced by the next good download.

### Tunnel

- The spec 01 builder gains `.addAddress("fd47:7561:7264::1", 128)` and one `.addRoute(cidr)` per
  range in the route set (about 1,730 on 2026-10-06), next to the existing `addRoute("10.111.222.2",
  32)`. Everything else in the spec 01 builder stays, including `allowFamily` for both families,
  `addDisallowedApplication(packageName)` and `setMetered(false)`.
- **Swap on new lists:** when a download changes the route set, or the hourly check finds that a
  list has aged past 7 days,
  build and `establish()` a new tunnel with the new routes, start a new `PacketLoop` on it, then
  stop the old loop and close the old descriptor, in that order, on the main thread like the rest
  of start and stop. The old loop is stopped (its `running` flag false) **before** its descriptor
  is closed, so its read failure is not reported as an `Error` stop. If the new `establish()`
  returns null or throws, protection stops with `StopReason.Error` as in spec 01.
- At start, the stored lists are loaded on `Dispatchers.IO` together with the name list, before
  `establish()`.

### Packets to a listed address

The read loop now parses the IP version first:

- IPv4 + UDP to `10.111.222.2:53` → the spec 01 DNS path, unchanged.
- Otherwise, IPv4 or IPv6 whose destination is in the route set (checked against the set, not
  assumed from the route) → a **refusal**:
  - TCP with SYN set and ACK clear → write back a TCP RST+ACK: source and destination addresses and
    ports swapped, sequence number 0, acknowledgement number = the SYN's sequence number + 1 +
    payload length, window 0, no options, valid TCP checksum over the IPv4 or IPv6 pseudo-header,
    and for IPv4 a valid header checksum, TTL 64.
  - UDP → write back an ICMP destination unreachable from the blocked address to the sender: IPv4
    type 3 code 13 (communication administratively prohibited), body = the original IP header plus
    the first 8 bytes after it; IPv6 type 1 code 1, body = as much of the original packet as keeps
    the reply within 1,280 bytes, checksum over the IPv6 pseudo-header.
  - Anything else (other TCP segments, other protocols, fragments) is dropped without a reply.
- Everything else is dropped silently, as in spec 01.
- Writes go through the existing write lock.
- **Counting:** a refusal counts as one blocked connection the first time its flow is seen in this
  start. The flow is (protocol, source port, destination address, destination port); a bounded set
  of the most recent 1,024 flows keeps TCP retransmits of the same SYN and further packets of the
  same UDP flow from counting twice. The counter resets at each start, like spec 01's.

### Notifications

- A third channel, `blocked_addresses` ("Blokerede adresser" / "Blocked addresses"), importance
  DEFAULT, created with the other two.
- The first time a given address is refused in a start, post one notification on that channel:
  title `notif_address_title`, text `notif_address_text_app` with the name of the app that tried,
  the address and the list's screen name, or `notif_address_text` (no app) when Android cannot
  tell. Tapping opens the app. Each address gets its own notification id within the start.
- **Which app:** before the refusal is sent (while the app's socket still exists), the service
  asks `ConnectivityManager.getConnectionOwnerUid(protocol, source, destination)`, which Android
  answers for connections through the asking app's own VPN, and names the UID's apps with
  `PackageManager` (two names joined by " / " for a shared UID; `app_android_system` for a UID
  below `Process.FIRST_APPLICATION_UID` with no visible package; otherwise no name). It is asked
  only for a notification that is actually posted, at most 20 times a start. Android shows this
  app only apps with a launcher icon, through the manifest's `<queries>` (below); an app without
  one is not named.
- At most **20** such notifications per start; after that, refusals are still counted on the screen
  but no more are posted, so a busy piece of malware cannot flood the shade.
- The ongoing and alert notifications of spec 01 are unchanged.

## Permissions

None added. `INTERNET` (spec 01) already covers the downloads; no storage permission is needed for
`filesDir`; no alarm, boot or work-scheduling permission is used. The manifest gains a `<queries>`
element for `MAIN`/`LAUNCHER` intents, so apps with a launcher icon can be named; it is not a
permission, and `QUERY_ALL_PACKAGES` is not used.

## State

`ProtectionState` gains:

```kotlin
val blockedAddressCount: Int = 0,         // since the current start; reset to 0 on each start
val addressLists: List<ListStatus> = emptyList(),  // one per list, in AddressList order

enum class AddressList(val source: String) {        // `source` is shown as is, in both languages
    DropV4("Spamhaus DROP (IPv4)"), DropV6("Spamhaus DROP (IPv6)"), Feodo("abuse.ch Feodo Tracker"),
}
enum class ListState { NotYet, Active, TooOld }
data class StoredList(val fetched: Long, val entries: Int)   // epoch ms; entries kept after filtering
data class ListStatus(val list: AddressList, val state: ListState, val entries: Int, val fetched: Long?)
```

Each list has its own state, because downloads fail independently: `NotYet` when no copy is
stored (`entries` 0, `fetched` null), `Active` when the stored copy is at most 7 days old, `TooOld`
when it is older (it is left out of the route set; `entries` 0, `fetched` its fetch time). Only
`Active` lists feed the route set. A pure function decides this from the stored lists and `now`,
always returning the three lists in `AddressList` order:

```kotlin
fun addressLists(stored: Map<AddressList, StoredList>, now: Long): List<ListStatus>
```

## The screen

Spec 01's journey is unchanged except in the **Protected** row, whose counter block now reads, in
this order: the names counter (spec 01), `counter_addresses`, the name list line (spec 01), and one
address-list part: first `address_blocking_off` if no list is `Active`, then one line per list, in
`AddressList` order, so the owner sees exactly which coverage is missing:

| `ListState` | Line |
|---|---|
| NotYet | `address_list_not_yet` with `source` |
| Active | `address_list_active` with `source`, `entries` (locale grouping) and the date of `fetched` (`DateFormat.getDateInstance(DateFormat.MEDIUM)`) |
| TooOld | `address_list_too_old` with `source` and the date of `fetched` |

`HomeUi` gains `val addressLists: List<ListStatus>` (empty in every row but Protected) and
`val addressBlockingOff: Boolean` (true in Protected when no list is `Active`), so the mapping is
unit-tested; `HomeScreen` formats the numbers and dates.

### Accessibility — required

- The whole counter block (both counters and both list lines) stays one TalkBack unit
  (`mergeDescendants`) and is **not** a live region.
- Words carry the meaning; no new color, icon or tone. If a new color pair is ever added it gets a
  line in `ContrastTest`.
- Text in `sp` via `MaterialTheme.typography`; at 200% font size nothing clips.
- Notifications use the same words as the screen.

### Strings (Danish in `values/`, English in `values-en/`)

| Key | Danish | English |
|---|---|---|
| counter_addresses | Blokerede adresser siden start: %1$d | Blocked addresses since start: %1$d |
| address_blocking_off | Blokering af adresser er ikke aktiv. Appen henter listerne, når telefonen er på nettet. | Address blocking isn't active. The app downloads the lists when the phone is online. |
| address_list_active | %1$s: %2$s på listen, hentet %3$s | %1$s: %2$s on the list, downloaded %3$s |
| address_list_too_old | %1$s: sat på pause, sidst hentet %2$s. Gamle lister kan blokere adresser, som nu bruges af andre. | %1$s: paused, last downloaded %2$s. Old lists can block addresses that others use now. |
| address_list_not_yet | %1$s: ikke hentet endnu | %1$s: not downloaded yet |
| channel_blocked_addresses | Blokerede adresser | Blocked addresses |

Two spec 01 strings change, because traffic to listed addresses now enters the tunnel too (the old
words said only DNS does). Replace them in both languages:

| Key | Danish | English |
|---|---|---|
| closing_line | Kun DNS-opslag og forbindelser til kendte farlige adresser går gennem appen. Ingen server, ingen konto. Apps med deres egen sikre DNS går uden om navnefilteret. | Only DNS lookups and connections to known-bad addresses pass through the app. No server, no account. Apps with their own secure DNS bypass the name filter. |
| card_lockdown_body | "Bloker forbindelser uden VPN" er slået til. Appen sender kun DNS-opslag og forbindelser til kendte farlige adresser gennem sin tunnel, så med den indstilling kan telefonen slet ikke komme på nettet. Slå indstillingen fra under VPN-indstillinger, og start igen. | "Block connections without VPN" is on. The app sends only DNS lookups and connections to known-bad addresses through its tunnel, so with that setting the phone can't reach the internet at all. Turn the setting off in VPN settings, then start again. |
| notif_address_title | Farlig adresse blokeret | Dangerous address blocked |
| notif_address_text_app | %1$s prøvede at forbinde til %2$s, som står på listen %3$s. Forbindelsen blev afvist. | %1$s tried to connect to %2$s, which is on the %3$s list. The connection was refused. |
| app_android_system | Android-systemet | Android system |
| notif_address_text | En app prøvede at forbinde til %1$s, som står på listen %2$s. Forbindelsen blev afvist. | An app tried to connect to %1$s, which is on the %2$s list. The connection was refused. |

`%2$s` in `notif_address_text` is the list's screen name from the table under **The lists**
("Spamhaus DROP", "Spamhaus DROPv6", "abuse.ch Feodo Tracker"); these names are not translated.

## Files

Android-free logic in plain Kotlin, unit-tested on the JVM. Every code file under 200 lines.

- `net/Checksum.kt` — the one's-complement `sum`/`checksum` helpers, moved out of `Ipv4Udp.kt`
  (now `internal`) so every builder shares them.
- `net/IpPacket.kt` — `data class IpPacket(version: Int, protocol: Int, srcIp: ByteArray,
  dstIp: ByteArray, headerLength: Int, totalLength: Int)`;
  `fun parseIpPacket(buf: ByteArray, length: Int): IpPacket?` (IPv4 with IHL > 5; IPv6 with a
  fixed 40-byte header whose next header is TCP or UDP directly; null for extension headers,
  fragments, and lengths past `length`).
- `net/TcpReset.kt` — `fun tcpResetFor(buf: ByteArray, p: IpPacket): ByteArray?` (null unless SYN
  set and ACK clear).
- `net/IcmpUnreachable.kt` — `fun icmpUnreachableFor(buf: ByteArray, p: IpPacket): ByteArray`
  (IPv4 type 3 code 13; IPv6 type 1 code 1, at most 1,280 bytes).
- `iplist/Cidr.kt` — `data class Cidr(address: ByteArray, prefix: Int)`;
  `fun parseCidr(text: String): Cidr?`; `fun Cidr.contains(ip: ByteArray): Boolean`;
  `fun Cidr.overlaps(other: Cidr): Boolean`; `toString()` gives the normalised form.
- `iplist/Reserved.kt` — the reserved ranges above and `fun isReserved(c: Cidr): Boolean`.
- `iplist/ListParsers.kt` — `fun parseSpamhausJson(body: String): List<Cidr>?` (null without the
  metadata line), `fun parseFeodo(body: String): List<Cidr>?` (null on any bad line),
  `fun acceptList(id: String, cidrs: List<Cidr>): List<Cidr>?` (reserved filter, merge,
  plausibility; null when rejected).
- `iplist/RouteSet.kt` — `class RouteSet(lists: Map<String, List<Cidr>>)` with `val routes:
  List<Cidr>` (merged across lists), `val size: Int` and `fun listFor(ip: ByteArray): String?` (the
  id of a list that holds the address, for the notification).
- `iplist/AddressLists.kt` — `AddressList`, `ListState`, `StoredList`, `ListStatus` and
  `addressLists(...)`.
- `iplist/RecentFlows.kt` — `class RecentFlows(capacity: Int)` with `fun firstTime(key: Any):
  Boolean` (true the first time a key is seen; forgets the oldest past `capacity`).
- `vpn/AddressListStore.kt` (Android) — reads and writes `filesDir/iplists/`, downloads with
  `HttpURLConnection`, uses the parsers above.
- `vpn/GuardVpnService.kt`, `vpn/PacketLoop.kt` — the tunnel swap, the hourly check and the refusal
  path; split a file if it would pass 200 lines (for example a `vpn/Refusals.kt`).
- `notify/Notifications.kt` — the third channel and the address notification.
- `ui/HomeUi.kt`, `ui/HomeScreen.kt` — the counter block.

## Acceptance tests (JVM, offline, under `app/src/test/java/dk/cocode/guard/`)

No real sockets, no downloads, no name lookups, no Android framework. Fixtures are small
hand-written strings in the tests, never copies of the real lists.

- `iplist/CidrTest`
  - `parsesIpv4Range` (`192.0.2.0/24`), `parsesIpv6Range` (`2001:db8::/32`), `rejectsHostBits`
    (`192.0.2.7/24`), `rejectsBadPrefix` (`/33`, `/129`, `/-1`, no slash), `rejectsGarbage`,
    `containsMatchesInsideOnly` (edges of the range in and out), `overlapsBothWays`,
    `normalisedText` (`2001:0db8:0000::/32` → `2001:db8::/32`).
- `iplist/ReservedTest`
  - `privateRangesAreReserved` (each IPv4 and IPv6 range in the list, and a range overlapping one,
    e.g. `10.0.0.0/7`), `defaultRoutesAreReserved` (`0.0.0.0/0`, `::/0`), `tunnelAddressesAreReserved`,
    `nat64IsReserved` (`64:ff9b::/96`), `publicRangeIsNot` (`203.0.113.0/24`).
- `iplist/ListParsersTest`
  - `spamhausLinesParse` (two range lines + metadata → two CIDRs), `spamhausWithoutMetadataIsRejected`,
    `spamhausIpv6Parses`, `feodoSkipsCommentsAndBlanks`, `feodoBadLineRejectsAll`,
    `reservedEntriesAreDropped`, `containedRangesAreMerged` (a `/32` inside a listed `/24` adds no
    route), `tooFewDropRangesIsRejected` (499 `drop_v4` ranges → null; 500 → accepted),
    `emptyFeodoIsAccepted`.
- `iplist/RouteSetTest`
  - `findsTheList` (an address in a Feodo `/32` that is not inside a DROP range → `feodo`),
    `addressOutsideIsNull`, `ipv6Lookup`, `routesMergeAcrossLists` (a Feodo `/32` inside a DROP
    range adds no route), `sizeCountsMergedRoutes`.
- `iplist/AddressListsTest`
  - `nothingStoredIsAllNotYet` (three lists, in order), `freshListIsActive` (entries and fetch
    time reported), `oldListIsTooOld` (fetch time reported, entries 0), `listsAreIndependent`
    (DROP IPv4 fresh, DROP IPv6 old, Feodo missing → Active, TooOld, NotYet), `sevenDaysIsTheEdge`
    (exactly 7 days is still Active, 7 days + 1 ms is TooOld).
- `iplist/RecentFlowsTest`
  - `firstTimeOnly`, `seenAgainIsKeptLongest` (forgotten by recency, not insertion order),
    `forgetsOldestPastCapacity`.
- `vpn/RefusalsTest` (in-memory tunnel): among others `noticeNamesTheAppAskedOncePerAddress` and
  `stoppedLoopLeavesTheFlowToTheNextLoop` (a loop retired by a tunnel swap neither counts nor
  remembers a flow, so the new loop counts and announces it).
- `vpn/TunnelSwapTest`: `newTunnelComesUpBeforeOldCloses`, `failedEstablishLeavesOldAlone`,
  `failedLoopStartClosesNewTunnel`.
- `vpn/AddressListDownloadTest` (a fake `HttpURLConnection`, no sockets): `okBodyIsStored`,
  `redirectIsNotFollowedAndStoresNothing`, `errorStatusStoresNothing`, `oversizeBodyIsRejected`,
  `cancelDropsDownloadInFlight`.
- `net/IpPacketTest`
  - `parsesIpv4WithOptions`, `parsesIpv6Tcp`, `rejectsIpv6ExtensionHeader`, `rejectsTruncated`,
    `rejectsIpv4Fragment`.
- `net/TcpResetTest`
  - `resetForIpv4Syn` (ports and addresses swapped, flags RST+ACK, ack = seq + 1, header checksum
    sums to `0xFFFF`, TCP checksum with pseudo-header sums to `0xFFFF`), `resetForIpv6Syn`,
    `noResetForSynAck`, `noResetWithoutSyn`.
- `net/IcmpUnreachableTest`
  - `ipv4Code13` (type 3, code 13, quotes the original header + 8 bytes, checksums verify),
    `ipv6Code1` (type 1, code 1, pseudo-header checksum verifies), `ipv6ReplyFitsIn1280` (a
    1,500-byte original gives a 1,280-byte reply).
- `ui/HomeUiTest` (extend)
  - `protectedShowsEveryList` (the three `ListStatus` values, in order), `offLineWhenNoListActive`
    (true with none Active, false with one), `addressListsOnlyWhenProtected`.
- `net/Ipv4UdpTest` and every spec 01 test still pass unchanged after the checksum move.

The existing `ContrastTest` must still pass. `./gradlew buildSmoke --no-daemon` is the gate.

## Checked by hand on the owner's phone (not part of the gate)

With TalkBack on: start protection on mobile data and hear "not active" and the three "not
downloaded yet" lines change to three list lines after the first download; in a browser, open `http://<an address from the Feodo list>/`
and see it fail at once, then get one notification naming the address and list; open the same
address again and see the counter rise without a second notification; open a normal site by its
IP address (for example the one `example.com` resolves to) and see it load; confirm ads are still
blocked by name; with about 1,730 routes, starting still reaches "Beskyttet" within 2 seconds;
set the phone's date 8 days ahead and see "not active" and three "paused" lines,
then set it back; font size at maximum with nothing clipped.

## Out of scope

Downloading or updating the AdGuard name list, attributing a blocked connection to the app that
made it, a history screen of blocked addresses, addresses the owner adds or allows, other IP lists
(FireHOL, DShield, Emerging Threats), inbound filtering, IPv6 extension-header parsing, TCP DNS,
Google Play.
