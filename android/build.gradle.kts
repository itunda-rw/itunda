plugins {
    id("com.android.application") version "8.2.2" apply false
    id("com.android.library") version "8.2.2" apply false
    // Bumped from 1.9.22 to 2.1.0 project-wide (2026-07-10): react-android:0.80.3's
    // stdlib metadata (already proven to compile at Kotlin 2.1.0 in
    // packages/saronite/packages/brownfield-module's standalone build) needs a Kotlin
    // compiler newer than 1.9.22 can read, and Gradle resolves one plugin classpath
    // version per build -- a subproject can't pin a different version of the same
    // plugin. Kotlin 2.x also requires the new plugin-based Compose compiler instead
    // of the old composeOptions.kotlinCompilerExtensionVersion mechanism -- see each
    // compose-enabled module's build.gradle.kts.
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
}
