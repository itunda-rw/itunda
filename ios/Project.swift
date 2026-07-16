import Foundation
import ProjectDescription

func makeMicroFeature(
    name: String,
    dependencies: [TargetDependency] = []
) -> [Target] {
    // Fixed (2026-07-11): a real `xcodebuild` run against the `Itunda-Workspace`
    // aggregate scheme found every Feature*Example target fails to link ("entry
    // point (_main) undefined") except Banking/Payments -- each is declared as a
    // runnable .app product below, but 7 of 9 feature modules only have a
    // placeholder Dummy.swift in Example/Sources/, with no real @main. Rather than
    // hand-maintain a list that can drift from reality, check the filesystem for
    // real content (more than a lone Dummy.swift) the same way this manifest
    // already declares `sources:` globs against the real directory tree --
    // Tuist manifests are just Swift code the tuist binary evaluates, so this is a
    // real filesystem check at generate time, not a guess.
    let exampleSourcesDir = "Features/\(name)/Example/Sources"
    let exampleFiles = (try? FileManager.default.contentsOfDirectory(atPath: exampleSourcesDir)) ?? []
    let hasRealExampleContent = exampleFiles.contains { $0 != "Dummy.swift" }

    var targets: [Target] = [
        Target(
            name: "Feature\(name)Interface",
            platform: .iOS,
            product: .framework,
            bundleId: "rw.itunda.feature.\(name.lowercased()).interface",
            infoPlist: .default,
            sources: ["Features/\(name)/Interface/Sources/**"],
            dependencies: []
        ),
        Target(
            name: "Feature\(name)",
            platform: .iOS,
            product: .framework,
            bundleId: "rw.itunda.feature.\(name.lowercased())",
            infoPlist: .default,
            sources: ["Features/\(name)/Sources/**"],
            dependencies: [
                .target(name: "Feature\(name)Interface")
            ] + dependencies
        ),
        Target(
            name: "Feature\(name)Testing",
            platform: .iOS,
            product: .framework,
            bundleId: "rw.itunda.feature.\(name.lowercased()).testing",
            infoPlist: .default,
            sources: ["Features/\(name)/Testing/Sources/**"],
            dependencies: [
                .target(name: "Feature\(name)Interface")
            ]
        ),
        Target(
            name: "Feature\(name)Tests",
            platform: .iOS,
            product: .unitTests,
            bundleId: "rw.itunda.feature.\(name.lowercased()).tests",
            infoPlist: .default,
            sources: ["Features/\(name)/Tests/**"],
            dependencies: [
                .target(name: "Feature\(name)"),
                .target(name: "Feature\(name)Testing")
            ]
        ),
    ]

    if hasRealExampleContent {
        targets.append(
            Target(
                name: "Feature\(name)Example",
                platform: .iOS,
                product: .app,
                bundleId: "rw.itunda.feature.\(name.lowercased()).example",
                infoPlist: .default,
                sources: ["Features/\(name)/Example/Sources/**"],
                dependencies: [
                    .target(name: "Feature\(name)"),
                    .target(name: "Feature\(name)Testing")
                ]
            )
        )
    }

    return targets
}

var allTargets: [Target] = []

let coreModules = ["DesignSystem", "Network", "Testing", "Identity", "Consent", "Ledger", "Risk"]
for core in coreModules {
    allTargets.append(
        Target(
            name: "Core\(core)",
            platform: .iOS,
            product: .framework,
            bundleId: "rw.itunda.core.\(core.lowercased())",
            infoPlist: .default,
            sources: ["Core/\(core)/Sources/**"],
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
        name: "ItundaPaymentsSDK",
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
    appDependencies.append(.target(name: "Feature\(feature)"))
}
appDependencies.append(.target(name: "CoreRisk"))
// Added 2026-07-11 for the Benefits/Shop/All tab rebuild (App/Sources/
// BenefitsShopAllScreens.swift) -- those screens use IDS/TdsColors design tokens
// directly, same as ContentView.swift's own Dynamic Type fix needed UIKit, but this
// one's a real cross-module type dependency, not just an inline helper.
appDependencies.append(.target(name: "CoreDesignSystem"))
// Added 2026-07-12 for TransferFlowContainer.swift's real biometric confirm gate
// (NIDABiometricAuth) -- App needs its own direct dependency declared, not just
// transitive access through FeaturePayments's own internal use of the same module.
appDependencies.append(.target(name: "CoreIdentity"))

allTargets.append(
    Target(
        name: "ItundaApp",
        platform: .iOS,
        product: .app,
        bundleId: "rw.itunda.app",
        // Real login/session flow (2026-07-11, see App/Sources/NetworkClient.swift)
        // talks to services/backend over plain HTTP -- no TLS cert to terminate
        // against for a local dev backend. Same blanket, dev-only exception
        // Android's AndroidManifest.xml already carries via
        // android:usesCleartextTraffic="true"; ATS blocks this by default otherwise.
        infoPlist: .extendingDefault(with: [
            "NSAppTransportSecurity": [
                "NSAllowsArbitraryLoads": true,
            ],
        ]),
        sources: ["App/Sources/**"],
        dependencies: appDependencies,
        // Real granite mini-app host (2026-07-16, see App/Sources/Saronite/) needs one
        // small ObjC helper (SaroniteBrickBridge.m) for two RN-internal APIs Swift's
        // ClangImporter can't see directly (RCTHost's real type, RCTModuleRegistry's
        // undocumented -moduleForName:) -- Tuist generates the Xcode project
        // non-interactively, so there's no Xcode UI prompt to auto-wire a bridging header
        // the way adding an ObjC file via Xcode normally would; set explicitly instead.
        settings: .settings(base: [
            "SWIFT_OBJC_BRIDGING_HEADER": "App/Sources/Saronite/Itunda-Bridging-Header.h",
        ])
    )
)

// Added 2026-07-11 to actually check accessibility focus order (docs/
// ACCESSIBILITY.md's one remaining open item) against the app's real accessibility
// tree via XCUITest -- the same underlying tree VoiceOver reads -- rather than
// leaving it as "no live device, can't check" indefinitely. See
// App/UITests/FocusOrderTests.swift.
allTargets.append(
    Target(
        name: "ItundaAppUITests",
        platform: .iOS,
        product: .uiTests,
        bundleId: "rw.itunda.app.uitests",
        infoPlist: .default,
        sources: ["App/UITests/**"],
        dependencies: [
            .target(name: "ItundaApp")
        ]
    )
)

let project = Project(
    name: "Itunda",
    targets: allTargets
)
