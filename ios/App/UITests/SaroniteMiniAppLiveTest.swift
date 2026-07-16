//
//  SaroniteMiniAppLiveTest.swift
//  Real end-to-end XCUITest coverage of the granite mini-app host -- the CocoaPods/Tuist
//  bridge (see docs/ARCHITECTURE.md's mini-app host row) makes the RN/Fabric/Hermes runtime
//  buildable and launchable; this proves a real mini-app screen actually renders real
//  backend data. Same live-driving discipline as MoneyFlowLiveTests.swift: log in for real,
//  tap for real, screenshot for real, against a live simulator + a real services/backend
//  instance + a real Metro dev server serving packages/saronite/host-app's real JS bundle.
//
//  REAL, LIVE-VERIFIED END TO END (2026-07-16 → 2026-07-17), after a real, multi-pass
//  investigation that found and fixed six real bugs, the last three uncovering a single
//  definitive root cause:
//
//  1. Missing native pods (react-native-safe-area-context/screens/svg were never added --
//     this Podfile is hand-written, not RN-CLI-autolinked).
//  2. `RCTDevLoadingView`'s "Loading from Metro..." overlay only hides on
//     `RCTJavaScriptDidLoadNotification`, posted only by the legacy `RCTCxxBridge` path --
//     the real bridgeless `RCTInstance` this app's `RCTHost` uses posts a differently-named
//     real notification instead. Disabled the overlay; the bundle was loading fine
//     underneath the whole time.
//  3. `RCTHost.start()` alone left a correctly-sized root view with zero subviews forever --
//     `RCTReactNativeFactory.startReactNativeWithModuleName:inWindow:` (RN's real documented
//     brownfield entry point) is what actually attaches a real Fabric component tree.
//  4. **The definitive root cause**: `react-native-safe-area-context`'s and
//     `react-native-screens`' own podspecs gate their *real* Fabric component source files
//     behind `ENV['RCT_NEW_ARCH_ENABLED'] == '1'` checked at `pod install` time -- a
//     Ruby/CocoaPods-time env var never set, completely separate from the
//     `OTHER_CPLUSPLUSFLAGS`/`OTHER_SWIFT_FLAGS` build-time flags `use_react_native!` already
//     set correctly. Without it, both podspecs silently compiled only their legacy
//     (pre-Fabric) classes. Fixed by setting `ENV['RCT_NEW_ARCH_ENABLED'] = '1'` at the top
//     of `ios/Podfile`, before any pod is evaluated.
//  5. Even with the real Fabric classes compiled, nothing told Fabric's runtime component
//     registry about them -- `RCTReactNativeFactoryDelegate`'s optional
//     `thirdPartyFabricComponents` override (which the official app template always
//     implements, returning `pod install`'s own codegen'd `RCTThirdPartyComponentsProvider`)
//     was never implemented, so Fabric silently fell back to
//     `RCTLegacyViewManagerInteropComponentView`'s dynamic legacy-view-manager discovery --
//     for the *wrong*, non-Fabric-aware version of these components, which is what produced
//     every "unrecognized selector" crash this investigation hit. Fixed in
//     `SaroniteHost.swift`. `import ReactCodegen` doesn't build from Swift *or* from a plain
//     `.m` file (its headers transitively fail to find `<memory>` in Clang's whole-module
//     compilation context) -- routed through `SaroniteBrickBridge`'s pure `NSClassFromString`
//     + selector-based runtime reflection instead, sidestepping the header import entirely.
//  6. `react-native-safe-area-context`'s `SafeAreaProvider` withholds all children until its
//     first `onInsetsChange` event fires (`{insets != null ? children : null}`) -- once the
//     real Fabric component (from fix 4/5) is what's actually instantiated, its real,
//     Fabric-native event emission fires this correctly on its own. No JS-side
//     `initialMetrics` fallback patch was needed after all -- that was chasing a symptom of
//     fixes 4/5 still being missing, not a separate root cause.
//
//  Net result: `react-native-safe-area-context+5.6.2.patch`'s legacy `onInsetsChange`
//  removal is no longer needed (the real Fabric component doesn't use that code path at
//  all) and was reverted. Real content renders: a real bill list
//  (`REG - Electricity 35,000 RWF`, `WASAC - Water 8,500 RWF`) matching the real backend's
//  actual seeded response exactly, confirmed via a direct `curl` cross-check against
//  `GET /api/v1/bills/pending`, not just a screenshot.
//
//  Honest remaining gap in *this test file*, not the app: automating a tap on the real
//  "Pay" button hit real XCUITest-specific friction (ambiguous label matches against the
//  main app's own persistent tab bar "Pay" tab still present underneath the sheet; a
//  `app.snapshot()`-derived coordinate that found the right element but didn't reliably
//  land the tap). The `payBill()` round trip itself is the same, already-real, already-
//  tested code path every other real payment flow in this app uses (`SaroniteBrownfieldModule
//  .payBill` → real `URLSession` → `POST /api/v1/bills/pay`) -- not re-verified live here,
//  but not a new risk either. A future pass can pick up the coordinate-mapping investigation
//  where this one left off rather than re-deriving it from scratch.
//
//  Added 2026-07-16, root-caused and closed 2026-07-17.
//

import XCTest

final class SaroniteMiniAppLiveTest: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    private func login(_ app: XCUIApplication) {
        let phoneField = app.textFields["Phone number"]
        if phoneField.waitForExistence(timeout: 5) {
            phoneField.tap()
            phoneField.typeText("+250788123456")
            let passwordField = app.secureTextFields["Password"]
            passwordField.tap()
            passwordField.typeText("password123")
            app.buttons["Log in"].tap()
        }
    }

    private func attachScreenshot(named name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    /// Real end-to-end proof: opens the granite-hosted pay-bills mini-app and confirms real
    /// bill data from the real backend renders -- not a placeholder, not a crash. The exact
    /// values asserted here (`REG - Electricity`, `35,000 RWF`, `WASAC - Water`) are the real
    /// backend's actual seeded response, independently cross-checked live via a direct
    /// `curl GET /api/v1/bills/pending` against the same running backend this test drives.
    func testPayBillsMiniAppRendersRealBackendData() throws {
        let app = XCUIApplication()
        app.launch()
        login(app)

        let tabBar = app.tabBars.firstMatch
        XCTAssertTrue(tabBar.waitForExistence(timeout: 15), "Tab bar should appear after login")

        tabBar.buttons["All"].tap()
        let payBillsRow = app.staticTexts["Pay bills"]
        XCTAssertTrue(payBillsRow.waitForExistence(timeout: 10), "'All' tab should show the Mini apps section with a Pay bills row")
        attachScreenshot(named: "01-all-tab")

        // Plain `.tap()` reported "Not hittable" on a real run (element exists in the
        // accessibility tree with real on-screen coordinates, but XCUITest's hit-test
        // resolution failed to compute a tappable point after auto-scroll) -- a coordinate
        // tap bypasses that heuristic and hits the real screen point directly, same
        // workaround class as MoneyFlowLiveTests.swift already documents for a different
        // SwiftUI/XCUITest quirk in this toolchain.
        payBillsRow.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()

        // Real RN/Fabric mount + a real GET /api/v1/bills/pending round trip.
        let regBill = app.staticTexts["REG - Electricity"]
        XCTAssertTrue(regBill.waitForExistence(timeout: 20), "Real bill data from the real backend should render")
        XCTAssertTrue(app.staticTexts["35,000 RWF"].exists, "Real bill amount should render")
        XCTAssertTrue(app.staticTexts["WASAC - Water"].exists, "Second real bill should also render")
        attachScreenshot(named: "02-real-bill-list")

        XCTAssertTrue(app.state == .runningForeground, "App should still be running, not crashed")
    }
}
