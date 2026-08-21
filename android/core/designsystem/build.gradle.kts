plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "rw.itunda.core.designsystem"
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
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation(project(":core:network")) // For SDUI models + superAppErrorMessage

    // Added 2026-07-22 for HoodShared.kt -- the cross-feature Marketplace/Community/
    // Jobs/Property UI atoms (rememberRealLocationRequester, HoodReportAction,
    // NeighborhoodSetupPrompt) relocated here from app/ui/SuperAppTabs.kt so
    // :features:marketplace:impl can share them without depending on :app.
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Added 2026-07-23 for RouteMiniMap.kt -- relocated here from app/ui/RouteMiniMap.kt
    // so Marketplace/Jobs/Property/Eats can render a real drawn route directly instead
    // of receiving it as an injected routeMiniMap callback.
    implementation("org.maplibre.gl:android-sdk:13.3.1")

    // Added 2026-08-05 for IdsAvatar.kt -- same version already used by :app and
    // :features:talk:impl, so this stays a single real dependency, not a second one.
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Added 2026-08-21 for CameraQrScanner.kt -- relocated here from
    // :features:shop:impl (item 244) once a real second Feature (:features:talk:impl,
    // for the Open Chat join-by-scan flow) needed the identical capability -- same
    // "promote once a real 2nd cross-Feature need exists" precedent RouteMiniMap/
    // HoodShared/IdsAvatar above already establish, not a premature extraction. Same
    // real CameraX + ML Kit Barcode Scanning versions merchantapp's own separate copy
    // (a deliberately different Gradle application, out of scope for this promotion)
    // already uses.
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    // Real non-money-critical QR generation (item 244) -- Open Chat's join code is
    // NOT a payment code, so the "duplicate per app for money-critical safety"
    // precedent PayQrCodeUtil.kt/QrCodeUtil.kt establish doesn't apply here; a
    // shared generator is the honest, simpler choice for this specific real use.
    implementation("com.google.zxing:core:3.5.3")
}
