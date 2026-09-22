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
            isMinifyEnabled = false
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Networking -- a real, minimal client scoped to exactly what a rider needs
    // (auth + rw.itunda.eats' rider endpoints + notifications), not a dependency on
    // :app's own much larger ApiService (an application module can't depend on
    // another application module anyway).
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Real session storage, mirroring :app's own TokenStore.kt exactly.
    implementation(libs.androidx.security.crypto)

    // Real GPS: both the rider's own live location push (POST /riders/location,
    // POST /orders/{id}/rider-location while on an active delivery) and the
    // distance-to-restaurant/customer shown on each delivery card.
    implementation(libs.play.services.location)

    // Real shared design tokens (Tds) -- the same visual language the consumer app
    // uses, reused rather than re-invented for a second app on the same product.
    implementation(project(":core:designsystem"))
}