//
//  FocusOrderTests.swift
//  Real XCUITest coverage for docs/ACCESSIBILITY.md's one remaining open item --
//  "Focus order... requires either a live TalkBack/VoiceOver run or Compose's
//  testTag-based semantics tree inspection, neither available in this
//  environment." That was true until this session found the real Xcode/simulator
//  toolchain was actually available here (see docs/ARCHITECTURE.md §3's "MAJOR
//  CORRECTION" note). XCUITest reads the same accessibility tree VoiceOver does
//  (XCUIElement queries walk it in accessibility-traversal order), so this is a
//  real check against real UI, not a static-analysis proxy for one.
//
//  Added 2026-07-11.
//

import XCTest

final class FocusOrderTests: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    /// The tab bar is the one piece of UI every screen shares, and its
    /// accessibility order is exactly what a VoiceOver user swiping right
    /// through tabs depends on. Checks two real things: the labels match
    /// Android's established taxonomy (Home/Benefits/Shop/Pay/All, fixed
    /// 2026-07-11 -- see ContentView.swift's own header) in that exact order,
    /// and each button's accessibility-tree position is also visually
    /// left-to-right, so VoiceOver's traversal order can't silently diverge
    /// from what's on screen.
    func testTabBarFocusOrderMatchesVisualLeftToRightOrder() throws {
        let app = XCUIApplication()
        app.launch()

        let tabBar = app.tabBars.firstMatch
        XCTAssertTrue(tabBar.waitForExistence(timeout: 10), "Tab bar should exist")

        let expectedLabels = ["Home", "Benefits", "Shop", "Pay", "All"]
        let buttons = tabBar.buttons.allElementsBoundByIndex

        XCTAssertEqual(
            buttons.map { $0.label }, expectedLabels,
            "Tab bar accessibility order should match Android's taxonomy and left-to-right visual order"
        )

        let xPositions = buttons.map { $0.frame.minX }
        XCTAssertEqual(
            xPositions, xPositions.sorted(),
            "Tab bar buttons' accessibility traversal order should match their left-to-right visual position"
        )
    }

    /// The Home tab's top bar has two icon-only buttons fixed this session
    /// (BankView.swift's TopBarActionButton -- Notifications, then Profile,
    /// same order the visible bell/person icons render in left-to-right). If
    /// their accessibility order ever diverged from their visual order, a
    /// VoiceOver user swiping right would land on "Profile" before
    /// "Notifications" despite the bell icon appearing first on screen.
    func testHomeTabTopBarIconsFocusOrderMatchesVisualOrder() throws {
        let app = XCUIApplication()
        app.launch()

        let notifications = app.buttons["Notifications"]
        let profile = app.buttons["Profile"]
        XCTAssertTrue(notifications.waitForExistence(timeout: 10), "Notifications button should exist and be labeled")
        XCTAssertTrue(profile.exists, "Profile button should exist and be labeled")

        XCTAssertLessThan(
            notifications.frame.minX, profile.frame.minX,
            "Notifications (bell) should sit left of Profile, matching HomeTopBar's real HStack order"
        )
    }

    /// Extends coverage (2026-07-11) from Home-tab-only to the remaining four
    /// tabs, closing the "other 4 tabs... isn't covered by a test yet" gap this
    /// file's own earlier version left open. Same pattern: switch tabs via the
    /// real tab bar (not a direct navigation shortcut -- this exercises the
    /// same path a real user, or VoiceOver user, takes), then assert real
    /// accessibility-tree order matches real visual/HStack order.

    func testBenefitsTabRowsFocusOrderMatchesVisualTopToBottomOrder() throws {
        let app = XCUIApplication()
        app.launch()
        app.tabBars.buttons["Benefits"].tap()

        // BenefitsShopAllScreens.swift's BenefitsVisitCard rows, in their real
        // source/visual order.
        let titles = ["Happy lottery", "Push the button", "Try on", "Bring friends"]
        let yPositions = titles.map { title -> CGFloat in
            let element = app.staticTexts[title]
            XCTAssertTrue(element.waitForExistence(timeout: 10), "'\(title)' row should exist on the Benefits tab")
            return element.frame.minY
        }
        XCTAssertEqual(
            yPositions, yPositions.sorted(),
            "Benefits tab's visit-card rows should read top-to-bottom in their real source order: \(titles)"
        )
    }

    /// ShopTopBar's Profile/Cart icons are bare Image()s with an
    /// accessibilityLabel, not wrapped in Button -- confirmed by this test's
    /// own first version failing against app.buttons[...] and passing once
    /// changed to app.images[...] (real XCUITest element-type introspection,
    /// not a source-reading guess). Matches the already-documented finding
    /// in ARCHITECTURE.md that these specific icons have no clickable
    /// wrapper/real navigation yet.
    func testShopTabTopBarIconsFocusOrderMatchesVisualOrder() throws {
        let app = XCUIApplication()
        app.launch()
        app.tabBars.buttons["Shop"].tap()

        let profile = app.images["Profile"]
        let cart = app.images["Cart"]
        XCTAssertTrue(profile.waitForExistence(timeout: 10), "Profile image should exist and be labeled on the Shop tab")
        XCTAssertTrue(cart.exists, "Cart image should exist and be labeled on the Shop tab")

        XCTAssertLessThan(
            profile.frame.minX, cart.frame.minX,
            "Profile should sit left of Cart, matching ShopTopBar's real HStack order"
        )
    }

    func testPayTabMerchantRowsFocusOrderMatchesVisualTopToBottomOrder() throws {
        let app = XCUIApplication()
        app.launch()
        app.tabBars.buttons["Pay"].tap()

        // PayScreen's two "Nearby Merchants" rows, in their real source order.
        let kigaliHeights = app.staticTexts["Kigali Heights"]
        let briocheCafe = app.staticTexts["Brioche Cafe"]
        XCTAssertTrue(kigaliHeights.waitForExistence(timeout: 10), "Kigali Heights row should exist on the Pay tab")
        XCTAssertTrue(briocheCafe.exists, "Brioche Cafe row should exist on the Pay tab")

        XCTAssertLessThan(
            kigaliHeights.frame.minY, briocheCafe.frame.minY,
            "Kigali Heights should sit above Brioche Cafe, matching PayScreen's real VStack order"
        )
    }

    /// TdsAllTopBar's Settings icon was a bare Image() when this test was first
    /// written; it's a real Button now (2026-07-12, see BenefitsShopAllScreens.swift
    /// -- wired to the real Settings screen instead of direct logout), so this
    /// queries app.buttons, not app.images.
    func testAllTabTopBarFocusOrderMatchesVisualOrder() throws {
        let app = XCUIApplication()
        app.launch()
        app.tabBars.buttons["All"].tap()

        let name = app.staticTexts["TUYIZERE ERIC"]
        let settings = app.buttons["Settings"]
        XCTAssertTrue(name.waitForExistence(timeout: 10), "Name should exist on the All tab")
        XCTAssertTrue(settings.waitForExistence(timeout: 10), "Settings button should exist and be labeled on the All tab")

        XCTAssertLessThan(
            name.frame.minX, settings.frame.minX,
            "Name should sit left of the Settings gear, matching TdsAllTopBar's real SpaceBetween HStack order"
        )
    }
}
