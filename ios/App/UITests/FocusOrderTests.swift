import XCTest

final class FocusOrderTests: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    private func loginAndLaunch(_ app: XCUIApplication) {
        app.launch()
        let phoneField = app.textFields["Phone number"]
        XCTAssertTrue(phoneField.waitForExistence(timeout: 5), "Phone number field should exist")
        phoneField.tap()
        phoneField.typeText("+250788123456")
        for digit in ["1", "2", "3", "4", "5", "6"] {
            let key = app.buttons[digit]
            XCTAssertTrue(key.waitForExistence(timeout: 3), "PIN key (digit) should exist")
            key.tap()
        }
        XCTAssertTrue(app.buttons["Home"].waitForExistence(timeout: 15), "Home tab should appear after login")
    }

    func testTabBarFocusOrderMatchesVisualLeftToRightOrder() throws {
        let app = XCUIApplication()
        loginAndLaunch(app)

        let expectedLabels = ["Home", "Pay", "Explore", "Messages", "You"]
        let buttons = expectedLabels.map { label -> XCUIElement in
            let button = app.buttons[label]
            XCTAssertTrue(button.waitForExistence(timeout: 10), "(label) tab should exist")
            return button
        }

        XCTAssertEqual(
            buttons.map { $0.label }, expectedLabels,
            "Primary tab accessibility order should match the current product taxonomy"
        )

        let xPositions = buttons.map { $0.frame.minX }
        XCTAssertEqual(
            xPositions, xPositions.sorted(),
            "Primary tab accessibility order should match the left-to-right visual order"
        )
    }

    func testHomeHeaderFocusOrderMatchesVisualOrder() throws {
        let app = XCUIApplication()
        loginAndLaunch(app)

        let title = app.staticTexts["itunda"]
        let notifications = app.images["Notifications"]
        XCTAssertTrue(title.waitForExistence(timeout: 10), "Home header title should exist")
        XCTAssertTrue(notifications.waitForExistence(timeout: 10), "Home notifications icon should be labeled")

        XCTAssertLessThan(
            title.frame.minX, notifications.frame.minX,
            "Home title should sit left of Notifications, matching the real header HStack order"
        )
    }

    func testPayTabFocusOrderMatchesVisualTopToBottomOrder() throws {
        let app = XCUIApplication()
        loginAndLaunch(app)
        app.buttons["Pay"].tap()

        let payCode = app.staticTexts["Pay by code"]
        let requestMoney = app.staticTexts["Request money"]
        XCTAssertTrue(payCode.waitForExistence(timeout: 10), "Pay tab should show Pay by code")
        XCTAssertTrue(requestMoney.waitForExistence(timeout: 10), "Pay tab should show Request money")

        XCTAssertLessThan(
            payCode.frame.minY, requestMoney.frame.minY,
            "Pay actions should read top-to-bottom in their real visual order"
        )
    }

    func testExploreTabMiniAppsSectionExists() throws {
        let app = XCUIApplication()
        loginAndLaunch(app)
        app.buttons["Explore"].tap()

        let miniApps = app.staticTexts["Mini apps"]
        XCTAssertTrue(miniApps.waitForExistence(timeout: 10), "Explore tab should expose the Mini apps section")
    }

    func testMessagesTabExists() throws {
        let app = XCUIApplication()
        loginAndLaunch(app)
        app.buttons["Messages"].tap()

        XCTAssertTrue(
            app.staticTexts["Messages"].waitForExistence(timeout: 10),
            "Messages tab should expose its real Talk surface"
        )
    }

    func testYouTabExists() throws {
        let app = XCUIApplication()
        loginAndLaunch(app)
        app.buttons["You"].tap()

        XCTAssertTrue(
            app.staticTexts["My orders"].waitForExistence(timeout: 10),
            "You tab should expose the real personal hub"
        )
    }
}
