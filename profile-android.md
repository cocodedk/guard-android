# Profile: android-gradle (Guard for Android)

Copied from graph-loop's `profiles/android-gradle.md` and given this repository's commands. The loop
reads the first indented line under each field; it does not know what they mean.

## suite_command

    ./gradlew buildSmoke --no-daemon

The one canonical check: debug build, unit tests and lint. CI runs the same task.

## build_command

    ./gradlew assembleDebug --no-daemon

## artifact

    app/build/outputs/apk/debug/app-debug.apk

## account

    personal

## proof_command

    ./gradlew testDebugUnitTest --tests '<class>'

Never `-q`: at quiet level the console carries no per-test failure text.

## report

    app/build/test-results/testDebugUnitTest/TEST-<class>.xml

The failing test names and their exception types live here, not in the console.

## paths_the_gate_needs

    $ANDROID_HOME          the SDK, wherever the machine keeps it
    $GRADLE_USER_HOME      a writable cache directory, outside any masked home
    $ANDROID_USER_HOME     a writable directory holding a copy of the debug.keystore the owner's
                           own builds sign with (by default ~/.android/; match fingerprints with
                           `apksigner verify --print-certs`): without it the build signs with a
                           throwaway key, and its APK cannot update the installed app. This hands
                           the build's code that debug key; a dedicated per-project debug key
                           avoids it, at the cost of one reinstall (which clears the app's data)

**This project:** `ANDROID_USER_HOME` holds a debug key made for this project only, never the
owner's personal `~/.android` key: autonomous builds run code that can read it, and no build of
this app has been installed yet, so there is nothing to update.

A gate box that hides the user's home hides both. The SDK is read-only; the cache must be writable
and should survive between gates, or every gate downloads the toolchain again.

## machine_is_ready

    ./gradlew --version

Fails when the JDK is a JRE or when the wrapper cannot download. It runs no task, so it does not
check the SDK; a missing SDK shows up in the suite as "SDK location not found" (below).

## the_machine_not_the_card

    SDK location not found
    does not provide the required capabilities
    Daemon startup failed
    Could not start ... daemon
    Unable to locate a Java Runtime
    Cannot allocate memory

A gate ending with one of these is the machine's fault. The card keeps its status and its rounds.

## after_a_gate

This repository's gate commands pass `--no-daemon`, so each build's daemon is single-use and stops
with the build. A command without it would leave a daemon alive per gate, each holding a gigabyte or
more, and the machine would run out of memory with the next card blamed.

## red_first

A judge is red when every case in the report fails with `NotImplementedError` from a `TODO()` body.
Count the failures in the XML, not in the console.

## house

The JDK is not pinned in the repository, no toolchain resolver is declared, and release keeps R8 and
resource shrinking on — an F-Droid build must stay reproducible. The manifest gains only the
permissions the spec being built names in its Permissions section. The rest of the house rules are
in `CLAUDE.md`.
