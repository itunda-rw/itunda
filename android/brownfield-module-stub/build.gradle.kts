// Real Android library module standing in for `@granite-js/brownfield-module`'s
// own missing Gradle project (2026-07-12, granite-adoption stage 7).
//
// brick_modules.gradle (from the real `brick-module` npm package, applied in
// ../settings.gradle.kts) auto-includes `@granite-js/brownfield-module` as its
// own Gradle subproject for every package.json dependency carrying a
// `brickModule.android.package` field -- a generic rule that doesn't fit this
// specific package: `@granite-js/brownfield-module`'s own `android/` directory
// (packages/saronite/node_modules/@granite-js/brownfield-module/android/) ships
// bare `.kt` typegen source files with no `build.gradle` of its own, so Gradle
// can't configure it as a real library ("No variants exist").
//
// This project needs `run.granite.brownfield.GraniteBrownfieldModuleSpec` to
// exist and compile -- confirmed necessary by reading the real generated
// `android/.brick/src/main/kotlin/BrickModuleImpl.kt` directly: it genuinely
// imports that type and casts a real module-registry lookup to it. An initial
// attempt at a fully-empty stub was wrong -- reverted once this became clear.
//
// The real vendored source
// (packages/saronite/node_modules/@granite-js/brownfield-module/android/src/main/kotlin/run/granite/brownfield/typegen/GraniteBrownfieldModuleSpec.kt)
// is NOT compiled here, deliberately: it has a real upstream bug, confirmed by
// a genuine Kotlin compiler error, not a guess -- the interface declares both
// `val schemeUri: String` (compiles to a `getSchemeUri(): String` JVM method,
// standard Kotlin property codegen) AND an explicit `fun getSchemeUri(): String`
// with the identical JVM signature, which the Kotlin compiler rejects outright
// as a "Platform declaration clash" on the interface's own declaration lines --
// this fails regardless of how any implementing class is written, since the
// interface itself doesn't compile. `src/main/kotlin/.../GraniteBrownfieldModuleSpec.kt`
// in this project is itunda's own corrected replica of the exact same public
// contract (same package, same member names minus the clashing property),
// compiled instead -- not modifying the vendored node_modules file, which
// wouldn't survive a reinstall and isn't itunda's to change; kept in sync with
// the JS spec (packages/saronite/node_modules/@granite-js/brownfield-module/src/spec/GraniteBrownfieldModule.brick.ts)
// which itself only ever calls `getSchemeUri()`, not the deprecated property.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "run.granite.brownfield"
    compileSdk = 34

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
    implementation(project(":brick-module"))
    implementation("com.facebook.react:react-android")
    // GraniteBrownfieldModuleTypes.kt (the real vendored source compiled into
    // this module) uses @SerializedName -- matches the Gson version
    // brick_modules.gradle's own configureBrickModules adds to :app.
    implementation(libs.gson.brownfield)
}
