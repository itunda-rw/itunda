// Standalone Android library module for Saronite's native bridge — the
// reference implementation, kept independently buildable and verifiable on
// its own (see saronite/README.md). The main itunda app embeds its own copy
// of this same code directly in android/app (rw.itunda.saronite.brownfield),
// rather than depending on this module as a Gradle subproject, so a future
// change here can be verified in isolation before being ported into the app.
// Single-project build: this file is both the root and the only module,
// so plugin versions are declared here directly rather than via a parent
// `apply false` block (matching itunda's main android/build.gradle.kts,
// just collapsed to one project since there's nothing else to share it with).
plugins {
    id("com.android.library") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.1.0"
}

android {
    namespace = "rw.itunda.saronite.brownfield"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // React Native's Android artifact ships the bridge classes used below
    // (ReactContextBaseJavaModule, ReactPackage, Promise, WritableMap, ...).
    // compileOnly because the host app supplies the actual RN runtime at
    // integration time — this module doesn't bundle its own copy of RN.
    compileOnly("com.facebook.react:react-android:0.80.3")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
}
