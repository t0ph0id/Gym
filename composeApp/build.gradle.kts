// Shared code: all UI and logic (Compose Multiplatform). The Android app itself is in :androidApp;
// an iOS app would be a second, equally thin wrapper around this module.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    android {
        namespace = "com.gym.app.shared"
        compileSdk = 37
        minSdk = 26
        withHostTest {} // runs commonTest on the JVM (testAndroidHostTest)
    }
    // iPhone: the shared code compiles to a framework that the (future) Xcode app wraps, the same way
    // :androidApp wraps it on Android. Only the iOS "actual" bits live in iosMain.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

            // Firebase (Kotlin Multiplatform wrapper, works on Android + iOS)
            api("dev.gitlive:firebase-auth:2.8.0")
            api("dev.gitlive:firebase-firestore:2.8.0")

            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")

            // Exercise photos (loaded from a CDN, cached on disk)
            implementation("io.coil-kt.coil3:coil-compose:3.6.3")
            implementation("io.coil-kt.coil3:coil-network-ktor3:3.6.3")
            implementation("io.ktor:ktor-client-core:3.6.1") // recipe link previews
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.6.1") // network engine for Coil and link previews
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.13.0") // BackHandler
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
            implementation("io.ktor:ktor-client-okhttp:3.6.1") // network engine for Coil
            // GitLive's Firebase wrappers leave the Google Firebase versions to a BOM.
            implementation(project.dependencies.platform("com.google.firebase:firebase-bom:34.18.0"))
        }
    }
}
