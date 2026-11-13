// Screenshot loop (2026-11-09): renders real itunda Compose screens to PNG on the JVM
// (Robolectric + Roborazzi, no emulator) so they can be compared against the HTML
// prototype. CI (.github/workflows/android-screenshots.yml) runs this and pushes the
// PNGs to the `ci-screenshots` branch. Test-only: nothing here ships in any APK.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi") version "1.43.0"
}

android {
    namespace = "rw.itunda.screenshots"
    compileSdk = 34
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL } }
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    implementation(project(":features:home:impl"))
    implementation(project(":features:pay:impl"))
    implementation(project(":features:menu:impl"))
    implementation(project(":features:talk:impl"))
    implementation(project(":features:my:impl"))
    implementation(project(":features:payments:impl"))
    implementation(project(":features:shop:impl"))

    testImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
