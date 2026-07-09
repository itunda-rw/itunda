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

        vectorDrawables {
            useSupportLibrary = true
        }
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
    implementation("androidx.activity:activity-compose:1.8.2")
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

    // Project Modules
    implementation(project(":core:designsystem"))
    implementation(project(":core:risk"))
    implementation(project(":core:identity"))
    implementation(project(":features:banking:impl"))
    implementation(project(":features:payments:impl"))

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
}
