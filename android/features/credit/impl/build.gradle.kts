plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "rw.itunda.feature.credit.impl"
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

// Real content moved in 2026-09-02 (see docs/ARCHITECTURE.md / CLAUDE.md's own
// note on this) -- LoansScreen.kt/CreditScoreScreen.kt/StudentLoanScreen.kt/
// VupLoanScreen.kt/LoansCreditPanels.kt relocated here from :app's own ui/
// package, mirroring iOS's already-real Features/Credit split. Zero behavior
// change -- these files had zero rw.itunda.app.* imports before the move,
// only rw.itunda.core.designsystem/rw.itunda.core.network, so this was a pure
// package-declaration + Gradle-module move, not a decomposition.
dependencies {
    implementation(project(":features:credit:api"))
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
}
