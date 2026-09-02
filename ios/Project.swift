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
        Target.target(
            name: "Feature\(name)Interface",
            destinations: .iOS,
            product: .framework,
            bundleId: "rw.itunda.feature.\(name.lowercased()).interface",
            infoPlist: .default,
            sources: ["Features/\(name)/Interface/Sources/**"],
            dependencies: []
        ),
        Target.target(
            name: "Feature\(name)",
            destinations: .iOS,
            product: .framework,
            bundleId: "rw.itunda.feature.\(name.lowercased())",
            infoPlist: .default,
            sources: ["Features/\(name)/Sources/**"],
            dependencies: [
                .target(name: "Feature\(name)Interface")
            ] + dependencies
        ),
        Target.target(
            name: "Feature\(name)Testing",
            destinations: .iOS,
            product: .framework,
            bundleId: "rw.itunda.feature.\(name.lowercased()).testing",
            infoPlist: .default,
            sources: ["Features/\(name)/Testing/Sources/**"],
            dependencies: [
                .target(name: "Feature\(name)Interface")
            ]
        ),
        Target.target(
            name: "Feature\(name)Tests",
            destinations: .iOS,
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
            Target.target(
                name: "Feature\(name)Example",
                destinations: .iOS,
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
        Target.target(
            name: "Core\(core)",
            destinations: .iOS,
            product: .framework,
            bundleId: "rw.itunda.core.\(core.lowercased())",
            infoPlist: .default,
            sources: ["Core/\(core)/Sources/**"],
            // Real bug found 2026-07-20 by actually launching ItundaRiderApp on a
            // Simulator: CoreDesignSystem/Sources/SDUI/SduiRenderer.swift imports
            // CoreNetwork directly, but this target declared zero dependencies --
            // it only ever "worked" for ItundaApp because that target separately,
            // redundantly declares CoreNetwork itself (see featureDependencies
            // below), so CoreNetwork.framework happened to already be embedded in
            // its bundle. Any new target depending on CoreDesignSystem alone (the
            // new ItundaRiderApp/ItundaMerchantApp targets) crashed at launch with
            // a real dyld "Library not loaded: @rpath/CoreNetwork.framework" error
            // -- CoreNetwork.framework was never embedded since Tuist only embeds
            // a target's own declared dependencies. Fixed at the source (declare
            // CoreDesignSystem's own real dependency) rather than patching every
            // consumer to redundantly re-declare it.
            dependencies: core == "DesignSystem" ? [.target(name: "CoreNetwork")] : []
        )
    )
}

let featureDependencies: [TargetDependency] = [
    .target(name: "CoreDesignSystem"),
    .target(name: "CoreNetwork"),
    .target(name: "CoreIdentity")
]

// "Maps" added 2026-08-19 -- reopens the multi-agent isolation initiative for iOS
// (Android's own :features:maps was already extracted 2026-07-23, see
// android/features/maps/impl's own header comment). MapScreenView.swift/
// RecentMapSearchesStore.swift moved from App/Sources -- confirmed via a real
// dependency audit before moving anything (matching Android's own precedent): neither
// depends on RouteMiniMap/LiveRiderMiniMap/SimpleLiveRiderMiniMap (still App/Sources-
// only, consumed by EatsScreen/HoodScreen/ShopScreen, which are themselves still
// un-extracted App-level screens -- promoting those into a shared Core module is a
// separate, not-yet-needed step, same reasoning Android's own RouteMiniMap ->
// core/designsystem promotion only happened once a real cross-Feature need existed).
// No MainViewModel/AppState/@EnvironmentObject coupling, no injected callback needed
// (unlike Android's real onOrderDelivery callback for the Delivery pill) -- this
// extraction is fully self-contained.
// "Certificate"/"Identity"/"Support" added 2026-08-30 -- real, sourced Toss precedent
// (toss.tech/article/slash23-iOS's own example Microfeature list names "본인확인"
// (identity verification) as its own standalone module, not folded into a generic
// bucket) -- one module per real feature, matching Toss's own granularity, rather
// than guessing a shared "Engagement" home for three unrelated concerns. `Identity`
// here means KYC personal-identity submission, a different concern than
// `CoreIdentity`'s device/biometric identity -- kept as two separate modules
// deliberately, same real distinction Toss's own device-binding vs.
// 본인확인(identity verification) draw.
// "Home" added 2026-09-02 -- iOS parity pass for Android's own :features:home:impl
// extraction (see [[project_itunda_feature_isolation]]). HomeTabContent.swift moved
// from App/Sources, same confirmed-self-contained-after-decoupling pattern "Maps"
// used above: was @ObservedObject-coupled to BankViewModel (App/Sources-only, the
// same App-target-local view-model Android's own MainViewModel decoupling
// addressed), narrowed to plain params. DiscoverRowData (was FeatureBanking-only)
// promoted to CoreDesignSystem instead of FeatureHome depending on FeatureBanking
// directly, which scripts/ios-silo-boundary-check.py forbids -- no extra
// cross-feature dependency needed, just the standard featureDependencies set.
// "Pay" added 2026-09-02 -- iOS parity pass continued (see "Home"'s own comment
// above). PayScreen (App/Sources/PayHomeExtras.swift) + ShopPay.swift/
// CouponBoxScreenView.swift/MembershipScreenView.swift moved. Real blockers found:
// CardScreenView (shared with ContentView.swift/BenefitsShopAllScreens.swift, stays
// in :App) and SupportScreenView (FeatureSupport's own Sources, not Interface --
// a direct import would be a forbidden cross-Feature dependency) both switched to
// generic @ViewBuilder injection, matching this codebase's own established
// `<Content: View>` pattern (RoomLockGate/EatsOrderRow/CommerceOrderRow); Saronite's
// reward-tasks mini-app (:App-only) switched to a plain onOpenRewardsMiniApp
// callback, matching FeatureAssets' OverviewScreenView's own onOpenRewards
// precedent exactly.
// "Menu" added 2026-09-02 -- iOS parity pass continued (see "Home"/"Pay"'s own
// comments above), matching Android's own already-real :features:menu:impl
// exactly. EntireMenuScreen (App/Sources/BenefitsShopAllScreens.swift) is a
// catalog of ~45 destinations spanning nearly every product in the app -- unlike
// Home/Pay's handful of App-only dependencies, a generic-ViewBuilder-injection
// signature here would need 45+ type parameters, far less readable than the
// alternative. Converted every destination to a plain `onOpenX: () -> Void`
// callback instead, mirroring Android's real MenuScreen.kt signature exactly
// (same real screen, same real architecture on both platforms) -- ContentView.swift
// now owns every destination's state + presentation, the same role
// ItundaAppScreen.kt already plays on Android.
// "My" added 2026-09-02 -- iOS parity pass CLOSES here (see "Home"/"Pay"/"Menu"'s
// own comments above), matching Android's own already-real :features:my:impl.
// MyTabView (App/Sources/BenefitsShopAllScreens.swift) was the cleanest of the
// four: only plain `onSwitchToX: () -> Void` callbacks, no App-only screen type
// injected directly. Real blockers found anyway: SessionManager/AuthResult (the
// app's own central session/auth orchestrator, tied to ItundaApp.swift's own
// lifecycle -- not a design-system component) replaced with a plain
// `onUpdatePin: (String, String) async -> String?` callback on the moved
// PinUpgradeCard; AccountPinPad (shared with LoginScreen, which stays in :App)
// promoted to CoreDesignSystem; WishlistHeart (shared with 6 other App-only
// screens) and its whole ItundaFaceHearts.swift file promoted too -- the doc
// comment there had explicitly deferred this exact move "because... iOS hasn't
// split those domains into separate Feature modules yet"; TalkScreen.errorMessage
// (App-only, 23 other real callers) got its own local per-file copy, same
// established convention as this file's own formatAmount duplication.
let featureModules = ["Payments", "Bills", "Merchant", "Credit", "Wealth", "Insurance", "Engagement", "Assets", "Banking", "Maps", "Certificate", "Identity", "Support", "Home", "Pay", "Menu", "My"]
for feature in featureModules {
    allTargets.append(contentsOf: makeMicroFeature(name: feature, dependencies: featureDependencies))
}

allTargets.append(
    Target.target(
        name: "ItundaPaymentsSDK",
        destinations: .iOS,
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
// BenefitsShopAllScreens.swift) -- those screens use IDS/IdsPalette design tokens
// directly, same as ContentView.swift's own Dynamic Type fix needed UIKit, but this
// one's a real cross-module type dependency, not just an inline helper.
appDependencies.append(.target(name: "CoreDesignSystem"))
// Added 2026-07-12 for TransferFlowContainer.swift's real biometric confirm gate
// (NIDABiometricAuth) -- App needs its own direct dependency declared, not just
// transitive access through FeaturePayments's own internal use of the same module.
appDependencies.append(.target(name: "CoreIdentity"))
// Added 2026-07-23: NetworkClient.swift/KeychainTokenStore.swift promoted from App
// to CoreNetwork (App/Sources -> Core/Network/Sources), mirroring Android's
// ApiService relocation ([[itunda-feature-isolation]]) -- Feature modules can't
// depend back on App, so any Feature that calls the network needs NetworkClient to
// live somewhere Features can reach. App itself was previously getting CoreNetwork
// only transitively (via CoreDesignSystem's own dependency on it); declare it
// directly rather than relying on transitive linking, the same lesson the
// CoreDesignSystem->CoreNetwork dependency comment above already documents.
appDependencies.append(.target(name: "CoreNetwork"))

allTargets.append(
    Target.target(
        name: "ItundaApp",
        destinations: .iOS,
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
            // Real "my location" blue dot on the Map screen (2026-07-19), runtime-
            // requested via CLLocationManager, never assumed granted -- see
            // MapScreenView.swift's own doc comment.
            "NSLocationWhenInUseUsageDescription": "itunda uses your real location to show it on the map and give you directions.",
            // `itunda://maps` opens Itunda's authenticated, self-hosted map from
            // another Itunda surface or a partner app. Do not advertise unsupported
            // route/place parameters before their complete contract exists.
            "CFBundleURLTypes": [[
                "CFBundleURLSchemes": ["itunda"],
            ]],
            // Real camera QR scanning (product-feel/Pay-parity port, §237) -- first
            // camera capability anywhere in this app target. Without this key iOS
            // crashes immediately on the first AVCaptureDevice access rather than
            // showing a permission prompt; matches bank-mfe's/Android's own real
            // "point your camera at the merchant's QR code" copy.
            "NSCameraUsageDescription": "itunda uses your camera to scan a merchant's payment QR code.",
            // Real NFC "collect a transit fare by tapping a rider's phone" (2026-08-27,
            // direct user follow-up: "for simplification we need nfc") -- see
            // TransitCollectScreenView.swift's own doc comment. iOS can only ever be
            // the reader side (third-party card emulation is Apple-restricted to
            // Apple Pay/Wallet), so this app only ever needs NFCTagReaderSession, not
            // HostApduService's iOS equivalent, which doesn't exist for third-party
            // apps. select-identifiers must list the exact same self-assigned AID
            // Android's apduservice.xml/TransitHceService.kt declare -- Apple requires
            // every ISO 7816 AID a reader session may select to be pre-declared here.
            "NFCReaderUsageDescription": "itunda uses NFC to collect a transit fare when a rider taps their phone.",
            "com.apple.developer.nfc.readersession.iso7816.select-identifiers": ["F04954554E4441"],
            // Real typeface fix (2026-08-13, direct user feedback: "we are still far away
            // from toss") -- see Android's identical Pretendard.kt for the full sourced
            // account (github.com/orioncactus/pretendard, SIL Open Font License 1.1). iOS
            // was rendering every IDS.Typography style in the plain system font
            // (UIFont.systemFont, confirmed via grep before this fix, not an Android-only
            // gap). UIAppFonts must list every embedded font file by its real filename --
            // iOS won't discover bundled TTFs on its own the way it does with `resources:`
            // for other asset types.
            "UIAppFonts": [
                "Pretendard-Regular.ttf",
                "Pretendard-Medium.ttf",
                "Pretendard-SemiBold.ttf",
                "Pretendard-Bold.ttf",
            ],
        ]),
        sources: ["App/Sources/**"],
        // Real app icon (2026-08-22, direct user identity work): the app previously
        // had no AppIcon.appiconset at all -- ItundaApp built and ran fine with
        // Xcode/Tuist's blank default, so the gap was never caught by a build
        // failure. "petal" shape in indigo, same mark as Android's
        // ic_launcher_foreground.xml and web's favicon.svg -- see
        // App/Resources/Assets.xcassets/AppIcon.appiconset and
        // project_itunda_brand_identity.md for the full derivation.
        resources: ["App/Resources/Fonts/**", "App/Resources/Assets.xcassets/**"],
        // Real code-signing entitlement NFCTagReaderSession requires (2026-08-27) --
        // see the infoPlist select-identifiers comment above for the full account.
        // Without this, an NFCTagReaderSession fails to start on a real device even
        // with NFCReaderUsageDescription present; that key alone covers the older
        // NDEF-reading API, not ISO 7816 tag polling.
        entitlements: .dictionary([
            "com.apple.developer.nfc.readersession.formats": ["TAG"],
        ]),
        dependencies: appDependencies,
        // Real granite mini-app host (2026-07-16, see App/Sources/Saronite/) needs one
        // small ObjC helper (SaroniteBrickBridge.m) for two RN-internal APIs Swift's
        // ClangImporter can't see directly (RCTHost's real type, RCTModuleRegistry's
        // undocumented -moduleForName:) -- Tuist generates the Xcode project
        // non-interactively, so there's no Xcode UI prompt to auto-wire a bridging header
        // the way adding an ObjC file via Xcode normally would; set explicitly instead.
        settings: .settings(base: [
            "SWIFT_OBJC_BRIDGING_HEADER": "App/Sources/Saronite/Itunda-Bridging-Header.h",
            "ASSETCATALOG_COMPILER_APPICON_NAME": "AppIcon",
        ])
    )
)

// Real standalone delivery-rider app (2026-07-20) -- a rider's own app, separate
// installable target/bundle id (rw.itunda.rider), matching how Coupang Eats' rider
// app is a distinct product from the consumer app rather than a mode inside it.
// Same Tuist project/workspace as ItundaApp (simplest path to a second real .app
// product without standing up a second Tuist project), but does NOT depend on
// ItundaApp or pull in any Podfile entry -- CocoaPods integration is per-target and
// the Podfile below has no `target 'ItundaRiderApp'` block, so this target gets
// none of ItundaApp's RN/Saronite/MapLibre pods. Only depends on CoreDesignSystem
// for shared visual tokens, mirroring Android's own :riderapp -> :core:designsystem
// dependency choice exactly.
allTargets.append(
    Target.target(
        name: "ItundaRiderApp",
        destinations: .iOS,
        product: .app,
        bundleId: "rw.itunda.rider",
        infoPlist: .extendingDefault(with: [
            "NSAppTransportSecurity": [
                "NSAllowsArbitraryLoads": true,
            ],
            "NSLocationWhenInUseUsageDescription": "Itunda Rider uses your real location so buyers can see you're on the way and to rank nearby deliveries.",
        ]),
        sources: ["RiderApp/Sources/**"],
        dependencies: [
            .target(name: "CoreDesignSystem"),
        ]
    )
)

// Real standalone merchant/POS app (2026-07-20) -- a shop owner's own app, the
// second slice of the "dedicated app per role" effort (rider app already done).
// Same rationale as ItundaRiderApp: same workspace, no dependency on ItundaApp, no
// Podfile entry (so none of its RN/Saronite/MapLibre pods), only CoreDesignSystem.
allTargets.append(
    Target.target(
        name: "ItundaMerchantApp",
        destinations: .iOS,
        product: .app,
        bundleId: "rw.itunda.merchant",
        infoPlist: .extendingDefault(with: [
            "NSAppTransportSecurity": [
                "NSAllowsArbitraryLoads": true,
            ],
        ]),
        sources: ["MerchantApp/Sources/**"],
        dependencies: [
            .target(name: "CoreDesignSystem"),
        ]
    )
)

// Real standalone agent-operator app (item 131) -- the physical cash-in/cash-out till
// operator's own app, the third slice of the "dedicated app per role" effort (rider
// and merchant apps already done). An agent operator is assigned via ops-mfe's Agents
// tab (item 129), then signs in here with their existing itunda account -- this app
// has no register/onboarding screen of its own, matching RiderApp's own precedent.
// Same rationale as ItundaRiderApp/ItundaMerchantApp: same workspace, no dependency
// on ItundaApp, no Podfile entry, only CoreDesignSystem. Ported from Android's own
// :agentapp module, which already had this real cash-in/cash-out/till-count feature
// set built and working.
allTargets.append(
    Target.target(
        name: "ItundaAgentApp",
        destinations: .iOS,
        product: .app,
        bundleId: "rw.itunda.agent",
        infoPlist: .extendingDefault(with: [
            "NSAppTransportSecurity": [
                "NSAllowsArbitraryLoads": true,
            ],
            // Added 2026-08-30 (no-manual-code-UX sweep) alongside AgentApp's first
            // camera capability -- CashOperationScreen's own QrScanCameraView.
            "NSCameraUsageDescription": "itunda Agent uses your camera to scan a customer's withdrawal code QR.",
        ]),
        sources: ["AgentApp/Sources/**"],
        dependencies: [
            .target(name: "CoreDesignSystem"),
        ]
    )
)

// Added 2026-07-11 to actually check accessibility focus order (docs/
// ACCESSIBILITY.md's one remaining open item) against the app's real accessibility
// tree via XCUITest -- the same underlying tree VoiceOver reads -- rather than
// leaving it as "no live device, can't check" indefinitely. See
// App/UITests/FocusOrderTests.swift.
allTargets.append(
    Target.target(
        name: "ItundaAppUITests",
        destinations: .iOS,
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
