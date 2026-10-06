# Contributing to Guard for Android

## Local setup

1. Install a JDK 17+ and the Android SDK; point `ANDROID_HOME` (or `sdk.dir` in `local.properties`)
   at the SDK.
2. Clone the repository and run `bash scripts/install-hooks.sh`.
3. Run `./gradlew buildSmoke`. It must end in `BUILD SUCCESSFUL`.

## Build and test

| Command | What it does |
|---|---|
| `./gradlew buildSmoke` | Debug build, unit tests, lint. CI and the pre-push hook run exactly this. |
| `./gradlew testDebugUnitTest` | Unit tests only |
| `./gradlew assembleRelease` | Release build (R8 on); signed only when the signing variables are set |

Unit tests run on the JVM and offline: no real sockets, no name lookups, no device.

## What the hooks enforce

- **pre-commit:** lint.
- **commit-msg:** [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`,
  `docs:`, `ci:`, `chore:` …).
- **pre-push:** the push URL is under `github.com/cocodedk`, no force-push to `main`, and
  `buildSmoke` passes.

Never skip a hook with `--no-verify`; fix what it reports.

## Branch naming

`feat/<topic>`, `fix/<topic>`, `docs/<topic>`, `ci/<topic>`, `chore/<topic>`. Work reaches `main`
only through a pull request with green CI.

## Accessibility rules

Every change keeps the app usable without sight:

- Every control has a spoken label that says what it does.
- A status change is announced (live region), and the screen title is a heading.
- Meaning never rests on color alone; words come with every state.
- Touch targets are at least 48dp; text is in `sp` and nothing clips at 200% font size.
- New colors go into `GuardColors` and get a pair in `ContrastTest`.

## Coding conventions

- Kotlin official style. Code files under 200 lines; split at a natural seam.
- No Google or Play library, and no permission a spec does not name.
- User-facing text lives in `res/values/strings.xml` (Danish, the default) and
  `res/values-en/strings.xml` (English). Plain words, short sentences.

## The website

`website/` is plain HTML and CSS, deployed by `deploy-pages.yml` on every push to `main` that
touches it. Danish at `/`, English at `/en/`. Preview with
`python3 -m http.server -d website`. It must meet WCAG 2.2 AA.

## PR checklist

- [ ] `./gradlew buildSmoke` passes
- [ ] New behaviour has a unit test
- [ ] Both languages updated for any new text
- [ ] Checked with TalkBack, if a screen changed
