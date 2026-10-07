# 03 — Which app was blocked: recent blocks by app

## Goal

When a name or an address is blocked, the owner can see **which app** asked for it, so an app that
keeps reaching ad, tracking or malware hosts can be found and removed. The home screen gets a
"Seneste blokeringer" section: the apps with recent blocks, how many, and what they tried to reach.
Everything stays in memory on the phone and is cleared at every start and stop. Closes issues #7
and #8.

Builds on specs 01 and 02 as merged. Design authority:
`docs/superpowers/specs/2026-10-05-guard-android-setup-design.md`. Read `CLAUDE.md` first; its rules
apply, including accessibility and the 200-line limit.

## What is recorded

One **block event** per blocked lookup and per newly refused connection:

```kotlin
enum class BlockKind { Name, Address }
data class BlockEvent(val kind: BlockKind, val target: String, val app: String?)
```

- `target` is the blocked name as `DnsMessage` parsed it (case kept, no trailing dot; matching against
  the block list ignores case), or
  the refused address as `ipText` writes it.
- `app` is the app's name from `appOwning(...)` (`vpn/AppNames.kt`, unchanged), or null when Android
  cannot tell. "Android-systemet" comes from `appOwning` itself for system UIDs; nothing else is
  guessed.
- **Blocked names (issue #7):** when `PacketLoop.answer` finds `blockList.isBlocked(q.name)`, it asks
  for the owner of the query's socket **before** writing the reply (the socket may close right after):
  protocol UDP (`OsConstants.IPPROTO_UDP`), local = the packet's source address and port, remote =
  `10.111.222.2:53`. `PacketLoop` gets this as a constructor parameter
  `ownerOf: (UdpPacket) -> String? = { null }`, so its JVM tests stay free of Android; the service
  passes `{ p -> appOwning(IPPROTO_UDP, p.srcIp, p.srcPort, p.dstIp, p.dstPort) }`. Lookups that are
  forwarded record nothing and ask nothing.
- **Refused addresses (issue #8):** each flow that `Refusals.firstTime` reports as new is recorded
  once, with `appOwning` asked for that flow (TCP or UDP by the packet's protocol). Retransmits and
  later packets of the same flow are neither recorded nor asked about. The per-address notification
  from spec 02 is unchanged; when it is posted for the same flow it reuses the name already looked
  up, so Android is asked once per flow.
- Whether Android names the asking app for a lookup, or reports its own resolver (shown as
  "Android-systemet"), is what the phone says; the hand check below confirms which.

## Keeping and grouping (Android-free, `recent/RecentBlocks.kt`)

```kotlin
data class AppBlocks(val app: String?, val count: Int, val latest: List<String>)

class RecentBlocks(private val capacity: Int = 200) {
    fun add(event: BlockEvent)          // drops the oldest event once capacity is reached
    fun groups(max: Int = 5): List<AppBlocks>
    fun clear()
}
```

- `groups` groups the kept events by `app` (null is its own group), orders groups by their most recent
  event (newest first), and returns at most `max`. `count` is that group's number of kept events
  (both kinds together). `latest` is the group's distinct targets, newest first, at most 3.
- It is thread-safe (`@Synchronized`): lookups are answered on several worker threads.
- `ProtectionRepository` owns one `RecentBlocks` and publishes `groups()` in
  `ProtectionState.recentBlocks: List<AppBlocks>` (default empty) after every `add` and `clear`.
- It is cleared at every start (where the counters are reset) and at every stop, by any path. Nothing
  is written to disk, logged or sent anywhere.

## The screen

A section on `HomeScreen`, after the counter block and before the primary button:

| State | Section |
|---|---|
| Protected (either Protected row of spec 01), `recentBlocks` not empty | Shown |
| Protected, `recentBlocks` empty | Shown with `recent_none` only |
| Any other state | Not shown |

- Title `recent_title`, a `heading()`.
- One row per group, at most 5, newest first: `recent_group` with the app (or `recent_unknown_app`
  for null), the count, and the latest targets joined with ", ". Each row is one TalkBack stop
  (`semantics(mergeDescendants = true)`), plain text, no button, no icon that carries meaning.
- Under the rows, `recent_note` in the quiet text color.
- The section is **not** a live region: it changes with every block and would talk over the screen.
- Text in `sp`, no fixed heights; at 200% font size rows wrap and nothing clips.
- `HomeUi` gets `showRecent: Boolean`; `homeUi` sets it for the two Protected rows only. The rows
  themselves come from `state.recentBlocks`.

### Strings (Danish in `values/`, English in `values-en/`)

| Key | Danish | English |
|---|---|---|
| recent_title | Seneste blokeringer | Recent blocks |
| recent_group | %1$s: %2$d blokeret, senest %3$s | %1$s: %2$d blocked, latest %3$s |
| recent_unknown_app | En ukendt app | An unknown app |
| recent_none | Intet er blokeret endnu siden start. | Nothing has been blocked since start. |
| recent_note | Listen findes kun på telefonen og slettes, når beskyttelsen starter eller stopper. | This list stays on the phone and is cleared when protection starts or stops. |

## Permissions

None added. The manifest's existing `<queries>` already lets `appOwning` name launcher apps.

## Files

- `recent/RecentBlocks.kt` (new, Android-free): `BlockKind`, `BlockEvent`, `AppBlocks`, `RecentBlocks`.
- `vpn/PacketLoop.kt`: the `ownerOf` parameter and the record on a blocked name.
- `vpn/Refusals.kt`: record each new flow; reuse its looked-up name for the notification.
- `vpn/ProtectionRepository.kt`: owns `RecentBlocks`, publishes `recentBlocks`, clears it.
- `vpn/GuardVpnService.kt` is at 194 lines: move code out (for example the start and stop
  bookkeeping into a small helper file) so it stays under 200 after this change.
- `ui/HomeUi.kt`, `ui/HomeScreen.kt` (or a new `ui/RecentBlocksSection.kt` if `HomeScreen.kt` would
  pass 200 lines), `res/values/strings.xml`, `res/values-en/strings.xml`.

## Acceptance tests (JVM, offline, under `app/src/test/java/dk/cocode/guard/`)

- `recent/RecentBlocksTest`
  - `groupsByAppNewestFirst`: events for A, B, A → groups [A (2), B (1)].
  - `unknownAppIsItsOwnGroup`: null app events group together and never merge with a named app.
  - `latestIsDistinctNewestFirstAtMostThree`: 5 targets for one app, one repeated → 3 distinct,
    newest first.
  - `atMostMaxGroups`: 7 apps → 5 groups.
  - `capacityDropsOldest`: capacity 3, four events → the first is gone from counts and targets.
  - `clearEmptiesEverything`.
- `vpn/PacketLoopTest` (keep the file under 200 lines; put new cases in a new
  `PacketLoopOwnerTest.kt` that reuses `PacketLoopFakes.kt`)
  - `blockedNameRecordsTheOwner`: `ownerOf` returns "Chrome" → `recentBlocks` has a Chrome group
    with the blocked name.
  - `blockedNameWithUnknownOwnerIsRecordedAsNull`.
  - `forwardedNameAsksNothingAndRecordsNothing`: `ownerOf` is never called for a forwarded name.
- `vpn/RefusalsTest` (new or existing): `newFlowIsRecordedOnce` (a retransmit records nothing more);
  `notificationReusesTheRecordedName` (the name lookup runs once for a flow that is also notified).
- `ui/HomeUiTest`: `recentShownOnlyWhenProtected` for every journey row.
- `vpn/ProtectionRepository` clearing: `startAndStopClearRecentBlocks`, through whatever function the
  service calls at start and at stop.

`./gradlew buildSmoke --no-daemon` is the gate; `ContrastTest` must still pass.

## Checked by hand on the owner's phone (not part of the gate)

With TalkBack: block an ad host in Chrome and hear one row "Chrome: 1 blokeret, senest …"; confirm
whether lookups are named for the app or as "Android-systemet" (record which in issue #7); a Feodo or
DROP address refused from an app shows that app's row; stop and start: the section is empty; 200%
font: rows wrap without clipping.

## Out of scope

History kept across stops or on disk, a notification per blocked name, tapping a row for details,
uninstalling or blocking an app from the screen, exporting the list, changes to the website or store
texts (done separately).
