import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // No kotlin.android plugin: AGP 9 provides Kotlin support itself and rejects it.
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The version lives in gradle.properties, where the release workflow and F-Droid's
// checkupdates both read it. See the comment there before bumping it.
val appVersionName: String = providers.gradleProperty("VERSION_NAME").get()
val appVersionCode: Int = providers.gradleProperty("VERSION_CODE").get().toInt()

// Signing material only ever arrives through the environment. A missing keystore
// is not an error — it just means this is a local build, which stays unsigned.
val keystorePath = System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
val keystorePassword = System.getenv("KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
val keyAliasEnv = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() }
val keyPasswordEnv = System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() }
val keystoreFile = keystorePath?.let { rootProject.file(it).absoluteFile }?.takeIf { it.isFile }
val hasSigningConfig = keystoreFile != null && keystorePassword != null &&
    keyAliasEnv != null && keyPasswordEnv != null

android {
    namespace = "dk.cocode.guard"
    // 37 is a floor, not a preference: androidx.core 1.19.0 and lifecycle 2.11.0 declare
    // minCompileSdk=37 in their AAR metadata, and nothing resolves under it.
    // targetSdk 36 is the newest runtime behaviour this app has been checked against;
    // AGP 9 defaults targetSdk to compileSdk, so it is set explicitly below.
    compileSdk = 37

    defaultConfig {
        applicationId = "dk.cocode.guard"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv
            }
        }
    }

    buildTypes {
        release {
            // R8 shrinks and optimises the release build. Nothing here uses reflection, so no
            // keep rules are needed yet.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    packaging {
        jniLibs {
            // The only .so files in the APK are prebuilts from AndroidX. AGP strips them with
            // whatever NDK it finds, so a rebuild without that exact NDK produces different
            // bytes — F-Droid's builder has none unless its recipe pins one. Keeping the
            // symbols leaves the libraries exactly as their AARs ship them, which rebuilds
            // identically anywhere, and costs a few kB.
            keepDebugSymbols += "**/*.so"
        }
    }

    lint {
        // The build must fail on a real lint error, but a warning should not block
        // a merge — CI runs this on every PR.
        abortOnError = true
        warningsAsErrors = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // AGP otherwise adds a "Dependency metadata" block to the APK signing block,
    // encrypted with a key only Google Play holds. F-Droid rejects APKs that carry
    // it, and it lands in the published release APK that F-Droid verifies against.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// AGP 9 removed android.kotlinOptions; the Kotlin plugin's own block replaces it.
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

// No Google or network library here on purpose: VpnService, DatagramSocket and
// ConnectivityManager ship with Android, which keeps the dependency surface small
// and the F-Droid build reproducible.
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
