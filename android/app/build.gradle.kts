plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

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

    // Project Modules
    implementation(project(":core:designsystem"))
    implementation(project(":core:risk"))
    implementation(project(":core:identity"))
    implementation(project(":features:payments:impl"))
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
    // Old Native Modules API, no autolinking/codegen -- see MiniAppActivity.kt and
    // ItundaApplication.kt for why this is a deliberately manual, minimal integration
    // rather than pulling in the full React Native Gradle plugin.
    // Downgraded from 0.80.3 (2026-07-10): that version's core bridge init
    // unconditionally dlopen()s libreact_featureflagsjni.so, which the AAR
    // ships only as C++ headers for -- the actual .so is normally produced by
    // the official React Native Gradle plugin's own CMake step, which this
    // deliberately manual/plugin-free integration does not run. Verified live
    // on-device: the app installs and launches, but crashes with
    // UnsatisfiedLinkError the moment a mini-app Activity initializes RN.
    // 0.72.x predates that mandatory native build step.
    implementation("com.facebook.react:react-android:0.72.17")
    implementation("com.facebook.react:hermes-android:0.72.17")
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
