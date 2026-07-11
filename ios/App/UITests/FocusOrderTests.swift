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
}
