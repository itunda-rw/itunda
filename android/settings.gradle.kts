pluginManagement {
    // Real React Native Gradle Plugin (2026-07-12, granite-adoption stage 2) --
    // resolved from packages/saronite/host-app's node_modules (a transitive dep of
    // react-native itself, not a separate install) via composite-build plugin
    // substitution rather than a Maven coordinate/version, since this plugin isn't
    // published with a version number consumers pin directly.
    // Path is where npm actually hoists this dependency in packages/saronite's
    // workspace tree -- re-verify after any `npm install` there, since npm's
    // hoisting decision can move it (it was nested under host-app/node_modules
    // before the RN 0.72.17 -> 0.75.4 bump changed the dependency graph enough to
    // hoist it here instead).
    includeBuild("../packages/saronite/host-app/node_modules/@react-native/gradle-plugin")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Real settings-level autolinking (2026-07-12, granite-adoption stage 2). Originally
// skipped on the theory there was "nothing new to autolink yet" (the Saronite bridge
// stays manually registered via ItundaApplication.kt's getPackages()) -- wrong: at
// RN 0.80.x the plugin's :app:generateAutolinkingPackageList task runs unconditionally
// once com.facebook.react is applied and unconditionally requires
// build/generated/autolinking/autolinking.json to exist, regardless of whether
// anything is actually being autolinked. This replaces the older
// @react-native-community/cli-platform-android native_modules.gradle mechanism
// itunda's docs/comments referenced (that mechanism is what RN 0.72 used; RN 0.80
// moved autolinking config generation into this settings plugin instead).
plugins {
    id("com.facebook.react.settings")
}

extensions.configure<com.facebook.react.ReactSettingsExtension> {
    // workingDirectory defaults to one level up from this file (`android/../`) --
    // itunda's JS project root is not there, same non-standard-layout reason as
    // app/build.gradle.kts's react { } block.
    autolinkLibrariesFromCommand(
        workingDirectory = file("../packages/saronite/host-app")
    )
}

dependencyResolutionManagement {
    // Relaxed from FAIL_ON_PROJECT_REPOS (2026-07-12, granite-adoption stage 2): the
    // real React Native Gradle Plugin adds its own project-level `maven { }`
    // repository when applied to :app (a real, concrete build failure confirmed
    // this: "Build was configured to prefer settings repositories over project
    // repositories but repository 'maven' was added by plugin 'com.facebook.react'").
    // PREFER_SETTINGS still uses the repositories declared here as the priority
    // source for everything else -- it only stops erroring when a project-level
    // script (this one plugin, not itunda's own code) adds one of its own.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "itunda"

include(":app")

// Core Bounded Contexts
include(":core:designsystem")
include(":core:network")
include(":core:testing")
include(":core:identity")
include(":core:consent")
include(":core:ledger")
include(":core:risk")

// Feature Bounded Contexts
include(":features:payments:api")
include(":features:payments:impl")
include(":features:payments:testing")

include(":features:bills:api")
include(":features:bills:impl")
include(":features:bills:testing")

include(":features:merchant:api")
include(":features:merchant:impl")
include(":features:merchant:testing")

include(":features:credit:api")
include(":features:credit:impl")
include(":features:credit:testing")

include(":features:wealth:api")
include(":features:wealth:impl")
include(":features:wealth:testing")

include(":features:insurance:api")
include(":features:insurance:impl")
include(":features:insurance:testing")

include(":features:engagement:api")
include(":features:engagement:impl")
include(":features:engagement:testing")

include(":features:assets:api")
include(":features:assets:impl")
include(":features:assets:testing")

include(":features:banking:api")
include(":features:banking:impl")
include(":features:banking:testing")

// Itunda Pay SDK (For 3rd party integrations)
include(":sdk:pay")
