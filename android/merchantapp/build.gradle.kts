plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "rw.itunda.merchant"
    compileSdk = 34

    defaultConfig {
        // A real distinct app -- distinct applicationId, own listing, own install,
        // matching how a real merchant/POS app is a separate product from the
        // consumer app and from :riderapp, not a build flavor of :app.
        applicationId = "rw.itunda.merchant"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${project.findProperty("apiBaseUrl") ?: "http://10.0.2.2:4001/"}\"",
        )
    }

    buildTypes {
        release {
            // Real Toss-parity performance/security fix (2026-08-09), same as :app -- see
            // proguard-rules.pro's own header for why Gson needed explicit keep rules first.
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
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Networking -- a real, minimal client scoped to exactly what a merchant/POS app
    // needs (rw.itunda.merchant's own endpoints + rw.itunda.eats' restaurant-order
    // endpoints), not a dependency on :app's own much larger ApiService.
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Real QR code rendering for the register/POS checkout flow -- itunda://pay?intentId=
    // encoded as a real scannable QR bitmap for a customer's own itunda app to scan, same
    // payload convention merchant-mfe's web POS screen already established. Pure encoding
    // (ZXing core only, no camera/scanning dependency needed here).
    implementation("com.google.zxing:core:3.5.3")

    // Real camera-based QR scanning (2026-08-11) -- the customer-presented payment
    // code flow (see ApiService.kt's own chargeByCustomerCode doc comment) is the
    // FIRST real camera-scanning capability anywhere in this monorepo, on any
    // platform/app -- confirmed via a full-repo audit before building this. CameraX
    // for the real camera preview/frame pipeline, ML Kit Barcode Scanning for
    // on-device (no network round-trip, no per-scan cost) QR decoding -- the
    // standard modern Android combination, not a third-party scanning SDK.
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    implementation(project(":core:designsystem"))
}
