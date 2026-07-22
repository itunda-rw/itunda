plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.facebook.react")
}

// Real React Native Gradle Plugin config (2026-07-12, granite-adoption stage 2).
// itunda's JS project root is NOT the default `..` the plugin assumes (that default
// fits the standard co-located android/+node_modules/ layout the plugin was built
// for) -- it lives at packages/saronite/host-app, a sibling of packages/, three
// levels up and back down from here. Every path below is explicit for that reason.
react {
    root = file("../../packages/saronite/host-app")
    // Hoisting location changes across `npm install` runs in packages/saronite as
    // the dependency graph shifts -- re-verify against settings.gradle.kts's
    // includeBuild path (same caveat) whenever this stops resolving.
    reactNativeDir = file("../../packages/saronite/node_modules/react-native")
    entryFile = file("../../packages/saronite/host-app/index.js")
    // Real fix (2026-07-13, granite-adoption stage 7 completion): react-native-safe-
    // area-context's own generateCodegenSchemaFromJavaScript task crashed with
    // "Cannot find module '.../host-app/node_modules/@react-native/codegen/...'".
    // Traced into ReactPlugin.kt (com.facebook.react's own Gradle plugin source,
    // read directly from node_modules): codegenDir is NOT resolved per-module --
    // ReactPlugin only wires codegenDir from :app's own `react { }` extension into a
    // single shared `rootExtension` (the "com.android.application" branch of
    // ReactPlugin.apply), and every OTHER autolinked library module's codegen task
    // reads that same shared value (`it.codegenDir.set(rootExtension.codegenDir)`).
    // Left at its default it resolves to `root/node_modules/@react-native/codegen`
    // == host-app/node_modules/@react-native/codegen, which doesn't exist -- npm
    // hoists @react-native/codegen to packages/saronite/node_modules instead (a
    // workspace root, not host-app), confirmed by `ls`. Explicit override needed
    // here for exactly the same reason root/reactNativeDir already need one above.
    codegenDir = file("../../packages/saronite/node_modules/@react-native/codegen")
    // Real fix, same root cause as codegenDir above: PackageList.java correctly
    // referenced com.th3rdwave.safeareacontext.SafeAreaContextPackage (autolinking.json
    // discovery itself works), but compilation failed with "package ... does not
    // exist" -- the actual Gradle *project dependency* (implementation(project(":react-
    // native-safe-area-context"))) that makes those compiled classes visible to :app
    // is not automatic. Read directly from ReactExtension.kt: settings.gradle.kts's
    // autolinkLibrariesFromCommand() only settings.include()s the project; the
    // dependency-wiring step is this separate function, which its own doc comment says
    // "should be invoked inside the react {} block in the app's build.gradle" --
    // itunda's block never did. brick-module/granite-js_brownfield-module never hit
    // this because brick_modules.gradle wires their dependencies itself (see the
    // Project Modules section below) -- only genuinely-standard-autolinked libraries
    // like react-native-safe-area-context were missing it.
    autolinkLibrariesWithApp()
}

// brick-module's own react-native-helpers.gradle (2026-07-12, granite-adoption
// stage 7) shells out to `node --print "require.resolve('react-native/package.json')"`
// from android/ by default, which fails in itunda's non-standard layout (same
// class of issue react { reactNativeDir = ... } above already works around for
// the official plugin) -- it explicitly checks this ext property first, per its
// own `safeAppExtGet("REACT_NATIVE_NODE_MODULES_DIR", null)`.
extra["REACT_NATIVE_NODE_MODULES_DIR"] = file("../../packages/saronite/node_modules/react-native")

android {
    namespace = "rw.itunda.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "rw.itunda.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Real bug, found 2026-07-11 running FocusOrderTest.kt for the first time:
        // this was never set, so it silently defaulted to the ancient, pre-AndroidX
        // android.test.InstrumentationTestRunner (`pm list instrumentation` on a real
        // emulator confirmed it), which only understands legacy JUnit3 TestCase
        // subclasses -- any real @Test-annotated JUnit4 class (every test in this
        // repo, Android or otherwise) silently reported "No tests found" rather than
        // actually running. There was no androidTest source set before this session
        // to ever notice.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        // Added 2026-07-11 -- real step toward docs/ARCHITECTURE.md §2's Granite-
        // mechanism backlog item ("dynamic bundle loading from a CDN instead of a
        // local Metro server"). Empty by default -- inert unless explicitly set via
        // -PminiAppBundleCdnUrl=..., so existing debug (Metro) and release (packaged
        // asset) behavior are both completely unchanged unless someone opts in. See
        // ItundaApplication.kt's ReactNativeHost.getJSBundleFile() override for
        // where this is actually consumed, and its own doc comment for the honest
        // scope/risk this carries -- deliberately not live-verified in this
        // environment (see the same file's comment for why).
        buildConfigField(
            "String",
            "MINIAPP_BUNDLE_CDN_URL",
            "\"${project.findProperty("miniAppBundleCdnUrl") ?: ""}\""
        )

        // Real login/session flow needs a real base URL -- NetworkClient.kt previously
        // hardcoded "http://10.0.2.2:8080/", the emulator-only loopback alias, at the
        // wrong port (services/backend listens on 4001, see its application.yml) --
        // and a physical device can't resolve 10.0.2.2 at all. Defaults to the emulator
        // alias at the right port; override for a physical device on the same LAN with
        // e.g. `-PapiBaseUrl=http://192.168.0.63:4001/` (2026-07-11 fix).
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${project.findProperty("apiBaseUrl") ?: "http://10.0.2.2:4001/"}\""
        )

        // Same real gap as API_BASE_URL above, just discovered later (2026-07-21):
        // MapScreen.kt had these two hardcoded straight at the private cloud's
        // internal-only Multipass bridge address (192.168.252.3), unreachable for any
        // device other than the Mac itself or another device on the same bridge --
        // a real device testing over the public HTTPS endpoint got a permanently
        // blank map with no way to fix it short of a rebuild. Mirrors bank-mfe's own
        // VITE_TILES_BASE_URL/VITE_GLYPHS_BASE_URL env vars (same LAN default, same
        // override mechanism), so both platforms follow the same real pattern.
        buildConfigField(
            "String",
            "TILES_BASE_URL",
            "\"${project.findProperty("tilesBaseUrl") ?: "http://192.168.252.3:8090"}\""
        )
        buildConfigField(
            "String",
            "GLYPHS_BASE_URL",
            "\"${project.findProperty("glyphsBaseUrl") ?: "http://192.168.252.3:8091"}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    // collectAsStateWithLifecycle() for MainActivity's login-gate StateFlow collection.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    // FragmentActivity, not just ComponentActivity, is required by BiometricPrompt's
    // constructor (androidx.biometric:1.1.0) -- see NIDABiometricAuth.kt.
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // Networking & Architecture
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    // Real session storage for the login flow (2026-07-11): access/refresh tokens are
    // real bearer credentials, not app preferences -- EncryptedSharedPreferences, not
    // plain SharedPreferences. See network/TokenStore.kt.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Real self-hosted Rwanda map (2026-07-19) -- itunda's own MapLibre GL tile server
    // (see docs/TOSS_PARITY_MATRIX.md's Maps row), not Google Maps. Plain Maven Central
    // coordinate, no new repository needed (already declared in settings.gradle.kts).
    implementation("org.maplibre.gl:android-sdk:13.3.1")
    // Real "my location" blue dot (2026-07-19) -- FusedLocationProviderClient, the
    // standard modern Android location API (battery-efficient, real GPS/network fusion).
    // `google()` is already a declared repository for this project.
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Real product-image loading (2026-07-21) -- Coil, the standard modern
    // Compose-native async image loader. Closes docs/DESIGN_REFERENCES.md Section 5's
    // #4/#5 recommendations (real product images on catalog/grid cards): this app had
    // zero image-loading capability anywhere before this (confirmed by repo-wide
    // search), since no client feature needed one until real merchant-supplied product
    // photo URLs existed. Plain Maven Central coordinate, no new repository needed.
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Project Modules
    implementation(project(":core:designsystem"))
    implementation(project(":core:risk"))
    implementation(project(":core:identity"))
    // Real shared networking layer (2026-07-22) -- NetworkClient/ApiService/
    // TokenStore/etc. relocated here from :app itself, see :core:network's own
    // build.gradle.kts doc comment for why.
    implementation(project(":core:network"))
    implementation(project(":features:payments:impl"))
    // Real proof-of-slice Feature extraction (2026-07-22/23) -- Marketplace pulled out
    // of app/ui/SuperAppTabs.kt, see MarketplaceScreen.kt's own header comment.
    implementation(project(":features:marketplace:impl"))
    implementation(project(":features:jobs:impl"))
    implementation(project(":features:property:impl"))
    implementation(project(":features:community:impl"))
    implementation(project(":features:shop:impl"))
    implementation(project(":features:eats:impl"))
    implementation(project(":features:talk:impl"))
    // features:banking:impl deliberately has no dependency here (2026-07-11): its
    // real screens (BankScreen.kt, MySpendingScreen.kt) were intentionally deleted
    // in 061cff6 as unreachable and superseded by ItundaAppScreen.kt's Home tab,
    // built directly against real Toss reference screenshots -- see docs/ARCHITECTURE.md
    // §3. Re-adding them would recreate the exact "two things doing the same job"
    // duplication this repo has spent this session eliminating elsewhere (backend,
    // SDKs, shared-utils). The module itself stays declared in settings.gradle.kts
    // as a placeholder for a genuinely distinct future banking feature, matching the
    // other empty feature modules -- it just has nothing to depend on yet.

    // Apps-in-Itunda mini-app host (Saronite/Granite-pattern brownfield RN integration).
    // Real React Native Gradle Plugin as of 2026-07-12 (granite-adoption stage 2) --
    // see the `react { }` block above. No version pin here anymore: the plugin
    // resolves the react-android/hermes-android artifact versions itself from the
    // react-native package.json found at `reactNativeDir`, which is how the
    // previous manual pin's whole reason for existing (avoiding the plugin's CMake
    // step, which produces the .so files a newer react-android AAR expects to
    // dlopen -- see git history on this block for the exact UnsatisfiedLinkError
    // that forced the earlier 0.80.3 downgrade) is now handled correctly instead of
    // avoided. Still pinned to 0.72.17 at the JS/react-native-package level
    // (packages/saronite/host-app/package.json) -- this stage introduces the
    // plugin only, no version change yet (see docs/ARCHITECTURE.md backlog and the
    // granite-adoption plan for the staged version-upgrade path).
    implementation("com.facebook.react:react-android")
    implementation("com.facebook.react:hermes-android")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Real instrumented UI tests (2026-07-11) -- androidx.compose.ui.test reads the
    // same semantics tree TalkBack does, so this is a real live accessibility check
    // against a real emulator, not a static-analysis proxy for one. See
    // androidTest/.../FocusOrderTest.kt, the iOS equivalent of
    // ios/App/UITests/FocusOrderTests.swift.
    // The compose-bom platform must be applied per-configuration -- declaring it
    // once under implementation doesn't cover androidTestImplementation, which
    // otherwise fails to resolve ui-test-junit4's version at all.
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    // 3.7.0, not 3.5.1 (2026-07-11): 3.5.1's InputManagerEventInjectionStrategy
    // reflectively calls the hidden android.hardware.input.InputManager.getInstance()
    // -- removed/renamed by the real emulator's API 36 (Android 16) platform,
    // confirmed via a live NoSuchMethodException on that exact call. 3.7.0 targets
    // newer platforms correctly.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
