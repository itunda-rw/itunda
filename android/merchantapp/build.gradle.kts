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

    // Networking -- a real, minimal client scoped to exactly what a merchant/POS app
    // needs (rw.itunda.merchant's own endpoints + rw.itunda.eats' restaurant-order
    // endpoints), not a dependency on :app's own much larger ApiService.
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.security.crypto)

    // Real QR code rendering for the register/POS checkout flow -- itunda://pay?intentId=
    // encoded as a real scannable QR bitmap for a customer's own itunda app to scan, same
    // payload convention merchant-mfe's web POS screen already established. Pure encoding
    // (ZXing core only, no camera/scanning dependency needed here).
    implementation(libs.zxing.core)

    implementation(project(":core:designsystem"))
}