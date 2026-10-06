# Cocode Guard for Android — setup design

Date: 2026-10-05. Owner: Babak (bb@cocode.dk). Source idea: [IDEA.md](../../../IDEA.md).

## Goal

Give the phone the protection the Cocode Guard box gives a home network: dangerous sites and ads
blocked for every app, on any network. This design covers two things:

1. **The project's infrastructure**, built by hand in one session with the `android-setup` skill
   and merged as one pull request.
2. **Handing feature work to graph-loop's lean loop**, starting with spec 01 (a DNS-only local
   tunnel that blocks one list).

Success for this session: the infrastructure PR is open and passes `./gradlew buildSmoke`; after
the owner merges it, the suite passes on `main` the way the loop runs it, graph-loop has a proven
contact, and spec 01 is written and handed to the loop.

## Decisions (settled with the owner)

| Topic | Decision |
|---|---|
| Repository | Public `cocodedk/guard-android`, GPL-3.0 |
| Price | Free software, free of charge for everyone; no paid tier, no box required (owner, 2026-10-05) |
| App id | `dk.cocode.guard` |
| Stack | Kotlin + Jetpack Compose, one `app` module, minSdk 29 (raised from 26 by spec 01, for `DnsResolver` and the Always-on/lockdown checks), compile 37, target 36 |
| Distribution | F-Droid + GitHub releases; no Google libraries; Play later, if ever |
| Website | Own GitHub Pages site in this repo at `android.guard.cocode.dk` |
| Languages | Danish (default) + English, for both the app and the site |
| Visual style | The box site's tokens (`../network-defence/website/styles.css`), not the naval theme |
| Upstream DNS | The current network's DNS in spec 01; an owner-chosen resolver (network DNS by default) in spec 02 |
| Session scope | Infrastructure + graph-loop setup + spec 01 |
| Accessibility | Blind-friendly is a hard requirement: TalkBack-first app, WCAG 2.2 AA site |
| Driving | The owner handed every decision, including app UI/UX, to Claude (2026-10-05) |

## Accessibility (applies to every part)

Blind and low-vision people must be able to use the app and the site without sighted help.

- **App:** every control has a spoken label that says what it does ("Start beskyttelse"), not
  what it looks like; the status is announced by TalkBack when it changes (polite live region);
  the screen title is a heading; a card's title and body read as one unit and its button is its
  own stop; meaning never
  rests on color alone (each state has words and an icon); touch targets are at least 48dp; text
  scales with the system font size up to 200% without clipping; reading order follows the visual
  order; notifications carry the same plain words as the screen.
- **Colors:** every text/background pair in the app theme meets WCAG AA (4.5:1 for body text,
  3:1 for large text and icons), enforced by a unit test in the gate.
- **Site:** WCAG 2.2 AA — semantic landmarks, one `h1`, skip link, visible focus, alt text,
  `lang` per page, no information by color alone, contrast checked.

## Part 1 — Infrastructure (android-setup)

- **Scaffold:** written by hand from the Gradle/Compose skeleton of `../weather-android` and
  `../Battleship` (no Android Studio). Package root `dk.cocode.guard`.
- **Gate:** `./gradlew buildSmoke` (lint, unit tests, debug assemble) is the one canonical check —
  CI's check and the loop's `suite_command`.
- **F-Droid from day one:** R8 and resource shrinking on in release, no JDK toolchain pin, no
  foojay resolver, version in `gradle.properties`, `dependenciesInfo` off, `keepDebugSymbols`.
- **Release:** SHA-pinned `ci.yml` and `release-apk.yml`; the release APK is attached under the
  stable name `GuardAndroid.apk`; signing is conditional on secrets, set once with
  `scripts/setup-signing.sh`. The release workflow never triggers on the tag it pushes.
- **Hooks and repo:** pre-commit from android-setup; commit-msg, pre-push, branch protection and
  git hygiene from `github-setup`; `scripts/setup-repo.sh`.
- **Docs:** `README.md`, `CONTRIBUTING.md`, `LICENSE` (GPL-3.0), `llms.txt`, `CLAUDE.md`.
- **Website:** `website/` deployed by `deploy-pages.yml`, `CNAME` = `android.guard.cocode.dk`.
  Danish at `/`, English at `/en/`, hreflang, `robots.txt`, `sitemap.xml`, favicon. Privacy pages
  at `/privacy/` and `/en/privacy/`, stating that the app collects nothing, sends lookups only to
  the chosen DNS resolver and has no server. Styled with the box site's tokens (night `#0f1e36`,
  Schibsted Grotesk, ok/notice/urgent colors). The link from guard.cocode.dk is a separate change
  in `network-defence`, not part of this work.
- **App theme:** Compose color scheme and type built from the same tokens.

Release gate (after merge): the release APK downloads with HTTP 200 from
`releases/latest/download/GuardAndroid.apk`, and `https://android.guard.cocode.dk/privacy/`
answers 200.

## Part 2 — Spec 01: DNS filter over a local tunnel

The "VPN" is Android's `VpnService` API used as a local tunnel that ends inside the app. There is
no VPN server and no account; in spec 01 only DNS lookups enter it. Spec 02
(`docs/lean/02-ip-block.md`) adds routes for known-bad address ranges, refused inside the app, so
the rule becomes: only DNS and traffic to listed bad addresses enter the tunnel, and nothing is
ever relayed.

- **Tunnel shape:** a private address and one fake DNS server (`10.111.222.2`); the only route is
  that /32, so only DNS enters the tunnel and all other traffic bypasses it. The app excludes
  itself with `addDisallowedApplication`, so its upstream sockets go out directly.
- **Lookup path:** read an IPv4/UDP packet from the tunnel → parse the DNS question → a blocked
  name gets `0.0.0.0` for A and `::` for AAAA (other types: an empty NOERROR answer) → any other
  name is forwarded to the upstream and its reply is wrapped back into the tunnel.
- **Upstream:** the current Wi-Fi or mobile network's DNS, reached through Android's own
  `DnsResolver.rawQuery` (it encrypts the lookup when Private DNS is on, so the filter never
  downgrades it to plaintext).
- **Block list:** a snapshot of the AdGuard DNS filter committed in `app/src/main/assets/`.
  It is never fetched at build time or run time. (Spec 02's IP lists are the exception: the phone
  downloads them from their publishers, since they go stale in days and Spamhaus may not be
  redistributed.) Only `||domain^` block rules and `@@||domain^`
  exceptions are used; a rule matches the domain and all its subdomains; an exception wins over a
  block. Other rule types are skipped.
- **UI (one screen):** status in plain words ("Beskyttet" / "Ikke beskyttet" and English
  equivalents), a Start/Stop button, the number of blocked lookups since start, and a warning card
  when Android's Private DNS is set to a specific server (strict mode bypasses the filter), read
  from `LinkProperties.privateDnsServerName`. Automatic mode needs no warning (spec 01 explains).
- **Stop means stop:** a foreground notification while protecting. If the tunnel is revoked
  (`onRevoke`: another VPN app took over, or the VPN was turned off in settings), cannot run
  (Android's "Block connections without VPN"), or the service fails, a notification says that
  protection stopped and DNS lookups are no longer filtered. Spec 01 holds the full journey.
- **Permissions:** `BIND_VPN_SERVICE` on the service, `INTERNET` and `ACCESS_NETWORK_STATE`
  (install-time, for the upstream socket and the network callback), `POST_NOTIFICATIONS`,
  `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` (service
  `foregroundServiceType="systemExempted"`). Nothing else.
- **Tests (JVM, offline, part of the gate):** DNS question parsing and answer building; IPv4/UDP
  packet parse and build including checksums; rule parsing and subdomain/exception matching. No
  real sockets or name lookups in tests. Device behaviour is checked by hand on the owner's phone.
- **Out of scope:** a boot receiver of our own (Android's Always-on VPN setting starts the
  service and is honoured), list updates, allowlist UI, per-site alerts, DoH
  blocking, a history of blocked lookups, the owner-chosen upstream (spec 02).

## Part 3 — graph-loop

- **Workspace:** `scratchpad/lean/` in the repo, git-ignored.
- **Contact:** `bb@cocode.dk`, proven with `graph-goal.py contact` through graph-loop's existing
  `smtp.env`; the owner confirms the test mail arrived.
- **Profile:** `profile-android.md`, copied from graph-loop's `profiles/android-gradle.md`, linked
  from `CLAUDE.md`, with:
  - `suite_command`: `./gradlew buildSmoke --no-daemon`
  - `build_command`: `./gradlew assembleDebug --no-daemon`
  - `artifact`: `app/build/outputs/apk/debug/app-debug.apk`
  - `account`: `personal` (the owner exports `GRAPH_ACCOUNTS=personal=$HOME/.claude-personal`
    where the loop is started)
  - a `house` note that spec 01 may add exactly the permissions listed in Part 2.
- **Proof on main:** the suite passes from a clean clone with a scrubbed environment and an empty
  `HOME`, with `ANDROID_HOME`, `GRADLE_USER_HOME` and `ANDROID_USER_HOME` set to usable paths.
- **Specs:** `docs/lean/01-dns-filter-vpn.md` (Part 2 in full, with acceptance tests);
  `docs/lean/lessons.md` starts empty.
- **CLAUDE.md** gets a "Building with the lean loop" section: specs live in `docs/lean/`, tests
  stay offline, nothing is installed on a device from a build.
- **Run:** `lean.py --workspace scratchpad/lean --repo . --spec docs/lean/01-dns-filter-vpn.md`
  in the background, after the infrastructure PR is merged and no other branch is open on origin.

## Risks

- **Private DNS and apps with their own DoH** bypass the filter (IDEA.md questions 2–3). Spec 01
  warns about Private DNS; DoH is a later spec.
- **One VPN at a time:** a work VPN cannot run alongside the app; Android's own dialog covers it.
- **Phone makers killing background apps:** the foreground service and the stop notification
  make a stop visible; battery-optimisation guidance is a later spec.
