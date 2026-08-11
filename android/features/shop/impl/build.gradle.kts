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
    implementation("androidx.activity:activity-compose:1.8.2")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Real camera-based QR scanning (2026-08-11) -- lets a customer scan a merchant's
    // own itunda://pay?intentId= QR directly instead of typing the payment code (see
    // PayByCodeCard's own doc comment for that typed flow, still kept as the fallback
    // when a camera isn't usable). Same CameraX + ML Kit combination merchantapp's
    // CameraQrScanner.kt already established for the reverse direction (merchant
    // scanning a customer's code) -- this app has its own copy rather than a shared
    // module, same "duplicate for money-critical safety, separate apps" precedent
    // PayQrCodeUtil.kt/QrCodeUtil.kt already set.
    // 1.4.2, not 1.3.4: CameraX 1.3.x ships libimage_processing_util_jni.so built
    // without 16KB-page alignment (real, confirmed via readelf on our own APK
    // 2026-08-12) -- Google fixed this starting 1.4.0.
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
}
