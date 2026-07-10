import ProjectDescription

func makeMicroFeature(
    name: String,
    dependencies: [TargetDependency] = []
) -> [Target] {
    return [
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
    ]
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
