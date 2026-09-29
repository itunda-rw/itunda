plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "rw.itunda.feature.shop.impl"
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":features:shop:api"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))

    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation(libs.androidx.activity.compose)

    implementation(libs.retrofit)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.coil.compose)

    // Real camera-based QR scanning (2026-08-11) -- lets a customer scan a merchant's
    // own itunda://pay?intentId= QR directly instead of typing the payment code (see
    // PayByCodeCard's own doc comment for that typed flow, still kept as the fallback
    // when a camera isn't usable). `CameraQrScanner` itself moved to
    // :core:designsystem (item 244, 2026-08-21) once :features:talk:impl needed the
    // identical capability for Open Chat -- this module now only calls the shared
    // composable (see the CameraQrScanner import in ShopPayCards.kt), so the
    // CameraX/ML Kit dependencies themselves live in :core:designsystem's own
    // build.gradle.kts instead of being redeclared here.
}
