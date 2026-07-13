plugins {
    // Bumped from 8.2.2 to 8.9.2 (2026-07-12, granite-adoption stage 2): same class
    // of composite-build version-alignment issue as the Kotlin bump above --
    // @react-native/gradle-plugin@0.80.3's own build declares AGP 8.9.2 (read
    // directly from its gradle/libs.versions.toml), which requires Gradle >= 8.11.1
    // (a real, concrete build failure confirmed this: "Minimum supported Gradle
    // version is 8.11.1. Current version is 8.9."). gradle-wrapper.properties
    // bumped to match. Applies project-wide, not just :app -- every core:*/features:*
    // module using com.android.library picks this up too; re-verify each still
    // builds (see the granite-adoption stage 2 checkpoint commit for what was
    // actually re-verified).
    // Bumped again to 8.12.0 (2026-07-12, final granite-adoption stage 3 hop, RN
    // 0.80.3 -> 0.84.0): same alignment reasoning as the 8.2.2 -> 8.9.2 bump above --
    // @react-native/gradle-plugin@0.84.0's own libs.versions.toml declares this.
    id("com.android.application") version "8.12.0" apply false
    id("com.android.library") version "8.12.0" apply false
    // Bumped from 1.9.22 to 2.1.0 project-wide (2026-07-10): react-android:0.80.3's
    // stdlib metadata (already proven to compile at Kotlin 2.1.0 in
    // packages/saronite/packages/brownfield-module's standalone build) needs a Kotlin
    // compiler newer than 1.9.22 can read, and Gradle resolves one plugin classpath
    // version per build -- a subproject can't pin a different version of the same
    // plugin. Kotlin 2.x also requires the new plugin-based Compose compiler instead
    // of the old composeOptions.kotlinCompilerExtensionVersion mechanism -- see each
    // compose-enabled module's build.gradle.kts.
    // Bumped again to 2.1.20 (2026-07-12, granite-adoption stage 2): the real
    // @react-native/gradle-plugin, included via settings.gradle.kts's includeBuild,
    // is its own separate Gradle build with its own Kotlin Gradle Plugin version --
    // at react-native 0.80.x that's exactly 2.1.20 (confirmed by reading its
    // gradle/libs.versions.toml directly), and a composite-build's included plugin
    // build does NOT automatically align its KGP version with the consuming
    // project's. Mismatched KGP versions on the combined classpath threw a real,
    // concrete error confirming this: "Found interface
    // org.jetbrains.kotlin.gradle.dsl.KotlinTopLevelExtension, but class was
    // expected" (a binary-incompatible DSL type shape between KGP 1.9.24, which
    // react-native 0.72-0.77's gradle-plugin declares, and 2.1.0). Matching itunda's
    // own root version to the included build's exactly, rather than relying on
    // cross-version classloader tolerance, is the correct fix -- not a version
    // React Native itself requires.
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    // Real React Native Gradle Plugin (2026-07-12, granite-adoption stage 2) -- no
    // version string here since it's resolved via settings.gradle.kts's
    // includeBuild composite-build substitution, not a published Maven coordinate.
    id("com.facebook.react") apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

// Root `ext` properties (2026-07-12, granite-adoption stage 7) -- itunda's own
// modules configure compileSdk/minSdk/etc. directly and literally per-module, not
// via shared root ext vars, but brick-module's own android/build.gradle (a
// real, third-party Gradle script, not itunda's code) reads this older-style
// convention (`rootProject.ext.has("compileSdkVersion")`, matching what
// react-native's own pre-Gradle-Plugin project template used to generate) to
// self-configure. Additive only -- doesn't change how any of itunda's own
// modules resolve these values.
extra["compileSdkVersion"] = 34
extra["minSdkVersion"] = 26
extra["targetSdkVersion"] = 34
extra["kotlinVersion"] = "2.1.20"

// Real fix (2026-07-13, granite-adoption stage 7 completion): react-native-svg's
// own android/build.gradle (a real, third-party Gradle script) reads this same
// property name as app/build.gradle.kts's own REACT_NATIVE_NODE_MODULES_DIR
// comment above documents for brick-module, but via `rootProject.ext.has(...)`
// specifically (`safeExtGet`, read directly from react-native-svg/android/build.gradle)
// rather than the app-project-scoped ext brick-module's own helper checks --
// :app's own `extra[...]` assignment above is invisible here; Gradle project
// `extra`/`ext` blocks are NOT inherited from child to root. Without this,
// react-native-svg falls back to shelling out to `node --print
// "require.resolve('react-native/package.json')"` from android/'s own working
// directory, which fails in itunda's non-standard monorepo layout (confirmed
// live: "Unable to resolve react-native location in node_modules").
extra["REACT_NATIVE_NODE_MODULES_DIR"] = file("../packages/saronite/node_modules/react-native")

