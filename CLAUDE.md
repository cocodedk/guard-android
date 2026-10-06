# CLAUDE.md — Cocode Guard for Android

An Android app that blocks dangerous sites and ads for every app on the phone by filtering DNS on
the phone. Free software (GPL-3.0), free of charge. Sister project: `../network-defence` (the box).

**The design is the authority:** `docs/superpowers/specs/2026-10-05-guard-android-setup-design.md`.
Features are specified one per file in `docs/lean/NN-name.md`. If code and spec disagree, say so
rather than guessing.

## Rules

- **Only DNS and traffic to listed bad addresses enter the tunnel.** `VpnService` is a local tunnel
  ending inside the app; its routes are the fake DNS address and the ranges on the bad-address
  lists. Traffic to a listed address is refused inside the app; nothing is ever relayed to the
  internet, and there is no remote server. All other traffic stays off the tunnel.
- **No Cocode server, no account, no analytics, no remote code.** The name list ships in
  `assets/`. IP lists are downloaded on the phone straight from their publishers: data, never code.
- **Stop means stop, said plainly.** Any path that ends protection without the owner asking for it
  posts a notification saying DNS lookups are no longer filtered. When the owner stops it, the
  screen says so. Never fail silently, and never say "protected" while the filter is bypassed.
- **Few permissions.** Only those the spec being built names in its Permissions section.
- **No Google or Play libraries.** F-Droid builds must stay reproducible: no JDK toolchain pin, no
  foojay resolver, version only in `gradle.properties`, R8 on in release.
- **Plain words** in every message, Danish (`values/`, the default) and English (`values-en/`).

## Accessibility — blind-friendly is a requirement

- Every control has a spoken label saying what it does (`contentDescription` / `semantics`), not
  what it looks like.
- The screen title is a `heading()`; a status that changes is a polite `liveRegion`.
- A card's title and body read as one unit (`mergeDescendants`); its button is its own stop, so
  it can be found and activated on its own.
- Meaning never rests on color alone: every state has words, plus an icon where useful.
- Touch targets ≥ 48dp; text in `sp`; screens scroll so 200% font size never clips.
- Every color comes from `GuardColors` and every text pair is in `ContrastTest` (AA: 4.5:1 text,
  3:1 large text and icons).
- Notifications use the same words as the screen.

## Architecture

Kotlin, Jetpack Compose (Material 3), one `app` module, package root `dk.cocode.guard`:

- `MainActivity.kt`: sets `GuardTheme` and the home screen.
- `ui/`: screens. `ui/theme/`: `GuardColors` (box palette) and `GuardTheme`.
  The contrast math lives with `ContrastTest` in `src/test`.
- Packages that spec 01 adds (`vpn/`, `dns/`, `blocklist/`) keep Android-free logic (packet and DNS
  parsing, rule matching) in plain Kotlin, so it is unit-tested on the JVM.

## Commands

```sh
./gradlew buildSmoke --no-daemon   # the gate: debug build + unit tests + lint
./gradlew testDebugUnitTest        # unit tests
bash scripts/install-hooks.sh      # pre-commit lint, commit-msg, pre-push buildSmoke
python3 -m http.server -d website  # preview the site
```

Gate profile: [profile-android.md](profile-android.md).

## Building with the lean loop

- Specs for graph-loop's lean loop live in `docs/lean/NN-name.md`, one feature each, built in
  order; `docs/lean/lessons.md` holds hints people add.
- The loop builds autonomously in a worktree and opens a pull request; it does not stop to ask a
  person at each step. Questions about a spec come back by mail before the build starts.
- Tests stay offline: JVM unit tests only, no real sockets, no name lookups, no device or emulator.
- Nothing is installed on a phone from a build. Trying a build on a device is the owner's step.
