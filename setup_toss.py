import os
import shutil

# 1. Android settings.gradle.kts
android_settings_content = """pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "itunda"

include(":app")

// Core Bounded Contexts
include(":core:designsystem")
include(":core:network")
include(":core:testing")
include(":core:identity")
include(":core:consent")
include(":core:ledger")
include(":core:risk")

// Feature Bounded Contexts
include(":features:payments:api")
include(":features:payments:impl")
include(":features:payments:testing")

include(":features:bills:api")
include(":features:bills:impl")
include(":features:bills:testing")

include(":features:merchant:api")
include(":features:merchant:impl")
include(":features:merchant:testing")

include(":features:credit:api")
include(":features:credit:impl")
include(":features:credit:testing")

include(":features:wealth:api")
include(":features:wealth:impl")
include(":features:wealth:testing")

include(":features:insurance:api")
include(":features:insurance:impl")
include(":features:insurance:testing")

include(":features:engagement:api")
include(":features:engagement:impl")
include(":features:engagement:testing")

include(":features:assets:api")
include(":features:assets:impl")
include(":features:assets:testing")

include(":features:banking:api")
include(":features:banking:impl")
include(":features:banking:testing")

// Itunda Pay SDK (For 3rd party integrations)
include(":sdk:pay")
"""

# 2. iOS Project.swift
ios_project_content = """import ProjectDescription

func makeMicroFeature(
    name: String,
    dependencies: [TargetDependency] = []
) -> [Target] {
    return [
        Target(
            name: "Feature\\(name)Interface",
            platform: .iOS,
            product: .framework,
            bundleId: "rw.itunda.feature.\\(name.lowercased()).interface",
            infoPlist: .default,
            sources: ["Features/\\(name)/Interface/Sources/**"],
            dependencies: []
        ),
        Target(
            name: "Feature\\(name)",
            platform: .iOS,
            product: .framework,
            bundleId: "rw.itunda.feature.\\(name.lowercased())",
            infoPlist: .default,
            sources: ["Features/\\(name)/Sources/**"],
            dependencies: [
                .target(name: "Feature\\(name)Interface")
            ] + dependencies
        ),
        Target(
            name: "Feature\\(name)Testing",
            platform: .iOS,
            product: .framework,
            bundleId: "rw.itunda.feature.\\(name.lowercased()).testing",
            infoPlist: .default,
            sources: ["Features/\\(name)/Testing/Sources/**"],
            dependencies: [
                .target(name: "Feature\\(name)Interface")
            ]
        ),
        Target(
            name: "Feature\\(name)Tests",
            platform: .iOS,
            product: .unitTests,
            bundleId: "rw.itunda.feature.\\(name.lowercased()).tests",
            infoPlist: .default,
            sources: ["Features/\\(name)/Tests/**"],
            dependencies: [
                .target(name: "Feature\\(name)"),
                .target(name: "Feature\\(name)Testing")
            ]
        ),
        Target(
            name: "Feature\\(name)Example",
            platform: .iOS,
            product: .app,
            bundleId: "rw.itunda.feature.\\(name.lowercased()).example",
            infoPlist: .default,
            sources: ["Features/\\(name)/Example/Sources/**"],
            dependencies: [
                .target(name: "Feature\\(name)"),
                .target(name: "Feature\\(name)Testing")
            ]
        )
    ]
}

var allTargets: [Target] = []

let coreModules = ["DesignSystem", "Network", "Testing", "Identity", "Consent", "Ledger", "Risk"]
for core in coreModules {
    allTargets.append(
        Target(
            name: "Core\\(core)",
            platform: .iOS,
            product: .framework,
            bundleId: "rw.itunda.core.\\(core.lowercased())",
            infoPlist: .default,
            sources: ["Core/\\(core)/Sources/**"],
            dependencies: []
        )
    )
}

let featureDependencies: [TargetDependency] = [
    .target(name: "CoreDesignSystem"),
    .target(name: "CoreNetwork"),
    .target(name: "CoreIdentity")
]

let featureModules = ["Payments", "Bills", "Merchant", "Credit", "Wealth", "Insurance", "Engagement", "Assets", "Banking"]
for feature in featureModules {
    allTargets.append(contentsOf: makeMicroFeature(name: feature, dependencies: featureDependencies))
}

allTargets.append(
    Target(
        name: "ItundaPaySDK",
        platform: .iOS,
        product: .framework,
        bundleId: "rw.itunda.sdk.pay",
        infoPlist: .default,
        sources: ["SDK/Pay/Sources/**"],
        dependencies: []
    )
)

var appDependencies: [TargetDependency] = []
for feature in featureModules {
    appDependencies.append(.target(name: "Feature\\(feature)"))
}

allTargets.append(
    Target(
        name: "ItundaApp",
        platform: .iOS,
        product: .app,
        bundleId: "rw.itunda.app",
        infoPlist: .default,
        sources: ["App/Sources/**"],
        dependencies: appDependencies
    )
)

let project = Project(
    name: "Itunda",
    targets: allTargets
)
"""

android_build_template = """plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "rw.itunda.{namespace}"
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
"""

def main():
    print("Setting up Toss Architecture...")

    # Write settings files
    with open("android/settings.gradle.kts", "w") as f:
        f.write(android_settings_content)

    with open("ios/Project.swift", "w") as f:
        f.write(ios_project_content)

    # Clean old core modules if we are fully replacing
    if os.path.exists("android/core"):
        shutil.rmtree("android/core")
    if os.path.exists("ios/Core"):
        shutil.rmtree("ios/Core")

    # Android setup
    core_modules = ["designsystem", "network", "testing", "identity", "consent", "ledger", "risk"]
    for m in core_modules:
        d = f"android/core/{m}"
        os.makedirs(d, exist_ok=True)
        with open(f"{d}/build.gradle.kts", "w") as f:
            f.write(android_build_template.replace("{namespace}", f"core.{m}"))
            
    feature_modules = ["payments", "bills", "merchant", "credit", "wealth", "insurance", "engagement", "assets", "banking"]
    for f_mod in feature_modules:
        for sub in ["api", "impl", "testing"]:
            d = f"android/features/{f_mod}/{sub}"
            os.makedirs(d, exist_ok=True)
            with open(f"{d}/build.gradle.kts", "w") as f:
                f.write(android_build_template.replace("{namespace}", f"feature.{f_mod}.{sub}"))

    # iOS setup
    ios_core_modules = ["DesignSystem", "Network", "Testing", "Identity", "Consent", "Ledger", "Risk"]
    for m in ios_core_modules:
        d = f"ios/Core/{m}/Sources"
        os.makedirs(d, exist_ok=True)
        with open(f"{d}/Dummy.swift", "w") as f:
            f.write(f"// Dummy file for {m}")

    ios_feature_modules = ["Payments", "Bills", "Merchant", "Credit", "Wealth", "Insurance", "Engagement", "Assets", "Banking"]
    for f_mod in ios_feature_modules:
        for sub in ["Interface/Sources", "Sources", "Testing/Sources", "Tests", "Example/Sources"]:
            d = f"ios/Features/{f_mod}/{sub}"
            os.makedirs(d, exist_ok=True)
            with open(f"{d}/Dummy.swift", "w") as f:
                f.write(f"// Dummy file for {f_mod} {sub}")

    print("Architecture generated successfully!")

if __name__ == "__main__":
    main()
