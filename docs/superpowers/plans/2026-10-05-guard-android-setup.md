# Guard Android Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A public `cocodedk/guard-android` repo whose `main` passes `./gradlew buildSmoke`, with CI,
release, a blind-friendly da/en site with privacy pages, and spec 01 handed to graph-loop.

**Architecture:** Copy the proven skeleton of `../weather-android` (AGP 9.3.1, Gradle 9.6.1,
Compose, no Google libraries) and adapt names. Hand-build only infrastructure and an accessible
app shell; the DNS filter itself is built by graph-loop from `docs/lean/01-dns-filter-vpn.md`.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Gradle Kotlin DSL, GitHub Actions, static HTML/CSS.

**Spec:** `docs/superpowers/specs/2026-10-05-guard-android-setup-design.md`

## Global Constraints

- App id and namespace `dk.cocode.guard`; minSdk 26; compileSdk 37 (weather's floor); targetSdk 36.
- No Google/Play libraries; R8 + resource shrinking on in release; `dependenciesInfo` off;
  `keepDebugSymbols += "**/*.so"`; no JDK toolchain pin; no foojay resolver.
- Version only in `gradle.properties` (`VERSION_NAME=0.1.0`, `VERSION_CODE=1001`).
- Every GitHub Action SHA-pinned (copy the SHAs from weather-android's workflows).
- Release asset name `GuardAndroid.apk`; release workflow is `workflow_dispatch` only.
- Danish is the default language (`values/`), English in `values-en/`. Site: da at `/`, en at `/en/`.
- Colors from `../network-defence/website/styles.css`; every text pair ≥ 4.5:1, icons ≥ 3:1.
- Code files under 200 lines. Conventional Commits. Never `--no-verify`.
- Never commit a keystore, password or `.env`.

## Review Focus

1. **TalkBack on the shell screen:** status text must be read as a heading + live region, not as
   loose text → covered by semantics in Task 1 and checked by hand with TalkBack on the owner's
   phone after the loop's spec 01.
2. **Large font (200%):** the shell must not clip → layout uses `verticalScroll`, no fixed heights.
3. **Low contrast after a palette tweak:** → `ContrastTest` in the gate (Task 1).
4. **Clean-environment gate:** suite passes with empty `HOME` → Task 7 proof.
5. **Site keyboard/screen-reader use:** skip link, focus outline, landmarks → Task 4 checklist.

---

### Task 1: Gradle scaffold, theme and accessible shell

**Files:**
- Create (copy from weather-android, rename `Weather`→`Guard`, `dk.cocode.weather`→`dk.cocode.guard`):
  `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat`,
  `gradle/wrapper/*`, `app/build.gradle.kts` (drop datastore + icons-extended until a spec needs
  them; `targetSdk = 36`), `app/proguard-rules.pro`, `.gitignore`.
- Create: `app/src/main/AndroidManifest.xml` (no permissions), `app/src/main/res/values/strings.xml`
  (da), `app/src/main/res/values-en/strings.xml`, `app/src/main/res/values/themes.xml`,
  launcher icon (adaptive, shield glyph, `mipmap-anydpi-v26`).
- Create: `app/src/main/java/dk/cocode/guard/MainActivity.kt`,
  `.../ui/theme/GuardColors.kt` (tokens + `contrastRatio`), `.../ui/theme/Theme.kt`,
  `.../ui/HomeScreen.kt`.
- Test: `app/src/test/java/dk/cocode/guard/ui/theme/ContrastTest.kt`.

**Interfaces:**
- Produces: `object GuardColors { val Night, OnNight, OnNightQuiet, Ok, Notice, Urgent: Color }`,
  `fun contrastRatio(a: Color, b: Color): Double`, `@Composable fun GuardTheme(content)`,
  `@Composable fun HomeScreen()` — spec 01 replaces HomeScreen's body.

- [ ] **Step 1:** Write `ContrastTest`: for each pair (OnNight/Night, OnNightQuiet/Night ≥ 4.5;
  Ok/Night, Notice/Night, Urgent/Night ≥ 3.0) assert `contrastRatio(fg, bg) >= min`, plus
  `contrastRatio(Color.White, Color.Black)` ≈ 21.0 within 0.01.
- [ ] **Step 2:** `./gradlew testDebugUnitTest` → FAIL (unresolved `GuardColors`).
- [ ] **Step 3:** Implement `GuardColors.kt` with WCAG relative luminance
  (`c<=0.03928 ? c/12.92 : ((c+0.055)/1.055)^2.4`, `L=0.2126R+0.7152G+0.0722B`,
  ratio `(L1+0.05)/(L2+0.05)`). If a pair fails, lighten that token for the app and say so in a comment.
- [ ] **Step 4:** `Theme.kt` (dark scheme from tokens; system font, scales with user setting),
  `HomeScreen.kt`: title with `Modifier.semantics { heading() }`, a status line
  "Ikke beskyttet endnu" with `liveRegion = Polite`, short explanation text, `verticalScroll`.
- [ ] **Step 5:** `./gradlew buildSmoke --no-daemon` → BUILD SUCCESSFUL.
- [ ] **Step 6:** Commit `feat: scaffold app with accessible theme and contrast test`.

### Task 2: Build, release and repo tooling

**Files (copy from weather-android, adapt names, app id, APK name `GuardAndroid.apk`, site URL):**
`.github/workflows/ci.yml`, `release-apk.yml`, `deploy-pages.yml` (publishes `website/`),
`.github/dependabot.yml`, `.githooks/{pre-commit,commit-msg,pre-push}`, `scripts/install-hooks.sh`,
`scripts/setup-signing.sh`, `scripts/setup-repo.sh`, `fastlane/metadata/android/{da-DK,en-US}/`
(`title.txt`, `short_description.txt`, `full_description.txt`).

- [ ] **Step 1:** Copy, then `grep -rniE 'weather|dk\.cocode\.weather' .github .githooks scripts fastlane` → no hits.
- [ ] **Step 2:** `shellcheck scripts/*.sh .githooks/*` → clean; `bash scripts/install-hooks.sh`.
- [ ] **Step 3:** Confirm every `uses:` line carries a 40-char SHA: `grep -n 'uses:' .github/workflows/*.yml`.
- [ ] **Step 4:** Commit `ci: add CI, release, pages workflows and repo scripts`.

### Task 3: Documentation and loop profile

**Files:** `README.md`, `CONTRIBUTING.md`, `SECURITY.md`, `LICENSE` (GPL-3.0 text), `llms.txt`,
`CLAUDE.md` (architecture, package layout, build commands, accessibility rules, "Building with the
lean loop" section, link to `profile-android.md`), `profile-android.md` (graph-loop's
`profiles/android-gradle.md` + `## suite_command` `./gradlew buildSmoke --no-daemon`,
`## build_command` `./gradlew assembleDebug --no-daemon`, `## artifact`
`app/build/outputs/apk/debug/app-debug.apk`, `## account` `personal`, and a `house` line allowing
exactly the spec-01 permissions).

- [ ] **Step 1:** Write the files; README states it is free software (GPL-3.0, free of charge) and gives the "local tunnel, not a VPN service" explanation; the site says the same.
- [ ] **Step 2:** `grep -n '^## ' profile-android.md` shows suite_command, build_command, artifact, account.
- [ ] **Step 3:** Commit `docs: add README, contributing, license, CLAUDE.md and loop profile`.

### Task 4: Website (delegated to a Sonnet worker, verified here)

**Files:** `website/index.html` (da), `website/en/index.html`, `website/privacy/index.html`,
`website/en/privacy/index.html`, `website/styles.css` (box tokens), `website/favicon.svg`,
`website/CNAME` (`android.guard.cocode.dk`), `robots.txt`, `sitemap.xml`.

- [ ] **Step 1:** Brief the worker with the design doc's Website and Accessibility sections.
- [ ] **Step 2:** Verify: each page has `lang`, one `h1`, `<main>`, skip link, hreflang pair,
  canonical, visible `:focus-visible`; privacy pages say: no data collected, lookups go only to the
  chosen DNS resolver, no server, no account, no analytics.
- [ ] **Step 3:** Serve locally (`python3 -m http.server -d website`) and check with Playwright:
  tab order reaches every link; accessibility snapshot shows landmarks.
- [ ] **Step 4:** Commit `feat(site): add da/en site with privacy pages`.

### Task 5: Spec 01 for the loop

**Files:** `docs/lean/01-dns-filter-vpn.md`, `docs/lean/lessons.md` (empty header).

- [ ] **Step 1:** Write spec 01 from design Part 2 + Accessibility: goal, full UI journey (every
  state: off, asking permission, permission refused, starting, protected, Private DNS warning,
  stopped by another VPN, error), exact da/en strings, TalkBack labels, files, acceptance tests
  (JVM, named), out of scope, the allowed permissions.
- [ ] **Step 2:** Commit `docs: add lean spec 01 (DNS filter over a local tunnel)`.

### Task 6: Publish

- [ ] **Step 1:** `gh repo create cocodedk/guard-android --public --source . --remote origin`;
  push `main` with the design commit only; push the setup branch; `gh pr create`.
- [ ] **Step 2:** Wait for CI green; merge (squash, delete branch).
- [ ] **Step 3:** Enable Pages (source GitHub Actions, custom domain). Tell the owner: add DNS
  `CNAME android.guard.cocode.dk → cocodedk.github.io`, run `./scripts/setup-signing.sh`.
- [ ] **Step 4:** `./scripts/setup-repo.sh` (branch protection).

### Task 7: graph-loop

- [ ] **Step 1:** `python3 ~/0-projects/graph-loop/graph/graph-goal.py --workspace scratchpad/lean contact bb@cocode.dk`; owner confirms the mail.
- [ ] **Step 2:** Clean clone of origin `main` into the scratchpad; run
  `env -i PATH=$PATH HOME=$(mktemp -d) ANDROID_HOME=$HOME_REAL/Android/Sdk GRADLE_USER_HOME=<cache> ANDROID_USER_HOME=<copy> ./gradlew buildSmoke --no-daemon` → BUILD SUCCESSFUL.
- [ ] **Step 3:** Run `lean.py --workspace scratchpad/lean --repo . --spec docs/lean/01-dns-filter-vpn.md --profile profile-android.md` in the background; answer grill questions in the spec (exit 2) and rerun.

### Task 8: Release gate (after spec 01's PR is merged and signing is set)

- [ ] **Step 1:** Review and merge the loop's PR after CI green.
- [ ] **Step 2:** `gh workflow run release-apk.yml`, watch it, then
  `curl -sIL -o /dev/null -w '%{http_code}\n' https://github.com/cocodedk/guard-android/releases/latest/download/GuardAndroid.apk` → 200
  and the same for `https://android.guard.cocode.dk/privacy/` → 200.
