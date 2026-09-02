plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.facebook.react")
}

// Real FCM push (2026-08-12) -- see ItundaMessagingService.kt's own doc comment for
// the full account of why FCM is the transport (Android has no OS-sanctioned
// alternative for waking a backgrounded app -- this is a platform constraint, not a
// vendor choice; every real Android app, itunda included, sits on top of it). The
// google-services plugin needs a real google-services.json downloaded from the
// Firebase Console (Project Settings -> General -> Your apps -> itunda, applicationId
// rw.itunda.app) -- something only the project owner can generate, not something this
// build can fabricate. Applied conditionally so the build stays green either way:
// without the file, this app compiles and runs exactly as before (PushConfig.kt's
// backend counterpart falls back the same way); with it, FCM lights up with zero
// further code changes.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
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

    // Real fix (2026-08-09), found while verifying the release-build R8/minify change below:
    // release builds (never exercised by CI, which only runs assembleDebug -- see
    // .github/workflows/ci-cd.yml) failed with "Couldn't determine Hermesc location" --
    // node_modules/react-native/sdks/hermesc/ genuinely doesn't exist in this environment
    // (the prebuilt-binary download step react-native's own postinstall normally runs never
    // completed here). A real, working universal (arm64+x86_64) hermesc binary already exists
    // a few packages over though, pulled in as a transitive dependency of the RN toolchain
    // (`hermes-compiler`) -- point at it, but only as a fallback so a future environment where
    // the real sdks/hermesc download succeeds isn't silently overridden by this local path.
    val defaultHermescDir = file("../../packages/saronite/node_modules/react-native/sdks/hermesc")
    val fallbackHermesc = file("../../packages/saronite/node_modules/hermes-compiler/hermesc/osx-bin/hermesc")
    if (!defaultHermescDir.exists() && fallbackHermesc.exists()) {
        hermesCommand = fallbackHermesc.absolutePath
    }
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

    // Real Android 16KB memory-page-size fix (2026-08-12) -- found live via the
    // real "Android 앱 호환성" system dialog on the physical test device: the
    // default NDK this project resolved to (no ndkVersion was ever pinned, so AGP
    // fell back to whatever's locally cached -- confirmed r27.0.12077973, no r28+
    // present) does not produce 16KB-page-aligned native libraries by default.
    // r28 is the first NDK release where 16KB alignment is the actual default
    // (developer.android.com/16kb-page-size); r29 (installed here) supersedes it.
    // Pinned here AND in the root build.gradle.kts's subprojects block below so
    // every autolinked React Native native module (react-native-screens/-svg/
    // -safe-area-context, Hermes, JSI/libfbjni) and itunda's own native
    // dependencies (MapLibre, ML Kit barcode-scanning) all rebuild against the
    // same, real 16KB-compliant toolchain -- a mismatched NDK across modules
    // would silently re-introduce the exact misalignment this fixes.
    ndkVersion = "29.0.14206865"

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
        // internal-only Multipass bridge address, unreachable for any device other
        // than the Mac itself or another device on the same bridge -- a real device
        // testing over the public HTTPS endpoint got a permanently blank map with no
        // way to fix it short of a rebuild. Mirrors bank-mfe's own
        // VITE_TILES_BASE_URL/VITE_GLYPHS_BASE_URL env vars (same LAN default, same
        // override mechanism), so both platforms follow the same real pattern.
        //
        // Address corrected 2026-07-27: itunda-dc-b (192.168.252.3, the address these
        // defaults used to point at) was decommissioned -- the surviving sole node is
        // itunda-dc-a, 192.168.252.4. bank-mfe's own equivalent was fixed the same day
        // by switching to a same-origin relative path through the public nginx proxy,
        // but that trick doesn't exist for a native client that always calls a fixed
        // base URL -- this default stays a LAN address (matching apiBaseUrl's own
        // dev-convenience default above), just the current correct one.
        buildConfigField(
            "String",
            "TILES_BASE_URL",
            "\"${project.findProperty("tilesBaseUrl") ?: "http://192.168.252.4:8090"}\""
        )
        buildConfigField(
            "String",
            "GLYPHS_BASE_URL",
            "\"${project.findProperty("glyphsBaseUrl") ?: "http://192.168.252.4:8091"}\""
        )
    }

    buildTypes {
        release {
            // Real Toss-parity performance/security fix (2026-08-09): this was `false` with a
            // proguard-rules.pro reference that didn't even exist as a file -- meaning release
            // builds shipped completely unshrunk and unobfuscated. See proguard-rules.pro's own
            // header for why Gson needed explicit keep rules before this could be flipped on.
            isMinifyEnabled = true
            isShrinkResources = true
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

    // Real FCM push client (2026-08-12) -- see ItundaMessagingService.kt's own doc
    // comment. Safe to compile/run even without google-services.json applied above:
    // every call site into FirebaseMessaging guards on FirebaseApp.getApps(context)
    // being non-empty first, so an unconfigured build just no-ops push registration
    // rather than crashing.
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging-ktx")

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
    implementation(project(":features:maps:impl"))
    // Real content moved in 2026-09-02: LoansScreen.kt/CreditScoreScreen.kt/
    // StudentLoanScreen.kt/VupLoanScreen.kt/LoansCreditPanels.kt relocated from
    // :app's own ui/ package into :features:credit:impl, mirroring iOS's
    // already-real Features/Credit split (see CLAUDE.md's own note).
    implementation(project(":features:credit:impl"))
    // Real content moved in 2026-09-02 (Banking Feature-module decomposition slice
    // 5, following the 2026-07-11 note this comment used to carry): the note above
    // rejected re-adding BankScreen.kt/MySpendingScreen.kt, which were genuinely
    // superseded duplicates -- this is a different move, relocating the ALREADY-
    // canonical BankHubScreen/NewSavingsGoalDialog (and their supporting
    // ShellRow/ShellSection/RoundUpSettingsDialog) out of ItundaAppScreen.kt into
    // their own Feature module, not recreating a deleted duplicate. See
    // [[project_itunda_feature_isolation]] for the full account.
    implementation(project(":features:banking:impl"))
    // Real content moved in 2026-09-02 (Home Feature-module decomposition, following
    // the user's explicit "Continue into HomeTab next" direction): unlike Banking/
    // Credit, Home had no pre-existing empty scaffold on either platform -- it's
    // genuinely cross-vertical (pulls content from Marketplace/Community/Jobs/
    // Property), so this is a NEW module, not filling in an already-signaled one.
    // See [[project_itunda_feature_isolation]] for the full account.
    implementation(project(":features:home:impl"))
    // Real content moved in 2026-09-02 (Pay Feature-module decomposition, same
    // "Continue into HomeTab next" scope, which also covered Pay/Menu/My): PayTab
    // and its 6 supporting screens (MyPaymentCodeCard/AccountCardCarousel/
    // PayHomeExtras/PayMoneyDetailScreen/CouponBoxScreen/MembershipScreen). Three
    // real prerequisites resolved first -- see [[project_itunda_feature_isolation]]
    // -- before this dependency could be added cleanly.
    implementation(project(":features:pay:impl"))
    // Real content moved in 2026-09-02 (Menu Feature-module decomposition, same
    // scope as Home/Pay): MenuScreen and its supporting IconGridSection/AllTopBar/
    // MenuSearchBar/menuSections. 5 rw.itunda.app.miniapps.* Activity/loader
    // references replaced with injected callbacks -- see
    // [[project_itunda_feature_isolation]].
    implementation(project(":features:menu:impl"))

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
    // Real customer-presented payment code QR/barcode rendering (2026-08-11, barcode
    // added item 242) -- ZXing core only, same as merchantapp's own QR generation
    // (QrCodeUtil.kt).
    implementation("com.google.zxing:core:3.5.3")

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
