import XCTest

/// Deterministic accessibility checks for the real primary navigation.
/// These tests do not require a running backend: they inspect the SwiftUI shell
/// with a simulator-only test launch argument, never a production auth bypass.
final class FocusOrderTests: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @discardableResult
    private func launchHomeForNavigationTest(_ app: XCUIApplication) -> XCUIElement {
        app.launchArguments = ["-ui-testing-authenticated"]
        app.launch()
        let tabBar = app.tabBars.firstMatch
        XCTAssertTrue(tabBar.waitForExistence(timeout: 10), "Primary tab bar should be visible")
        return tabBar
    }

    func testTabBarFocusOrderMatchesVisualLeftToRightOrder() throws {
        let app = XCUIApplication()
        let tabBar = launchHomeForNavigationTest(app)

        let expectedLabels = ["Home", "Pay", "Explore", "Messages", "You"]
        let buttons = tabBar.buttons.allElementsBoundByIndex
        XCTAssertEqual(
            buttons.map { $0.label },
            expectedLabels,
            "Primary tabs should be exposed to VoiceOver in the same order they appear visually"
        )

        let xPositions = buttons.map { $0.frame.minX }
        XCTAssertEqual(
            xPositions,
            xPositions.sorted(),
            "Tab traversal order should match the left-to-right visual order"
        )
    }

    func testHomeHeaderIsAccessible() throws {
        let app = XCUIApplication()
        _ = launchHomeForNavigationTest(app)

        XCTAssertTrue(
            app.staticTexts["itunda"].waitForExistence(timeout: 10),
            "The Home tab should expose the itunda heading to accessibility services"
        )
    }

    func testPayTabCanBeSelected() throws {
        assertTabCanBeSelected("Pay")
    }

    func testExploreTabCanBeSelected() throws {
        assertTabCanBeSelected("Explore")
    }

    func testMessagesTabCanBeSelected() throws {
        assertTabCanBeSelected("Messages")
    }

    func testYouTabCanBeSelected() throws {
        assertTabCanBeSelected("You")
    }

    private func assertTabCanBeSelected(
        _ label: String,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let app = XCUIApplication()
        let tabBar = launchHomeForNavigationTest(app)
        let tab = tabBar.buttons[label]
        XCTAssertTrue(tab.waitForExistence(timeout: 5), "\(label) tab should exist", file: file, line: line)
        tab.tap()
        XCTAssertTrue(tab.isSelected, "\(label) should be selected after tapping it", file: file, line: line)
    }
}
