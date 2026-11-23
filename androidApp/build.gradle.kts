// The Android app: MainActivity plus the Android-only services (Google sign-in, photos, rest alarm).
// Everything else is shared code in :composeApp.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firebase config comes from google-services.json (see README). Without it the app still builds
// and shows a "Firebase isn't set up" screen.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.gym.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gym.app"
        minSdk = 26
        targetSdk = 36 // Android 16
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        // An optimised build for your own phones (shrunk and much faster than debug). Signed with
        // the debug key so it installs over the debug app and Google sign-in (registered SHA-1)
        // keeps working. A Play Store release would need its own upload key instead.
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation("androidx.activity:activity-compose:1.13.0")
    // Play services pulls in an old Fragment; activity-result APIs need 1.3+ (lint checks this).
    implementation("androidx.fragment:fragment:1.9.1")
    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-firestore") // wiping the offline cache on sign-out

    // Google sign-in via Android's Credential Manager (system account picker)
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.1")
}
