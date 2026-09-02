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
    includeBuild("../packages/saronite/node_modules/@react-native/gradle-plugin")
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

// Real brick-module autolinking (2026-07-12, granite-adoption stage 7) -- the
// mechanism that generates the native Kotlin glue @granite-js/brownfield-module's
// GraniteBrownfieldModule needs to actually register as a TurboModule (confirmed
// necessary live: importing it without this throws
// "TurboModuleRegistry.getEnforcing(...): 'BrickModule' could not be found").
// Groovy script, applied and invoked from Kotlin DSL via the extra-properties
// closure it defines -- brick-module's own header comment documents the Groovy
// call form (`apply from: file(...)` then `applyBrickModules(settings, [...])`);
// Kotlin DSL has no equivalent call-sugar for a Groovy ext closure, so it's
// invoked explicitly as a groovy.lang.Closure instead.
apply(from = "../packages/saronite/node_modules/brick-module/android/brick_modules.gradle")

@Suppress("UNCHECKED_CAST")
val applyBrickModules = extra["applyBrickModules"] as groovy.lang.Closure<Any?>
applyBrickModules.call(
    settings,
    mapOf(
        "projectRoot" to "../packages/saronite/host-app",
        "appProject" to "app",
    ),
)

// Redirect :granite-js_brownfield-module to a real, minimal, itunda-owned stub
// (see android/brownfield-module-stub/build.gradle.kts for the full reasoning).
// applyBrickModules above already ran settings.include(":granite-js_brownfield-module")
// with its projectDir pointed at packages/saronite/node_modules/@granite-js/
// brownfield-module/android -- a directory with real .kt source but no
// build.gradle, which Gradle can't configure as a library ("No variants exist").
// Confirmed via the actual generated code that nothing there is needed at
// compile time; this redirect is a real fix, not a bypass.
project(":granite-js_brownfield-module").projectDir = file("brownfield-module-stub")

// `brick-module` itself contains the runtime registry/package classes that the
// generated bridge and MiniAppActivity genuinely use. Its published Gradle
// script, however, is evaluated during React configuration and reads an empty
// app extension before normal app configuration is possible. Compile the exact
// vendored Kotlin source through an Itunda-owned wrapper instead of patching
// node_modules or dropping the runtime dependency.
project(":brick-module").projectDir = file("brick-module-stub")

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

// Real standalone delivery-rider app (2026-07-20) -- a rider's own app, separate
// installable APK (applicationId rw.itunda.rider), matching how Coupang Eats' rider
// app is a distinct product from the consumer app rather than a mode inside it. Does
// NOT depend on :app (an application module can't depend on another application
// module) -- has its own minimal Retrofit/OkHttp client hitting the same real
// backend, scoped to exactly what a rider needs (see riderapp/build.gradle.kts).
include(":riderapp")

// Real standalone merchant/POS app (2026-07-20) -- a shop owner's own app, separate
// installable APK (applicationId rw.itunda.merchant), the second slice of the
// "dedicated app per role" effort (rider app already done). Own minimal client,
// same independence rationale as :riderapp.
include(":merchantapp")

// Store-agent operations are a separate, least-privilege install.  This is not a
// consumer-app mode: its API only derives the assigned agent from the signed-in
// operator, so a cashier cannot select or operate another store's till.
include(":agentapp")

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

// Real proof-of-slice Feature extraction (2026-07-22/23) -- Marketplace pulled out of
// app/ui/SuperAppTabs.kt into its own Feature module, matching Toss's real published
// Microfeatures architecture (toss.tech/article/slash23-iOS). See
// features/marketplace/impl/.../MarketplaceScreen.kt's own header comment.
include(":features:marketplace:api")
include(":features:marketplace:impl")
include(":features:marketplace:testing")

include(":features:jobs:api")
include(":features:jobs:impl")
include(":features:jobs:testing")

include(":features:property:api")
include(":features:property:impl")
include(":features:property:testing")

include(":features:community:api")
include(":features:community:impl")
include(":features:community:testing")

include(":features:shop:api")
include(":features:shop:impl")
include(":features:shop:testing")

include(":features:eats:api")
include(":features:eats:impl")
include(":features:eats:testing")

include(":features:talk:api")
include(":features:talk:impl")
include(":features:talk:testing")

include(":features:maps:api")
include(":features:maps:impl")
include(":features:maps:testing")

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

include(":features:home:api")
include(":features:home:impl")
include(":features:home:testing")

include(":features:pay:api")
include(":features:pay:impl")
include(":features:pay:testing")

// Itunda Pay SDK (For 3rd party integrations)
include(":sdk:pay")

// Real static-analysis guardrail (2026-07-23) for docs/MULTI_AGENT_ISOLATION.md's
// silo model -- a standalone Kotlin/JVM module (Konsist parses source files
// directly off disk, so it needs no dependency on any feature module) asserting
// no feature's `impl` package imports another feature's `impl` package.
include(":architecture-test")
