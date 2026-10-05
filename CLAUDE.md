# CLAUDE.md — Cocode Guard for Android

An Android app that blocks dangerous sites and ads for every app on the phone by filtering DNS on
the phone. Free software (GPL-3.0), free of charge. Sister project: `../network-defence` (the box).

**The design is the authority:** `docs/superpowers/specs/2026-10-05-guard-android-setup-design.md`.
Features are specified one per file in `docs/lean/NN-name.md`. If code and spec disagree, say so
rather than guessing.

## Rules

- **Only DNS enters the tunnel.** `VpnService` is a local tunnel ending inside the app; its only
  route is the fake DNS address. Never route other traffic, never add a remote server.
- **No Cocode server, no account, no analytics, no remote code.** Block lists ship in `assets/`.
- **Stop means stop, said plainly.** Any path that ends protection posts a notification saying the
  phone now uses normal DNS without blocking. Never fail silently.
- **Few permissions.** Only those a spec names (see `profile-android.md`, `house`).
- **No Google or Play libraries.** F-Droid builds must stay reproducible: no JDK toolchain pin, no
  foojay resolver, version only in `gradle.properties`, R8 on in release.
- **Plain words** in every message, Danish (`values/`, the default) and English (`values-en/`).

## Accessibility — blind-friendly is a requirement

- Every control has a spoken label saying what it does (`contentDescription` / `semantics`), not
  what it looks like.
- The screen title is a `heading()`; a status that changes is a polite `liveRegion`.
- Grouped content (a warning card and its action) reads as one unit (`mergeDescendants`).
- Meaning never rests on color alone: every state has words, plus an icon where useful.
- Touch targets ≥ 48dp; text in `sp`; screens scroll so 200% font size never clips.
- Every color comes from `GuardColors` and every text pair is in `ContrastTest` (AA: 4.5:1 text,
  3:1 large text and icons).
- Notifications use the same words as the screen.

## Architecture

Kotlin, Jetpack Compose (Material 3), one `app` module, package root `dk.cocode.guard`:

- `MainActivity.kt`: sets `GuardTheme` and the home screen.
- `ui/`: screens. `ui/theme/`: `GuardColors` (box palette, contrast math) and `GuardTheme`.
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
