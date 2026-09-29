plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "rw.itunda.core.network"
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

// Real shared networking layer (2026-07-22) -- the actual NetworkClient/ApiService/
// TokenStore/SessionManager/etc. moved here from :app so real Feature modules
// (:features:marketplace:impl and beyond) can depend on real network access directly,
// instead of needing :app (which would create a forbidden Feature->App->Feature
// cycle, forcing every extracted Feature into an unwieldy "dumb view with one
// callback per API call" shape). Versions mirror :app's own exactly -- this is a
// relocation, not a library upgrade.
dependencies {
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // Real FCM token registration (2026-08-12) -- see SessionManager.currentPushToken's
    // own doc comment. Play-services-backed Task<String>.await() extension, the
    // standard way to bridge FirebaseMessaging's Task API into a suspend function
    // without hand-rolling a suspendCancellableCoroutine wrapper.
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
}
