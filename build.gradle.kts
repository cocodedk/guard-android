// These four move together and cannot be bumped one at a time: AGP 9.3.1 refuses to
// apply on anything below Gradle 9.5, and declares a hard dependency on Kotlin Gradle
// plugin 2.2.10. The Compose compiler plugin must match the Kotlin version exactly.
plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}

// One command for CI and the pre-push hook, so what blocks a push is exactly what
// blocks a merge.
tasks.register("buildSmoke") {
    group = "verification"
    description = "Build debug, run unit tests, and lint."
    dependsOn(":app:assembleDebug", ":app:testDebugUnitTest", ":app:lintDebug")
}
