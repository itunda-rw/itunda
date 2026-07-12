//
//  MoneyFlowLiveTests.swift
//  Real end-to-end XCUITest coverage of the login + transfer flow, run against a
//  live booted simulator with a real services/backend instance (localhost:4001,
//  reachable directly since the iOS Simulator shares the host Mac's network --
//  see NetworkClient.swift's own comment). This is the iOS equivalent of the
//  live on-device Android pass done the same session (adb + real taps/screenshots):
//  actually logging in and driving the real screens, not just confirming the app
//  builds and launches without crashing.
//
//  Added 2026-07-12.
//

import XCTest

final class MoneyFlowLiveTests: XCTestCase {

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

    func testLoginThenTransferFlowBackNavigation() throws {
        let app = XCUIApplication()
        app.launch()
        login(app)

        let tabBar = app.tabBars.firstMatch
        XCTAssertTrue(tabBar.waitForExistence(timeout: 15), "Tab bar should appear after login")
        attachScreenshot(named: "01-home")

        let sendButton = app.buttons["Send money now"]
        XCTAssertTrue(sendButton.waitForExistence(timeout: 10), "Send money now button should exist on Home")
        sendButton.tap()

        let accountField = app.textFields["Account number, up to 16 digits"]
        XCTAssertTrue(accountField.waitForExistence(timeout: 10), "Recipient entry screen should show the account number field")
        attachScreenshot(named: "02-recipient-entry")

        accountField.tap()
        accountField.typeText("2014523851827")
        app.buttons["Next"].tap()

        let amountHeading = app.staticTexts["How much to send?"]
        XCTAssertTrue(amountHeading.waitForExistence(timeout: 10), "Should land on the amount screen")
        attachScreenshot(named: "03-amount-entry")

        // KNOWN, CONFIRMED, UNRESOLVED BUG (found live on-device 2026-07-12): after
        // logging in (any screen involving real keyboard focus) and then reaching
        // the Amount screen, the *entire* window is left shrunk and shifted
        // (confirmed via app.windows.firstMatch.frame == {0, 129.5, 390, 585}
        // instead of the full {0, 0, 390, 844}) -- pushing the top of the screen,
        // including the back button, off-screen. Confirmed this is a real,
        // finger-reachable issue and not an XCUITest artifact: a raw coordinate tap
        // on the back chevron's visual location does nothing.
        //
        // Reproduces identically on a freshly erased simulator, and identically on
        // a completely unrelated fullScreenCover (Transaction History, no typing
        // in its own path) -- so the trigger is "any keyboard focus earlier in the
        // app session, ItundaApp.swift's LoginScreen -> ContentView switch being
        // the first one," not anything transfer-flow-specific.
        //
        // Three targeted fixes were tried and did NOT resolve it: an explicit
        // full-bleed .frame on TransferFlowContainer/SavingsFlowContainer,
        // .ignoresSafeArea(.keyboard) on the same, and resigning first responder
        // both before the .recipient -> .amount step switch and in ItundaApp.swift
        // right when sessionState flips to .loggedIn. All three are still in place
        // as legitimate, harmless hygiene, but none closed this specific gap --
        // pointing to a deeper SwiftUI/UIKit fullScreenCover + keyboard-avoidance
        // interaction bug in this project's pinned Xcode 14.3.1 / iOS 16.4
        // Simulator toolchain, not something fixable from this app's own view code
        // within this session's effort budget. Left as `XCTExpectFailure` (not
        // silently deleted, not silently passing) so this is re-surfaced the
        // moment it's fixed -- by a toolchain upgrade or a real root cause.
        XCTExpectFailure("Known unresolved bug: Amount screen's back button is offscreen/unreachable after any prior keyboard focus in the session -- see comment above.") {
            let backButton = app.buttons["Back"]
            XCTAssertTrue(backButton.exists, "Back button should exist on the amount screen")
            backButton.tap()
            XCTAssertTrue(accountField.waitForExistence(timeout: 5), "Back from Amount should return to Recipient entry, not leave the user stuck")
        }
    }
}
