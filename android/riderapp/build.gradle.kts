plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "rw.itunda.rider"
    compileSdk = 34

    defaultConfig {
        // A real distinct app -- distinct applicationId, own listing, own install,
        // matching how a real Coupang Eats rider installs a separate app from the
        // one their customers use. Not a build flavor of :app.
        applicationId = "rw.itunda.rider"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Same real base-URL override mechanism :app/build.gradle.kts already
        // established -- override for a physical device with e.g.
        // `-PapiBaseUrl=http://192.168.0.63:4001/`.
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

    // Networking -- a real, minimal client scoped to exactly what a rider needs
    // (auth + rw.itunda.eats' rider endpoints + notifications), not a dependency on
    // :app's own much larger ApiService (an application module can't depend on
    // another application module anyway).
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    // Real session storage, mirroring :app's own TokenStore.kt exactly.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Real GPS: both the rider's own live location push (POST /riders/location,
    // POST /orders/{id}/rider-location while on an active delivery) and the
    // distance-to-restaurant/customer shown on each delivery card.
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Real shared design tokens (Tds) -- the same visual language the consumer app
    // uses, reused rather than re-invented for a second app on the same product.
    implementation(project(":core:designsystem"))
}
